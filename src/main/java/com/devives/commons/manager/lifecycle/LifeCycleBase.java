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
import com.devives.commons.lang.Ref;
import com.devives.commons.lang.call.Try;
import com.devives.commons.publisher.Publisher;
import com.devives.commons.state.State;
import com.devives.commons.state.StateHolder;
import com.devives.commons.state.Stateful;

import java.util.Objects;

/**
 * Base implementation of {@link LifeCycle} that drives the state machine of {@link LifeCycleStates}
 * and notifies {@link LifeCycle.Listener}s about transitions.
 * <p>
 * Subclasses implement {@link #onStart()} and {@link #onStop()} and may override the
 * {@code onStarting}/{@code onStarted}/{@code onStopping}/{@code onStopped} hooks and
 * {@link #onFailure(Ref)}.
 *
 * <p><strong>State transitions.</strong></p>
 * <ul>
 *     <li>{@link #start()}: {@code STOPPED} (or {@code FAILED}, see {@link #isStartAfterFailAllowed()})
 *     &rarr; {@code STARTING} &rarr; {@code STARTED};
 *     <li>{@link #stop()}: {@code STARTED} &rarr; {@code STOPPING} &rarr; {@code STOPPED};
 *     <li>any failure during {@code start()} or {@code stop()} &rarr; {@code FAILED}.
 * </ul>
 * A call to {@code start()} or {@code stop()} made in any other state is silently ignored.
 *
 * <p><strong>Thread safety.</strong></p>
 * The transition into {@code STARTING}/{@code STOPPING} is an atomic compare-and-set on the
 * {@link StateHolder}, so of concurrent callers only one performs the operation; the others return
 * immediately without waiting for it to complete. Thread safety of listener registration and
 * notification is defined by the supplied {@link Publisher}.
 */
abstract class LifeCycleBase extends Stateful<State> implements LifeCycle {

    private static final long serialVersionUID = 1L;
    private final Publisher<Listener> publisher_;
    private volatile boolean isStartAfterFailAllowed = true;

    /**
     * Creates a life cycle object.
     *
     * @param stateHolder holder of the current state; its current value is used as the initial state.
     * @param publisher   publisher used to notify registered {@link LifeCycle.Listener}s.
     * @throws NullPointerException if {@code publisher} is {@code null}.
     */
    protected LifeCycleBase(StateHolder<State> stateHolder, Publisher<Listener> publisher) {
        super(stateHolder);
        publisher_ = Objects.requireNonNull(publisher);
    }

    /**
     * Returns whether {@link #start()} may be called again after a failure,
     * i.e. from the {@link LifeCycleStates#FAILED} state. Allowed by default.
     *
     * @return {@code true} if restart from {@code FAILED} is allowed.
     */
    protected boolean isStartAfterFailAllowed() {
        return isStartAfterFailAllowed;
    }

    /**
     * Sets whether {@link #start()} may be called again after a failure.
     * If {@code false}, a call to {@code start()} in the {@link LifeCycleStates#FAILED} state is ignored.
     *
     * @param value {@code true} to allow restart from {@code FAILED}.
     */
    protected void setStartAfterFailAllowed(boolean value) {
        isStartAfterFailAllowed = value;
    }

    /**
     * Returns the publisher used to notify {@link LifeCycle.Listener}s.
     *
     * @return the publisher (non-null).
     */
    protected Publisher<Listener> getPublisher() {
        return publisher_;
    }

    /**
     * Registers a listener of life cycle events.
     *
     * @param listener the listener to register.
     * @throws NullPointerException if {@code listener} is {@code null}.
     */
    protected void addListener(Listener listener) {
        publisher_.getListeners().add(Objects.requireNonNull(listener));
    }

    /**
     * Unregisters a previously registered listener of life cycle events.
     *
     * @param listener the listener to unregister.
     * @throws NullPointerException if {@code listener} is {@code null}.
     */
    protected void removeListener(Listener listener) {
        publisher_.getListeners().remove(Objects.requireNonNull(listener));
    }

    /**
     * Returns whether the current state is {@link LifeCycleStates#STARTED}.
     *
     * @return {@code true} if the object is in the {@code STARTED} state.
     */
    protected boolean isStarted() {
        return getStateHolder().isExpected(LifeCycleStates.STARTED);
    }

    /**
     * Returns whether the current state is {@link LifeCycleStates#STARTING}.
     *
     * @return {@code true} if the object is in the {@code STARTING} state.
     */
    protected boolean isStarting() {
        return getStateHolder().isExpected(LifeCycleStates.STARTING);
    }

