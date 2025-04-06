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

import com.devives.commons.lang.function.FailableProcedure;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;

/**
 * Класс реализует функциональность подсчёта использований и отложенного закрытия объекта.
 * <p>
 * Принимает в качестве аргумента конструктора, ссылку на метод объекта, делегирующего управление своим закрытием.
 */
public class SynchronizedLazyClosingDirector implements LazyCloseableAsync {

    private final static long OPENED = 0;
    /**
     * Фьючерс закрытия объекта.
     */
    private final CompletableFuture<Void> lazyCloseFuture_ = new CompletableFuture<>();
    /**
     * Этот метод будет вызван при выполнении условия: счётчик использований равен "0" и вызван метод {@link #closeAsync()}.
     */
    private final FailableProcedure closeDelegate_;
    /**
     * Синхронизирует доступ к полям {@link #closeTimeStamp_} и {@link #usageCounter_}.
     */
    private final Object lock_ = new Object();
    /**
     * Флаг указывает что объект находится в состоянии отложенного закрытия. Как только будут закрыты все ссылки
     * на объект, он будет закрыт.
     */
    private long closeTimeStamp_ = OPENED;
    /**
     * Счётчик использований.
     */
    private long usageCounter_ = 0;

    /**
     * @param closeDelegate Ссылка на метод объекта, делегирующего управление закрытием.
     */
    public SynchronizedLazyClosingDirector(FailableProcedure closeDelegate) {
        closeDelegate_ = Objects.requireNonNull(closeDelegate, "closeDelegate");
    }

    /**
     * Возвращает время начала отложенного закрытия.
     *
     * @return {@code -1}, если объект не помечен к закрытию, иначе число миллисекунд.
     */
    public long getLazyCloseTimeMills() {
        synchronized (lock_) {
            return closeTimeStamp_;
        }
    }

    /**
     * Возвращает значение флага, указывающего на необходимость закрытия объекта после уменьшения числа
     * использований до "0".
     *
     * @return true, если объект предназначен к закрытию, иначе false.
     */
    public boolean isLazyClose() {
        synchronized (lock_) {
            return closeTimeStamp_ != OPENED;
        }
    }

    /**
     * Возвращает фьючерс, указывающий на факт закрытия объекта.
     *
     * @return Фьючерс
     */
    public Future<Void> getLazyCloseFuture() {
        return lazyCloseFuture_;
    }

    /**
     * Возвращает число использований.
     *
     * @return число использований
     */
    public long getUsageCount() {
        synchronized (lock_) {
            return usageCounter_;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    public long incUsageCount() {
        final long usages;
        synchronized (lock_) {
            // Allow increment usages if `closeAsync()` was called but `usageCounter_ > 0`.
            if (closeTimeStamp_ == OPENED || usageCounter_ > 0) {
                usages = ++usageCounter_;
            } else {
                throw new RuntimeException("Can't acquire closed object.");
            }
        }
        return usages;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    public long decUsageCount() {
        final boolean needClose;
        final long usages;
        synchronized (lock_) {
            if (usageCounter_ == 0) {
                throw new RuntimeException("Usage counter becomes below zero.");
            }
            usages = --usageCounter_;
            needClose = usageCounter_ == 0 && closeTimeStamp_ != OPENED;
        }
        if (needClose) {
            this.doLazyClose();
        }
        return usages;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    public synchronized CompletionStage<Void> closeAsync() {
        boolean needClose = false;
        synchronized (lock_) {
            if (closeTimeStamp_ == OPENED) {
                closeTimeStamp_ = System.currentTimeMillis();
                needClose = usageCounter_ == 0;
            }
        }
        if (needClose) {
            this.doLazyClose();
        }
        return lazyCloseFuture_;
    }

    private void doLazyClose() {
        try {
            closeDelegate_.accept();
            lazyCloseFuture_.complete(null);
        } catch (Throwable e) {
            lazyCloseFuture_.completeExceptionally(e);
        }
    }

}
