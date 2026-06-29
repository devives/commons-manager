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

import com.devives.commons.lang.ExceptionUtils;
import com.devives.commons.lang.call.Try;
import com.devives.commons.manager.*;

import java.io.Serializable;
import java.util.*;

/**
 * Не синхронизированная реализация контейнера элементов с жизненным циклом.
 */
public final class DefaultLifeCycleContainer<T extends LifeCycle> extends AbstractLifeCycle implements LifeCycleContainer<T> {
    private static final long serialVersionUID = 1L;
    /**
     * Содержит список управляемых элементов.
     */
    private final LinkedHashSet<T> itemSet = new LinkedHashSet<T>();
    /**
     * Управляет запуском и остановкой управляемых элементов.
     */
    private final LifeCycleManager<T> manager = new LifeCycleManager<T>();

    public void addItem(T lifeCycle) {
        Objects.requireNonNull(lifeCycle, "Item cannot be null");
        if (!itemSet.add(lifeCycle)) {
            throw new IllegalArgumentException("A duplicate element in a container.");
        }
        if (isStarted()) {
            Try.runnable(() -> {
                internalStartItem(lifeCycle);
            }).onCatch((th) -> {
                itemSet.remove(lifeCycle);
                throw th;
            }).run();
        }
    }

    public void removeItem(T lifeCycle) {
        Objects.requireNonNull(lifeCycle, "Item cannot be null");
        try {
            internalStopItem(lifeCycle);
        } finally {
            itemSet.remove(lifeCycle);
        }
    }

    @Override
    public Collection<T> getItems() {
        return Collections.unmodifiableCollection(itemSet);
    }

    private void internalStartItem(T lifeCycle) {
        manager.add(lifeCycle);
    }

    private void internalStopItem(T lifeCycle) {
        manager.remove(lifeCycle);
    }

    @SuppressWarnings("unchecked")
    private void internalStopItems() {
        final List<Exception> collector = new ArrayList<>();
        final LifeCycle[] array = itemSet.toArray(new LifeCycle[0]);
        for (int i = array.length - 1; i >= 0; i--) {
            try {
                internalStopItem((T) array[i]);
            } catch (Exception e) {
                collector.add(e);
            }
        }
        ExceptionUtils.throwCollected(collector);
    }

    private void internalStartItems() {
        itemSet.forEach(this::internalStartItem);
    }

    @Override
    protected void onStart() throws Exception {
        internalStartItems();
    }

    @Override
    protected void onStop() throws Exception {
        internalStopItems();
    }

    @Override
    protected FailureAction onFailure(Throwable th) {
        internalStopItems();
        return super.onFailure(th);
    }

    @Override
    public void addListener(Listener listener) {
        super.addListener(listener);
    }

    @Override
    public void removeListener(Listener listener) {
        super.removeListener(listener);
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
     * Manager of {@link LifeCycle} instances.
     * <p>
     * Class is an example of {@link ConcurrentHashManager} usage in more complex scenarios.
     *
     * @param <O> type of managed object
     */
    private final static class LifeCycleManager<O extends LifeCycle> implements Serializable {

        private static final long serialVersionUID = 1L;
        private final Manager<IdentityWrapper<O>, O> internalManager_ = new HashManager<>(new LifeCycleAdapter<>());

        private IdentityWrapper<O> buildKey(O object) {
            return new IdentityWrapper<>(object);
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

}
