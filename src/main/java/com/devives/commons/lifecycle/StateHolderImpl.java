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

import java.util.Objects;
import java.util.function.Supplier;

public class StateHolderImpl implements StateHolder {
    protected State state;

    public StateHolderImpl(State initialState) {
        this.state = Objects.requireNonNull(initialState, "initialState");
    }

    public State get() {
        return state;
    }

    @Override
    public boolean trySet(State expected, State state) {
        final boolean result = this.state == Objects.requireNonNull(expected, "expected");
        if (result) {
            this.state = Objects.requireNonNull(state, "state");
        }
        return result;
    }

    public void set(State state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    @Override
    public void validate(State expected) {
        validate(expected, () -> new InvalidStateException("State '" + state + "' not equal expected '" + expected + "'"));
    }

    @Override
    public <E extends Exception> void validate(State expected, Supplier<E> exceptionSupplier) throws E {
        if (!Objects.equals(state, Objects.requireNonNull(expected, "expected"))) {
            throw exceptionSupplier.get();
        }
    }

}
