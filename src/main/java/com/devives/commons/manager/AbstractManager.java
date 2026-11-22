/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.devives.commons.manager;

import com.devives.commons.lang.ExceptionUtils;
import com.devives.commons.lang.call.Try;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.devives.commons.manager.Manager.*;

/**
 * Абстрактная реализация менеджера объектов с жизненным циклом: создание, запуск, остановка, уничтожение.
 *
 * @param <K> type of key
 * @param <O> type of managed object
 * @author Vladimir Ivanov {@code <ivvlev@devives.com>}
 */
public abstract class AbstractManager<K, O> implements Manager<K, O> {

    private final Map<K, Entry<O>> entryMap_;
    private final LockSource<K> lockSource_;
    private final ManagedAdapter<O> defaultAdapter_;
    private final Hooks<K, O> hooks_;
    /**
     * Keys whose entry is in a lifecycle transition (initialization or de-initialization) currently
     * running on the calling thread.
     * <p>
     * Cross-thread access to a transitioning entry is already excluded by the per-key write lock, so this
     * per-thread registry guards the only remaining window: a reentrant operation on the same key from a
     * user callback (factory, adapter, hook or listener) running in the same thread under the same lock.
     * Such an operation would repeat an already running phase over captured references — double
     * stop/destroy, an orphaned second object, or an NPE over an entry cleared by the nested call — so it
     * is rejected with {@link ManagerException} instead.
     * <p>
     * Registration is done by the chokepoints of both transitions, {@link #doInitializeEntry(Object, Entry,
     * ObjectFactory, ManagedAdapter)} and {@link #doDeinitializeEntry(Object, Entry)}; the operation entry
     * points {@code doComputeIfAbsent}/{@code doReplace}/{@code doRemove} only check the set. Reentrant
     * reads ({@code get}/{@code getIfPresent}) are not gated. Write operations requested from the
     * {@link #onEntryGot(Entry)} notification are gated separately by {@link #entryGotCallbackDepth_}.
     */
    private final ThreadLocal<Set<K>> transitioningKeys_ = ThreadLocal.withInitial(HashSet::new);
    /**
     * Keys whose {@link #onEntryGot(Entry)} notification is currently running on the calling thread,
     * with nesting depth.
     * <p>
     * The notification runs while the calling thread already holds the key lock (a read lock on the
     * {@code get()}/{@code getIfPresent()} paths and on the entry-found branch of
     * {@code doComputeIfAbsent()}; a write lock on the entry-borrowed branch). A write operation on the
     * same key requested by user code inside the notification cannot pass the lock: under
     * {@code RWLockSource} a write-over-read request by the owning thread blocks forever
     * (self-deadlock, CM-09), while under reentrant or noop sources the same call would silently mutate
     * the entry underneath the ongoing read. The request is therefore rejected with
     * {@link ManagerException} uniformly, independently of the lock source.
     * <p>
     * A map with depth instead of a set: the notification can legitimately recurse via a reentrant
     * {@code get()}/{@code getIfPresent()} from the callback itself, and unregistering on return from the
     * nested notification must not open the gate while an outer notification of the same key is still
     * running. Reentrant reads are not gated.
     */
    private final ThreadLocal<Map<K, Integer>> entryGotCallbackDepth_ = ThreadLocal.withInitial(HashMap::new);
    /**
     * The cached values view. The field is intentionally non-volatile: done similarly to
     * {@code java.util.concurrent.ConcurrentHashMap#values()}, which caches its view fields
     * ({@code keySet}, {@code values}, {@code entrySet}) in plain transient fields as well.
     * <p>
     * This is a deliberate, analyzed trade-off ("benign data races", Java Concurrency in Practice, §16.3), not a defect.
     * <p>
     * Do not add {@code volatile} here.
     */
    private transient Collection<O> values_;
    /**
     * The cached keys view. The field is intentionally non-volatile: done similarly to
     * {@code java.util.concurrent.ConcurrentHashMap#keySet()} and {@code HashMap#keySet()}, which cache
     * their view fields in plain transient fields as well. The data race in the check-then-set idiom is
     * benign: {@code Collections.unmodifiableSet} over the live map key set view is an immutable, stateless
     * wrapper, so racing instances are behaviorally indistinguishable — the only observable outcome is
     * duplicate equivalent views, the losers are garbage-collected.
     * <p>
     * This is a deliberate, analyzed trade-off ("benign data races", Java Concurrency in Practice, §16.3), not a defect.
     * <p>
     * Do not add {@code volatile} here.
     */
    private transient Set<K> keySet_;

