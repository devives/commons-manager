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

/**
 * Interface of an object with a life cycle.
 */
public interface LifeCycle {

    /**
     * Starts the component.
     *
     * @throws Exception If the component fails to start.
     * @see LifeCycleStates#isStarted()
     * @see #stop()
     * @see LifeCycleStates#isFailed()
     */
    void start() throws Exception;

    /**
     * Stops the component.
     * The component may wait for current activities to complete
     * normally, but it can be interrupted.
     *
     * @throws Exception If the component fails to stop.
     * @see LifeCycleStates#isStopped()
     * @see #start()
     * @see LifeCycleStates#isFailed()
     */
    void stop() throws Exception;



    /**
     * Listener.
     * A listener for Lifecycle events.
     */
    interface Listener extends java.util.EventListener {

        default void onStarting(LifeCycle object) {

        }

        default void onStarted(LifeCycle object) {

        }

        default void onFailure(LifeCycle object, Throwable throwable) {

        }

        default void onStopping(LifeCycle object) {

        }

        default void onStopped(LifeCycle object) {

        }
    }

}
