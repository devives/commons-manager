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

public final class RWLockSource<K> extends AbstractLockSource<K> implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean fair_;

    public RWLockSource(boolean fair) {
        fair_ = fair;
    }

    @Override
    protected <E extends Manager.Lock> E doAcquireLock() {
        return (E) new RWLock(fair_);
    }

    final class RWLock extends AbstractLock {
        private final ReentrantReadWriteLock readWriteLock_;

        /**
         *
         * @param fair true — учитывает порядок обращений потоков, false — НЕ учитывает порядок обращений потоков.
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
