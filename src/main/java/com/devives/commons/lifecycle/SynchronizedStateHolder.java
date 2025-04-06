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

import com.devives.commons.lang.function.FailableFunction;
import com.devives.commons.lang.function.FailableProcedure;

/**
 * Concurrent state holder.
 */
public interface SynchronizedStateHolder extends StateHolder {

    /**
     * Execute an anonymous method in {@code synchronized} code block.
     *
     * @param procedure anonymous method
     * @throws Exception thrown from anonymous method.
     */
    void performAtomicWork(FailableProcedure procedure) throws Exception;

    /**
     * Execute an anonymous method in {@code synchronized} code block.
     *
     * @param function anonymous method.
     * @param <R>      type of anonymous method result.
     * @return result of anonymous method.
     * @throws Exception thrown from anonymous method.
     */
    <R> R performAtomicWork(FailableFunction<R> function) throws Exception;
}
