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
package com.devives.commons.lifecycle;

import java.util.function.Supplier;

/**
 * State holder interface. Implementations can be thread-safe or thread-unsafe.
 */
public interface StateHolder {

    /**
     * Return object state.
     *
     * @return state.
     */
    State get();

    /**
     * Try set the objet state.
     * <p>
     * If current state is equal {@code expected}, {@code value} will be set.
     *
     * @param expected expected state.
     * @param value    new state.
     * @return {@code true}, if value was set, else {@code false}.
     */
    boolean trySet(State expected, State value);

    /**
     * Set the objet state.
     *
     * @param state state.
     */
    void set(State state);

    /**
     * Checks whether the current state is equivalent to the expected state.
     *
     * @param expected Expected state.
     * @throws InvalidStateException if the current state is equivalent to the expected state.
     */
    void validate(State expected);

    /**
     * Checks whether the current state is equivalent to the expected state.
     *
     * @param expected          Expected state.
     * @param exceptionSupplier Exception instance supplier.
     * @param <E>               Type of exception.
     * @throws E if the current state is equivalent to the expected state.
     */
    <E extends Exception> void validate(State expected, Supplier<E> exceptionSupplier) throws E;

}