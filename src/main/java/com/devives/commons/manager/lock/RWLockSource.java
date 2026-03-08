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

import com.devives.commons.manager.Manager;

import java.io.Serializable;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Lock source backed by {@link ReentrantReadWriteLock} with one lock instance per manager key.
 * <p>
 * Unlike {@link SyncLockSource}, this implementation allows concurrent readers for the same key and
 * separates read and write access, which is better suited for read-heavy manager workloads.
 *
 * @param <K> key type
 */
public final class RWLockSource<K> extends AbstractLockSource<K> implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean fair_;

    /**
     * Creates a lock source backed by {@link ReentrantReadWriteLock}.
     *
     * @param fair {@code true} enables fair scheduling and reduces the risk of writer starvation under sustained read load,
     *             but usually decreases throughput and may increase average lock acquisition latency due to stricter queueing;
     *             <p>
     *             – {@code false} uses a non-fair policy that typically provides better (8-10x manager operations) throughput ,
     *             but under high contention can indefinitely delay write operations such as object replacement, stop, and removal.
     */
    public RWLockSource(boolean fair) {
        fair_ = fair;
    }

    @Override
    protected <E extends Manager.Lock> E doAcquireLock() {
        return (E) new RWLock(fair_);
    }

    /**
     * Per-key read-write lock implementation used by {@link RWLockSource}.
     */
    final class RWLock extends AbstractLock {
        private final ReentrantReadWriteLock readWriteLock_;

        /**
         * @param fair {@code true} favors predictable scheduling and lowers the probability of write starvation;
         *             {@code false} usually gives higher throughput, but may starve waiting writers when readers arrive continuously.
         */
        protected RWLock(boolean fair) {
            readWriteLock_ = new ReentrantReadWriteLock(fair);
        }

        @Override
        public void lockRead() {
            readWriteLock_.readLock().lock();
        }

        @Override
        public void unlockRead() {
            readWriteLock_.readLock().unlock();
        }

        @Override
        public void lockWrite() {
            readWriteLock_.writeLock().lock();
        }

        @Override
        public void unlockWrite() {
            readWriteLock_.writeLock().unlock();
        }

        @Override
        public void upgradeLock() {
            readWriteLock_.readLock().unlock();
            readWriteLock_.writeLock().lock();
        }

        @Override
        public void downgradeLock() {
            readWriteLock_.readLock().lock();
            readWriteLock_.writeLock().unlock();
        }
    }

}