    /**
     * Returns whether the current state is {@link LifeCycleStates#STOPPING}.
     *
     * @return {@code true} if the object is in the {@code STOPPING} state.
     */
    protected boolean isStopping() {
        return getStateHolder().isExpected(LifeCycleStates.STOPPING);
    }

    /**
     * Returns whether the current state is {@link LifeCycleStates#STOPPED}.
     *
     * @return {@code true} if the object is in the {@code STOPPED} state.
     */
    protected boolean isStopped() {
        return getStateHolder().isExpected(LifeCycleStates.STOPPED);
    }

    /**
     * Returns whether the current state is {@link LifeCycleStates#FAILED}.
     *
     * @return {@code true} if the object is in the {@code FAILED} state.
     */
    protected boolean isFailed() {
        return getStateHolder().isExpected(LifeCycleStates.FAILED);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Does nothing unless the object is in the {@link LifeCycleStates#STOPPED} state
     * (or {@link LifeCycleStates#FAILED}, if {@link #isStartAfterFailAllowed()}).
     * Otherwise switches to {@link LifeCycleStates#STARTING} and performs:
     * <ol>
     *     <li>{@link #onStarting()};
     *     <li>notification {@link LifeCycle.Listener#onStarting(LifeCycle)};
     *     <li>{@link #onStart()};
     *     <li>switch to {@link LifeCycleStates#STARTED};
     *     <li>{@link #onStarted()};
     *     <li>notification {@link LifeCycle.Listener#onStarted(LifeCycle)}.
     * </ol>
     * If any step fails, {@link #onFailure(Ref)} is called, listeners are notified via
     * {@link LifeCycle.Listener#onFailure(LifeCycle, Throwable)} and the state is set to
     * {@link LifeCycleStates#FAILED}.
     *
     * @throws Exception the start failure (possibly replaced by {@link #onFailure(Ref)}), if it returned
     *                   {@link FailureAction#RETHROW}, or an exception thrown during failure handling
     *                   (with the original failure added to it as suppressed).
     */
    @Override
    public void start() throws Exception {
        State[] expected = isStartAfterFailAllowed
                ? new State[]{LifeCycleStates.FAILED, LifeCycleStates.STOPPED}
                : new State[]{LifeCycleStates.STOPPED};
        if (getStateHolder().trySet(expected, LifeCycleStates.STARTING)) {
            Try.runnable(() -> {
                beginStart();
                doStart();
                getStateHolder().set(LifeCycleStates.STARTED);
                endStart();
            }).onCatch((th) -> {
                final Ref<Throwable> thRef = Ref.of(th);
                if (handleFailure(thRef) == FailureAction.RETHROW) {
                    throw thRef.get();
                }
            }).run();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Does nothing unless the object is in the {@link LifeCycleStates#STARTED} state.
     * Otherwise switches to {@link LifeCycleStates#STOPPING} and performs:
     * <ol>
     *     <li>{@link #onStopping()};
     *     <li>notification {@link LifeCycle.Listener#onStopping(LifeCycle)};
     *     <li>{@link #onStop()};
     *     <li>switch to {@link LifeCycleStates#STOPPED};
     *     <li>{@link #onStopped()};
     *     <li>notification {@link LifeCycle.Listener#onStopped(LifeCycle)}.
     * </ol>
     * If any step fails, {@link #onFailure(Ref)} is called, listeners are notified via
     * {@link LifeCycle.Listener#onFailure(LifeCycle, Throwable)} and the state is set to
     * {@link LifeCycleStates#FAILED}.
     *
     * @throws Exception the stop failure (possibly replaced by {@link #onFailure(Ref)}), if it returned
     *                   {@link FailureAction#RETHROW}, or an exception thrown during failure handling
     *                   (with the original failure added to it as suppressed).
     */
    @Override
    public void stop() throws Exception {
        if (getStateHolder().trySet(LifeCycleStates.STARTED, LifeCycleStates.STOPPING)) {
            Try.runnable(() -> {
                beginStop();
                doStop();
                getStateHolder().set(LifeCycleStates.STOPPED);
                endStop();
            }).onCatch((th) -> {
                final Ref<Throwable> thRef = Ref.of(th);
                if (handleFailure(thRef) == FailureAction.RETHROW) {
                    throw thRef.get();
                }
            }).run();
        }
    }

    private void beginStart() {
        onStarting();
        publishStarting();
    }

    private void doStart() throws Exception {
        onStart();
    }

    private void endStart() {
        onStarted();
        publishStarted();
    }

    /**
     * Called at the beginning of {@link #start()}, in the {@link LifeCycleStates#STARTING} state,
     * before listeners are notified. Does nothing by default.
     */
    protected void onStarting() {
    }

    /**
     * Starts the object and its subordinate objects.
     * Called in the {@link LifeCycleStates#STARTING} state.
     *
     * @throws Exception if the object fails to start.
     */
    protected abstract void onStart() throws Exception;

    /**
     * Called after the object has started successfully, in the {@link LifeCycleStates#STARTED} state,
     * before listeners are notified. Does nothing by default.
     */
    protected void onStarted() {
    }

    private void beginStop() {
        onStopping();
        publishStopping();
    }

    /**
     * Called at the beginning of {@link #stop()}, in the {@link LifeCycleStates#STOPPING} state,
     * before listeners are notified. Does nothing by default.
     */
    protected void onStopping() {
    }

    private void doStop() throws Exception {
        onStop();
    }

    /**
     * Stops the object and its subordinate objects.
     * Called in the {@link LifeCycleStates#STOPPING} state.
     *
     * @throws Exception if the object fails to stop.
     */
    protected abstract void onStop() throws Exception;

    private void endStop() {
        onStopped();
        publishStopped();
    }

    /**
     * Called after the object has stopped successfully, in the {@link LifeCycleStates#STOPPED} state,
     * before listeners are notified. Does nothing by default.
     */
    protected void onStopped() {
    }

    /**
     * Handles a failure of {@link #start()} or {@link #stop()}: calls {@link #onFailure(Ref)},
     * notifies listeners and sets the {@link LifeCycleStates#FAILED} state. All three steps are
     * executed even if some of them fail; their exceptions are collected and thrown afterwards.
     * The {@code FAILED} state is set last, so that handlers can determine at which stage the failure occurred.
     *
     * Listeners receive the failure held by {@code thRef} after {@link #onFailure(Ref)} has returned,
     * i.e. the replacement, if {@code onFailure} has replaced it.
     *
     * @param thRef reference to the failure; {@link #onFailure(Ref)} may replace the value.
     * @return the action that defines whether the failure must be rethrown.
     */
    private FailureAction handleFailure(Ref<Throwable> thRef) {
        final Ref<FailureAction> action = Ref.of(FailureAction.RETHROW);
        ExceptionUtils.collectAndThrow(
                () -> action.set(Objects.requireNonNull(onFailure(thRef))),
                () -> publishFailure(thRef.get()),
                () -> getStateHolder().set(LifeCycleStates.FAILED)
        );
        return action.get();
    }

    /**
     * Called when {@link #start()} or {@link #stop()} fails, before listeners are notified
     * and before the state is set to {@link LifeCycleStates#FAILED}.
     * <p>
     * The stage at which the failure occurred can be determined from {@code getStateHolder().get()}:
     * {@code STARTING}/{@code STOPPING} &mdash; in {@code onStarting}/{@code onStart} or
     * {@code onStopping}/{@code onStop} (or the corresponding notification);
     * {@code STARTED}/{@code STOPPED} &mdash; in {@link #onStarted()}/{@link #onStopped()}
     * or the corresponding notification.
     * <p>
     * The failure may be replaced (e.g. wrapped) via {@code thRef.set(...)}; {@code null} is not allowed.
     * The replacement is passed to {@link LifeCycle.Listener#onFailure(LifeCycle, Throwable)} and,
     * if {@link FailureAction#RETHROW} is returned, thrown from {@link #start()}/{@link #stop()}.
     *
     * @param thRef reference to the failure (non-nullable).
     * @return the action that defines whether the failure must be rethrown (non-null);
     * {@link FailureAction#RETHROW} by default.
     */
    protected FailureAction onFailure(Ref<Throwable> thRef) {
        return FailureAction.RETHROW;
    }

    private void publishStarting() {
        publisher_.publish(listener -> listener.onStarting(this));
    }

    private void publishStarted() {
        publisher_.publish(listener -> listener.onStarted(this));
    }

    private void publishStopping() {
        publisher_.publish(listener -> listener.onStopping(this));
    }

    private void publishStopped() {
        publisher_.publish(listener -> listener.onStopped(this));
    }

    private void publishFailure(Throwable th) {
        publisher_.publish(listener -> listener.onFailure(this, th));
    }

}
