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
package com.devives.commons.manager.specials;

import com.devives.commons.lang.function.FailableConsumer;
import com.devives.commons.lifecycle.Closeable;
import com.devives.commons.lifecycle.LifeCycleAbst;

import java.util.Objects;

/**
 * Implementation of {@link ManagedObj}.
 *
 * @param <SELF> self type.
 */
public abstract class ManagedObjAbst<SELF extends ManagedObj> extends LifeCycleAbst implements Closeable {

    private final FailableConsumer<SELF, Exception> removeCallback_;

    public ManagedObjAbst(FailableConsumer<SELF, Exception> removeCallback) {
        removeCallback_ = Objects.requireNonNull(removeCallback);
    }

    @Override
    public final void close() throws Exception {
        onClose();
    }

    protected void onClose() throws Exception {
        removeCallback_.accept((SELF) this);
    }
}
