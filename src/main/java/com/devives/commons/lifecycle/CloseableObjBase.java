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
 * The class contains common code for all implementations of the CloseableObj.
 */
public abstract class CloseableObjBase extends StateObjAbst {

    /**
     * Indicates whether the resource is open.
     *
     * @return true if the resource is open.
     */
    public boolean isOpening() {
        return getStateHolder().get() == States.OPENING;
    }

    /**
     * Indicates whether the resource is open.
     *
     * @return true if the resource is open.
     */
    protected boolean isOpened() {
        return getStateHolder().get() == States.OPENED;
    }

    public boolean isClosing() {
        return getStateHolder().get() == States.CLOSING;
    }

    /**
     * Indicates whether the resource has been closed.
     *
     * @return true if the resource is closed.
     */
    protected boolean isClosed() {
        return getStateHolder().get() == States.CLOSED;
    }

    /**
     * Checks whether the current state is equivalent to {@link States#OPENED}.
     *
     * @throws InvalidStateException if object not opened.
     */
    protected void validateOpened() throws InvalidStateException {
        getStateHolder().validate(States.OPENED);
    }

    /**
     * Method is called before closing the object and makes a decision about whether the object can be closed.
     *
     * @return {@code true} if the object can be closed, otherwise {@code false}.
     * @throws Exception if something went wrong.
     */
    protected boolean beforeClose() throws Exception {
        return true;
    }

    /**
     * Resource release.
     *
     * @throws Exception if this resource cannot be closed.
     */
    protected void close() throws Exception {
        final StateHolder stateHolder = getStateHolder();
        final boolean performClose = stateHolder.doAtomicWork(() -> {
            final State state = stateHolder.get();
            if (state != States.CLOSING && state != States.CLOSED && beforeClose()) {
                stateHolder.set(States.CLOSING);
                return true;
            }
            return false;
        });
        if (performClose) {
            try {
                doClose();
            } finally {
                stateHolder.set(States.CLOSED);
            }
        }
    }

    /**
     * Resource release.
     *
     * @throws Exception if this resource cannot be closed.
     */
    protected final void doClose() throws Exception {
        Exception exception = null;
        try {
            onClose();
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            try {
                afterClose(exception);
            } catch (Exception e2) {
                if (exception != null) {
                    e2.addSuppressed(exception);
                }
                exception = e2;
            }
        }
        if (exception != null) {
            throw exception;
        }
    }

    /**
     * Called when the object is closing.
     * <p>
     * Override this method to release resources.
     * A single call to this method is guaranteed.
     *
     * @throws Exception if something went wrong.
     */
    protected abstract void onClose() throws Exception;

    /**
     * Called when the object has been closed.
     * <p>
     * Guarantees a single call to this method.
     *
     * @param exception the exception thrown by the {@link #onClose()} or {@code null} if no exception.
     * @throws Exception if something went wrong.
     */
    protected void afterClose(Exception exception) throws Exception {

    }

    protected static abstract class States {
        public static final State OPENING = StateFactory.named("OPENING");
        public static final State OPENED = StateFactory.named("OPENED");
        public static final State CLOSING = StateFactory.named("CLOSING");
        public static final State CLOSED = StateFactory.named("CLOSED");
    }
}