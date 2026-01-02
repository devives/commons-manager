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

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Thread-safe concurrent implementation of {@link Manager}.
 *
 * @param <K> type of key
 * @param <O> type of managed object
 * @author Vladimir Ivanov {@code <ivvlev@devives.com>}
 */
public abstract class AbstractManager<K, O> implements Manager<K, O> {
    private final Map<K, Entry<O>> entryMap_;
    private final LockSource lockSource_;
    private final ManagedAdapter<O> defaultAdapter_;
    private transient volatile Collection<O> values;

    protected AbstractManager(Map<K, Entry<O>> entryMap, LockSource lockSource) {
        entryMap_ = Objects.requireNonNull(entryMap, "entryMap");
        lockSource_ = Objects.requireNonNull(lockSource, "entryLockSource");
        defaultAdapter_ = new NoopManagedAdapter();
    }

    protected AbstractManager(Map<K, Entry<O>> entryMap, LockSource lockSource, ManagedAdapter<O> defaultAdapter) {
        entryMap_ = Objects.requireNonNull(entryMap, "entryMap");
        lockSource_ = Objects.requireNonNull(lockSource, "entryLockSource");
        defaultAdapter_ = Objects.requireNonNull(defaultAdapter, "defaultAdapter");
    }

    @Override
    public boolean containsKey(K key) {
        return getIfPresent(key) != null;
    }

    @Override
    public Set<K> keySet() {
        return Collections.unmodifiableSet(entryMap_.keySet());
    }

