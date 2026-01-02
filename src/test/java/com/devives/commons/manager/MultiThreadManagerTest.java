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

import com.devives.commons.Task;
import com.devives.commons.lang.ExceptionUtils;
import com.devives.commons.manager.lock.RWLockSource;
import com.devives.commons.manager.lock.SyncLockSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class MultiThreadManagerTest {

    private final ExecutorService executorService_ = Executors.newScheduledThreadPool(10);
    private Manager<String, SimpleItem> manager_;
    private final int durationSec = 1;

    private static Stream<Arguments> provideConcurrentManager() {
        return Stream.of(
                Arguments.of("FairRWLock", new ConcurrentHashManager<String, SimpleItem>(new RWLockSource<>(true))),
                Arguments.of("NoFairRWLock", new ConcurrentHashManager<String, SimpleItem>(new RWLockSource<>(false))),
                Arguments.of("SyncLock", new ConcurrentHashManager<String, SimpleItem>(new SyncLockSource<>()))
        );
    }

    @ParameterizedTest
    @MethodSource("provideConcurrentManager")
    public void test_onAssertErrors(String desc, Manager<String, SimpleItem> manager) throws Exception {
        manager_ = manager;
        List<Future<?>> futureList = new ArrayList<>();
        List<Task> taskList = Arrays.asList(new TaskCompute(), new TaskGet(), new TaskGetIfPresent(), new TaskRemove(), new TaskKeySet(), new TaskValues());
        taskList.forEach(task -> futureList.add(executorService_.submit(task)));
        Thread.sleep(TimeUnit.SECONDS.toMillis(durationSec));
        taskList.forEach(Task::cancel);
        try {
            try {
                futureList.forEach(future -> {
                    // Таймаут на случай блокировки потоков и зависания.
                    ExceptionUtils.passChecked(() -> future.get(2, TimeUnit.SECONDS));
                });
            } finally {
                executorService_.shutdownNow();
            }
            TaskCompute taskCompute = taskList.stream().filter(TaskCompute.class::isInstance).findFirst().map(TaskCompute.class::cast).get();
            TaskRemove taskRemove = taskList.stream().filter(TaskRemove.class::isInstance).findFirst().map(TaskRemove.class::cast).get();
            TaskGet taskGet = taskList.stream().filter(TaskGet.class::isInstance).findFirst().map(TaskGet.class::cast).get();
            TaskGetIfPresent taskGetIfPresent = taskList.stream().filter(TaskGetIfPresent.class::isInstance).findFirst().map(TaskGetIfPresent.class::cast).get();
            Assertions.assertTrue(taskCompute.getSuccessCount() - taskRemove.getSuccessCount() <= 1);
            Assertions.assertTrue(taskGet.getSuccessCount() > 0);
            Assertions.assertTrue(taskGetIfPresent.getSuccessCount() > 0);
        } finally {
            taskList.forEach(Task::printStatistic);
        }
    }

    @Test
    public void size_expectedZero() throws Exception {
        manager_ = new ConcurrentHashManager<>();
        List<Future<?>> futureList = new ArrayList<>();
        List<Task> taskList = Arrays.asList(new TaskGet(), new TaskGetIfPresent(), new TaskRemove(), new TaskSizeZero());
        taskList.forEach(task -> futureList.add(executorService_.submit(task)));
        Thread.sleep(TimeUnit.SECONDS.toMillis(durationSec));
        taskList.forEach(Task::cancel);
        try {
            try {
                futureList.forEach(future -> {
                    // Таймаут на случай блокировки потоков и зависания.
                    ExceptionUtils.passChecked(() -> future.get(2, TimeUnit.SECONDS));
                });
            } finally {
                executorService_.shutdownNow();
            }
        } finally {
            taskList.forEach(Task::printStatistic);
        }
    }

    private class TaskSizeZero extends Task {
        @Override
        protected void doWork() {
            Assertions.assertEquals(0, manager_.size());
        }
    }

    private class TaskCompute extends Task {
        @Override
        protected void doWork() {
            SimpleItem simpleItem = manager_.computeIfAbsent("item1", (ObjectFactory<SimpleItem>) () -> {
                getSuccessCounter().incrementAndGet();
                return new SimpleItem();
            });
            Assertions.assertNotNull(simpleItem);
        }
    }

    private class TaskGet extends Task {
        @Override
        protected void doWork() throws Exception {
            SimpleItem simpleItem = manager_.get("item1");
            getSuccessCounter().incrementAndGet();
            Assertions.assertNotNull(simpleItem);
        }
    }

    private class TaskGetIfPresent extends Task {
        @Override
        protected void doWork() {
            SimpleItem simpleItem = manager_.getIfPresent("item1");
            if (simpleItem != null) {
                getSuccessCounter().incrementAndGet();
            }
        }
    }

    private class TaskKeySet extends Task {
        @Override
        protected void doWork() throws Exception {
            for (String key : manager_.keySet()) {
                getSuccessCounter().incrementAndGet();
                Assertions.assertNotNull(key);
            }
        }
    }


    private class TaskValues extends Task {
        @Override
        protected void doWork() throws Exception {
            manager_.values().forEach((v) -> {
                Assertions.assertNotNull(v);
                getSuccessCounter().incrementAndGet();
            });
        }
    }

    private class TaskRemove extends Task {
        @Override
        protected void doWork() {
            //ExceptionUtils.passChecked(() -> Thread.sleep(100));
            SimpleItem simpleItem = manager_.remove("item1");
            if (simpleItem != null) {
                getSuccessCounter().incrementAndGet();
            }
        }
    }

    private static class SimpleItem {

        private final long timestamp = System.nanoTime();

        @Override
        public String toString() {
            return super.toString() + ": timestamp = " + timestamp;
        }
    }


}
