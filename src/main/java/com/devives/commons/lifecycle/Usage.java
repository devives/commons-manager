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

import com.devives.commons.lang.function.ExceptionProcedure;

import java.util.Objects;

/**
 * Utility class with a reference to a captured instance.
 * <p>
 * Used in constructs like:
 * <pre>{@code
 * try (Usage<Item> itemUsage = manager.acquire()){
 *     itemUsage.get().doWork();
 * }
 * }</pre>
 *
 * @param <T> The type of the instance to which a reference is obtained.
 */
public final class Usage<T> implements AutoCloseable {

    private final T instance_;
    private final long count_;
    private final ExceptionProcedure decrementer_;

    /**
     * The constructor.
     *
     * @param instance          the instance being captured.
     * @param count             the current count of uses.
     * @param decrementCallback the callback to decrease the use counter.
     */
    public Usage(T instance, long count, ExceptionProcedure decrementCallback) {
        instance_ = Objects.requireNonNull(instance);
        count_ = count;
        decrementer_ = Objects.requireNonNull(decrementCallback);
    }

    /**
     * Returns the count of uses of the object at the time of getting the reference.
     *
     * @return the count.
     */
    public long getCount() {
        return count_;
    }

    /**
     * Returns a reference to the captured instance.
     *
     * @return the instance.
     */
    public T get() {
        return instance_;
    }

    @Override
    public void close() throws Exception {
        decrementer_.accept();
    }

    /**
     * Increases the use counter of the object {@code instance} and creates a new instance of {@link Usage}.
     *
     * @param instance the instance with a usage counter.
     * @param <T>      the type with usage counting.
     * @return a new instance of {@link Usage}
     */
    public static <T extends UsageCounter> Usage<T> of(T instance) {
        return new Usage<T>(instance, instance.incUsageCount(), instance::decUsageCount);
    }
}