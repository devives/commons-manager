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
package com.devives.commons.manager.specials;

import com.devives.commons.lifecycle.LifeCycle;
import com.devives.commons.lifecycle.SynchronizedCloseableAbst;
import com.devives.commons.manager.*;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager of {@link LifeCycle} instances.
 *
 * Class is an example of {@link ConcurrentHashManager} usage in more complex scenarios.
 *
 * @param <O> type of managed object
 */
public class LifeCycleManager<O extends LifeCycle> extends SynchronizedCloseableAbst implements Serializable {

    private static final long serialVersionUID = 4885566608544537251L;
    private final Map<IdentityWrapper<O>, Object> object2keyMap_ = new ConcurrentHashMap<>();
    private final Manager<Object, O> internalManager_ = new InternalManager();

    private final class InternalManager extends ConcurrentHashManager<Object, O> {
        private static final long serialVersionUID = 737665934900182556L;

        @Override
        protected void onObjectCreated(Object key, O object) throws Exception {
            object2keyMap_.put(new IdentityWrapper<>(object), key);
        }

        @Override
        protected void onObjectDestroying(O object) throws Exception {
            object2keyMap_.remove(new IdentityWrapper<>(object));
        }

    }

    /**
     * Creates a new instance of class <tt>&lt;O&gt;</tt>.
     *
     * @param factory factory of managed objects.
     * @return new instance of managed object.
     */
    public O create(KeyedObjectFactory<Object, O, LifeCycleManager<O>> factory) {
        final Object key = factory.buildKey();
        return internalManager_.computeIfAbsent(key, new ManagedFactory<O>() {
            @Override
            public O createObject() throws Exception {
                return factory.createObject(key, LifeCycleManager.this);
            }

            @Override
            public void startObject(O object) throws Exception {
                factory.startObject(object);
            }

            @Override
            public void failureObject(O object, Throwable throwable) {
                factory.failureObject(object, throwable);
            }

            @Override
            public void stopObject(O object) throws Exception {
                factory.stopObject(object);
            }

            @Override
            public void destroyObject(O object) throws Exception {
                factory.destroyObject(object);
            }
        });
    }

    /**
     * Return managed object by given key.
     *
     * @param key key of managed object
     * @return exist instance of managed object.
     * @throws ManagerException if no instance with {@code key} present in manager.
     */
    public O get(String key) throws ManagerException {
        return internalManager_.get(key);
    }

    /**
     * Remove managed object from manager.
     *
     * @param object managed object.
     */
    public void remove(O object) {
        final Object key = object2keyMap_.remove(new IdentityWrapper<>(object));
        if (key != null) {
            O removed = internalManager_.remove(key);
            assert object.equals(removed);
        }
    }

    /**
     * Returns a {@link Set} view of the keys contained in this manager.
     *
     * @return a set view of the keys contained in this manager.
     * @see ConcurrentHashMap#keySet()
     */
    public Set<Object> keySet() {
        return internalManager_.keySet();
    }

    /**
     * Returns a value indicating the absence of objects.
     *
     * @return {@code true}, if managed is empty, else {@code false}.
     */

    public boolean isEmpty() {
        return internalManager_.isEmpty();
    }

    /**
     * Return count of objects in manager.
     *
     * @return count.
     */
    public long size() {
        return internalManager_.size();
    }

    @Override
    protected void onClose() throws Exception {
        object2keyMap_.clear();
        internalManager_.removeAll();
    }

}
