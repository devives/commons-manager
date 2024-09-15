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

import com.devives.commons.lang.Ref;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LazyClosingDirectorTest {

    @Test
    public void close_afterCreate_callbackWasCalled() throws Exception {
        Ref<Boolean> calledRef = new Ref<>(false);
        SynchronizedLazyClosingDirector director = new SynchronizedLazyClosingDirector(() -> calledRef.set(true));
        director.closeAsync();
        Assertions.assertTrue(calledRef.get());
    }

    @Test
    public void getUsageCount_afterIncUsageCount_equalsOne() throws Exception {
        SynchronizedLazyClosingDirector director = new SynchronizedLazyClosingDirector(() -> {
        });
        director.incUsageCount();
        Assertions.assertEquals(1, director.getUsageCount());
    }

    @Test
    public void close_afterAutoRelease_callbackWasCalled() throws Exception {
        Ref<Boolean> calledRef = new Ref<>(false);
        SynchronizedLazyClosingDirector director = new SynchronizedLazyClosingDirector(() -> calledRef.set(true));
        try (Usage usage = Usage.of(director)) {
            Assertions.assertFalse(calledRef.get());
            director.closeAsync();
            Assertions.assertFalse(calledRef.get());
        }
        Assertions.assertTrue(calledRef.get());
    }

    @Test
    public void close_afterRelease_callbackWasCalled() throws Exception {
        Ref<Boolean> calledRef = new Ref<>(false);
        SynchronizedLazyClosingDirector director = new SynchronizedLazyClosingDirector(() -> calledRef.set(true));
        director.incUsageCount();
        Assertions.assertFalse(calledRef.get());
        director.closeAsync();
        Assertions.assertFalse(calledRef.get());
        director.decUsageCount();
        Assertions.assertTrue(calledRef.get());
    }
}