    protected AbstractManager(Map<K, Entry<O>> entryMap) {
        this(entryMap,
                noopLockSource(),
                noopManagedAdapter(),
                noopHooks());
    }

    protected AbstractManager(Map<K, Entry<O>> entryMap,
                              LockSource<K> lockSource,
                              ManagedAdapter<O> defaultAdapter,
                              Hooks<K, O> hooks) {
        entryMap_ = Objects.requireNonNull(entryMap, "entryMap");
        lockSource_ = Objects.requireNonNull(lockSource, "entryLockSource");
        defaultAdapter_ = Objects.requireNonNull(defaultAdapter, "defaultAdapter");
        hooks_ = Objects.requireNonNull(hooks, "hooks");
    }

    @Override
    public boolean containsKey(K key) {
        Objects.requireNonNull(key);
        return doGetIfPresent(key, false) != null;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super O> action) {
        Objects.requireNonNull(action);
        keySet().forEach((k) -> {
            O v = doGetIfPresent(k, false);
            if (v != null) {
                action.accept(k, v);
            }
        });
    }

    @Override
    public Set<K> keySet() {
        Set<K> ks = keySet_;
        if (ks == null) {
            ks = Collections.unmodifiableSet(entryMap_.keySet());
            keySet_ = ks;
        }
        return ks;
    }

    /**
     * Return unmodifiable collection of values contained in manager.
     *
     * @return Unmodifiable collection of values
     */
    public Collection<O> values() {
        Collection<O> vals = values_;
        if (vals == null) {
            vals = Collections.unmodifiableCollection(createValuesCollection(entryMap_::values));
            values_ = vals;
        }
        return vals;
    }

    protected Collection<O> createValuesCollection(Supplier<Collection<Entry<O>>> valuesSupplier) {
        return new ValuesCollection<>(valuesSupplier);
    }

    protected static class ValuesCollection<O> extends AbstractCollection<O> {

        private final Supplier<Collection<Entry<O>>> valuesSupplier_;

        public ValuesCollection(Supplier<Collection<Entry<O>>> valuesSupplier) {
            valuesSupplier_ = Objects.requireNonNull(valuesSupplier);
        }

        protected Collection<Entry<O>> values() {
            return valuesSupplier_.get();
        }

        @Override
        public Iterator<O> iterator() {
            return new ValuesIterator<>(values().iterator());
        }

        @Override
        public int size() {
            return values().size();
        }

        @Override
        public boolean contains(Object v) {
            if (v == null) {
                return false;
            }
            for (O value : this) {
                if (v.equals(value)) {
                    return true;
                }
            }
            return false;
        }

        private static class ValuesIterator<O> implements Iterator<O> {
            private final Iterator<Entry<O>> iterator_;

            public ValuesIterator(Iterator<Entry<O>> iterator) {
                iterator_ = Objects.requireNonNull(iterator);
            }

            public boolean hasNext() {
                return iterator_.hasNext();
            }

            public O next() {
                return iterator_.next().getObject();
            }
        }
    }


    @Override
    public O get(K key) throws ManagerException {
        try {
            Objects.requireNonNull(key);
            final O result = doGet(key);
            if (result == null) {
                throw new ManagerException(String.format("The manager does not contain an object with the key '%s'.", key), null);
            }
            return result;
        } catch (Exception e) {
            throw ExceptionUtils.asUnchecked(e);
        }
    }

    @Override
    public O getIfPresent(K key) {
        Objects.requireNonNull(key);
        return doGetIfPresent(key, true);
    }

