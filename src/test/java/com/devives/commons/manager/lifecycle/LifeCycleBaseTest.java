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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LifeCycleBaseTest {

    @Test
    public void start_failureWithDefaultAction_rethrowsAndMarksFailed() {
        final RuntimeException expected = new RuntimeException("start failed");
        final AbstractLifeCycle lifeCycle = new DefaultFailureLifeCycle(expected, null);

        final RuntimeException actual = Assertions.assertThrows(RuntimeException.class, lifeCycle::start);

        Assertions.assertSame(expected, actual);
        Assertions.assertTrue(lifeCycle.isFailed());
    }

    @Test
    public void start_failureWithSuppressAction_marksFailedWithoutRethrow() throws Exception {
        final AbstractLifeCycle lifeCycle = new SuppressingFailureLifeCycle(new RuntimeException("start failed"), null);

        lifeCycle.start();

        Assertions.assertTrue(lifeCycle.isFailed());
    }

    @Test
    public void stop_failureWithSuppressAction_marksFailedWithoutRethrow() throws Exception {
        final AbstractLifeCycle lifeCycle = new SuppressingFailureLifeCycle(null, new RuntimeException("stop failed"));

        lifeCycle.start();
        lifeCycle.stop();

        Assertions.assertTrue(lifeCycle.isFailed());
    }

    private static class DefaultFailureLifeCycle extends AbstractLifeCycle {
        private final RuntimeException startFailure;
        private final RuntimeException stopFailure;

        private DefaultFailureLifeCycle(RuntimeException startFailure, RuntimeException stopFailure) {
            this.startFailure = startFailure;
            this.stopFailure = stopFailure;
        }

        @Override
        protected void onStart() {
            if (startFailure != null) {
                throw startFailure;
            }
        }

        @Override
        protected void onStop() {
            if (stopFailure != null) {
                throw stopFailure;
            }
        }
    }

    private static final class SuppressingFailureLifeCycle extends DefaultFailureLifeCycle {

        private SuppressingFailureLifeCycle(RuntimeException startFailure, RuntimeException stopFailure) {
            super(startFailure, stopFailure);
        }

        @Override
        protected FailureAction onFailure(Throwable th) {
            return FailureAction.SUPPRESS;
        }
    }

}
