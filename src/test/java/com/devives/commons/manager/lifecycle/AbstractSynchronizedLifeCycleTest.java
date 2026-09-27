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

import com.devives.commons.state.State;
import com.devives.commons.state.StateHolder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AbstractSynchronizedLifeCycleTest {

    private static final long GATE_AWAIT_TIMEOUT_MS = 10_000L;

    private static final long STATE_QUERY_TIMEOUT_MS = 500L;

    @Test
    public void start_doStartIsRunning_stateQueriesAreNotBlocked() throws Exception {
        final GatedSynchronizedLifeCycle lifeCycle = new GatedSynchronizedLifeCycle();
        final ExecutorService executor = newDaemonExecutor();
        try {
            final Future<?> starting = submitLifeCycleCall(executor, lifeCycle::start);
            Assertions.assertTrue(lifeCycle.startGate.awaitEntered(GATE_AWAIT_TIMEOUT_MS), "onStart() is not entered");

            Assertions.assertEquals(
                    LifeCycleStates.STARTING,
                    awaitStateQuery("SynchronizedStateHolderImpl.get()", executor, lifeCycle.stateHolder()::get)
            );
            Assertions.assertTrue(
                    awaitStateQuery("SynchronizedStateHolderImpl.isExpected(State...)", executor,
                            () -> lifeCycle.stateHolder().isExpected(LifeCycleStates.STARTING))
            );
            awaitStateQuery("SynchronizedStateHolderImpl.validate(State...)", executor, () -> {
                lifeCycle.stateHolder().validate(LifeCycleStates.STARTING);
                return null;
            });

            lifeCycle.startGate.releasePhase();
            starting.get(GATE_AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            Assertions.assertEquals(LifeCycleStates.STARTED, lifeCycle.stateHolder().get());
        } finally {
            lifeCycle.startGate.releasePhase();
            lifeCycle.stopGate.releasePhase();
            executor.shutdownNow();
        }
    }

    @Test
    public void stop_doStopIsRunning_stateQueriesAreNotBlocked() throws Exception {
        final GatedSynchronizedLifeCycle lifeCycle = new GatedSynchronizedLifeCycle();
        final ExecutorService executor = newDaemonExecutor();
        try {
            final Future<?> starting = submitLifeCycleCall(executor, lifeCycle::start);
            Assertions.assertTrue(lifeCycle.startGate.awaitEntered(GATE_AWAIT_TIMEOUT_MS), "onStart() is not entered");
            lifeCycle.startGate.releasePhase();
            starting.get(GATE_AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            final Future<?> stopping = submitLifeCycleCall(executor, lifeCycle::stop);
            Assertions.assertTrue(lifeCycle.stopGate.awaitEntered(GATE_AWAIT_TIMEOUT_MS), "onStop() is not entered");

            Assertions.assertEquals(
                    LifeCycleStates.STOPPING,
                    awaitStateQuery("SynchronizedStateHolderImpl.get()", executor, lifeCycle.stateHolder()::get)
            );
            Assertions.assertTrue(
                    awaitStateQuery("SynchronizedStateHolderImpl.isExpected(State...)", executor,
                            () -> lifeCycle.stateHolder().isExpected(LifeCycleStates.STOPPING))
            );
            awaitStateQuery("SynchronizedStateHolderImpl.validate(State...)", executor, () -> {
                lifeCycle.stateHolder().validate(LifeCycleStates.STOPPING);
                return null;
            });

            lifeCycle.stopGate.releasePhase();
            stopping.get(GATE_AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            Assertions.assertEquals(LifeCycleStates.STOPPED, lifeCycle.stateHolder().get());
        } finally {
            lifeCycle.startGate.releasePhase();
            lifeCycle.stopGate.releasePhase();
            executor.shutdownNow();
        }
    }

    @Test
    public void start_doStartIsRunning_unsynchronizedLifeCycleStateQueriesReturn() throws Exception {
        final GatedPlainLifeCycle lifeCycle = new GatedPlainLifeCycle();
        final ExecutorService executor = newDaemonExecutor();
        try {
            final Future<?> starting = submitLifeCycleCall(executor, lifeCycle::start);
            Assertions.assertTrue(lifeCycle.startGate.awaitEntered(GATE_AWAIT_TIMEOUT_MS), "onStart() is not entered");

            // StateHolderImpl.state_ не volatile, поэтому прочитанное во время onStart() значение может быть
            // устаревшим: закрепляется только факт возврата из метода без ожидания мьютекса.
            awaitStateQuery("StateHolderImpl.get()", executor, lifeCycle.stateHolder()::get);
            awaitStateQuery("StateHolderImpl.isExpected(State...)", executor,
                    () -> lifeCycle.stateHolder().isExpected(LifeCycleStates.STARTING));

            lifeCycle.startGate.releasePhase();
            starting.get(GATE_AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } finally {
            lifeCycle.startGate.releasePhase();
            executor.shutdownNow();
        }
    }

    private static Future<?> submitLifeCycleCall(ExecutorService executor, LifeCycleCall call) {
        return executor.submit(() -> {
            call.run();
            return null;
        });
    }

    private interface LifeCycleCall {
        void run() throws Exception;
    }

    private static <T> T awaitStateQuery(String method, ExecutorService executor, Callable<T> query) throws Exception {
        final Future<T> future = executor.submit(query);
        try {
            return future.get(STATE_QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException th) {
            // Поток, заблокированный на мониторе, прерыванием не снимается, поэтому задача не отменяется:
            // она освобождается вызовом releasePhase() в finally вызывающего теста.
            throw new AssertionError("Метод " + method + " не вернулся за " + STATE_QUERY_TIMEOUT_MS
                    + " мс: вызов заблокирован мьютексом holder'а, который удерживает поток start()/stop()", th);
        }
    }

    private static ExecutorService newDaemonExecutor() {
        return Executors.newCachedThreadPool(runnable -> {
            final Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });
    }

    private static final class PhaseGate {
        private final CountDownLatch entered_ = new CountDownLatch(1);
        private final CountDownLatch release_ = new CountDownLatch(1);

        private void enter() throws InterruptedException {
            entered_.countDown();
            release_.await();
        }

        private boolean awaitEntered(long timeoutMs) throws InterruptedException {
            return entered_.await(timeoutMs, TimeUnit.MILLISECONDS);
        }

        private void releasePhase() {
            release_.countDown();
        }
    }

    private static final class GatedSynchronizedLifeCycle extends AbstractSynchronizedLifeCycle {
        private final PhaseGate startGate = new PhaseGate();
        private final PhaseGate stopGate = new PhaseGate();

        @Override
        protected void onStart() throws Exception {
            startGate.enter();
        }

        @Override
        protected void onStop() throws Exception {
            stopGate.enter();
        }

        private StateHolder<State> stateHolder() {
            return getStateHolder();
        }
    }

    private static final class GatedPlainLifeCycle extends AbstractLifeCycle {
        private final PhaseGate startGate = new PhaseGate();

        @Override
        protected void onStart() throws Exception {
            startGate.enter();
        }

        @Override
        protected void onStop() throws Exception {
        }

        private StateHolder<State> stateHolder() {
            return getStateHolder();
        }
    }

}
