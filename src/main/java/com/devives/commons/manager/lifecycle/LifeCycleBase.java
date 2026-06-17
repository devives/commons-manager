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
import com.devives.commons.state.State;
import com.devives.commons.state.StateHolder;
import com.devives.commons.state.Stateful;

import java.util.Objects;

abstract class LifeCycleBase extends Stateful<State> implements LifeCycle {

    private static final long serialVersionUID = 1L;
    private final Publisher<Listener> publisher_;
    private volatile boolean isStartAfterFailAllowed = true;

    protected LifeCycleBase(StateHolder<State> stateHolder, Publisher<Listener> publisher) {
        super(stateHolder);
        publisher_ = Objects.requireNonNull(publisher);
    }

    protected boolean isStartAfterFailAllowed() {
        return isStartAfterFailAllowed;
    }

    protected void setStartAfterFailAllowed(boolean value) {
        isStartAfterFailAllowed = value;
    }

    protected Publisher<Listener> getPublisher() {
        return publisher_;
    }

    protected void addListener(Listener listener) {
        publisher_.getListeners().add(Objects.requireNonNull(listener));
    }

    protected void removeListener(Listener listener) {
        publisher_.getListeners().remove(Objects.requireNonNull(listener));
    }

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
        State[] expected = isStartAfterFailAllowed
                ? new State[]{States.FAILED, States.STOPPED}
                : new State[]{States.STOPPED};
        if (getStateHolder().trySet(expected, States.STARTING)) {
            Try.runnable(() -> {
                beginStart();
                doStart();
                getStateHolder().set(States.STARTED);
                endStart();
            }).onCatch((th -> {
                handleFailure(th);
                throw th;
            })).run();
        }
    }

    @Override
    public void stop() throws Exception {
        if (getStateHolder().trySet(States.STARTED, States.STOPPING)) {
            Try.runnable(() -> {
                beginStop();
                doStop();
                getStateHolder().set(States.STOPPED);
                endStop();
            }).onCatch((th -> {
                handleFailure(th);
                throw th;
            })).run();
        }
    }

    private void beginStart() {
        onStarting();
        publishStarting();
    }

    protected void publishStarting() {
        publisher_.publish(listener -> listener.onStarting(this));
    }

    private void doStart() throws Exception {
        onStart();
    }

    private void endStart() {
        onStarted();
        publishStarted();
    }

    protected void publishStarted() {
        publisher_.publish(listener -> listener.onStarted(this));
    }

    protected void onStarting() {
    }

    protected abstract void onStart() throws Exception;

    protected void onStarted() {
    }

    private void beginStop() {
        onStopping();
        publishStopping();
    }

    protected void onStopping() {
    }

    protected void publishStopping() {
        publisher_.publish(listener -> listener.onStopping(this));
    }

    private void doStop() throws Exception {
        onStop();
    }

    protected abstract void onStop() throws Exception;

    private void endStop() {
        onStopped();
        publishStopped();
    }

    protected void onStopped() {
    }

    protected void publishStopped() {
        publisher_.publish(listener -> listener.onStopped(this));
    }

    /**
     * Состояние {@link States#FAILED} устанавливается после вызова всех методов,
     * что бы можно было понять: на каком этапе возникла ошибка.
     *
     * @param th исключение, приведшее к краху.
     */
    private void handleFailure(Throwable th) {
        ExceptionUtils.collectAndThrow(
                () -> onFailure(th),
                () -> publisher_.publish(listener -> listener.onFailure(this, th)),
                () -> getStateHolder().set(States.FAILED)
        );
    }

    protected void onFailure(Throwable th) {

    }

}
