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


import java.util.Objects;

/**
 * Wrapper for management objects.
 *
 * @param <T> type of objects in the manager
 */
public final class IdentityWrapper<T> {
    /**
     * Wrapped object
     */
    private final T object;

    /**
     * Constructs a wrapper for an instance.
     *
     * @param object object to wrap
     */
    public IdentityWrapper(final T object) {
        this.object = Objects.requireNonNull(object);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean equals(final Object other) {
        return other instanceof IdentityWrapper && ((IdentityWrapper) other).object == object;
    }

    /**
     * @return the wrapped object
     */
    public T getObject() {
        return object;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(object);
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("IdentityWrapper [object=");
        builder.append(object);
        builder.append("]");
        return builder.toString();
    }
}