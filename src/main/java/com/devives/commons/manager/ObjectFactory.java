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

/**
 * Factory of objects managed by the manager.
 *
 * @param <O> type of objects to create.
 */
public interface ObjectFactory<O> extends LifeCycleAdapter<O> {

    /**
     * Creates a new instance of class <tt>&lt;O&gt;</tt>.
     *
     * <h3>Notes</h3>
     * The method has no arguments, to allow the default constructor to be used as a factory.
     * <pre>{@code
     * String key = "Item1";
     * SimpleTestItem item1 = manager.computeIfAbsent(key, () -> SimpleTestItem::new);
     * }</pre>
     * If you need to pass a key or a reference to a manager to the constructor, you can do this through a closure.
     * <pre>{@code
     * Manager manager = getManager();
     * String key = "Item1";
     * SimpleTestItem item1 = manager.computeIfAbsent(key, () -> new SimpleTestItem(key, manager));
     * }</pre>
     *
     * @return new instance of <tt>&lt;O&gt;</tt>.
     * @throws Exception if creation failed.
     */
    O createObject() throws Exception;
}