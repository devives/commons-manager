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

import com.devives.commons.lang.tuple.Tuple2;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * An abstract, thread-safe, implementation of a closable resource.
 */
public abstract class ConcurrentCloseableObjAbst extends CloseableObjBaseAbst implements ConcurrentCloseableObj {

    /**
     * Sharing future across threads.
     * <p>
     * Read/Write is synchronized.
     */
    private CompletableFuture<Void> closeFuture_;

    @Override
    protected StateHolder buildStateHolder() {
        return new SynchronizedStateHolderImpl(States.OPENED);
    }

    @Override
    protected SynchronizedStateHolder getStateHolder() {
        return (SynchronizedStateHolder) super.getStateHolder();
    }

    /**
     * {@inheritDoc}
     */
    public final CompletionStage<Void> closeAsync() {
        try {
            final SynchronizedStateHolder stateHolder = getStateHolder();
            final Tuple2<Boolean, CompletableFuture<Void>> tuple2 = stateHolder.performAtomicWork(() -> {
                final State state = stateHolder.get();
                if (state != States.CLOSING && state != States.CLOSED && canBeClosed()) {
                    stateHolder.set(States.CLOSING);
                    closeFuture_ = new CompletableFuture<>();
                    return Tuple2.of(true, closeFuture_);
                } else {
                    CompletableFuture<Void> closeFuture = closeFuture_ != null ? closeFuture_ : CompletableFuture.completedFuture(null);
                    return Tuple2.of(false, closeFuture);
                }
            });
            final boolean performClose = tuple2._1;
            final CompletableFuture<Void> closeFuture = tuple2._2;
            if (performClose) {
                try {
                    try {
                        doClose();
                    } finally {
                        stateHolder.set(States.CLOSED);
                    }
                    closeFuture.complete(null);
                } catch (Throwable e) {
                    closeFuture.completeExceptionally(e);
                }
            }
            return closeFuture;
        } catch (Throwable e) {
            CompletableFuture<Void> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    @Override
    protected final void doClose() throws Exception {
        super.doClose();
    }

}
