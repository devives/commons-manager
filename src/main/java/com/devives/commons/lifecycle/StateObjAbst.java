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

/**
 * Base abstract class of stateful objects.
 */
public abstract class StateObjAbst {
    private final StateHolder stateHolder_;

    /**
     * Return state holder instance.
     *
     * @return State holder instance.
     */
    protected StateHolder getStateHolder() {
        return stateHolder_;
    }

    protected StateObjAbst() {
        stateHolder_ = Objects.requireNonNull(buildStateHolder(), "Method `buildStateHolder()` return `null`.");
    }

    /**
     * Строит контейнер состояния объекта.
     *
     * @return new {@link StateHolder} instance.
     */
    protected abstract StateHolder buildStateHolder();

}
