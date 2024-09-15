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

import com.devives.commons.lang.exception.ExceptionUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class MultiThreadObjectManagerTest {

    private final SimpleItemManager manager_ = new SimpleItemManager();
    private final ExecutorService executorService_ = Executors.newScheduledThreadPool(5);

    @Test
    public void test() throws Exception {
        List<Future<?>> futureList = new ArrayList<>();
        List<Task> taskList = Arrays.asList(new TaskCompute(), new TaskGet(), new TaskFind(), new TaskIterate(), new TaskRemove());
        taskList.forEach(task -> futureList.add(executorService_.submit(task)));
        Thread.sleep(TimeUnit.SECONDS.toMillis(10));
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

    private class TaskCompute extends Task {
        @Override
        protected void doWork() {
            SimpleItem simpleItem = manager_.computeIfAbsent("item1", () -> {
                getSuccessCounter().incrementAndGet();
                return SimpleItem::new;
            });
            Assertions.assertNotNull(simpleItem);
        }
    }

    private class TaskIterate extends Task {
        @Override
        protected void doWork() throws Exception {
            for (SimpleItem item : manager_) {
                getSuccessCounter().incrementAndGet();
                Assertions.assertNotNull(item);
            }
        }
    }

    private class TaskGet extends Task {
        @Override
        protected void doWork() throws Exception {
            Assertions.assertNotNull(manager_.get("item1"));
        }
    }

    private class TaskFind extends Task {
        @Override
        protected void doWork() {
            SimpleItem simpleItem = manager_.getIfPresent("item1");
            if (simpleItem != null) {
                getSuccessCounter().incrementAndGet();
            }
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

    private static class SimpleItemManager extends ConcurrentManagerImpl<String, SimpleItem> {

        @Override
        protected void onObjectDestroyed(SimpleItem object) throws Exception {
            object.close();
        }
    }


    private static class SimpleItem implements AutoCloseable {

        private final long timestamp = System.nanoTime();
        private volatile boolean opened_ = true;

        public SimpleItem() {
//            try {
//                Thread.sleep(50);
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
        }

        @Override
        public void close() throws Exception {
            opened_ = false;
        }

        public boolean isOpened() {
            return opened_;
        }

        @Override
        public String toString() {
            return super.toString() + ": timestamp = " + timestamp;
        }
    }


}
