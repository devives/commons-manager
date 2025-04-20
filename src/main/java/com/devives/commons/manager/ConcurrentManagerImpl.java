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
import com.devives.commons.lifecycle.SynchronizedCloseableAbst;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Thread-safe implementation of {@link Manager}.
 *
 * @param <K> type of key
 * @param <O> type of managed object
 * @author Vladimir Ivanov {@code <ivvlev@devives.com>}
 */
public class ConcurrentManagerImpl<K, O> extends SynchronizedCloseableAbst implements Manager<K, O>, Serializable {
    private static final long serialVersionUID = 2387557309207392162L;
    private final Map<K, EntryLock> lockMap_ = new ConcurrentHashMap<>();
    private final Map<K, Entry<O>> entryMap_ = new ConcurrentHashMap<>();

    @Override
    public boolean containsKey(K key) {
        return getIfPresent(key) != null;
    }

    @Override
    public Set<K> keySet() {
        return entryMap_.keySet();
    }

    public List<O> values() {
        return entryMap_.values().stream()
                .map(Entry::getObject)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public O get(K key) throws ManagerException {
        try {
            Objects.requireNonNull(key);
            validateOpened();
            final O result = doGet(key);
            if (result == null) {
                throw createManagerException(String.format("The manager does not contain an object with the key '%s'.", key), null);
            }
            return result;
        } catch (Exception e) {
            throw ExceptionUtils.asUnchecked(e);
        }
    }

    @Override
    public O getIfPresent(K key) {
        Objects.requireNonNull(key);
        validateOpened();
        return doGetIfPresent(key);
    }

    @Override
    public O computeIfAbsent(K key, Supplier<ObjectFactory<O>> factorySupplier) {
        try {
            Objects.requireNonNull(key);
            Objects.requireNonNull(factorySupplier);
            validateOpened();
            return doComputeIfAbsent(key, factorySupplier);
        } catch (Exception e) {
            throw ExceptionUtils.asUnchecked(e);
        }
    }

    @Override
    public O put(K key, ObjectFactory<O> factory) {
        try {
            Objects.requireNonNull(key);
            Objects.requireNonNull(factory);
            validateOpened();
            return doReplace(key, factory);
        } catch (Exception e) {
            throw ExceptionUtils.asUnchecked(e);
        }
    }

    @Override
    public O remove(K key) {
        try {
            Objects.requireNonNull(key);
            validateOpened();
            return doRemove(key);
        } catch (Exception e) {
            throw ExceptionUtils.asUnchecked(e);
        }
    }

    @Override
    public List<O> removeAll() {
        validateOpened();
        return doRemoveAll();
    }

    @Override
    public boolean isEmpty() {
        return entryMap_.isEmpty();
    }

    @Override
    public long size() {
        return entryMap_.size();
    }

    @Override
    protected void onClose() throws Exception {
        doRemoveAll();
    }

    protected final O doGet(K key) {
        final EntryLock entryLock = acquireEntryLock(key);
        try {
            entryLock.readLock().lock();
            try {
                O result = null;
                final Entry<O> entry = internalGetEntryIfPresent(key);
                if (entry != null) {
                    result = entry.getObject();
                    if (result != null) {
                        onEntryGet(entry);
                    }
                }
                return result;
            } finally {
                entryLock.readLock().unlock();
            }
        } finally {
            releaseEntryLock(key);
        }
    }

    protected final O doGetIfPresent(K key) {
        O result = null;
        // Optimistically get entry without lock.
        final Entry<O> entry = internalGetEntryIfPresent(key);
        if (entry != null) {
            final EntryLock entryLock = acquireEntryLock(key);
            try {
                entryLock.readLock().lock();
                try {
                    result = entry.getObject();
                    // If object non set, it's equals entry not present.
                    if (result != null) {
                        onEntryGet(entry);
                    }
                } finally {
                    entryLock.readLock().unlock();
                }
            } finally {
                releaseEntryLock(key);
            }
        }
        return result;
    }

    protected final O doComputeIfAbsent(K key, Supplier<ObjectFactory<O>> factorySupplier) throws Exception {
        O result = null;
        final EntryLock entryLock = acquireEntryLock(key);
        try {
            entryLock.readLock().lock();
            try {
                ObjectAndAdapter<O> objectAndAdapter;
                Entry<O> entry = internalGetEntryIfPresent(key);
                if (entry == null) {
                    entryLock.readLock().unlock();
                    entryLock.writeLock().lock();
                    try {
                        entry = Optional.ofNullable(internalGetEntryIfPresent(key)).orElseGet(() -> newEntry(key));
                        objectAndAdapter = entry.getObjectAndAdapter();
                        if (objectAndAdapter == null) {
                            final ObjectFactory<O> factory = factorySupplier.get();
                            result = doObjectCreate(factory, key);
                            try {
                                objectAndAdapter = entry.initObjectAndAdapter(result, factory);
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
                                factory.destroyObject(result);
                                throw e;
                            }
                        }
                    } finally {
                        // Downgrade lock.
                        entryLock.readLock().lock();
                        entryLock.writeLock().unlock();
                    }
                } else {
                    objectAndAdapter = entry.getObjectAndAdapter();
                }
                onEntryGet(entry);
                result = objectAndAdapter.object;
            } finally {
                entryLock.readLock().unlock();
            }
        } finally {
            releaseEntryLock(key);
        }
        return result;
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

    protected final O doReplace(K key, ObjectFactory<O> factory) throws Exception {
        O result = null;
        final EntryLock entryLock = acquireEntryLock(key);
        try {
            entryLock.writeLock().lock();
            try {
                ObjectAndAdapter<O> objectAndAdapter;
                Entry<O> entry = internalGetEntryIfPresent(key);
                if (entry != null) {
                    objectAndAdapter = entry.getObjectAndAdapter();
                    if (objectAndAdapter != null) {
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
                entry = (entry != null) ? entry : newEntry(key);
                final O newObject = doObjectCreate(factory, key);
                try {
                    objectAndAdapter = entry.initObjectAndAdapter(newObject, factory);
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
                    factory.destroyObject(result);
                    throw e;
                }
                onEntryGet(entry);
            } finally {
                entryLock.writeLock().unlock();
            }
        } finally {
            releaseEntryLock(key);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    protected <E extends EntryLock> E newEntryLock(K key) {
        return (E) new EntryLock(key);
    }

    @SuppressWarnings("unchecked")
    protected <E extends Entry<?>> E newEntry(K key) {
        return (E) new Entry(key);
    }

    protected final O doRemove(K key) throws Exception {
        O result = null;
        Entry<O> entry = internalGetEntryIfPresent(key);
        if (entry != null) {
            final EntryLock entryLock = acquireEntryLock(key);
            try {
                Lock lock = entryLock.writeLock();
                lock.lock();
                try {
                    entry = internalGetEntryIfPresent(key);
                    if (entry != null) {
                        result = doRemoveEntry(key, entry);
                    }
                } finally {
                    lock.unlock();
                }
            } finally {
                releaseEntryLock(key);
            }
        }
        return result;
    }

    protected final O doRemoveEntry(K key, Entry<O> entry) throws Exception {
        O result = null;
        final ObjectAndAdapter<O> objectAndAdapter = entry.getObjectAndAdapter();
        if (objectAndAdapter != null) {
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

    protected final List<O> doRemoveAll() {
        final List<Throwable> exceptionList = new ArrayList<>();
        final List<O> list = new ArrayList<>();
        entryMap_.keySet().forEach(key -> {
            try {
                O item = doRemove((K) key);
                if (item != null) {
                    list.add(item);
                }
            } catch (Throwable e) {
                exceptionList.add(e);
            }
        });
        ExceptionUtils.throwCollected(exceptionList);
        return list;
    }

    protected final EntryLock acquireEntryLock(final K k) {
        return lockMap_.compute(k, (key, entry) -> {
            if (entry == null) {
                entry = newEntryLock(k);
            }
            entry.incInternalUsage();
            return entry;
        });
    }

    protected final void releaseEntryLock(final K k) {
        lockMap_.computeIfPresent(k, (key, e) -> {
            final long usages = e.decInternalUsage();
            if (usages == 0) {
                return null;
            } else if (usages > 0) {
                return e;
            } else {
                throw new RuntimeException("Counter below zero!");
            }
        });
    }

    protected Exception createManagerException(String message, Throwable cause) {
        return new ManagerException(message, cause);
    }

    protected void onEntryGet(Entry<O> entry) {

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
     * @param <I> type of managed object
     */
    protected final static class ObjectAndAdapter<I> {

        public final I object;
        public final LifeCycleAdapter<I> adapter;

        public ObjectAndAdapter(I object, LifeCycleAdapter<I> adapter) {
            this.object = Objects.requireNonNull(object);
            this.adapter = Objects.requireNonNull(adapter);
        }

    }

    protected static class EntryLock extends ReentrantReadWriteLock {
        private static final long serialVersionUID = -5529739544330238632L;
        /**
         * Number of threads with registered interest in this key.
         * register(K) increments this counter and deRegister(K) decrements it.
         * Invariant: empty entry will not be dropped unless internalUsageCount is 0.
         */
        private long internalUsageCount_ = 0;

        protected EntryLock(Object key) {
            super(true);
        }

        public long incInternalUsage() {
            return ++internalUsageCount_;
        }

        public long decInternalUsage() {
            return --internalUsageCount_;
        }

    }

    protected static class Entry<I> implements Serializable {
        private static final long serialVersionUID = -5529739784330238632L;
        /**
         * Volatile variable for the atomic non blocking read write operations.
         */
        private volatile ObjectAndAdapter<I> objectAndAdapter_ = null;

        protected Entry(Object key) {

        }

        public I getObject() {
            final ObjectAndAdapter<I> objectAndAdapter = objectAndAdapter_;
            return objectAndAdapter != null ? objectAndAdapter.object : null;
        }

        public ObjectAndAdapter<I> getObjectAndAdapter() {
            return objectAndAdapter_;
        }

        public ObjectAndAdapter<I> initObjectAndAdapter(I object, LifeCycleAdapter<I> lifeCycleAdapter) {
            final ObjectAndAdapter<I> objectAndAdapter = new ObjectAndAdapter<I>(object, lifeCycleAdapter);
            objectAndAdapter_ = objectAndAdapter;
            return objectAndAdapter;
        }

        public ObjectAndAdapter<I> clearObjectAndAdapter() {
            final ObjectAndAdapter<I> objectAndAdapter = objectAndAdapter_;
            objectAndAdapter_ = null;
            return objectAndAdapter;
        }

    }

}
