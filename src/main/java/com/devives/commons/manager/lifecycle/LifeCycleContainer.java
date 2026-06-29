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

import java.util.Collection;

/**
 * Интерфейс контейнера элементов, обладающих жизненным циклом.
 *
 * @param <T> тип элементов.
 */
public interface LifeCycleContainer<T extends LifeCycle> extends LifeCycle, ObservableLifeCycle {

    /**
     * Добавляет элемент в контейнер.
     * <p>
     * Если контейнер в состоянии {@link LifeCycleStates#STARTED}, вызывает {@link LifeCycle#start()} добавляемого объекта.
     *
     * @param lifeCycle добавляемый элемент
     * @throws IllegalArgumentException При повторном добавлении элемента в контейнер.
     */
    void addItem(T lifeCycle);

    /**
     * Удаляет элемент из контейнера.
     *
     * @param lifeCycle удаляемый элемент
     */
    void removeItem(T lifeCycle);

    /**
     * Возвращает коллекцию элементов, содержащихся в контейнере.
     *
     * @return не модифицируемая коллекция элементов.
     */
    Collection<T> getItems();
}
