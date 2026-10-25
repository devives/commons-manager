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
package com.devives.commons.manager.lifecycle;


import com.devives.commons.lifecycle.LifeCycle;
import com.devives.commons.manager.*;

import java.io.Serializable;

/**
 * Start and stop {@link LifeCycle} instances.
 * <p>
 * Class is an example of {@link ConcurrentHashManager} usage in more complex scenarios.
 *
 * @param <O> type of managed object
 */
final class SimpleLifeCycleManager<O extends LifeCycle> implements Serializable {

    private static final long serialVersionUID = 1L;
    private final Manager<IdentityWrapper<O>, O> internalManager_ = new HashManager<>(new LifeCycleAdapter<>());

    private IdentityWrapper<O> buildKey(O object) {
        return new IdentityWrapper<>(object);
    }

    private static final class LifeCycleAdapter<T extends LifeCycle> implements ManagedAdapter<T> {

        @Override
        public void startObject(T object) throws Exception {
            object.start();
        }

        @Override
        public void stopObject(T object) throws Exception {
            object.stop();
        }

    }

    /**
     * Creates a new instance of class <code>&lt;O&gt;</code>.
     *
     * @param object object.
     */
    public void add(final O object) {
        final IdentityWrapper<O> key = buildKey(object);
        internalManager_.computeIfAbsent(key, () -> object);
    }

    /**
     * Remove managed object from manager.
     *
     * @param object managed object.
     */
    public void remove(final O object) {
        final IdentityWrapper<O> key = buildKey(object);
        internalManager_.remove(key);
    }

    /**
     * Remove all items from manager.
     */
    public void clear() {
        internalManager_.clear();
    }
}