    @Override
    public O computeIfAbsent(K key, Function<K, O> factory) {
        return computeIfAbsent(key, factory, getDefaultAdapter());
    }

    @Override
    public O computeIfAbsent(K key, ObjectFactory<O> factory) {
        return computeIfAbsent(key, factory, getDefaultAdapter());
    }

    @Override
    public O computeIfAbsent(K key, ManagedFactory<O> factory) {
        return computeIfAbsent(key, factory::createObject, factory);
    }

    @Override
    public O computeIfAbsent(K key, Function<K, O> factory, ManagedAdapter<O> adapter) {
        return computeIfAbsent(key, new KeyedObjectFactory<>(key, factory), adapter);
    }

    @Override
    public O computeIfAbsent(K key, ObjectFactory<O> factory, ManagedAdapter<O> adapter) {
        try {
            Objects.requireNonNull(key, "The key value is required.");
            Objects.requireNonNull(factory, "The factory value is required.");
            Objects.requireNonNull(adapter, "The adapter value is required.");
            return doComputeIfAbsent(key, factory, adapter);
        } catch (Exception e) {
            throw ExceptionUtils.asUnchecked(e);
        }
    }

    private static class KeyedObjectFactory<K, O> implements ObjectFactory<O> {

        private final K key;
        private final Function<K, O> factory;

        public KeyedObjectFactory(K key, Function<K, O> factory) {
            this.key = Objects.requireNonNull(key, "The key value is required.");
            this.factory = Objects.requireNonNull(factory, "The factory value is required.");
        }

        @Override
        public O createObject() throws Exception {
            return factory.apply(key);
        }
    }

    @Override
    public O put(K key, Function<K, O> factory) {
        return put(key, new KeyedObjectFactory<>(key, factory), getDefaultAdapter());
    }

    @Override
    public O put(K key, ObjectFactory<O> factory) {
        return put(key, factory::createObject, getDefaultAdapter());
    }

    @Override
    public O put(K key, ManagedFactory<O> factory) {
        return put(key, factory::createObject, factory);
    }

    @Override
    public O put(K key, ObjectFactory<O> factory, ManagedAdapter<O> adapter) {
        try {
            Objects.requireNonNull(key, "The key value is required.");
            Objects.requireNonNull(factory, "The factory value is required.");
            Objects.requireNonNull(adapter, "The adapter value is required.");
            return doReplace(key, factory, adapter);
        } catch (Exception e) {
            throw ExceptionUtils.asUnchecked(e);
        }
    }

    @Override
    public O remove(K key) {
        try {
            Objects.requireNonNull(key);
            return doRemove(key);
        } catch (Exception e) {
            throw ExceptionUtils.asUnchecked(e);
        }
    }

    @Override
    public List<O> removeAll() {
        return doRemoveAll();
    }

    @Override
    public void clear() {
        doClear();
    }

    @Override
    public boolean isEmpty() {
        return entryMap_.isEmpty();
    }

    @Override
    public int size() {
        return entryMap_.size();
    }

    protected O doGet(K key) {
        final Lock entryLock = acquireLock(key);
        try {
            entryLock.lockRead();
            try {
                O result = null;
                final Entry<O> entry = internalGetEntryIfPresent(key);
                if (entry != null) {
                    result = entry.getObject();
                    if (result != null) {
                        notifyEntryGot(key, entry);
                    }
                }
                return result;
            } finally {
                entryLock.unlockRead();
            }
        } finally {
            releaseLock(key);
        }
    }

    /**
     *
     * @param key    ключ объекта
     * @param notify флаг, указывает на необходимость вызова внутреннего события {@link #onEntryGot(Entry)}.
     * @return найденный объект или null.
     */
    protected O doGetIfPresent(K key, boolean notify) {
        O result = null;
        // Optimistically get entry without lock.
        final Entry<O> entry = internalGetEntryIfPresent(key);
        if (entry != null) {
            final Lock entryLock = acquireLock(key);
            try {
                entryLock.lockRead();
                try {
                    result = entry.getObject();
                    // If object non set, it's equals entry not present.
                    if (notify && result != null) {
                        notifyEntryGot(key, entry);
                    }
                } finally {
                    entryLock.unlockRead();
                }
            } finally {
                releaseLock(key);
            }
        }
        return result;
    }

