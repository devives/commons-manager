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

import com.devives.commons.listener.ListenersBuilder;
import com.devives.commons.publisher.Publisher;
import com.devives.commons.publisher.Publishers;
import com.devives.commons.state.State;
import com.devives.commons.state.StateHolder;
import com.devives.commons.state.SynchronizedStateHolder;
import com.devives.commons.state.SynchronizedStateHolderImpl;

public abstract class AbstractSynchronizedLifeCycle extends LifeCycleBase {
    private static final long serialVersionUID = 1L;

    public AbstractSynchronizedLifeCycle() {
        this(LifeCycleStates.STOPPED);
    }

    public AbstractSynchronizedLifeCycle(State initialState) {
        this(new SynchronizedStateHolderImpl<>(initialState),
                Publishers.<Listener>builder()
                        .listeners(ListenersBuilder::setSynchronized)
                        .setIndependentDelivery()
                        .build());
    }

    public AbstractSynchronizedLifeCycle(StateHolder<State> stateHolder, Publisher<Listener> publisher) {
        super(stateHolder, publisher);
    }

    @Override
    protected SynchronizedStateHolder<State> getStateHolder() {
        return (SynchronizedStateHolder<State>) super.getStateHolder();
    }

    @Override
    public void start() throws Exception {
        getStateHolder().performAtomicWork(super::start);
    }

    @Override
    public void stop() throws Exception {
        getStateHolder().performAtomicWork(super::stop);
    }

}
