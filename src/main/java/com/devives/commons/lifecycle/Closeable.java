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
 * Interface of object which must be closed at the end of usage.
 *
 * <h3>Notes</h3>
 * <p><i>Why not just use {@link AutoCloseable}?</i></p>
 * <p>Often, using {@link AutoCloseable} is inconvenient. The development environment displays warnings about not
 * calling the {@link AutoCloseable#close()} in places where an object reference is returned, but where it is not necessary.</p>
 */
public interface Closeable {

    /**
     * Close the object and release its resources.
     *
     * @throws Exception if this resource cannot be closed.
     */
    void close() throws Exception;

}























