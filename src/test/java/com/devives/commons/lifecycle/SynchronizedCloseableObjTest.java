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

import com.devives.commons.Task;
import com.devives.commons.lang.ExceptionUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class SynchronizedCloseableObjTest {

    private final ExecutorService executorService_ = Executors.newScheduledThreadPool(2);

    @Test
    public void test() throws Exception {
        TestCloseableObj testCloseableObj = new TestCloseableObj();
        List<Future<?>> futureList = new ArrayList<>();
        List<Task> taskList = Arrays.asList(new TaskClose(testCloseableObj, 0), new TaskClose(testCloseableObj, 1000));
        taskList.forEach(task -> futureList.add(executorService_.submit(task)));
        try {
            try {
                futureList.forEach(future -> {
                    // Таймаут на случай блокировки потоков и зависания.
                    ExceptionUtils.passChecked(() -> future.get(5, TimeUnit.SECONDS));
                });
            } finally {
                executorService_.shutdownNow();
            }
        } finally {
            taskList.forEach(task -> Assertions.assertEquals(1, task.getSuccessCount()));
            taskList.forEach(Task::printStatistic);
        }
        Assertions.assertEquals(1, testCloseableObj.getCallCount());
    }

    private static class TaskClose extends Task {
        private final TestCloseableObj testCloseableObj_;
        private final long delay_;

        private TaskClose(TestCloseableObj testCloseableObj, long delay) {
            testCloseableObj_ = testCloseableObj;
            delay_ = delay;
        }

        @Override
        protected void doWork() throws Exception {
            Thread.sleep(delay_);
            testCloseableObj_.close();
            Assertions.assertTrue(testCloseableObj_.isClosed());
            getSuccessCounter().incrementAndGet();
            cancel();
        }
    }

    private static class TestCloseableObj extends SynchronizedCloseableAbst {

        private final AtomicLong callCounter_ = new AtomicLong();

        @Override
        protected void onClose() throws Exception {
            callCounter_.incrementAndGet();
            Thread.sleep(2000);
        }

        public long getCallCount() {
            return callCounter_.get();
        }
    }

}
