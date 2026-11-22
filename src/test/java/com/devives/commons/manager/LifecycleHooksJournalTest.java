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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Журнальные тесты порядка вызовов адаптера и {@link Manager.Hooks} на путях останова и уничтожения
 * {@link AbstractManager} (аудит-находки CM-03, CM-13). Проверяются зафиксированные контракты:
 * <ul>
 *     <li>освобождение безусловно: падение хука {@code onObjectStopping}/{@code onObjectDestroying}
 *     не ветит и не пропускает {@code stopObject}/{@code destroyObject};
 *     <li>{@code onObjectStopped} — после фактической остановки: при сбое {@code stopObject} не вызывается,
 *     сбой уведомляется через {@code onObjectFailure};
 *     <li>{@code onObjectDestroyed} — после попытки уничтожения: вызывается всегда, включая случай сбоя
 *     {@code destroyObject} (failure-роутинга на пути destroy нет);
 *     <li>исключения не теряются: первое — primary, остальные — в {@link Throwable#getSuppressed()}.
 * </ul>
 * Менеджер — однопоточный {@link HashManager}, чтобы порядок событий в журнале был детерминирован.
 */
public class LifecycleHooksJournalTest {

    @Test
    public void lifecycle_happyPath_hooksAndAdapterCalledInOrder() throws Exception {
        final Journal journal = new Journal();
        final Manager<String, Object> manager = new HashManager<>(new RecordingAdapter(journal), new RecordingHooks(journal));
        manager.computeIfAbsent("Item1", () -> new Object());
        manager.remove("Item1");
        Assertions.assertArrayEquals(
                new String[]{"created", "starting", "start", "started", "stopping", "stop", "stopped", "destroying", "destroy", "destroyed"},
                journal.toArray());
        Assertions.assertTrue(manager.isEmpty());
    }

    @Test
    public void remove_destroyAdapterFails_onObjectDestroyedStillCalledOnce() throws Exception {
        final Journal journal = new Journal();
        final RecordingAdapter adapter = new RecordingAdapter(journal);
        adapter.failDestroyObject = true;
        final Manager<String, Object> manager = new HashManager<>(adapter, new RecordingHooks(journal));
        manager.computeIfAbsent("Item1", () -> new Object());
        journal.events.clear();

        final IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, () -> manager.remove("Item1"));
        Assertions.assertEquals("destroyObject failure.", thrown.getMessage());
        Assertions.assertEquals(0, thrown.getSuppressed().length);
        // Условие onObjectDestroyed снято: терминальный хук доходит и при сбое адаптера.
        Assertions.assertArrayEquals(new String[]{"stopping", "stop", "stopped", "destroying", "destroy", "destroyed"}, journal.toArray());
        Assertions.assertTrue(manager.isEmpty());
    }

    @Test
    public void remove_destroyAdapterAndDestroyedHookFail_firstPrimarySecondSuppressed() throws Exception {
        final Journal journal = new Journal();
        final RecordingAdapter adapter = new RecordingAdapter(journal);
        adapter.failDestroyObject = true;
        final RecordingHooks hooks = new RecordingHooks(journal);
        hooks.failOnObjectDestroyed = true;
        final Manager<String, Object> manager = new HashManager<>(adapter, hooks);
        manager.computeIfAbsent("Item1", () -> new Object());
        journal.events.clear();

        final IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, () -> manager.remove("Item1"));
        Assertions.assertEquals("destroyObject failure.", thrown.getMessage());
        Assertions.assertArrayEquals(
                new String[]{"onObjectDestroyed failure."},
                Arrays.stream(thrown.getSuppressed()).map(Throwable::getMessage).toArray(String[]::new));
        Assertions.assertArrayEquals(new String[]{"stopping", "stop", "stopped", "destroying", "destroy", "destroyed"}, journal.toArray());
    }

    @Test
    public void remove_stopAdapterFails_stoppedHookSkippedFailureHookCalled() throws Exception {
        final Journal journal = new Journal();
        final RecordingAdapter adapter = new RecordingAdapter(journal);
        adapter.failStopObject = true;
        final Manager<String, Object> manager = new HashManager<>(adapter, new RecordingHooks(journal));
        manager.computeIfAbsent("Item1", () -> new Object());
        journal.events.clear();

        final IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, () -> manager.remove("Item1"));
        Assertions.assertEquals("stopObject failure.", thrown.getMessage());
        Assertions.assertEquals(0, thrown.getSuppressed().length);
        // onObjectStopped — только по факту остановки; сбой уведомлён через onObjectFailure.
        // Уничтожение при этом выполняется безусловно (best-effort teardown в doDeinitializeEntry).
        Assertions.assertArrayEquals(
                new String[]{"stopping", "stop", "failure", "destroying", "destroy", "destroyed"},
                journal.toArray());
        Assertions.assertTrue(manager.isEmpty());
    }

    @Test
    public void remove_stoppingHookFails_stopAndDestroyedNotVetoed() throws Exception {
        final Journal journal = new Journal();
        final RecordingHooks hooks = new RecordingHooks(journal);
        hooks.failOnObjectStopping = true;
        final Manager<String, Object> manager = new HashManager<>(new RecordingAdapter(journal), hooks);
        manager.computeIfAbsent("Item1", () -> new Object());
        journal.events.clear();

        final IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, () -> manager.remove("Item1"));
        Assertions.assertEquals("onObjectStopping failure.", thrown.getMessage());
        Assertions.assertEquals(0, thrown.getSuppressed().length);
        // Before-хук не ветит освобождение: stop прошёл, stopped отрапортован, сбой хука доставлен через onObjectFailure.
        Assertions.assertArrayEquals(
                new String[]{"stopping", "stop", "stopped", "failure", "destroying", "destroy", "destroyed"},
                journal.toArray());
        Assertions.assertTrue(manager.isEmpty());
    }

    @Test
    public void computeIfAbsent_createdHookFails_objectRolledBackDestroyedAndNoFailureHook() throws Exception {
        final Journal journal = new Journal();
        final RecordingHooks hooks = new RecordingHooks(journal);
        hooks.failOnObjectCreated = true;
        final Manager<String, Object> manager = new HashManager<>(new RecordingAdapter(journal), hooks);

        final IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
                () -> manager.computeIfAbsent("Item1", () -> new Object()));
        Assertions.assertEquals("onObjectCreated failure.", thrown.getMessage());
        // Откат уничтожает непубликованный объект; на пути destroy failure-хука нет — журнал без "failure".
        Assertions.assertArrayEquals(new String[]{"created", "destroying", "destroy", "destroyed"}, journal.toArray());
        Assertions.assertFalse(manager.containsKey("Item1"));
        Assertions.assertTrue(manager.isEmpty());
    }

    /**
     * Журнал событий: имя события добавляется до броска настроенного исключения, чтобы событие
     * присутствовало в журнале даже в отказной ветке.
     */
    private static final class Journal {
        private final List<String> events = new ArrayList<>();

        private void add(String event) {
            events.add(event);
        }

        private String[] toArray() {
            return events.toArray(new String[0]);
        }
    }

    private static final class RecordingAdapter implements ManagedAdapter<Object> {
        private final Journal journal;
        private boolean failStopObject;
        private boolean failDestroyObject;

        private RecordingAdapter(Journal journal) {
            this.journal = journal;
        }

        @Override
        public void startObject(Object object) throws Exception {
            journal.add("start");
        }

        @Override
        public void stopObject(Object object) throws Exception {
            journal.add("stop");
            if (failStopObject) {
                throw new IllegalStateException("stopObject failure.");
            }
        }

        @Override
        public void destroyObject(Object object) throws Exception {
            journal.add("destroy");
            if (failDestroyObject) {
                throw new IllegalStateException("destroyObject failure.");
            }
        }
    }

    private static final class RecordingHooks implements Manager.Hooks<String, Object> {
        private final Journal journal;
        private boolean failOnObjectCreated;
        private boolean failOnObjectStopping;
        private boolean failOnObjectDestroyed;

        private RecordingHooks(Journal journal) {
            this.journal = journal;
        }

        @Override
        public void onObjectCreated(String key, Object object) throws Exception {
            journal.add("created");
            if (failOnObjectCreated) {
                throw new IllegalStateException("onObjectCreated failure.");
            }
        }

        @Override
        public void onObjectStarting(Object object) throws Exception {
            journal.add("starting");
        }

        @Override
        public void onObjectStarted(Object object) throws Exception {
            journal.add("started");
        }

        @Override
        public void onObjectFailure(Object object, Throwable throwable) throws Exception {
            journal.add("failure");
        }

        @Override
        public void onObjectStopping(Object object) throws Exception {
            journal.add("stopping");
            if (failOnObjectStopping) {
                throw new IllegalStateException("onObjectStopping failure.");
            }
        }

        @Override
        public void onObjectStopped(Object object) throws Exception {
            journal.add("stopped");
        }

        @Override
        public void onObjectDestroying(Object object) throws Exception {
            journal.add("destroying");
        }

        @Override
        public void onObjectDestroyed(Object object) throws Exception {
            journal.add("destroyed");
            if (failOnObjectDestroyed) {
                throw new IllegalStateException("onObjectDestroyed failure.");
            }
        }
    }

}
