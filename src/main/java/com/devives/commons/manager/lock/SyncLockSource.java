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

/**
 * Lock source based on Java monitors and {@code wait}/{@code notifyAll}.
 * <p>
 * This implementation serializes both read and write access for the same key and supports reentrancy
 * only for the owning thread. It is simpler than {@link RWLockSource}, but does not allow concurrent readers.
 *
 * @param <K> key type
 */
public final class SyncLockSource<K> extends AbstractLockSource<K> implements Serializable {
    private static final long serialVersionUID = 1L;

    @Override
    protected <E extends Manager.Lock> E doAcquireLock() {
        return (E) new SyncLock();
    }

    /**
     * Per-key reentrant monitor lock that treats read and write operations identically.
     */
    class SyncLock extends AbstractLock {
        private int locked_ = 0;
        private long threadId_ = 0;

        private synchronized void lock() {
            final Thread currentThread = Thread.currentThread();
            try {
                long curThreadId = currentThread.getId();
                while (locked_ > 0 && curThreadId != threadId_) {
                    this.wait();
                }
                threadId_ = curThreadId;
                locked_++;
            } catch (InterruptedException e) {
                // Восстанавливаю флаг Thread.currentThread().isInterrupted()
                // На случай, если кто-то обработает InterruptedException как Exception и, не обратив внимание
                // на тип InterruptedException продолжит выполнение программы в текущем потоке.
                currentThread.interrupt();
                throw ExceptionUtils.asUnchecked(e);
            }
        }

        private synchronized void unlock() {
            if (--locked_ == 0) {
                threadId_ = 0;
                // Уведомляем все ожидающие потоки.
                // Первый разморозившийся захватит блокировку, остальные опять встанут в ожидание.
                // Это надёжнее чем, разморозить один поток вызовом notify(), и обрабатывать возможные ошибки this.wait().
                this.notifyAll();
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
