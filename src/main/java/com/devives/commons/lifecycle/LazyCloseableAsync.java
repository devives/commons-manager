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

import java.util.concurrent.CompletionStage;

/**
 * An interface for an object that supports usage counting and deferred object closure functionality.
 */
public interface LazyCloseableAsync extends UsageCounter, CloseableAsync {

    /**
     * The method closes the object and releases the used resources if the number of uses of the current object is "0".
     * If the number of uses of the current object is greater than "0", marks the object
     *
     * @return completion future.
     */
    CompletionStage<Void> closeAsync();
}
