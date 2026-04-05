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

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
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
public abstract class AbstractManager<K, O> implements Manager<K, O>, Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<K, Entry<O>> entryMap_;
    private final LockSource<K> lockSource_;
    private final ManagedAdapter<O> defaultAdapter_;
    private final Listener<K, O> listener_;
    /**
     * Done similarly to java.util.concurrent.ConcurrentHashMap#values.
     */
    private transient Collection<O> values;

    protected AbstractManager(Map<K, Entry<O>> entryMap) {
        this(entryMap,
                noopLockSource(),
                noopManagedAdapter(),
                noopListener());
    }

    protected AbstractManager(Map<K, Entry<O>> entryMap,
                              LockSource<K> lockSource,
                              ManagedAdapter<O> defaultAdapter,
                              Listener<K, O> listener) {
        entryMap_ = Objects.requireNonNull(entryMap, "entryMap");
        lockSource_ = Objects.requireNonNull(lockSource, "entryLockSource");
        defaultAdapter_ = Objects.requireNonNull(defaultAdapter, "defaultAdapter");
        listener_ = Objects.requireNonNull(listener, "listener");
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
        return Collections.unmodifiableSet(entryMap_.keySet());
    }

    /**
     * Return unmodifiable collection of values contained in manager.
     *
     * @return Unmodifiable collection of values
     */
    public Collection<O> values() {
        Collection<O> vals = values;
        if (vals == null) {
            vals = Collections.unmodifiableCollection(createValuesCollection(entryMap_::values));
            values = vals;
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
                        doEntryGot(entry);
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
                        doEntryGot(entry);
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
                        }
                        result = entry.getObjectAndAdapter().object;
                    } finally {
                        // Downgrade lock.
                        entryLock.downgradeLock();
                    }
                } else {
                    doEntryGot(entry);
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

    protected final Listener<K, O> getListener() {
        return listener_;
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

    protected final O doReplace(K key, ObjectFactory<O> factory, ManagedAdapter<O> adapter) throws Exception {
        O result = null;
        final Lock entryLock = acquireLock(key);
        try {
            entryLock.lockWrite();
            try {
                Entry<O> entry = internalGetEntryIfPresent(key);
                if (entry != null) {
                    if (entry.getObjectAndAdapter() != null) {
                        doDeinitializeEntry(key, entry);
                    }
                }
                entry = Optional.ofNullable(entry).orElseGet(this::doCreateEntry);
                doInitializeEntry(key, entry, factory, adapter);
                result = entry.getObjectAndAdapter().object;
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
        final O object = doObjectCreate(factory, key);
        Try.runnable(() -> {
            entry.initObjectAndAdapter(object, adapter);
            // Помещаю Entry в карту до вызова #doObjectStart(), что бы текущий поток, при
            // рекурсивном вызове текущего метода, мог получить ссылку на этот Entry.
            internalPutEntry(key, entry);
            Try.runnable(() -> {
                doObjectStart(object, adapter);
                Try.runnable(() -> {
                    doEntryAdded(entry);
                }).onCatch((th) -> {
                    doObjectStop(object, adapter);
                    throw th;
                }).run();
            }).onCatch((th) -> {
                internalRemoveAndClearEntry(key);
                throw th;
            }).run();
        }).onCatch((th) -> {
            doObjectDestroy(object, adapter);
            throw th;
        }).run();
    }

    protected final void doDeinitializeEntry(K key, Entry<O> entry) throws Exception {
        final ObjectAndAdapter<O> objectAndAdapter = entry.getObjectAndAdapter();
        doEntryRemoving(entry);
        Try.runnable(() -> {
            doObjectStop(objectAndAdapter.object, objectAndAdapter.adapter);
        }).doFinally(() -> {
            ExceptionUtils.collectAndThrow(
                    () -> internalRemoveAndClearEntry(key),
                    () -> doObjectDestroy(objectAndAdapter.object, objectAndAdapter.adapter)
            );
        }).run();
    }

    protected final <E extends Entry<O>> E doCreateEntry() {
        return Objects.requireNonNull(newEntry(), String.format("The `%s#newEntry()` method did not return an instance.", getClass().getCanonicalName()));
    }

    @SuppressWarnings("unchecked")
    protected <E extends Entry<O>> E newEntry() {
        return (E) new Entry();
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
        final List<Throwable> exceptionList = new ArrayList<>();
        final List<O> list = new ArrayList<>();
        final List<K> keys = new ArrayList<>(keySet());
        keys.forEach(key -> {
            try {
                // The write lock will set in doRemove().
                O item = doRemove(key);
                if (item != null) {
                    list.add(item);
                }
            } catch (Exception e) {
                exceptionList.add(new ManagerException("Error while removing key = '" + key + "'", e));
            }
        });
        ExceptionUtils.throwCollected(exceptionList);
        return list;
    }

    protected void doClear() {
        final List<Throwable> exceptionList = new ArrayList<>();
        final List<K> keys = new ArrayList<>(keySet());
        keys.forEach(key -> {
            try {
                // The write lock will set in doRemove().
                doRemove(key);
            } catch (Exception e) {
                exceptionList.add(new ManagerException("Error while removing key = '" + key + "'", e));
            }
        });
        ExceptionUtils.throwCollected(exceptionList);
    }

    protected final Lock acquireLock(K key) {
        return lockSource_.acquire(key);
    }

    protected final void releaseLock(K key) {
        lockSource_.release(key);
    }

    protected final void doEntryGot(Entry<O> entry) {
        onEntryGot(entry);
    }

    /**
     * This method is called after a reference to the managed object has been successfully obtained.
     * <p>
     * At the time of invocation, a read lock is set for the record.
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

    protected final O doObjectCreate(ObjectFactory<O> factory, K key) throws Exception {
        O object = factory.createObject();
        getListener().onObjectCreated(key, object);
        return object;
    }

    protected final void doObjectStart(O object, ManagedAdapter<O> adapter) throws Exception {
        try {
            getListener().onObjectStarting(object);
            adapter.startObject(object);
            getListener().onObjectStarted(object);
        } catch (Throwable th) {
            doObjectFailure(object, adapter, th);
        }
    }

    protected final void doObjectFailure(O object, ManagedAdapter<O> adapter, Throwable throwable) throws Exception {
        getListener().onObjectFailure(object, throwable);
    }

    protected final void doObjectStop(O object, ManagedAdapter<O> adapter) throws Exception {
        try {
            getListener().onObjectStopping(object);
            adapter.stopObject(object);
            getListener().onObjectStopped(object);
        } catch (Throwable th) {
            doObjectFailure(object, adapter, th);
        }
    }

    protected final void doObjectDestroy(O object, ManagedAdapter<O> adapter) throws Exception {
        getListener().onObjectDestroying(object);
        adapter.destroyObject(object);
        getListener().onObjectDestroyed(object);
    }

    /**
     * Immutable class for storing of the pair of objects.
     *
     * @param <O> type of managed object
     */
    protected final static class ObjectAndAdapter<O> implements Serializable {
        private static final long serialVersionUID = 1L;

        public final O object;
        public final ManagedAdapter<O> adapter;

        public ObjectAndAdapter(O object, ManagedAdapter<O> adapter) {
            this.object = Objects.requireNonNull(object);
            this.adapter = Objects.requireNonNull(adapter);
        }

    }

    protected static class Entry<O> implements Serializable {
        private static final long serialVersionUID = 1L;
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
