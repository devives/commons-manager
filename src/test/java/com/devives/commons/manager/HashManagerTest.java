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
import com.devives.commons.lang.function.FailableConsumer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class HashManagerTest {

    @Test
    public void get_emptyManager_exceptionThrow() throws Exception {
        forManager(manager -> {
            Assertions.assertThrows(ManagerException.class, () -> manager.get("Item1"));
        });
    }

    @Test
    public void computeIfAbsent_emptyManager_nonNull() throws Exception {
        forManager(manager -> {
            SimpleTestItem item1 = manager.computeIfAbsent("Item1", SimpleTestItem::new);
            Assertions.assertNotNull(item1);
        });
    }

    @Test
    public void computeIfAbsent_twiceCall_oneObject() throws Exception {
        forManager(manager -> {
            SimpleTestItem item1 = manager.computeIfAbsent("Item1", SimpleTestItem::new);
            SimpleTestItem item1_1 = manager.computeIfAbsent("Item1", SimpleTestItem::new);
            Assertions.assertEquals(item1, item1_1);
        });
    }

    @Test
    public void get_afterComputeIfAbsent_areEquals() throws Exception {
        forManager(manager -> {
            SimpleTestItem item1 = manager.computeIfAbsent("Item1", SimpleTestItem::new);
            SimpleTestItem item1_1 = manager.get("Item1");
            Assertions.assertEquals(item1, item1_1);
        });
    }

    @Test
    public void containsKey_emptyManager_returnFalse() throws Exception {
        forManager(manager -> {
            Assertions.assertFalse(manager.containsKey("Item1"));
        });
    }

    @Test
    public void containsKey_afterRemove_returnFalse() throws Exception {
        forManager(manager -> {
            manager.computeIfAbsent("Item1", SimpleTestItem::new);
            manager.remove("Item1");
            Assertions.assertFalse(manager.containsKey("Item1"));
        });
    }

    @Test
    public void containsKey_presentItem_doNotCallOnEntryGot() throws Exception {
        final AtomicInteger gotCounter = new AtomicInteger();
        final Manager<String, SimpleTestItem> manager = newTrackingManager(gotCounter);
        try {
            manager.computeIfAbsent("Item1", SimpleTestItem::new);
            gotCounter.set(0);

            Assertions.assertTrue(manager.containsKey("Item1"));
            Assertions.assertEquals(0, gotCounter.get());
        } finally {
            manager.clear();
        }
    }

    @Test
    public void remove_emptyManager_returnNull() throws Exception {
        forManager(manager -> {
            Assertions.assertNull(manager.remove("Item1"));
        });
    }

    @Test
    public void start_onStartedHookFailsAndRollbackStopFails_onObjectFailureCalledOnce() throws Exception {
        final AtomicInteger failureCounter = new AtomicInteger();
        final ManagedAdapter<Object> stopFailingAdapter = new ManagedAdapter<Object>() {
            @Override
            public void startObject(Object object) throws Exception {
            }

            @Override
            public void stopObject(Object object) throws Exception {
                throw new IllegalStateException("Stop failure.");
            }
        };
        final Manager.Hooks<String, Object> startedFailingHooks = new Manager.Hooks<String, Object>() {
            @Override
            public void onObjectStarted(Object object) throws Exception {
                throw new IllegalStateException("The onObjectStarted failure.");
            }

            @Override
            public void onObjectFailure(Object object, Throwable throwable) {
                failureCounter.incrementAndGet();
            }
        };
        final Manager<String, Object> manager = new HashManager<>(stopFailingAdapter, startedFailingHooks);
        final IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
                () -> manager.computeIfAbsent("Item1", () -> new Object()));
        // Primary — ошибка откатной остановки; исходная ошибка onObjectStarted не должна быть потеряна.
        Assertions.assertEquals("Stop failure.", thrown.getMessage());
        Assertions.assertArrayEquals(
                new String[]{"The onObjectStarted failure."},
                Arrays.stream(thrown.getSuppressed()).map(Throwable::getMessage).toArray(String[]::new));
        Assertions.assertEquals(1, failureCounter.get());
        Assertions.assertTrue(manager.isEmpty());
    }

    @Test
    public void start_onStartedHookFailsRollbackStopSucceeds_onObjectFailureCalledOnce() throws Exception {
        final AtomicInteger failureCounter = new AtomicInteger();
        final ManagedAdapter<Object> stopOkAdapter = new ManagedAdapter<Object>() {
            @Override
            public void startObject(Object object) throws Exception {
            }

            @Override
            public void stopObject(Object object) throws Exception {
            }
        };
        final Manager.Hooks<String, Object> startedFailingHooks = new Manager.Hooks<String, Object>() {
            @Override
            public void onObjectStarted(Object object) throws Exception {
                throw new IllegalStateException("The onObjectStarted failure.");
            }

            @Override
            public void onObjectFailure(Object object, Throwable throwable) {
                failureCounter.incrementAndGet();
            }
        };
        final Manager<String, Object> manager = new HashManager<>(stopOkAdapter, startedFailingHooks);
        final IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
                () -> manager.computeIfAbsent("Item1", () -> new Object()));
        Assertions.assertEquals("The onObjectStarted failure.", thrown.getMessage());
        Assertions.assertEquals(1, failureCounter.get());
        Assertions.assertTrue(manager.isEmpty());
    }

    @Test
    public void remove_afterComputeIfAbsent_areEquals() throws Exception {
        forManager(manager -> {
            SimpleTestItem item1 = manager.computeIfAbsent("Item1", SimpleTestItem::new);
            SimpleTestItem item1_1 = manager.get("Item1");
            Assertions.assertEquals(item1, item1_1);
        });
    }

    @Test
    public void removeAll_afterComputeIfAbsent_areEquals() throws Exception {
        forManager(manager -> {
            manager.computeIfAbsent("Item1", SimpleTestItem::new);
            manager.computeIfAbsent("Item2", SimpleTestItem::new);
            manager.computeIfAbsent("Item3", SimpleTestItem::new);
            Assertions.assertEquals(3, manager.removeAll().size());
            Assertions.assertTrue(manager.isEmpty());
        });
    }

    @Test
    public void put_absentKey_returnNull() throws Exception {
        forManager(manager -> {
            final SimpleTestItem item1 = new SimpleTestItem();

            final SimpleTestItem result = manager.put("Item1", () -> item1);

            Assertions.assertNull(result);
            Assertions.assertEquals(1, manager.size());
            Assertions.assertSame(item1, manager.get("Item1"));
        });
    }

    @Test
    public void put_existingKey_returnPreviousInstance() throws Exception {
        forManager(manager -> {
            final SimpleTestItem previous = manager.computeIfAbsent("Item1", SimpleTestItem::new);
            final SimpleTestItem replacement = new SimpleTestItem();

            final SimpleTestItem result = manager.put("Item1", () -> replacement);

            Assertions.assertSame(previous, result);
            Assertions.assertNotSame(replacement, result);
            Assertions.assertEquals(1, manager.size());
            Assertions.assertSame(replacement, manager.get("Item1"));
        });
    }

    @Test
    public void values_contains_existingValue_true() throws Exception {
        forManager(manager -> {
            final SimpleTestItem item1 = manager.computeIfAbsent("Item1", SimpleTestItem::new);
            manager.computeIfAbsent("Item2", SimpleTestItem::new);

            Assertions.assertTrue(manager.values().contains(item1));
        });
    }

    @Test
    public void values_contains_missingValue_false() throws Exception {
        forManager(manager -> {
            manager.computeIfAbsent("Item1", SimpleTestItem::new);
            manager.computeIfAbsent("Item2", SimpleTestItem::new);

            Assertions.assertFalse(manager.values().contains(new SimpleTestItem()));
        });
    }

    @Test
    public void values_contains_null_false() throws Exception {
        forManager(manager -> {
            manager.computeIfAbsent("Item1", SimpleTestItem::new);

            Assertions.assertFalse(manager.values().contains(null));
        });
    }

    @Test
    public void forEach_presentItem_doNotCallOnEntryGot() throws Exception {
        final AtomicInteger gotCounter = new AtomicInteger();
        final Manager<String, SimpleTestItem> manager = newTrackingManager(gotCounter);
        try {
            manager.computeIfAbsent("Item1", SimpleTestItem::new);
            gotCounter.set(0);

            manager.forEach((key, value) -> {
                Assertions.assertEquals("Item1", key);
                Assertions.assertNotNull(value);
            });

            Assertions.assertEquals(0, gotCounter.get());
        } finally {
            manager.clear();
        }
    }

    @Test
    public void isEmpty_emptyManager_true() throws Exception {
        forManager(manager -> {
            Assertions.assertTrue(manager.isEmpty());
        });
    }

    @Test
    public void isEmpty_nonEmptyManager_false() throws Exception {
        forManager(manager -> {
            manager.computeIfAbsent("Item1", SimpleTestItem::new);
            Assertions.assertFalse(manager.isEmpty());
        });
    }

    /**
     * Тесты итератора коллекции {@link Manager#values()}.
     */
    @Nested
    protected class ValuesIteratorTest {

        @Test
        public void next_onEmpty_throwNoSuchElementException() throws Exception {
            Manager<String, Integer> map = new ConcurrentHashManager<>();
            Assertions.assertThrows(NoSuchElementException.class, () -> map.values().iterator().next());
        }

        @Test
        public void next_oneElement_expectedValue() throws Exception {
            Manager<String, Integer> map = new ConcurrentHashManager<>();
            map.put("1", () -> 1);
            Iterator<Integer> iterator = map.values().iterator();
            Assertions.assertTrue(iterator.hasNext());
            Assertions.assertEquals(1, iterator.next());
        }

        @Test
        public void nextAfterClear_expectedValue() throws Exception {
            Manager<String, Integer> map = new ConcurrentHashManager<>();
            map.put("1", () -> 1);
            Iterator<Integer> iterator = map.values().iterator();
            Assertions.assertEquals(true, iterator.hasNext());
            map.clear();
            Assertions.assertTrue(map.isEmpty());
            Assertions.assertTrue(iterator.hasNext());
            Assertions.assertEquals(1, iterator.next());
            Assertions.assertFalse(iterator.hasNext());
        }


        @Test
        public void values_nonEmptyManager_expectedState() throws Exception {
            forManager(manager -> {
                manager.computeIfAbsent("Item1", SimpleTestItem::new);
                manager.computeIfAbsent("Item2", SimpleTestItem::new);
                manager.computeIfAbsent("Item3", SimpleTestItem::new);
                Assertions.assertFalse(manager.values().isEmpty());
                Assertions.assertEquals(3, manager.values().size());
            });
        }

        @Test
        public void nextOnEmpty_throwNoSuchElementException() throws Exception {
            forManager(manager -> {
                final Iterator<SimpleTestItem> iterator = manager.values().iterator();
                Assertions.assertThrows(NoSuchElementException.class, iterator::next);
            });
        }

        @Test
        public void nextWithoutHasNext_returnAllValuesAndThenThrow() throws Exception {
            forManager(manager -> {
                final SimpleTestItem item1 = manager.computeIfAbsent("Item1", SimpleTestItem::new);
                final SimpleTestItem item2 = manager.computeIfAbsent("Item2", SimpleTestItem::new);
                final Iterator<SimpleTestItem> iterator = manager.values().iterator();

                final SimpleTestItem value1 = iterator.next();
                final SimpleTestItem value2 = iterator.next();
                final Set<SimpleTestItem> values = new HashSet<>(Arrays.asList(value1, value2));

                Assertions.assertEquals(2, values.size());
                Assertions.assertTrue(values.contains(item1));
                Assertions.assertTrue(values.contains(item2));
                Assertions.assertThrows(NoSuchElementException.class, iterator::next);
            });
        }

        @Test
        public void hasNext_isIdempotent() throws Exception {
            forManager(manager -> {
                manager.computeIfAbsent("Item1", SimpleTestItem::new);
                manager.computeIfAbsent("Item2", SimpleTestItem::new);
                final Iterator<SimpleTestItem> iterator = manager.values().iterator();

                Assertions.assertTrue(iterator.hasNext());
                Assertions.assertTrue(iterator.hasNext());
                Assertions.assertNotNull(iterator.next());
                Assertions.assertNotNull(iterator.next());
                Assertions.assertFalse(iterator.hasNext());
            });
        }

        @Test
        public void next_untilExhausted_returnValuesAndThenThrowNoSuchElementException() throws Exception {
            forManager(manager -> {
                manager.computeIfAbsent("1", SimpleTestItem::new);
                manager.computeIfAbsent("2", SimpleTestItem::new);
                manager.computeIfAbsent("3", SimpleTestItem::new);
                Iterator<SimpleTestItem> iterator = manager.values().iterator();
                Assertions.assertNotNull(iterator.next());
                Assertions.assertNotNull(iterator.next());
                Assertions.assertNotNull(iterator.next());
                Assertions.assertThrows(NoSuchElementException.class, iterator::next);
            });
        }

        /**
         * Проверки теста зависят от реализации менеджера.
         */
        @Test
        public void next_afterManagerModification_expectedBehavior() throws Exception {
            forManager(manager -> {
                manager.computeIfAbsent("0", SimpleTestItem::new);
                manager.computeIfAbsent("1", SimpleTestItem::new);
                manager.computeIfAbsent("2", SimpleTestItem::new);
                List<SimpleTestItem> list = manager.values().stream().collect(Collectors.toList());
                Iterator<SimpleTestItem> iterator = manager.values().iterator();
                Assertions.assertEquals(list.get(0), iterator.next());
                manager.remove("1");
                Assertions.assertThrows(ConcurrentModificationException.class, iterator::next);
            });
        }

        @Test
        public void next_afterRemovedEntry_skipMissingValueAndContinueIteration() throws Exception {
            final OrderedMapManager<String, SimpleTestItem> manager = new OrderedMapManager<>();
            try {
                final SimpleTestItem item1 = manager.computeIfAbsent("Item1", SimpleTestItem::new);
                final SimpleTestItem item2 = manager.computeIfAbsent("Item2", SimpleTestItem::new);
                final SimpleTestItem item3 = manager.computeIfAbsent("Item3", SimpleTestItem::new);

                manager.remove("Item2");

                final List<SimpleTestItem> values = new ArrayList<>();
                manager.values().forEach(values::add);

                Assertions.assertEquals(2, values.size());
                Assertions.assertTrue(values.contains(item1));
                Assertions.assertFalse(values.contains(item2));
                Assertions.assertTrue(values.contains(item3));
            } finally {
                manager.clear();
            }
        }

    }

    protected <K, O> Manager<K, O> newManager() {
        return new HashManager<>();
    }

    protected <K, O> Manager<K, O> newManager(ManagedAdapter<O> defaultAdapter) {
        return new HashManager<>(defaultAdapter);
    }

    protected <K, O> Manager<K, O> newManager(ManagedAdapter<O> defaultAdapter, Manager.Hooks<K, O> hooks) {
        return new HashManager<>(defaultAdapter, hooks);
    }

    protected <K, O> Manager<K, O> newTrackingManager(AtomicInteger gotCounter) {
        return new HashManager<K, O>() {
            @Override
            protected void onEntryGot(Entry<O> entry) {
                gotCounter.incrementAndGet();
            }
        };
    }

    /**
     * Реентраные мутации того же ключа из пользовательских коллбэков (фабрика, адаптер, хуки),
     * выполняемые под той же блокировкой тем же потоком, запрещены: они повторяли бы уже идущую
     * фазу жизненного цикла по захваченным ссылкам (CM-07, CM-08, CM-18, CM-32).
     * Реентраное чтение ({@code get}/{@code getIfPresent}) и мутации других ключей разрешены.
     */
    @Nested
    public class ReentrantTransitionTest {

        @Test
        public void reentrantRemove_fromFactory_rejectedAndNothingCreated() throws Exception {
            final RecordingAdapter adapter = new RecordingAdapter();
            final AtomicReference<Manager<String, SimpleTestItem>> managerRef = new AtomicReference<>();
            final Manager<String, SimpleTestItem> manager = newManager(adapter);
            managerRef.set(manager);
            try {
                Assertions.assertThrows(ManagerException.class, () -> manager.computeIfAbsent("Item1", () -> {
                    managerRef.get().remove("Item1");
                    return new SimpleTestItem();
                }));
                Assertions.assertEquals(0, manager.size());
                Assertions.assertFalse(manager.containsKey("Item1"));
                Assertions.assertTrue(adapter.journal_.isEmpty());
            } finally {
                manager.clear();
            }
        }

        @Test
        public void reentrantPut_fromFactory_rejectedAndNothingCreated() throws Exception {
            final RecordingAdapter adapter = new RecordingAdapter();
            final AtomicReference<Manager<String, SimpleTestItem>> managerRef = new AtomicReference<>();
            final Manager<String, SimpleTestItem> manager = newManager(adapter);
            managerRef.set(manager);
            try {
                Assertions.assertThrows(ManagerException.class, () -> manager.computeIfAbsent("Item1", () -> {
                    managerRef.get().put("Item1", SimpleTestItem::new);
                    return new SimpleTestItem();
                }));
                Assertions.assertEquals(0, manager.size());
                Assertions.assertTrue(adapter.journal_.isEmpty());
            } finally {
                manager.clear();
            }
        }

        @Test
        public void reentrantComputeIfAbsent_fromFactory_rejectedAndNoOrphanObject() throws Exception {
            final RecordingAdapter adapter = new RecordingAdapter();
            final AtomicReference<Manager<String, SimpleTestItem>> managerRef = new AtomicReference<>();
            final Manager<String, SimpleTestItem> manager = newManager(adapter);
            managerRef.set(manager);
            try {
                Assertions.assertThrows(ManagerException.class, () -> manager.computeIfAbsent("Item1", () -> {
                    managerRef.get().computeIfAbsent("Item1", SimpleTestItem::new);
                    return new SimpleTestItem();
                }));
                Assertions.assertEquals(0, manager.size());
                // До фикса здесь оставался сиротый запущенный объект второй записи: start без stop/destroy.
                Assertions.assertTrue(adapter.journal_.isEmpty());
            } finally {
                manager.clear();
            }
        }

        @Test
        public void reentrantRemove_fromOnObjectStarting_rejectedAndInitRolledBack() throws Exception {
            final RecordingAdapter adapter = new RecordingAdapter();
            final AtomicReference<Manager<String, SimpleTestItem>> managerRef = new AtomicReference<>();
            final Manager<String, SimpleTestItem> manager = newManager(adapter, new Manager.Hooks<String, SimpleTestItem>() {
                @Override
                public void onObjectStarting(SimpleTestItem object) throws Exception {
                    managerRef.get().remove("Item1");
                }
            });
            managerRef.set(manager);
            try {
                Assertions.assertThrows(ManagerException.class, () -> manager.computeIfAbsent("Item1", SimpleTestItem::new));
                Assertions.assertEquals(0, manager.size());
                Assertions.assertFalse(manager.containsKey("Item1"));
                // Старт не был выполнен — откат инициализации обязан уничтожить созданный объект ровно один раз.
                Assertions.assertEquals(Collections.singletonList("destroy"), adapter.journal_);
            } finally {
                manager.clear();
            }
        }

        @Test
        public void reentrantRemove_fromOnObjectStarted_rejectedAndInitRolledBackOnce() throws Exception {
            final RecordingAdapter adapter = new RecordingAdapter();
            final AtomicReference<Manager<String, SimpleTestItem>> managerRef = new AtomicReference<>();
            final Manager<String, SimpleTestItem> manager = newManager(adapter, new Manager.Hooks<String, SimpleTestItem>() {
                @Override
                public void onObjectStarted(SimpleTestItem object) throws Exception {
                    managerRef.get().remove("Item1");
                }
            });
            managerRef.set(manager);
            try {
                Assertions.assertThrows(ManagerException.class, () -> manager.computeIfAbsent("Item1", SimpleTestItem::new));
                Assertions.assertEquals(0, manager.size());
                // Ровно один stop и один destroy: откат инициализации не должен дублироваться
                // внешним потоком по захваченным ссылкам (CM-18).
                Assertions.assertEquals(Arrays.asList("start", "stop", "destroy"), adapter.journal_);
            } finally {
                manager.clear();
            }
        }

        @Test
        public void reentrantRemove_fromOnObjectStopping_rejectedAndDeinitRunsOnce() throws Exception {
            final RecordingAdapter adapter = new RecordingAdapter();
            final AtomicReference<Manager<String, SimpleTestItem>> managerRef = new AtomicReference<>();
            final Manager<String, SimpleTestItem> manager = newManager(adapter, new Manager.Hooks<String, SimpleTestItem>() {
                @Override
                public void onObjectStopping(SimpleTestItem object) throws Exception {
                    managerRef.get().remove("Item1");
                }
            });
            managerRef.set(manager);
            try {
                manager.computeIfAbsent("Item1", SimpleTestItem::new);
                Assertions.assertThrows(ManagerException.class, () -> manager.remove("Item1"));
                Assertions.assertEquals(0, manager.size());
                // Вложенный remove отклонён — внешний deinit проходит ровно один раз (CM-08).
                Assertions.assertEquals(Arrays.asList("start", "stop", "destroy"), adapter.journal_);
            } finally {
                manager.clear();
            }
        }

        @Test
        public void reentrantGet_fromOnObjectStarting_returnsPublishedObject() throws Exception {
            final RecordingAdapter adapter = new RecordingAdapter();
            final AtomicReference<Manager<String, SimpleTestItem>> managerRef = new AtomicReference<>();
            final Manager<String, SimpleTestItem> manager = newManager(adapter, new Manager.Hooks<String, SimpleTestItem>() {
                @Override
                public void onObjectStarting(SimpleTestItem object) throws Exception {
                    Assertions.assertSame(object, managerRef.get().get("Item1"));
                }
            });
            managerRef.set(manager);
            try {
                final SimpleTestItem item = manager.computeIfAbsent("Item1", SimpleTestItem::new);
                Assertions.assertNotNull(item);
                Assertions.assertEquals(1, manager.size());
            } finally {
                manager.clear();
            }
        }

        @Test
        public void crossKeyRemove_fromFactory_allowed() throws Exception {
            final RecordingAdapter adapter = new RecordingAdapter();
            final Manager<String, SimpleTestItem> manager = newManager(adapter);
            try {
                manager.computeIfAbsent("Item1", SimpleTestItem::new);
                final SimpleTestItem item2 = manager.computeIfAbsent("Item2", () -> {
                    manager.remove("Item1");
                    return new SimpleTestItem();
                });
                Assertions.assertNotNull(item2);
                Assertions.assertFalse(manager.containsKey("Item1"));
                Assertions.assertTrue(manager.containsKey("Item2"));
                Assertions.assertEquals(Arrays.asList("start", "stop", "destroy", "start"), adapter.journal_);
            } finally {
                manager.clear();
            }
        }
    }

    /**
     * Write-операция того же ключа, запрошенная из уведомления {@code onEntryGot}, отклоняется: поток
     * уведомления уже держит лок ключа, и движок лока не может выдать write поверх него — под
     * {@code RWLockSource} такой запрос вешает поток навсегда (write-over-read, CM-09), под реентраными
     * и noop-источниками та же операция тихо порчала запись под идущим чтением. Отклонение единообразно
     * для всех источников (гейт {@code entryGotCallbackDepth_}). Реентраное чтение и операции на других
     * ключах легальны.
     * <p>
     * Предшествующий тест {@code reentrantRemove_fromOnEntryGot_staysLegalForCompleteEntry} фиксировал
     * легальность операции, достижимой только благодаря noop-локу {@code HashManager}; с CM-09 она
     * отклоняется на всех источниках.
     */
    @Nested
    public class EntryGotCallbackGateTest {

        @Test
        public void reentrantRemove_fromOnEntryGot_rejectedAndEntryAlive() throws Exception {
            final HashManager<String, SimpleTestItem> manager = new HashManager<String, SimpleTestItem>() {
                private boolean fired_;

                @Override
                protected void onEntryGot(Entry<SimpleTestItem> entry) {
                    if (fired_) {
                        return;
                    }
                    fired_ = true;
                    Assertions.assertThrows(ManagerException.class, () -> remove("Item1"));
                }
            };
            try {
                final SimpleTestItem item = manager.computeIfAbsent("Item1", SimpleTestItem::new);
                Assertions.assertSame(item, manager.getIfPresent("Item1"));
                Assertions.assertEquals(1, manager.size());
                Assertions.assertSame(item, manager.get("Item1"));
            } finally {
                manager.clear();
            }
        }

        @Test
        public void reentrantPut_fromOnEntryGot_rejectedAndOldObjectAlive() throws Exception {
            final HashManager<String, SimpleTestItem> manager = new HashManager<String, SimpleTestItem>() {
                private boolean fired_;

                @Override
                protected void onEntryGot(Entry<SimpleTestItem> entry) {
                    if (fired_) {
                        return;
                    }
                    fired_ = true;
                    Assertions.assertThrows(ManagerException.class, () -> put("Item1", SimpleTestItem::new));
                }
            };
            try {
                final SimpleTestItem item = manager.computeIfAbsent("Item1", SimpleTestItem::new);
                Assertions.assertSame(item, manager.get("Item1"));
                Assertions.assertEquals(1, manager.size());
            } finally {
                manager.clear();
            }
        }

        @Test
        public void reentrantComputeIfAbsent_fromOnEntryGot_rejected() throws Exception {
            final HashManager<String, SimpleTestItem> manager = new HashManager<String, SimpleTestItem>() {
                private boolean fired_;

                @Override
                protected void onEntryGot(Entry<SimpleTestItem> entry) {
                    if (fired_) {
                        return;
                    }
                    fired_ = true;
                    Assertions.assertThrows(ManagerException.class, () -> computeIfAbsent("Item1", SimpleTestItem::new));
                }
            };
            try {
                manager.computeIfAbsent("Item1", SimpleTestItem::new);
                Assertions.assertNotNull(manager.getIfPresent("Item1"));
                Assertions.assertEquals(1, manager.size());
            } finally {
                manager.clear();
            }
        }

        /**
         * Дискриминатор глубины против множества: вложенное чтение повторно прогоняет {@code onEntryGot}
         * (глубина 2), а выход из вложенного окна не должен открыть гейт, пока внешнее уведомление того
         * же ключа ещё выполняется.
         */
        @Test
        public void reentrantGet_fromOnEntryGot_allowedAndGateOpenOnlyAfterOuterReturns() throws Exception {
            final HashManager<String, SimpleTestItem> manager = new HashManager<String, SimpleTestItem>() {
                private boolean fired_;

                @Override
                protected void onEntryGot(Entry<SimpleTestItem> entry) {
                    if (fired_) {
                        return;
                    }
                    fired_ = true;
                    final SimpleTestItem nested = getIfPresent("Item1");
                    Assertions.assertSame(entry.getObject(), nested);
                    Assertions.assertThrows(ManagerException.class, () -> remove("Item1"));
                }
            };
            try {
                manager.computeIfAbsent("Item1", SimpleTestItem::new);
                Assertions.assertNotNull(manager.getIfPresent("Item1"));
                Assertions.assertEquals(1, manager.size());
                // Гейт закрывается по возврату из внешнего уведомления: обычный remove более не блокируется.
                Assertions.assertNotNull(manager.remove("Item1"));
            } finally {
                manager.clear();
            }
        }

        @Test
        public void crossKeyRemove_fromOnEntryGot_allowed() throws Exception {
            final HashManager<String, SimpleTestItem> manager = new HashManager<String, SimpleTestItem>() {
                private boolean fired_;

                @Override
                protected void onEntryGot(Entry<SimpleTestItem> entry) {
                    if (fired_) {
                        return;
                    }
                    fired_ = true;
                    remove("Item2");
                }
            };
            try {
                manager.computeIfAbsent("Item1", SimpleTestItem::new);
                manager.computeIfAbsent("Item2", SimpleTestItem::new);
                Assertions.assertNotNull(manager.getIfPresent("Item1"));
                Assertions.assertFalse(manager.containsKey("Item2"));
                Assertions.assertTrue(manager.containsKey("Item1"));
            } finally {
                manager.clear();
            }
        }
    }

    /**
     * Агрегирование ошибок массового удаления: единственная ошибка пробрасывается как ошибка
     * своего ключа, несколько — одним {@code ManagerException} со списком всех ключей и
     * подавленными причинами (по одной на каждый ключ). Ключи с упавшей остановкой удаляются
     * из менеджера в finally-блоке деинициализации (CM-21), поэтому после броска менеджер пуст.
     * <p>
     * Класс наследуется {@code ConcurrentHashManagerTest}, поэтому протокол проверяется и на снимке
     * ключей (база), и на слабосогласованном live-view (оверрайд {@code keysForIteration}).
     */
    @Nested
    protected class BulkRemovalTest {

        @Test
        public void removeAll_singleFailingStop_throwsPerKeyError() {
            final Manager<Integer, String> manager = newFailingStopManager();
            try {
                manager.computeIfAbsent(1, key -> "one");
                final ManagerException error = Assertions.assertThrows(ManagerException.class, manager::removeAll);
                // Сообщения проверяются на вхождение идентификаторов: переформулировка текста
                // не должна ломать тест.
                Assertions.assertTrue(error.getMessage().contains("1"));
                Assertions.assertTrue(error.getCause() instanceof IllegalStateException);
                Assertions.assertEquals(0, error.getSuppressed().length);
                Assertions.assertTrue(manager.isEmpty());
            } finally {
                manager.clear();
            }
        }

        @Test
        public void removeAll_twoFailingKeys_throwsAggregateErrorListingAllKeys() {
            final Manager<Integer, String> manager = newFailingStopManager();
            try {
                manager.computeIfAbsent(1, key -> "one");
                manager.computeIfAbsent(2, key -> "two");
                final ManagerException error = Assertions.assertThrows(ManagerException.class, manager::removeAll);
                // Вхождение идентификаторов обоих ключей; точная формулировка сообщения
                // не проверяется сознательно.
                final String message = error.getMessage();
                Assertions.assertTrue(message.contains("1"));
                Assertions.assertTrue(message.contains("2"));
                final Throwable[] suppressed = error.getSuppressed();
                Assertions.assertEquals(2, suppressed.length);
                for (Throwable suppressedError : suppressed) {
                    Assertions.assertTrue(
                            suppressedError.getMessage().contains("1") || suppressedError.getMessage().contains("2"));
                }
                Assertions.assertTrue(manager.isEmpty());
            } finally {
                manager.clear();
            }
        }

        private Manager<Integer, String> newFailingStopManager() {
            return newManager(new ManagedAdapter<String>() {
                @Override
                public void startObject(String object) {
                }

                @Override
                public void stopObject(String object) throws Exception {
                    throw new IllegalStateException("Stop fails for '" + object + "'.");
                }

                @Override
                public void destroyObject(String object) {
                }
            });
        }
    }

    /**
     * Идиома «пройтись и выкинуть»: {@code forEach} обходит источник ключей, переживающий мутацию
     * карты во время итерации ({@code keysForIteration}: снимок у небезопасных карт,
     * слабосогласованное live-view у конкурентных), поэтому мутация из action не бросает
     * {@code ConcurrentModificationException} (CM-12). Класс наследуется
     * {@code ConcurrentHashManagerTest} — проверены обе реализации.
     */
    @Nested
    protected class ForEachTest {

        @Test
        public void forEach_actionRemovesCurrentKey_completesWithoutCME() throws Exception {
            forManager(manager -> {
                manager.computeIfAbsent("Item1", SimpleTestItem::new);
                manager.computeIfAbsent("Item2", SimpleTestItem::new);
                manager.computeIfAbsent("Item3", SimpleTestItem::new);
                manager.forEach((key, value) -> {
                    try {
                        manager.remove(key);
                    } catch (Exception e) {
                        throw ExceptionUtils.asUnchecked(e);
                    }
                });
                Assertions.assertTrue(manager.isEmpty());
            });
        }

        /**
         * Добавленный из action ключ: снимок его не посетит, слабосогласованное live-view —
         * может посетить. Проверяется только гарантированная часть (контракт {@code Manager#forEach}).
         */
        @Test
        public void forEach_actionAddsKey_weaklyConsistentVisitResult() throws Exception {
            forManager(manager -> {
                manager.computeIfAbsent("Item1", SimpleTestItem::new);
                manager.computeIfAbsent("Item2", SimpleTestItem::new);
                manager.computeIfAbsent("Item3", SimpleTestItem::new);
                final AtomicInteger visited = new AtomicInteger();
                manager.forEach((key, value) -> {
                    try {
                        if (visited.incrementAndGet() == 1) {
                            manager.computeIfAbsent("NewItem", () -> new SimpleTestItem());
                        }
                    } catch (Exception e) {
                        throw ExceptionUtils.asUnchecked(e);
                    }
                });
                Assertions.assertTrue(visited.get() >= 3 && visited.get() <= 4,
                        "visited = " + visited.get());
                Assertions.assertTrue(manager.containsKey("NewItem"));
            });
        }

        /**
         * Строгий снимок снимковой реализации: добавленный из action ключ не посещается.
         * Менеджер конструируется напрямую ({@code HashManager}), тест осознанно проверяет
         * только снимковый контракт на обеих движках.
         */
        @Test
        public void forEach_snapshotIteration_addedKeyNotVisited() throws Exception {
            final HashManager<String, SimpleTestItem> manager = new HashManager<>();
            try {
                manager.computeIfAbsent("Item1", SimpleTestItem::new);
                manager.computeIfAbsent("Item2", SimpleTestItem::new);
                manager.computeIfAbsent("Item3", SimpleTestItem::new);
                final AtomicInteger visited = new AtomicInteger();
                manager.forEach((key, value) -> {
                    if (visited.incrementAndGet() == 1) {
                        manager.computeIfAbsent("NewItem", () -> new SimpleTestItem());
                    }
                });
                Assertions.assertEquals(3, visited.get());
                Assertions.assertTrue(manager.containsKey("NewItem"));
            } finally {
                manager.clear();
            }
        }
    }

    protected void forManager(FailableConsumer<Manager<String, SimpleTestItem>> consumer) throws Exception {
        Manager<String, SimpleTestItem> manager = newManager();
        try {
            consumer.accept(manager);
        } finally {
            manager.clear();
        }
    }

    @Test
    public void lifeCycle_onManagerClear_allRequiredMethodsCalled() throws Exception {
        Manager<String, TestCloseableItem> manager = newManager(new TestCloseableItemToManagedAdapter());
        TestCloseableItem item = manager.computeIfAbsent("item1", () -> Mockito.mock(TestCloseableItem.class));
        Mockito.verify(item, Mockito.atLeastOnce()).start();
        Mockito.verify(item, Mockito.atLeast(0)).stop();
        Mockito.verify(item, Mockito.atLeast(0)).close();
        manager.clear();
        Mockito.verify(item, Mockito.atLeastOnce()).stop();
        Mockito.verify(item, Mockito.atLeastOnce()).close();
    }

    @Test
    public void lifeCycle_onItemRemove_allRequiredMethodsCalled() throws Exception {
        final TestCloseableItem item;
        final Manager<String, TestCloseableItem> manager = newManager();
        try {
            item = manager.computeIfAbsent("item1", () -> Mockito.mock(TestCloseableItem.class));
            Mockito.verify(item, Mockito.atLeast(0)).start();
            manager.remove("item1");
            Mockito.verify(item, Mockito.atLeast(0)).stop();
            Mockito.verify(item, Mockito.atLeast(0)).close();
        } finally {
            manager.clear();
        }
    }

    @Test
    public void recurrentItemAccess_onLifeCycleEvents_itemIsAvailableInManager() throws Exception {
        final String key_item1 = "item1";
        final Manager<String, TestCloseableItem> manager = newManager();
        try {
            manager.computeIfAbsent(key_item1, new ObjectFactory<TestCloseableItem>() {
                @Override
                public TestCloseableItem createObject() {
                    return new TestCloseableItem() {

                        @Override
                        public void start() throws Exception {
                            Assertions.assertEquals(this, manager.get(key_item1));
                        }

                        @Override
                        public void stop() throws Exception {
                            Assertions.assertEquals(this, manager.get(key_item1));
                        }

                        @Override
                        public void close() throws Exception {
                            Assertions.assertEquals(this, manager.get(key_item1));
                        }
                    };
                }
            });
            manager.removeAll();
        } finally {
            manager.clear();
        }
    }


    private ManagedFactory<TestCloseableItem> createManagedFactory(Manager<String, TestCloseableItem> manager) {
        return new ManagedFactory<TestCloseableItem>() {

            @Override
            public TestCloseableItem createObject() throws Exception {
                return Mockito.mock(TestCloseableItem.class);
            }

            @Override
            public void startObject(TestCloseableItem object) throws Exception {
                object.start();
            }

            @Override
            public void stopObject(TestCloseableItem object) throws Exception{
                object.stop();
            }

            @Override
            public void destroyObject(TestCloseableItem object) throws Exception {
                object.close();
            }
        };
    }

    private ObjectFactory<TestCloseableItem> createObjectFactory(Manager<String, TestCloseableItem> manager) {
        return new ObjectFactory<TestCloseableItem>() {

            @Override
            public TestCloseableItem createObject() throws Exception {
                return Mockito.mock(TestCloseableItem.class);
            }

        };
    }

    private static class RecordingAdapter implements ManagedAdapter<SimpleTestItem> {
        final List<String> journal_ = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void startObject(SimpleTestItem object) throws Exception {
            journal_.add("start");
        }

        @Override
        public void stopObject(SimpleTestItem object) throws Exception {
            journal_.add("stop");
        }

        @Override
        public void destroyObject(SimpleTestItem object) throws Exception {
            journal_.add("destroy");
        }
    }

    private static class TestCloseableItemToManagedAdapter implements ManagedAdapter<TestCloseableItem> {
        @Override
        public void startObject(TestCloseableItem object) throws Exception {
            object.start();
        }

        @Override
        public void stopObject(TestCloseableItem object) throws Exception {
            object.stop();
        }

        @Override
        public void destroyObject(TestCloseableItem object) throws Exception {
            object.close();
        }

    }

    private static class TestCloseableItem {

        public void start() throws Exception {

        }

        public void stop() throws Exception {

        }

        protected void close() throws Exception {

        }
    }

    private static final class OrderedMapManager<K, O> extends AbstractManager<K, O> {

        private OrderedMapManager() {
            super(new LinkedHashMap<>());
        }

        @Override
        protected Iterable<K> keysForIteration() {
            // LinkedHashMap мутируется при удалении — обход только по снимку.
            return new ArrayList<>(keySet());
        }

        @Override
        protected List<O> doRemoveAll() {
            final List<O> list = new ArrayList<>();
            final List<K> keys = new ArrayList<>(keySet());
            keys.forEach(key -> {
                try {
                    final O item = doRemove(key);
                    if (item != null) {
                        list.add(item);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            return list;
        }

        @Override
        protected void doClear() {
            doRemoveAll();
        }
    }

    static final class SimpleTestItem {

    }

}
