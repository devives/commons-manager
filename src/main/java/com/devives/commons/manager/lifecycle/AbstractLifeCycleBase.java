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

import com.devives.commons.lang.ExceptionUtils;
import com.devives.commons.lang.call.Try;
import com.devives.commons.publisher.Publisher;
import com.devives.commons.state.StateHolder;
import com.devives.commons.state.Stateful;

import java.util.Objects;

abstract class AbstractLifeCycleBase extends Stateful implements LifeCycle {

    private static final long serialVersionUID = 1L;
    private final Publisher<Listener> publisher_;

    protected AbstractLifeCycleBase(StateHolder stateHolder, Publisher<Listener> publisher) {
        super(stateHolder);
        publisher_ = Objects.requireNonNull(publisher);
    }

    protected Publisher<Listener> getPublisher() {
        return publisher_;
    }

    @Override
    public void addListener(Listener listener) {
        publisher_.getListeners().add(Objects.requireNonNull(listener));
    }

    @Override
    public void removeListener(Listener listener) {
        publisher_.getListeners().remove(Objects.requireNonNull(listener));
    }

    @Override
    public boolean isStarted() {
        return getStateHolder().isExpected(States.STARTED);
    }

    @Override
    public boolean isStarting() {
        return getStateHolder().isExpected(States.STARTING);
    }

    @Override
    public boolean isStopping() {
        return getStateHolder().isExpected(States.STOPPING);
    }

    @Override
    public boolean isStopped() {
        return getStateHolder().isExpected(States.STOPPED);
    }

    @Override
    public boolean isFailed() {
        return getStateHolder().isExpected(States.FAILED);
    }

    @Override
    public void start() throws Exception {
        if (getStateHolder().isExpected(States.STOPPED)) {
            Try.runnable(() -> {
                beginStart();
                doStart();
                endStart();
            }).onCatch((th -> {
                doFailed(th);
                throw th;
            })).run();
        }
    }

    @Override
    public void stop() throws Exception {
        if (getStateHolder().isExpected(States.STARTED)) {
            Try.runnable(() -> {
                beginStop();
                doStop();
                endStop();
            }).onCatch((th -> {
                doFailed(th);
                throw th;
            })).run();
        }
    }

    private void beginStart() {
        getStateHolder().set(States.STARTING);
        onStarting();
        publisher_.publish(listener -> listener.onStarting(this));
    }

    private void doStart() throws Exception {
        onStart();
    }

    private void endStart() {
        getStateHolder().set(States.STARTED);
        onStarted();
        publisher_.publish(listener -> listener.onStarted(this));
    }

    protected void onStarting() {

    }

    protected abstract void onStart() throws Exception;

    protected void onStarted() {

    }

    private void beginStop() {
        getStateHolder().set(States.STOPPING);
        onStopping();
        publisher_.publish(listener -> listener.onStopping(this));
    }

    private void doStop() throws Exception {
        onStop();
    }

    private void endStop() {
        getStateHolder().set(States.STOPPED);
        onStopped();
        publisher_.publish(listener -> listener.onStopped(this));
    }

    protected void onStopping() {

    }

    protected abstract void onStop() throws Exception;

    protected void onStopped() {

    }

    private void doFailed(Throwable th) {
        getStateHolder().set(States.FAILED);
        ExceptionUtils.collectAndThrow(
                () -> onFailed(th),
                () -> publisher_.publish(listener -> listener.onFailure(this, th))
        );
    }

    protected void onFailed(Throwable th) {

    }

}
