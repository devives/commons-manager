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
package com.devives.commons.manager.lock;

import com.devives.commons.lifecycle.UsageCounter;
import com.devives.commons.manager.Manager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base implementation of {@link Manager.LockSource} that maintains one lock instance per manager key.
 * <p>
 * The source keeps locks in an internal concurrent map and removes them automatically when no thread
 * holds a registered interest in the corresponding key anymore.
 *
 * @param <K> key type
 */
public abstract class AbstractLockSource<K> implements Manager.LockSource<K> {

    protected final Map<K, Manager.Lock> map_ = new ConcurrentHashMap<>();

    public final Manager.Lock acquire(final K key) {
        return map_.compute(key, (k, lock) -> {
            if (lock == null) {
                lock = doAcquireLock();
            }
            ((AbstractLock) lock).incUsageCount();
            return lock;
        });
    }

    public final void release(final K key) {
        map_.computeIfPresent(key, (k, lock) -> {
            final long usages = ((AbstractLock) lock).decUsageCount();
            if (usages == 0) {
                doReleaseLock(lock);
                return null;
            } else if (usages > 0) {
                return lock;
            } else {
                throw new RuntimeException("Counter below zero!");
            }
        });
    }

    protected abstract <E extends Manager.Lock> E doAcquireLock();

    protected <E extends Manager.Lock> void doReleaseLock(E lock) {
        // Do nothing.
    }

    /**
     * Base lock implementation with reference counting used by {@link AbstractLockSource} to control
     * lock lifecycle inside the internal map.
     */
    protected abstract class AbstractLock implements Manager.Lock, UsageCounter {
        /**
         * Number of threads with registered interest in this key.
         * register(K) increments this counter and deRegister(K) decrements it.
         * Invariant: empty entry will not be dropped unless internalUsageCount is 0.
         */
        private int usageCount_ = 0;

        public int getUsageCount() {
            return usageCount_;
        }

        @Override
        public int incUsageCount() {
            return ++usageCount_;
        }

        @Override
        public int decUsageCount() {
            return --usageCount_;
        }

    }
}
