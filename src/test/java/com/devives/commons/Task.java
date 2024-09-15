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
package com.devives.commons;

import com.devives.commons.lang.util.DurationMeter;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;


public abstract class Task implements Runnable {
    private final DurationMeter runDurationMeter_ = new DurationMeter();
    private final DurationMeter workDurationMeter_ = new DurationMeter();
    private final AtomicLong failureCounter_ = new AtomicLong(0);
    private final AtomicLong iterationCounter_ = new AtomicLong(0);
    private final AtomicLong successCounter_ = new AtomicLong(0);
    private volatile boolean cancelled_ = false;

    public DurationMeter getWorkDurationMeter() {
        return workDurationMeter_;
    }

    public DurationMeter getRunDurationMeter() {
        return runDurationMeter_;
    }

    public long getFailureCount() {
        return failureCounter_.get();
    }

    public long getIterationCount() {
        return iterationCounter_.get();
    }

    public long getSuccessCount() {
        return successCounter_.get();
    }

    public long getIterationCounter() {
        return iterationCounter_.get();
    }

    public AtomicLong getSuccessCounter() {
        return successCounter_;
    }

    public void cancel() {
        cancelled_ = true;
    }

    @Override
    public final void run() {
        runDurationMeter_.measure(() -> {
            while (!cancelled_ && !Thread.currentThread().isInterrupted()) {
                iterationCounter_.incrementAndGet();
                try {
                    workDurationMeter_.measure(this::doWork);
                } catch (Exception e) {
                    failureCounter_.incrementAndGet();
                }
            }
        });
    }

    protected abstract void doWork() throws Exception;


    public static void printStatistic(Task task) {
        Logger logger = Logger.getLogger(task.getClass().getCanonicalName());
        logger.info(System.lineSeparator() +
                "Task name = " + task.getClass().getSimpleName() + System.lineSeparator() +
                "Run duration = " + task.getRunDurationMeter().durationMills() + " ms" + System.lineSeparator() +
                "Work duration = " + task.getWorkDurationMeter().durationMills() + " ms" + System.lineSeparator() +
                "Iteration count = " + task.getIterationCounter() + System.lineSeparator() +
                "Success count = " + task.getSuccessCount() + System.lineSeparator() +
                "Failure count = " + task.getFailureCount());
    }

}

