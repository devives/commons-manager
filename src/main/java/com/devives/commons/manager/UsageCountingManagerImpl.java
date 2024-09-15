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


import com.devives.commons.lang.exception.ExceptionUtils;
import com.devives.commons.lifecycle.CloseableObjAbst;

import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class UsageCountingManagerImpl<K, O> extends CloseableObjAbst implements UsageCountingObjectManager<K, O>, Serializable {

    private static final long serialVersionUID = 8389806804677309189L;
    private final LocalManagerImpl<K, O> manager_;

    public UsageCountingManagerImpl() {
        manager_ = new LocalManagerImpl<>();
    }

    @Override
    protected void onClose() throws Exception {
        manager_.close();
    }

    @Override
    public O acquire(K key) throws ManagerException {
        return manager_.get(key);
    }

    @Override
    public O acquire(K key, Supplier<ObjectFactory<O>> factorySupplier) {
        return manager_.computeIfAbsent(key, factorySupplier);
    }

    @Override
    public void release(K key) {
        manager_.release(key);
    }

    @Override
    public boolean isEmpty() {
        return manager_.isEmpty();
    }

    @Override
    public long size() {
        return manager_.size();
    }

    public boolean isRemoveUnusedObjects() {
        return manager_.isRemoveUnusedObjects();
    }

    public void setRemoveUnusedObjects(boolean value) {
        manager_.setRemoveUnusedObjects(value);
    }

    protected static class LocalManagerImpl<K, O> extends ConcurrentManagerImpl<K, O> {
        private static final long serialVersionUID = 242380967546124799L;
        private volatile boolean removeUnusedObjects_ = true;

        public boolean isRemoveUnusedObjects() {
            return removeUnusedObjects_;
        }

        public void setRemoveUnusedObjects(boolean removeUnusedObjects) {
            removeUnusedObjects_ = removeUnusedObjects;
        }

        @Override
        protected <E extends Entry<?>> E newEntry(K key) {
            return (E) new InheritedEntry<O>(key);
        }

        @Override
        protected void onEntryGet(Entry<O> entry) {
            ((InheritedEntry<O>) entry).incUsages();
        }

        public final void release(K key) {
            final EntryLock entryLock = acquireEntryLock(key);
            try {
                entryLock.readLock().lock();
                InheritedEntry<O> entry = (InheritedEntry<O>) internalGetEntryIfPresent(key);
                Objects.requireNonNull(entry, String.format("Key '%s' not present in manager.", key));
                long usages;
                try {
                    usages = entry.decUsages();
                } finally {
                    entryLock.readLock().unlock();
                }
                if (usages == 0 && removeUnusedObjects_) {
                    entryLock.writeLock().lock();
                    try {
                        if (entry.getUsages() == 0) {
                            doRemoveEntry(key, entry);
                        }
                    } finally {
                        entryLock.writeLock().unlock();
                    }
                }
            } catch (Exception e) {
                throw ExceptionUtils.asUnchecked(e);
            } finally {
                releaseEntryLock(key);
            }
        }

        protected static class InheritedEntry<I> extends Entry<I> {
            private static final long serialVersionUID = 1626761723478454362L;
            private final AtomicLong usageCounter = new AtomicLong();

            long getUsages() {
                return usageCounter.get();
            }

            long incUsages() {
                return usageCounter.incrementAndGet();
            }

            long decUsages() {
                // Счетчик не может уйти в минус. Если ушел - где-то проглядели
                return usageCounter.accumulateAndGet(-1, (x, dx) -> {
                    if (x == 0) {
                        throw new RuntimeException("Counter can not be lower zero.");
                    }
                    return x + dx;
                });
            }


            public AtomicLong getNumUsages() {
                return usageCounter;
            }


            public InheritedEntry(Object key) {
                super(key);
            }
        }
    }
}
