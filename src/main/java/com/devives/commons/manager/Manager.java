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
package com.devives.commons.manager;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Object manager interface.
 * <p>
 * The manager manages the life cycle of objects accessed via key.
 * </p>
 *
 * @param <K> type of key
 * @param <O> type of managed object
 * @author Vladimir Ivanov {@code <ivvlev@devives.com>}
 */
public interface Manager<K, O> {

    /**
     * Return instance of the class {@code O} corresponding to the key.
     * <p>
     * При получении ссылки на объект выполняется блокировка на чтение. Если в этот момент другой поток установил
     * блокировку на запись для выполняет операции запуска {@link ManagedAdapter#startObject(Object)} или
     * остановки {@link ManagedAdapter#stopObject(Object)} запрашиваемого объекта, метод будет ждать снятия блокировки
     * на запись.
     *
     * @param key key
     * @return instance of {@code O}
     * @throws ManagerException if {@code key} is not present in manager.
     */
    O get(K key) throws ManagerException;

    /**
     * Return instance of the class {@code O} corresponding to the key if it presents in manager.
     *
     * @param key key
     * @return instance of {@code O} or {@code null}.
     */
    O getIfPresent(K key);

    /**
     * If the specified key is not already associated with a value,
     * attempts to compute its value using the given mapping function
     * and enters it into this manager.
     *
     * @param key     key
     * @param factorySupplier the supplier to compute an objects factory.
     * @return instance of {@code O}
     * @see ConcurrentHashMap#computeIfAbsent(Object, Function)
     */
    O computeIfAbsent(K key, Supplier<ObjectFactory<O>> factorySupplier);

    /**
     * If the specified key is not already associated with a value,
     * attempts to compute its value using the given mapping function
     * and enters it into this manager.
     *
     * @param key             key
     * @param factorySupplier the supplier to compute an objects factory.
     * @return instance of {@code O}
     * @see ConcurrentHashMap#computeIfAbsent(Object, Function)
     */
    O computeIfAbsent(K key, Function<K, ObjectFactory<O>> factorySupplier);

    /**
     * Put new instance of {@code O} in to manager.
     *
     * @param key     key
     * @param factory object factory.
     * @return Previous instance of {@code O} if it's present.
     */
    O put(K key, ObjectFactory<O> factory);

    /**
     * Removes a key and its corresponding object from the manager.
     *
     * @param key key
     * @return instance of {@code O} or {@code null}.
     */
    O remove(K key);

    /**
     * Remove all instances from manager.
     *
     * @return a list containing al removed instances.
     */
    List<O> removeAll();

    /**
     * Remove all instances from manager.
     */
    void clear();

    /**
     * Check presence of {@code key} in the manager.
     *
     * @param key key
     * @return {@code true} if present, else {@code false}.
     * @see ConcurrentHashMap#containsKey(Object)
     */
    boolean containsKey(K key);

    /**
     * Returns a {@link Set} view of the keys contained in this manager.
     *
     * @return a set view of the keys contained in this manager.
     * @see ConcurrentHashMap#keySet()
     */
    Set<K> keySet();

    /**
     * Returns <tt>true</tt> if this manages contains no objects.
     *
     * @return {@code true} if manager is empty, else {@code false}.
     * @see ConcurrentHashMap#isEmpty()
     */
    boolean isEmpty();

    /**
     * Return the count of objects in manager.
     *
     * @return count of object
     */
    long size();

    /**
     * Call an {@code action} for each key-value pair.
     *
     * @param action action
     */
    default void forEach(BiConsumer<? super K, ? super O> action) {
        Objects.requireNonNull(action);
        keySet().forEach((k) -> {
            O v = getIfPresent(k);
            if (v != null) {
                action.accept(k, v);
            }
        });
    }

}
