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

import com.devives.commons.manager.LifeCycle;
import com.devives.commons.manager.LifeCycleAbst;

import java.util.LinkedHashSet;
import java.util.Objects;

/**
 * Синхронизированная реализация контейнера элементов с жизненным циклом.
 */
public class LifeCycleContainer extends LifeCycleAbst {
    /**
     * Содержит список управляемых элементов.
     */
    private final LinkedHashSet<LifeCycle> itemSet = new LinkedHashSet<>();
    /**
     * Управляет запуском и остановкой управляемых элементов.
     */
    private final LifeCycleManager<LifeCycle> manager = new LifeCycleManager<>();

    /**
     * Запускает контейнер и все элементы содержащиеся в контейнере.
     */
    @Override
    public void start() throws Exception {
        synchronized (itemSet) {
            super.start();
        }
    }

    /**
     * Останавливает все элементы содержащиеся в контейнере и контейнер
     */
    @Override
    public void stop() throws Exception {
        synchronized (itemSet) {
            super.stop();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param lifeCycle {@inheritDoc}
     * @throws IllegalArgumentException При повторном добавлении элемента в контейнер.
     */
    public void add(LifeCycle lifeCycle) {
        synchronized (itemSet) {
            if (!itemSet.add(lifeCycle)) {
                throw new IllegalArgumentException("A duplicate element in a container.");
            }
            if (isStarted()) {
                internalStartItem(lifeCycle);
            }
        }
    }

    public void remove(LifeCycle lifeCycle) {
        synchronized (itemSet) {
            internalStopItem(lifeCycle);
            itemSet.remove(lifeCycle);
        }
    }

    private void internalStartItem(LifeCycle lifeCycle) {
        manager.create(new LocalLifeCycleFactory(lifeCycle));
    }

    private void internalStopItem(LifeCycle lifeCycle) {
        manager.remove(lifeCycle);
    }

    @Override
    protected void onStart() throws Exception {
        itemSet.forEach(this::internalStartItem);
    }

    @Override
    protected void onStop() throws Exception {
        LifeCycle[] array = itemSet.toArray(new LifeCycle[0]);
        for (int i = array.length - 1; i >= 0; i--) {
            internalStopItem(array[i]);
        }
    }

    private static class LocalLifeCycleFactory implements KeyedObjectFactory<Object, LifeCycle, LifeCycleManager<LifeCycle>> {

        private final LifeCycle lifeCycle;

        public LocalLifeCycleFactory(LifeCycle lifeCycle) {
            this.lifeCycle = Objects.requireNonNull(lifeCycle, "lifeCycle");
        }

        @Override
        public String buildKey() {
            return lifeCycle.getClass().getCanonicalName();
        }

        @Override
        public LifeCycle createObject(Object key, LifeCycleManager<LifeCycle> manager) {
            return lifeCycle;
        }

        @Override
        public void startObject(LifeCycle object) throws Exception {
            lifeCycle.start();
        }

        @Override
        public void stopObject(LifeCycle object) throws Exception {
            lifeCycle.stop();
        }
    }

}