    /**
     * Return unmodifiable collection of values/ contained in manager.
     *
     * @return Unmodifiable collection of values
     */
    public Collection<O> values() {
        Collection<O> vals = values;
        if (vals == null) {
            vals = new AbstractCollection<O>() {
                public Iterator<O> iterator() {
                    return new Iterator<O>() {
                        private final Iterator<Entry<O>> i = entryMap_.values().iterator();
                        private volatile O nextValue;

                        public boolean hasNext() {
                            for (; ; ) {
                                boolean hasNext = i.hasNext();
                                nextValue = hasNext ? i.next().getObject() : null;
                                if (hasNext && nextValue != null) {
                                    return true;
                                } else if (nextValue == null) {
                                    return false;
                                }
                            }
                        }

                        public O next() {
                            return nextValue;
                        }

                    };
                }

                public int size() {
                    return entryMap_.values().size();
                }

                public boolean isEmpty() {
                    return entryMap_.values().isEmpty();
                }

                public boolean contains(Object v) {
                    throw new UnsupportedOperationException("Contains is not supported by manager values iterator.");
                }
            };
            values = vals;
        }
        return Collections.unmodifiableCollection(vals);
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
        return doGetIfPresent(key);
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

    protected final O doGet(K key) {
        final Lock entryLock = acquireLock(key);
        try {
            entryLock.lockRead();
            try {
                O result = null;
                final Entry<O> entry = internalGetEntryIfPresent(key);
                if (entry != null) {
                    result = entry.getObject();
                    if (result != null) {
                        onEntryGot(entry);
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

    protected final O doGetIfPresent(K key) {
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
                    if (result != null) {
                        onEntryGot(entry);
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

    protected final O doComputeIfAbsent(K key, ObjectFactory<O> factory, ManagedAdapter<O> adapter) throws Exception {
        O result = null;
        final Lock entryLock = acquireLock(key);
        try {
            entryLock.lockRead();
            try {
                ObjectAndAdapter<O> objectAndAdapter;
                Entry<O> entry = internalGetEntryIfPresent(key);
                if (entry == null) {
                    entryLock.upgradeLock();
                    try {
                        entry = Optional.ofNullable(internalGetEntryIfPresent(key)).orElseGet(this::newEntry);
                        objectAndAdapter = entry.getObjectAndAdapter();
                        if (objectAndAdapter == null) {
                            result = doObjectCreate(factory, key);
                            try {
                                objectAndAdapter = entry.initObjectAndAdapter(result, adapter);
                                internalPutEntry(key, entry);
                                try {
                                    doObjectStart(objectAndAdapter);
                                } catch (Throwable e) {
                                    try {
                                        doObjectFailure(objectAndAdapter, e);
                                    } finally {
                                        internalRemoveEntry(key);
                                        entry.clearObjectAndAdapter();
                                    }
                                    throw e;
                                }
                            } catch (Throwable e) {
                                adapter.destroyObject(result);
                                throw e;
                            }
                            onEntryAdded(entry);
                        }
                    } finally {
                        // Downgrade lock.
                        entryLock.downgradeLock();
                    }
                } else {
                    objectAndAdapter = entry.getObjectAndAdapter();
                }
                onEntryGot(entry);
                result = objectAndAdapter.object;
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
     * @return the previous value associated with <tt>key</tt>, or
     * <tt>null</tt> if there was no mapping for <tt>key</tt>.
     * @throws NullPointerException if the specified key is null
     * @see ConcurrentHashMap#remove(Object)
     */
    protected final Entry<O> internalRemoveEntry(final K k) {
        return entryMap_.remove(k);
    }

    protected final O doReplace(K key, ObjectFactory<O> factory, ManagedAdapter<O> adapter) throws Exception {
        O result = null;
        final Lock entryLock = acquireLock(key);
        try {
            entryLock.lockWrite();
            ObjectAndAdapter<O> objectAndAdapter;
            try {
                Entry<O> entry = internalGetEntryIfPresent(key);
                if (entry != null) {
                    objectAndAdapter = entry.getObjectAndAdapter();
                    if (objectAndAdapter != null) {
                        onEntryRemoving(entry);
                        try {
                            try {
                                doObjectStop(objectAndAdapter);
                            } catch (Throwable th) {
                                doObjectFailure(objectAndAdapter, th);
                                throw th;
                            } finally {
                                doObjectDestroy(objectAndAdapter);
                            }
                        } finally {
                            internalRemoveEntry(key);
                            entry.clearObjectAndAdapter();
                        }
                        result = objectAndAdapter.object;
                    }
                }
                entry = Optional.ofNullable(entry).orElseGet(this::newEntry);
                final O newObject = doObjectCreate(factory, key);
                try {
                    objectAndAdapter = entry.initObjectAndAdapter(newObject, adapter);
                    internalPutEntry(key, entry);
                    try {
                        doObjectStart(objectAndAdapter);
                    } catch (Throwable e) {
                        try {
                            doObjectFailure(objectAndAdapter, e);
                        } finally {
                            internalRemoveEntry(key);
                            entry.clearObjectAndAdapter();
                        }
                        throw e;
                    }
                } catch (Throwable e) {
                    adapter.destroyObject(result);
                    throw e;
                }
                onEntryAdded(entry);
            } finally {
                entryLock.unlockWrite();
            }
        } finally {
            releaseLock(key);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    protected <E extends Entry<?>> E newEntry() {
        return (E) new Entry();
    }

    protected final O doRemove(K key) throws Exception {
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
            onEntryRemoving(entry);
            try {
                try {
                    doObjectStop(objectAndAdapter);
                } catch (Throwable th) {
                    doObjectFailure(objectAndAdapter, th);
                    throw th;
                } finally {
                    doObjectDestroy(objectAndAdapter);
                }
            } finally {
                internalRemoveEntry(key);
                entry.clearObjectAndAdapter();
            }
            result = objectAndAdapter.object;
        }
        return result;
    }

    protected abstract List<O> doRemoveAll();

    protected abstract void doClear();

    protected final Lock acquireLock(K key) {
        return lockSource_.acquire(key);
    }

    protected final void releaseLock(K key) {
        lockSource_.release(key);
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
     * This method is called after a record has been successfully created and the managed object has been started.
     * <p>
     * At the time of invocation, a write lock is set for the record. The current thread will be able to obtain a valid object
     * by calling {@link #get(Object)} or {@link #getIfPresent(Object)}. Other threads will wait for the lock to be released.
     *
     * @param entry the record
     */
    protected void onEntryAdded(Entry<O> entry) {

    }

    /**
     * This method is called before stopping the managed object and deleting the record.
     * <p>
     * At the time of invocation, a write lock is set for the record.
     *
     * @param entry the record
     */
    protected void onEntryRemoving(Entry<O> entry) {

    }

    protected final O doObjectCreate(ObjectFactory<O> factory, K key) throws Exception {
        O object = factory.createObject();
        onObjectCreated(key, object);
        return object;
    }

    protected void onObjectCreated(K key, O object) throws Exception {

    }

    protected final void doObjectStart(ObjectAndAdapter<O> objectAndAdapter) throws Exception {
        onObjectStarting(objectAndAdapter.object);
        objectAndAdapter.adapter.startObject(objectAndAdapter.object);
        onObjectStarted(objectAndAdapter.object);
    }

    protected void onObjectStarting(O object) throws Exception {

    }

    protected void onObjectStarted(O object) throws Exception {

    }

    protected final void doObjectFailure(ObjectAndAdapter<O> objectAndAdapter, Throwable throwable) throws Exception {
        onObjectFailure(objectAndAdapter.object, throwable);
        objectAndAdapter.adapter.failureObject(objectAndAdapter.object, throwable);
    }

    protected void onObjectFailure(O object, Throwable throwable) throws Exception {

    }

    protected final void doObjectStop(ObjectAndAdapter<O> objectAndAdapter) throws Exception {
        onObjectStopping(objectAndAdapter.object);
        objectAndAdapter.adapter.stopObject(objectAndAdapter.object);
        onObjectStopped(objectAndAdapter.object);
    }

    protected void onObjectStopping(O object) throws Exception {

    }

    protected void onObjectStopped(O object) throws Exception {

    }

    protected final void doObjectDestroy(ObjectAndAdapter<O> objectAndAdapter) throws Exception {
        onObjectDestroying(objectAndAdapter.object);
        objectAndAdapter.adapter.destroyObject(objectAndAdapter.object);
        onObjectDestroyed(objectAndAdapter.object);
    }

    protected void onObjectDestroying(O object) throws Exception {

    }

    protected void onObjectDestroyed(O object) throws Exception {

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

    protected static class Entry<O> implements Serializable {
        private static final long serialVersionUID = -5529739784330238632L;
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

        public ObjectAndAdapter<O> initObjectAndAdapter(O object, ManagedAdapter<O> adapter) {
            final ObjectAndAdapter<O> objectAndAdapter = new ObjectAndAdapter<O>(object, adapter);
            objectAndAdapter_ = objectAndAdapter;
            return objectAndAdapter;
        }

        public void clearObjectAndAdapter() {
            objectAndAdapter_ = null;
        }

    }

}
