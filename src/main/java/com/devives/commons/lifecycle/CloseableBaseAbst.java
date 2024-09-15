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
public abstract class CloseableBaseAbst extends StateObjAbst {

    /**
     * Indicates whether the resource is open.
     *
     * @return <tt>true</tt> if the resource is open else <tt>false</tt>.
     */
    public boolean isOpening() {
        return getStateHolder().get() == States.OPENING;
    }

    /**
     * Indicates whether the resource is open.
     *
     * @return <tt>true</tt> if the resource is open else <tt>false</tt>.
     */
    public boolean isOpened() {
        return getStateHolder().get() == States.OPENED;
    }

    /**
     * Indicates whether the resource is closing.
     *
     * @return <tt>true</tt> if the resource is closing else <tt>false</tt>.
     */
    public boolean isClosing() {
        return getStateHolder().get() == States.CLOSING;
    }

    /**
     * Indicates whether the resource has been closed.
     *
     * @return <tt>true</tt> if the resource is closed else <tt>false</tt>.
     */
    public boolean isClosed() {
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
     * <h3>Notes</h3>
     * Do not write long running checks in this method. It will lock other threads, which checking object's state.
     *
     * @return {@code true} if the object can be closed, otherwise {@code false}.
     * @throws Exception if something went wrong.
     */
    protected boolean canBeClosed() throws Exception {
        return true;
    }

    /**
     * Perform closing an object.
     * <p>
     * The purpose of the method's existence is the ability to extend the logic of closing an object like:
     * <pre>{@code
     *  protected void doClose() throws Exception {
     *      beforeClose();
     *      onClose();
     *      afterClose();
     *  }
     * }</pre>
     *
     * @throws Exception if something went wrong.
     */
    protected void doClose() throws Exception {
        onClose();
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

    protected static abstract class States {
        public static final State OPENING = StateFactory.named("OPENING");
        public static final State OPENED = StateFactory.named("OPENED");
        public static final State CLOSING = StateFactory.named("CLOSING");
        public static final State CLOSED = StateFactory.named("CLOSED");
    }
}