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
package com.devives.commons.manager;

import com.devives.commons.lang.ExceptionUtils;
import com.devives.commons.manager.lock.AbstractLockSource;
import com.devives.commons.manager.lock.RWLockSource;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static com.devives.commons.manager.Manager.noopListener;
import static com.devives.commons.manager.Manager.noopManagedAdapter;

/**
 * Thread-safe concurrent implementation of {@link Manager}.
 * <p>
 * Обеспечивает межпотоковую синхронизацию создания/запуска/остановки/уничтожения объектов.
 *
 * @param <K> type of key
 * @param <O> type of managed object
 * @author Vladimir Ivanov {@code <ivvlev@devives.com>}
 */
public class ConcurrentHashManager<K, O> extends AbstractManager<K, O> implements Serializable {

    private static final long serialVersionUID = 1L;

    public ConcurrentHashManager() {
        super(new ConcurrentHashMap<>(), new RWLockSource<K>(false), noopManagedAdapter(), noopListener());
    }

    public ConcurrentHashManager(Listener<K, O> listener) {
        super(new ConcurrentHashMap<>(), new RWLockSource<K>(false), noopManagedAdapter(), listener);
    }

    public ConcurrentHashManager(ManagedAdapter<O> defaultAdapter) {
        super(new ConcurrentHashMap<>(), new RWLockSource<K>(false), defaultAdapter, noopListener());
    }

    public ConcurrentHashManager(ManagedAdapter<O> defaultAdapter, Listener<K, O> listener) {
        super(new ConcurrentHashMap<>(), new RWLockSource<K>(false), defaultAdapter, listener);
    }

    public ConcurrentHashManager(AbstractLockSource<K> lockSource) {
        super(new ConcurrentHashMap<>(), lockSource, noopManagedAdapter(), noopListener());
    }

    public ConcurrentHashManager(AbstractLockSource<K> lockSource, Listener<K, O> listener) {
        super(new ConcurrentHashMap<>(), lockSource, noopManagedAdapter(), listener);
    }

    public ConcurrentHashManager(AbstractLockSource<K> lockSource, ManagedAdapter<O> defaultAdapter) {
        super(new ConcurrentHashMap<>(), lockSource, defaultAdapter, noopListener());
    }

    protected ConcurrentHashManager(AbstractLockSource<K> lockSource, ManagedAdapter<O> defaultAdapter, Listener<K, O> listener) {
        super(new ConcurrentHashMap<>(), lockSource, defaultAdapter, listener);
    }

    protected final List<O> doRemoveAll() {
        final List<Throwable> exceptionList = new ArrayList<>();
        final List<O> list = new ArrayList<>();
        keySet().forEach(key -> {
            try {
                // The write lock will set in doRemove().
                O item = doRemove(key);
                if (item != null) {
                    list.add(item);
                }
            } catch (Throwable e) {
                exceptionList.add(new ManagerException("Error while removing key = '" + key + "'", e));
            }
        });
        ExceptionUtils.throwCollected(exceptionList);
        return list;
    }

    protected final void doClear() {
        final List<Throwable> exceptionList = new ArrayList<>();
        keySet().forEach(key -> {
            try {
                // The write lock will set in doRemove().
                doRemove(key);
            } catch (Throwable e) {
                exceptionList.add(new ManagerException("Error while removing key = '" + key + "'", e));
            }
        });
        ExceptionUtils.throwCollected(exceptionList);
    }

    @Override
    protected Collection<O> createValuesCollection(Supplier<Collection<Entry<O>>> valuesSupplier) {
        return new ValuesCollection<>(valuesSupplier);
    }

    protected static class ValuesCollection<O> extends AbstractManager.ValuesCollection<O> {

        public ValuesCollection(Supplier<Collection<Entry<O>>> valuesSupplier) {
            super(valuesSupplier);
        }

        @Override
        public Iterator<O> iterator() {
            return new ValuesIterator<O>(values().iterator());
        }

        /**
         * Итератор гарантирует not-null значения при переборе.
         * <pre>{@code
         * manager.values().forEach((v) -> Assertions.assertNotNull(v));
         * }</pre>
         *
         * @param <O> тип элемента коллекции.
         */
        private static class ValuesIterator<O> implements Iterator<O> {
            private final Iterator<Entry<O>> iterator_;
            private O nextValue_;

            public ValuesIterator(Iterator<Entry<O>> iterator) {
                iterator_ = Objects.requireNonNull(iterator);
                fillNextValue();
            }

            @Override
            public boolean hasNext() {
                return nextValue_ != null;
            }

            @Override
            public O next() {
                if (nextValue_ == null) {
                    throw new NoSuchElementException();
                }
                final O value = nextValue_;
                nextValue_ = null;
                // Keep one-element look-ahead to match ConcurrentHashMap iterator behavior.
                fillNextValue();
                return value;
            }

            private void fillNextValue() {
                while (nextValue_ == null && iterator_.hasNext()) {
                    Entry<O> entry = iterator_.next();
                    if (entry != null) {
                        O value = entry.getObject();
                        if (value != null) {
                            nextValue_ = value;
                        }
                    }
                }
            }
        }
    }

}
