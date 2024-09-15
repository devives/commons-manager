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

/**
 * An abstract, thread-unsafe, implementation of a closable resource.
 */
public abstract class CloseableAbst extends CloseableBaseAbst implements Closeable {

    @Override
    protected StateHolder buildStateHolder() {
        return new StateHolderImpl(States.OPENED);
    }

    /**
     * Release object's resources.
     * <p>
     * Closing of object can be cancelled by results of calling {@link #canBeClosed()} method.
     *
     * @throws Exception when resource closing failed.
     */
    public final void close() throws Exception {
        final StateHolder stateHolder = getStateHolder();
        final State state = stateHolder.get();
        if (state != States.CLOSING && state != States.CLOSED && canBeClosed()) {
            stateHolder.set(States.CLOSING);
            try {
                doClose();
            } finally {
                stateHolder.set(States.CLOSED);
            }
        }
    }

    @Override
    protected final void doClose() throws Exception {
        super.doClose();
    }

}