    protected final O doComputeIfAbsent(final K key, final ObjectFactory<O> factory, final ManagedAdapter<O> adapter) throws Exception {
        assertKeyNotTransitioning(key);
        assertKeyNotInEntryGotCallback(key);
        O result = null;
        final Lock entryLock = acquireLock(key);
        try {
            entryLock.lockRead();
            try {
                Entry<O> entry = internalGetEntryIfPresent(key);
                if (entry == null) {
                    entryLock.upgradeLock();
                    try {
                        entry = Optional.ofNullable(internalGetEntryIfPresent(key)).orElseGet(this::doCreateEntry);
                        assert entry != null;
                        if (entry.getObjectAndAdapter() == null) {
                            doInitializeEntry(key, entry, factory, adapter);
                        } else {
                            notifyEntryGot(key, entry);
                        }
                        result = entry.getObjectAndAdapter().object;
                    } finally {
                        // Downgrade lock.
                        entryLock.downgradeLock();
                    }
                } else {
                    notifyEntryGot(key, entry);
                    result = entry.getObjectAndAdapter().object;
                }
            } finally {
                entryLock.unlockRead();
            }
        } finally {
            releaseLock(key);
        }
        return result;
    }

    private ManagedAdapter<O> getDefaultAdapter() {
        return Objects.requireNonNull(defaultAdapter_, "The default managed adapter not set. It's must be passed in to manager constructor.");
    }

    protected final Hooks<K, O> getHooks() {
        return hooks_;
    }

    /**
     * @param k the key whose associated value is to be returned
     * @return the value to which the specified key is mapped, or
     * {@code null} if this map contains no mapping for the key
     * @throws NullPointerException if the specified key is null
     * @see ConcurrentHashMap#get(Object)
     */
    protected final Entry<O> internalGetEntryIfPresent(final K k) {
        return entryMap_.get(k);
    }

    /**
     * @param k     key with which the specified value is to be associated
     * @param entry value to be associated with the specified key
     * @throws NullPointerException if the specified key or value is null
     *                              and this map does not permit null keys or values
     * @see ConcurrentHashMap#put(Object, Object)
     */
    protected final void internalPutEntry(final K k, final Entry<O> entry) {
        entryMap_.put(k, entry);
    }

    /**
     * @param k key whose mapping is to be removed from the map
     * @return the previous value associated with <code>key</code>, or
     * <code>null</code> if there was no mapping for <code>key</code>.
     * @throws NullPointerException if the specified key is null
     * @see ConcurrentHashMap#remove(Object)
     */
    protected final Entry<O> internalRemoveAndClearEntry(final K k) {
        Entry<O> entry = internalRemoveEntry(k);
        if (entry != null) {
            entry.clearObjectAndAdapter();
        }
        return entry;
    }

    protected final Entry<O> internalRemoveEntry(final K k) {
        return entryMap_.remove(k);
    }

    /**
     * Replaces the object associated with the key. The previous object, if present, is deinitialized:
     * stopped, removed from the manager and destroyed. The new object is created, started and published
     * under the key.
     * <p>
     * Similarly to {@link java.util.Map#put(Object, Object)}, the method returns the previous object.
     * The new object can be obtained by {@link #get(Object)} or {@link #getIfPresent(Object)}.
     * <p>
     * Replacement is not atomic: {@code doDeinitializeEntry} runs before {@code doInitializeEntry}.
     * If creation or startup of the new object fails, the previous object is <em>not</em> restored
     * and the {@code key} is removed from the manager; the failure is not rolled back.
     *
     * @return the previous instance of {@code O} if it was present, otherwise {@code null}.
     * @throws ManagerException if {@code key} is already in a lifecycle transition performed by the
     *                          current thread (reentrant replacement of a key being initialized or removed)
     *                          or its {@link #onEntryGot(Entry)} notification is running on the current
     *                          thread (reentrant write from the callback, see {@link #entryGotCallbackDepth_}).
     */
    protected final O doReplace(K key, ObjectFactory<O> factory, ManagedAdapter<O> adapter) throws Exception {
        assertKeyNotTransitioning(key);
        assertKeyNotInEntryGotCallback(key);
        O result = null;
        final Lock entryLock = acquireLock(key);
        try {
            entryLock.lockWrite();
            try {
                Entry<O> entry = internalGetEntryIfPresent(key);
                if (entry != null) {
                    ObjectAndAdapter<O> objectAndAdapter = entry.getObjectAndAdapter();
                    if (objectAndAdapter != null) {
                        result = objectAndAdapter.object;
                        doDeinitializeEntry(key, entry);
                    }
                }
                entry = Optional.ofNullable(entry).orElseGet(this::doCreateEntry);
                doInitializeEntry(key, entry, factory, adapter);
            } finally {
                entryLock.unlockWrite();
            }
        } finally {
            releaseLock(key);
        }
        return result;
    }

