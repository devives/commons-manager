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

import com.devives.commons.manager.lock.AbstractLockSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ConcurrentHashManagerTest extends HashManagerTest {

    @Override
    protected <K, O> Manager<K, O> newManager() {
        return new ConcurrentHashManager<>();
    }

    @Override
    protected <K, O> Manager<K, O> newManager(ManagedAdapter<O> defaultAdapter) {
        return new ConcurrentHashManager<>(defaultAdapter);
    }

    @Override
    protected <K, O> Manager<K, O> newManager(ManagedAdapter<O> defaultAdapter, Manager.Hooks<K, O> hooks) {
        return new ConcurrentHashManager<>(defaultAdapter, hooks);
    }

    @Override
    protected <K, O> Manager<K, O> newTrackingManager(AtomicInteger gotCounter) {
        return new ConcurrentHashManager<K, O>() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onEntryGot(Entry<O> entry) {
                gotCounter.incrementAndGet();
            }
        };
    }

    private static class UpgradingLockSource extends AbstractLockSource<String> {
        Runnable onUpgrade;

        @Override
        @SuppressWarnings("unchecked")
        protected <E extends Manager.Lock> E doAcquireLock() {
            return (E) new UpgradingLock();
        }

        private final class UpgradingLock extends AbstractLock {
            @Override
            public void lockRead() {
            }

            @Override
            public void unlockRead() {
            }

            @Override
            public void lockWrite() {
            }

            @Override
            public void unlockWrite() {
            }

            @Override
            public void upgradeLock() {
                final Runnable action = onUpgrade;
                onUpgrade = null;
                if (action != null) {
                    action.run();
                }
            }

            @Override
            public void downgradeLock() {
            }
        }
    }

    private static class TrackingManager extends ConcurrentHashManager<String, String> {
        private final AtomicInteger gotCounter = new AtomicInteger();
        private final AtomicInteger addedCounter = new AtomicInteger();

        TrackingManager(UpgradingLockSource lockSource) {
            super(lockSource);
        }

        @Override
        protected void onEntryGot(Entry<String> entry) {
            gotCounter.incrementAndGet();
        }

        @Override
        protected void onEntryAdded(Entry<String> entry) {
            addedCounter.incrementAndGet();
        }
    }

    /**
     * Проверяется, что заимствование записи, созданной конкурентом, уведомляет {@code onEntryGot}:
     * если запись появляется между чтением под read-локом и перечитыванием после {@code upgradeLock()},
     * вызов обязан вернуть объект конкурента и вызвать {@code onEntryGot} — симметрично ветке
     * «запись найдена под read-локом». Пропуск уведомления занижает счётчик использований
     * в менеджерах с учётом использований: первый же {@code release} останавливает и уничтожает
     * объект, ещё используемый другими потоками.
     * <p>
     * Гонка воспроизводится детерминированно: тестовый {@code LockSource} в момент
     * {@code upgradeLock()} выполняет вложенный {@code computeIfAbsent}, публикующий запись,
     * что эквивалентно созданию записи другим потоком в ожидании write-лока.
     * <p>
     * Проверяемые состояния: возвращён объект вложенного вызова ({@code "inner"});
     * {@code onEntryAdded} вызван ровно один раз (создание записи); {@code onEntryGot} —
     * ровно один раз (заимствование). До исправления {@code onEntryGot} не вызывался ни разу.
     */
    @Test
    public void computeIfAbsent_entryCreatedDuringLockUpgrade_callsOnEntryGot() {
        final UpgradingLockSource lockSource = new UpgradingLockSource();
        final TrackingManager manager = new TrackingManager(lockSource);
        lockSource.onUpgrade = () -> manager.computeIfAbsent("Item1", () -> "inner");

        final String result = manager.computeIfAbsent("Item1", () -> "outer");

        Assertions.assertEquals("inner", result);
        Assertions.assertEquals(1, manager.addedCounter.get());
        Assertions.assertEquals(1, manager.gotCounter.get());
    }

    /**
     * Основной сценарий CM-09 — {@code RWLockSource}: write того же ключа из уведомления
     * {@code onEntryGot} вешал поток навсегда (write-over-read владельца). Теперь запрос отклоняется
     * гейтом до обращения к локу. Тест защищён превентивным таймаутом: при поломке гейта он провалится,
     * а не зависнет.
     */
    @Nested
    public class EntryGotCallbackUnderReadWriteLockTest {

        @Test
        public void reentrantRemove_fromOnEntryGot_rejectedWithoutDeadlock() {
            Assertions.assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                final ConcurrentHashManager<String, SimpleTestItem> manager = new ConcurrentHashManager<String, SimpleTestItem>() {
                    private static final long serialVersionUID = 1L;
                    private boolean fired_;

                    @Override
                    protected void onEntryGot(Entry<SimpleTestItem> entry) {
                        if (fired_) {
                            return;
                        }
                        fired_ = true;
                        // Реентраное чтение под RW-локом легально (read-over-read владельца)...
                        Assertions.assertSame(entry.getObject(), getIfPresent("Item1"));
                        // ...а write того же ключа обязан быть отклонён до запроса write-лока.
                        Assertions.assertThrows(ManagerException.class, () -> remove("Item1"));
                    }
                };
                try {
                    final SimpleTestItem item = manager.computeIfAbsent("Item1", SimpleTestItem::new);
                    Assertions.assertSame(item, manager.getIfPresent("Item1"));
                    Assertions.assertEquals(1, manager.size());
                } finally {
                    manager.clear();
                }
            });
        }
    }

    @Nested
    protected class ValuesIteratorTest extends HashManagerTest.ValuesIteratorTest {

        @Test
        @Override
        public void next_afterManagerModification_expectedBehavior() throws Exception {
            forManager(manager -> {
                manager.computeIfAbsent("0", SimpleTestItem::new);
                manager.computeIfAbsent("1", SimpleTestItem::new);
                manager.computeIfAbsent("2", SimpleTestItem::new);
                List<SimpleTestItem> list = manager.values().stream().collect(Collectors.toList());
                Iterator<SimpleTestItem> iterator = manager.values().iterator();
                Assertions.assertEquals(list.get(0), iterator.next());
                manager.remove("1");
                Assertions.assertEquals(list.get(1), iterator.next());
                Assertions.assertEquals(list.get(2), iterator.next());
                Assertions.assertFalse(iterator.hasNext());
            });
        }

    }
}
