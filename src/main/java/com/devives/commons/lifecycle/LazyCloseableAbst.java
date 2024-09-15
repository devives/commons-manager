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

public abstract class LazyCloseableAbst extends CloseableObjBase implements LazyCloseable {

    protected final LazyClosingDirector lazyClosingDirector_ = new LazyClosingDirector(this::lazyClose);

    @Override
    protected StateHolder buildStateHolder() {
        return new StateHolderImpl(States.OPENED);
    }

    @Override
    public long incUsageCount() {
        return lazyClosingDirector_.incUsageCount();
    }

    @Override
    public long decUsageCount() {
        return lazyClosingDirector_.decUsageCount();
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        return lazyClosingDirector_.closeAsync();
    }

    private void lazyClose() throws Exception {
        // Здесь не используется метод isOpened(), что бы не было возможности повлиять
        // на процесс закрытия ресурса его перекрытием.
        final StateHolder stateHolder = getStateHolder();
        final State state = stateHolder.get();
        if (state != States.CLOSING && state != States.CLOSED && beforeClose()) {
            stateHolder.set(States.CLOSING);
            try {
                doClose();
            } finally {
                stateHolder.set(States.CLOSED);
            }
        }

    }

}
