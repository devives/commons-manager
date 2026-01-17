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

import com.devives.commons.lang.ExceptionUtils;
import com.devives.commons.manager.Manager;

import java.io.Serializable;


public final class SyncLockSource<K> extends AbstractLockSource<K> implements Serializable {
    private static final long serialVersionUID = 1L;

    @Override
    protected <E extends Manager.Lock> E doAcquireLock() {
        return (E) new SyncLock();
    }

    class SyncLock extends AbstractLock {
        private int locked_ = 0;
        private long threadId_ = 0;

        private synchronized void lock() {
            try {
                long curThreadId = Thread.currentThread().getId();
                while (locked_ > 0 && curThreadId != threadId_) {
                    this.wait();
                }
                threadId_ = curThreadId;
                locked_++;
            } catch (InterruptedException e) {
                throw ExceptionUtils.asUnchecked(e);
            }
        }

        private synchronized void unlock() {
            if (--locked_ == 0) {
                threadId_ = 0;
                this.notify();
            }
        }

        @Override
        public void lockRead() {
            lock();
        }

        @Override
        public void unlockRead() {
            unlock();
        }

        @Override
        public void lockWrite() {
            lock();
        }

        @Override
        public void unlockWrite() {
            unlock();
        }

        @Override
        public void upgradeLock() {
            lock();
        }

        @Override
        public void downgradeLock() {
            unlock();
        }
    }

}
