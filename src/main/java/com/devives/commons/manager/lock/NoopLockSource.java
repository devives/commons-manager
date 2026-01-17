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

public final class NoopLockSource<K> implements Manager.LockSource<K>, Serializable {
    private static final long serialVersionUID = 1L;

    private final Manager.Lock noopLock = new NoopLock();

    @Override
    public Manager.Lock acquire(final K k) {
        return noopLock;
    }

    @Override
    public void release(final K k) {
        // Do nothing.
    }

    static class NoopLock implements Manager.Lock, Serializable {

        @Override
        public void lockRead() {

        }

        @Override
        public void unlockRead() {

        }

        @Override
        public void lockWrite() {

        }

        @Override
        public void unlockWrite() {

        }

        @Override
        public void upgradeLock() {

        }

        @Override
        public void downgradeLock() {

        }
    }

}
