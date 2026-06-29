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
 * Defines how a lifecycle operation should proceed after a failure has been handled.
 * <p>
 * Values of this type are returned by failure handlers to control whether
 * {@link LifeCycle#start()} or {@link LifeCycle#stop()} should propagate the
 * original failure to the caller or return normally after failure handling.
 */
public enum FailureAction {

    /**
     * Propagate the original failure from {@link LifeCycle#start()} or {@link LifeCycle#stop()}
     * after failure handling is complete.
     */
    RETHROW,

    /**
     * Suppress the original failure after failure handling is complete, allowing
     * {@link LifeCycle#start()} or {@link LifeCycle#stop()} to return normally.
     */
    SUPPRESS
}
