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

import com.devives.commons.publisher.Publisher;
import com.devives.commons.publisher.Publishers;
import com.devives.commons.state.State;
import com.devives.commons.state.StateHolder;
import com.devives.commons.state.StateHolderImpl;

public abstract class LifeCycleAbst<SELF extends LifeCycle, LISTENER extends LifeCycle.Listener<SELF>>
        extends LifeCycleBaseAbst<SELF, LISTENER> {

    public LifeCycleAbst() {
        this(States.STOPPED);
    }

    public LifeCycleAbst(State initialState) {
        this(new StateHolderImpl(initialState),
                Publishers.<LISTENER>builder().setIndependentDelivery().build());
    }

    public LifeCycleAbst(StateHolder stateHolder, Publisher<LISTENER> publisher) {
        super(stateHolder, publisher);
    }

}