    /**
     * Creates an object, binds it to the entry, publishes the entry in the manager map,
     * starts the object, and notifies entry lifecycle callbacks.
     * If any step fails, performs rollback of the already completed initialization stages.
     *
     * @param key     entry key
     * @param entry   entry to initialize
     * @param factory object factory
     * @param adapter managed object adapter
     * @throws Exception if creation or any subsequent initialization stage fails
     */
    protected final void doInitializeEntry(K key, Entry<O> entry, ObjectFactory<O> factory, ManagedAdapter<O> adapter) throws Exception {
        beginKeyTransition(key);
        try {
            final O object = doObjectCreate(factory, adapter, key);
            Try.runnable(() -> {
                entry.initObjectAndAdapter(object, adapter);
                // Помещаю Entry в карту до вызова #doStartObject(), что бы текущий поток, при
                // рекурсивном вызове текущего метода, мог получить ссылку на этот Entry.
                internalPutEntry(key, entry);
                Try.runnable(() -> {
                    doStartObject(object, adapter);
                    Try.runnable(() -> {
                        doEntryAdded(entry);
                    }).onCatch((th) -> {
                        doStopObject(object, adapter);
                        throw th;
                    }).run();
                }).onCatch((th) -> {
                    internalRemoveAndClearEntry(key);
                    throw th;
                }).run();
            }).onCatch((th) -> {
                internalDestroyObject(object, adapter);
                throw th;
            }).run();
        } finally {
            endKeyTransition(key);
        }
    }

    protected final void doDeinitializeEntry(K key, Entry<O> entry) throws Exception {
        beginKeyTransition(key);
        try {
            final ObjectAndAdapter<O> objectAndAdapter = entry.getObjectAndAdapter();
            doEntryRemoving(entry);
            Try.runnable(() -> {
                doStopObject(objectAndAdapter.object, objectAndAdapter.adapter);
            }).doFinally(() -> {
                ExceptionUtils.collectAndThrow(
                        () -> internalRemoveAndClearEntry(key),
                        () -> internalDestroyObject(objectAndAdapter.object, objectAndAdapter.adapter)
                );
            }).run();
        } finally {
            endKeyTransition(key);
        }
    }

    protected final <E extends Entry<O>> E doCreateEntry() {
        return Objects.requireNonNull(newEntry(), String.format("The `%s#newEntry()` method did not return an instance.", getClass().getCanonicalName()));
    }

    @SuppressWarnings("unchecked")
    protected <E extends Entry<O>> E newEntry() {
        return (E) new Entry<O>();
    }

    /**
     * The method is called after a write lock is set.
     * @param key   key
     * @param entry object entry
     * @return the removed object or {@code null}.
     * @throws Exception when stopping of the object is failed.
     */
    protected final O doRemoveEntry(K key, Entry<O> entry) throws Exception {
        O result = null;
        final ObjectAndAdapter<O> objectAndAdapter = entry.getObjectAndAdapter();
        if (objectAndAdapter != null) {
            doDeinitializeEntry(key, entry);
            result = objectAndAdapter.object;
        }
        return result;
    }

