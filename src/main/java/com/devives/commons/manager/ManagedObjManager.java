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

import com.devives.commons.lifecycle.Closeable;
import com.devives.commons.lifecycle.ManagedObj;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager of {@link ManagedObj} instances.
 *
 * @param <K> type of key
 * @param <O> type of managed object
 */
public interface ManagedObjManager<K, O extends ManagedObj> extends Closeable {

    /**
     * Return managed object by given key.
     *
     * @param key key of managed object
     * @return exist instance of managed object.
     * @throws ManagerException if no instance with {@code key} present in manager.
     */
    O get(String key) throws ManagerException;

    /**
     * Creates a new instance of class <tt>&lt;O&gt;</tt>.
     *
     * @param factory factory of managed objects.
     * @return new instance of managed object.
     */
    O create(ManagedObjFactory<K, O, ManagedObjManager<String, O>> factory);

    /**
     * Remove managed object from manager.
     *
     * @param object managed object.
     */
    void remove(O object);

    /**
     * Returns a {@link Set} view of the keys contained in this manager.
     *
     * @return a set view of the keys contained in this manager.
     * @see ConcurrentHashMap#keySet()
     */
    Set<String> keySet();

    /**
     * Returns a {@link Collection} view of the values contained in this manager.
     *
     * @return a collection view of the values contained in this manager.
     * @see ConcurrentHashMap#values()
     */
    List<O> values();

    /**
     * @return {@code true}, if managed is empty, else {@code false}.
     */
    boolean isEmpty();

    /**
     * Return count of objects in manager.
     *
     * @return count.
     */
    long size();

}
