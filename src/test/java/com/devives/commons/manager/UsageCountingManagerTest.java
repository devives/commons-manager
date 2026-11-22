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

import com.devives.commons.lang.function.FailableConsumer;
import com.devives.commons.listener.ListenersBuilder;
import com.devives.commons.publisher.Publishers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

public class UsageCountingManagerTest {

    @Test
    public void acquire_twiceCall_oneObject() throws Exception {
        forTestManager(manager -> {
            SimpleTestItem item1 = manager.acquire("Item1", SimpleTestItem::new);
            SimpleTestItem item1_1 = manager.acquire("Item1", SimpleTestItem::new);
            Assertions.assertEquals(item1, item1_1);
        });
    }

    @Test
    public void acquire_afterAcquire_areEquals() throws Exception {
        forTestManager(manager -> {
            SimpleTestItem item1 = manager.acquire("Item1", SimpleTestItem::new);
            SimpleTestItem item1_1 = manager.acquire("Item1");
            Assertions.assertEquals(item1, item1_1);
        });
    }

    @Test
    public void setRemoveUnusedObjects_false_areEquals() throws Exception {
        forTestManager(manager -> {
            manager.setRemoveUnusedObjects(false);
            SimpleTestItem item1 = manager.acquire("Item1", SimpleTestItem::new);
            manager.release("Item1");
            SimpleTestItem item1_1 = manager.acquire("Item1");
            Assertions.assertEquals(item1, item1_1);
        });
    }

    @Test
    public void setRemoveUnusedObjects_true_areEquals() throws Exception {
        forTestManager(manager -> {
            manager.setRemoveUnusedObjects(true);
            SimpleTestItem item1 = manager.acquire("Item1", SimpleTestItem::new);
            manager.release("Item1");
            SimpleTestItem item1_1 = manager.acquire("Item1", SimpleTestItem::new);
            Assertions.assertNotEquals(item1, item1_1);
        });
    }

    /**
     * Деинициализация из {@code release} идёт через {@code doRemoveEntry}/{@code doDeinitializeEntry}
     * мимо {@code doRemove} — вложенные мутации того же ключа из {@code onEntryRemoving}/слушателя
     * должны отклоняться и на этом пути (CM-08).
     */
    @Test
    public void release_reentrantRemoveFromBeforeRemoveItemListener_rejected() throws Exception {
        forTestManager(manager -> {
            final AtomicReference<Throwable> nestedError = new AtomicReference<>();
            manager.acquire("Item1", SimpleTestItem::new);
            manager.addListener(new UsageCountingManager.Listener<SimpleTestItem>() {
                @Override
                public void afterAddItem(SimpleTestItem object) {
                }

                @Override
                public void beforeRemoveItem(SimpleTestItem object) {
                    nestedError.set(Assertions.assertThrows(ManagerException.class, () -> manager.remove("Item1")));
                }
            });
            manager.release("Item1");
            Assertions.assertNotNull(nestedError.get());
            Assertions.assertTrue(manager.isEmpty());
        });
    }

    /**
     * Выпуск того же ключа из уведомления {@code onEntryGot} отклоняется: путь выгрузки в {@code release}
     * переходит с удерживаемого уведомлением read-лока на write через {@code upgradeLock} и подвешивал бы
     * поток навсегда под {@code RWLockSource} (CM-09). Превентивный таймаут страхует от зависания при
     * поломке гейта.
     */
    @Test
    public void release_fromEntryGotCallback_rejected() throws Exception {
        Assertions.assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            final AtomicReference<Throwable> nestedError = new AtomicReference<>();
            final UsageCountingManager.InternalManager<String, SimpleTestItem> internal =
                    new UsageCountingManager.InternalManager<String, SimpleTestItem>(
                            Publishers.<UsageCountingManager.Listener<SimpleTestItem>>builder()
                                    .listeners(ListenersBuilder::setSynchronized)
                                    .build()) {
                        private boolean fired_;

                        @Override
                        protected void onEntryGot(Entry<SimpleTestItem> entry) {
                            // super не вызываем сознательно: счётчик остаётся равным 1, и вложенный
                            // release доходит до пути выгрузки с upgradeLock.
                            if (fired_) {
                                return;
                            }
                            fired_ = true;
                            nestedError.set(Assertions.assertThrows(ManagerException.class, () -> release("Item1")));
                        }
                    };
            final UsageCountingManager<String, SimpleTestItem> manager = new UsageCountingManager<>(internal);
            manager.acquire("Item1", SimpleTestItem::new);
            // Второй acquire находит готовую запись и уведомляет onEntryGot.
            manager.acquire("Item1", SimpleTestItem::new);
            Assertions.assertNotNull(nestedError.get());
            Assertions.assertFalse(manager.isEmpty());
        });
    }

    @Test
    public void isEmpty_afterRelease_true() throws Exception {
        forTestManager(manager -> {
            manager.acquire("Item1", SimpleTestItem::new);
            manager.acquire("Item1", SimpleTestItem::new);
            manager.release("Item1");
            Assertions.assertFalse(manager.isEmpty());
            manager.release("Item1");
            Assertions.assertTrue(manager.isEmpty());
        });
    }

    private static void forTestManager(FailableConsumer<UsageCountingManager<String, SimpleTestItem>> consumer) throws Exception {
        UsageCountingManager<String, SimpleTestItem> manager = new UsageCountingManager<>();
        try {
            consumer.accept(manager);
        } finally {
            manager.clear();
        }
    }

    private static final class SimpleTestItem {
    }

}