    protected O doRemove(K key) throws Exception {
        assertKeyNotTransitioning(key);
        assertKeyNotInEntryGotCallback(key);
        O result = null;
        Entry<O> entry = internalGetEntryIfPresent(key);
        if (entry != null) {
            final Lock entryLock = acquireLock(key);
            try {
                entryLock.lockWrite();
                try {
                    entry = internalGetEntryIfPresent(key);
                    if (entry != null) {
                        result = doRemoveEntry(key, entry);
                    }
                } finally {
                    entryLock.unlockWrite();
                }
            } finally {
                releaseLock(key);
            }
        }
        return result;
    }

    protected List<O> doRemoveAll() {
        final List<O> removedItemList = new ArrayList<>();
        doRemoveKeys(keysForBulkRemoval(), removedItemList::add);
        return removedItemList;
    }

    protected void doClear() {
        doRemoveKeys(keysForBulkRemoval(), null);
    }

    /**
     * Источник ключей для массового удаления {@link #doRemoveAll()} и {@link #doClear()}.
     *
     * @return ключи, удаляемые операцией массового удаления.
     */
    protected abstract Iterable<K> keysForBulkRemoval();

    /**
     * Протокол массового удаления: удаляет каждый ключ через {@link #doRemove(Object)}, продолжая
     * обработку при ошибках.
     * <p>
     * {@link VirtualMachineError} прерывает операцию немедленно. Любая другая ошибка собирается в
     * {@code ManagerException("Error while removing key = '<key>'")}; единственная собранная ошибка
     * пробрасывается как есть, несколько агрегируются в
     * {@code ManagerException("Error while removing keys = [...]")} с подавленными ошибками ключей.
     * Ключи, упавшие на фазах остановки/уничтожения, всё равно удаляются с карты в finally-блоке
     * деинициализации (CM-21), поэтому бросок не оставляет таких ключей; остаётся только ключ,
     * удаление которого отклонировано хуком {@code onEntryRemoving}.
     *
     * @param keys        удаляемые ключи; допустимо live-view (см. {@link #keysForBulkRemoval()}).
     * @param removedSink приёмник удалённых объектов либо {@code null}. Приёмник обязателен только
     *                    там, где список удалённых входит в контракт ({@link #doRemoveAll()});
     *                    {@link #doClear()} передаёт {@code null}: накапливать ссылки на все
     *                    уничтоженные объекты до конца операции на больших менеджерах —
     *                    непрофильная нагрузка на память.
     */
    private void doRemoveKeys(Iterable<K> keys, Consumer<O> removedSink) {
        final List<ManagerException> exceptionList = new ArrayList<>();
        final List<K> failedKeyList = new ArrayList<>();
        for (K key : keys) {
            try {
                // The write lock will set in doRemove().
                final O item = doRemove(key);
                if (item != null && removedSink != null) {
                    removedSink.accept(item);
                }
            } catch (VirtualMachineError vme) {
                throw vme;
            } catch (Throwable e) {
                failedKeyList.add(key);
                exceptionList.add(new ManagerException("Error while removing key: '" + key + "'", e));
            }
        }
        if (exceptionList.size() == 1) {
            throw ExceptionUtils.asUnchecked(exceptionList.stream().findFirst().get());
        } else if (exceptionList.size() > 1) {
            ManagerException me = new ManagerException(String.format("Errors occurred while removing keys: %s", failedKeyList));
            exceptionList.forEach(me::addSuppressed);
            throw ExceptionUtils.asUnchecked(me);
        }
    }

    protected final Lock acquireLock(K key) {
        return lockSource_.acquire(key);
    }

    protected final void releaseLock(K key) {
        lockSource_.release(key);
    }

    /**
     * Marks the key as transitioning on the current thread for the scope of a lifecycle transition.
     *
     * @param key entry key
     * @throws ManagerException if the key is already transitioning on the current thread, i.e. the call is
     *                          a reentrant entry into a transition (see {@link #transitioningKeys_})
     */
    private void beginKeyTransition(K key) throws ManagerException {
        if (!transitioningKeys_.get().add(key)) {
            throw transitioningKeyError(key);
        }
    }

