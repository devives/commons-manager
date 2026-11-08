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
import com.devives.commons.manager.HashManager;
import com.devives.commons.manager.IdentityWrapper;
import com.devives.commons.manager.ManagedAdapter;
import com.devives.commons.manager.Manager;

/**
 * Start and stop {@link LifeCycle} instances.
 * <p>
 * Class is an example of {@link HashManager} usage in more complex scenarios.
 * <p>
 * Note on failures: when {@code start()} fails, the manager removes the entry and calls
 * {@link ManagedAdapter#destroyObject(Object)} on the object once, in the {@code FAILED} state.
 * {@code stop()} is not applicable there, and the adapter sees the object through
 * {@link LifeCycle} only, so it cannot release anything the object acquired before the failure.
 * Such resources must be released by the object itself in its {@code onFailure(Ref)} hook.
 *
 * @param <O> type of managed object
 */
final class SimpleLifeCycleManager<O extends LifeCycle> {

    private final Manager<IdentityWrapper<O>, O> internalManager_ = new HashManager<>(new LifeCycleAdapter<>());

    private IdentityWrapper<O> buildKey(O object) {
        return new IdentityWrapper<>(object);
    }

    /**
     * Maps the manager's operations onto {@link LifeCycle#start()} and {@link LifeCycle#stop()}.
     * Stopping is the only release path this adapter has; see the class note on failures.
     */
    private static final class LifeCycleAdapter<T extends LifeCycle> implements ManagedAdapter<T> {

        @Override
        public void startObject(T object) throws Exception {
            object.start();
        }

        @Override
        public void stopObject(T object) throws Exception {
            object.stop();
        }

        /**
         * Called by the manager once on an object whose start was rolled back, in the
         * {@code FAILED} state. Nothing can be done for it generically here: {@code stop()}
         * is not applicable in {@code FAILED}, and the adapter sees the object as
         * {@link LifeCycle} only. Releasing resources acquired before the failure is the
         * object's own {@code onFailure(Ref)} responsibility, so the override stays empty —
         * it exists to state that explicitly rather than inherit an unexplained default.
         */
        @Override
        public void destroyObject(T object) {

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