    private void endKeyTransition(K key) {
        final Set<K> keys = transitioningKeys_.get();
        keys.remove(key);
        if (keys.isEmpty()) {
            // Do not retain an empty set on pooled threads after the last transition of the scope.
            transitioningKeys_.remove();
        }
    }

    private void assertKeyNotTransitioning(K key) throws ManagerException {
        if (transitioningKeys_.get().contains(key)) {
            throw transitioningKeyError(key);
        }
    }

    private ManagerException transitioningKeyError(K key) {
        return new ManagerException(String.format(
                "The key '%s' is in a lifecycle transition performed by the current operation."
                        + " Reentrant modification of the same key from a factory, adapter, hook or listener is forbidden."
                        + " Throw an exception to cancel the ongoing operation; use get()/getIfPresent() for reentrant read access.",
                key));
    }

    /**
     * Notifies that the managed object has been obtained, guarding reentrant write operations on the
     * same key from the callback (see {@link #entryGotCallbackDepth_}).
     *
     * @param key   entry key
     * @param entry the record
     */
    private void notifyEntryGot(final K key, final Entry<O> entry) {
        beginEntryGotCallback(key);
        try {
            doEntryGot(entry);
        } finally {
            endEntryGotCallback(key);
        }
    }

    private void beginEntryGotCallback(K key) {
        entryGotCallbackDepth_.get().merge(key, 1, Integer::sum);
    }

    private void endEntryGotCallback(K key) {
        final Map<K, Integer> depths = entryGotCallbackDepth_.get();
        depths.compute(key, (k, depth) -> depth != null && depth > 1 ? depth - 1 : null);
        if (depths.isEmpty()) {
            // Do not retain an empty map on pooled threads after the last notification of the scope.
            entryGotCallbackDepth_.remove();
        }
    }

    /**
     * Rejects a write operation on a key whose {@link #onEntryGot(Entry)} notification is running on the
     * calling thread: the thread already holds the key lock, so the write could not acquire it.
     *
     * @param key entry key
     * @throws ManagerException if the notification for the key is running on the current thread
     */
    protected final void assertKeyNotInEntryGotCallback(final K key) throws ManagerException {
        if (entryGotCallbackDepth_.get().containsKey(key)) {
            throw entryGotCallbackKeyError(key);
        }
    }

    private ManagerException entryGotCallbackKeyError(K key) {
        return new ManagerException(String.format(
                "The entry-got notification for key '%s' is running on the current operation."
                        + " Write operations (computeIfAbsent/put/remove) on the same key from a hook or listener are forbidden:"
                        + " the key lock is already held by this thread and a write request would deadlock."
                        + " Throw an exception to cancel the ongoing operation; use get()/getIfPresent() for reentrant read access.",
                key));
    }

    protected final void doEntryGot(Entry<O> entry) {
        onEntryGot(entry);
    }

    /**
     * This method is called after a reference to the managed object has been successfully obtained.
     * <p>
     * At the time of invocation, a read lock is set for the record.
     * <p>
     * Write operations on the same key ({@code computeIfAbsent()}/{@code put()}/{@code remove()}) requested
     * from this notification are rejected with {@link ManagerException}: this thread already holds the key
     * lock, so a write request would deadlock under {@code RWLockSource} (CM-09). Reentrant
     * {@code get()}/{@code getIfPresent()} and operations on other keys are allowed.
     *
     * @param entry the record
     */
    protected void onEntryGot(Entry<O> entry) {

    }

    /**
     * Вызывается после старта и добавления объекта в менеджер, но перед снятием блокировки.
     *
     * @param entry added {@link Entry}
     */
    protected final void doEntryAdded(Entry<O> entry) {
        onEntryAdded(entry);
    }

    /**
     * This method is called after a record has been successfully created and the managed object has been started.
     * <p>
     * At the time of invocation, a write lock is set for the record. The current thread will be able to obtain a valid object
     * by calling {@link #get(Object)} or {@link #getIfPresent(Object)}. Other threads will wait for the lock to be released.
     * <p>
     * An exception thrown from this method rolls back the newly added record, stops and destroys the managed object,
     * and then aborts the current action.
     *
     * @param entry the record
     */
    protected void onEntryAdded(Entry<O> entry) {

    }

    protected final void doEntryRemoving(Entry<O> entry) {
        onEntryRemoving(entry);
    }

    /**
     * This method is called before stopping the managed object and deleting the record.
     * <p>
     * At the time of invocation, a write lock is set for the record.
     * <p>
     * An exception thrown from this method will stop the remove of object and stop the current action.
     * @param entry the record
     */
    protected void onEntryRemoving(Entry<O> entry) {

    }

    protected final O doObjectCreate(ObjectFactory<O> factory, ManagedAdapter<O> adapter, K key) throws Exception {
        final O object = Objects.requireNonNull(
                factory.createObject(),
                String.format("The object factory for key '%s' returned null.", key));
        Try.runnable(() -> {
            hooks_.onObjectCreated(key, object);
        }).onCatch((th) -> {
            internalDestroyObject(object, adapter);
            throw th;
        }).run();
        return object;
    }

    protected final void doStartObject(O object, ManagedAdapter<O> adapter) throws Exception {
        Try.runnable(() -> {
            hooks_.onObjectStarting(object);
            adapter.startObject(object);
            Try.runnable(() -> {
                hooks_.onObjectStarted(object);
            }).onCatch((th) -> {
                internalStopObject(object, adapter);
                throw th;
            }).run();
        }).onCatch((th) -> {
            internalFailObject(object, adapter, th);
            throw th;
        }).run();
    }

    protected final void internalFailObject(O object, ManagedAdapter<O> adapter, Throwable throwable) throws Exception {
        getHooks().onObjectFailure(object, throwable);
    }

    protected final void doStopObject(O object, ManagedAdapter<O> adapter) throws Exception {
        Try.runnable(() -> {
            internalStopObject(object, adapter);
        }).onCatch((th) -> {
            internalFailObject(object, adapter, th);
            throw th;
        }).run();
    }

    protected final void internalStopObject(final O object, final ManagedAdapter<O> adapter) throws Exception {
        ExceptionUtils.collectAndThrow(
                () -> hooks_.onObjectStopping(object),
                () -> {
                    // Исключение из адаптера будет направлено в #doObjectFailure() во внешнем методе.
                    adapter.stopObject(object);
                    hooks_.onObjectStopped(object);
                }
        );
    }

    protected final void internalDestroyObject(O object, ManagedAdapter<O> adapter) throws Exception {
        ExceptionUtils.collectAndThrow(
                () -> hooks_.onObjectDestroying(object),
                () -> adapter.destroyObject(object),
                () -> hooks_.onObjectDestroyed(object)
        );
    }

    /**
     * Immutable class for storing of the pair of objects.
     *
     * @param <O> type of managed object
     */
    protected final static class ObjectAndAdapter<O> {

        public final O object;
        public final ManagedAdapter<O> adapter;

        public ObjectAndAdapter(O object, ManagedAdapter<O> adapter) {
            this.object = Objects.requireNonNull(object);
            this.adapter = Objects.requireNonNull(adapter);
        }

    }

    protected static class Entry<O> {
        /**
         * Volatile variable for the atomic non blocking read write operations.
         */
        private volatile ObjectAndAdapter<O> objectAndAdapter_ = null;

        public O getObject() {
            final ObjectAndAdapter<O> objectAndAdapter = objectAndAdapter_;
            return objectAndAdapter != null ? objectAndAdapter.object : null;
        }

        public ObjectAndAdapter<O> getObjectAndAdapter() {
            return objectAndAdapter_;
        }

        private ObjectAndAdapter<O> initObjectAndAdapter(O object, ManagedAdapter<O> adapter) {
            final ObjectAndAdapter<O> objectAndAdapter = new ObjectAndAdapter<O>(object, adapter);
            objectAndAdapter_ = objectAndAdapter;
            return objectAndAdapter;
        }

        private void clearObjectAndAdapter() {
            objectAndAdapter_ = null;
        }

    }

}
