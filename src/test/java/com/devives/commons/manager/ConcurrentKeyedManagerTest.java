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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ConcurrentKeyedManagerTest {


    @Test
    public void get_emptyManager_exceptionThrow() throws Exception {
        forSimpleManager(manager -> {
            Assertions.assertThrows(ManagerException.class, () -> manager.get("Item1"));
        });
    }

    @Test
    public void computeIfAbsent_emptyManager_nonNull() throws Exception {
        forSimpleManager(manager -> {
            SimpleTestItem item1 = manager.computeIfAbsent("Item1", SimpleTestItem::new);
            Assertions.assertNotNull(item1);
        });
    }

    @Test
    public void computeIfAbsent_twiceCall_oneObject() throws Exception {
        forSimpleManager(manager -> {
            SimpleTestItem item1 = manager.computeIfAbsent("Item1", SimpleTestItem::new);
            SimpleTestItem item1_1 = manager.computeIfAbsent("Item1", SimpleTestItem::new);
            Assertions.assertEquals(item1, item1_1);
        });
    }

    @Test
    public void get_afterComputeIfAbsent_areEquals() throws Exception {
        forSimpleManager(manager -> {
            SimpleTestItem item1 = manager.computeIfAbsent("Item1", SimpleTestItem::new);
            SimpleTestItem item1_1 = manager.get("Item1");
            Assertions.assertEquals(item1, item1_1);
        });
    }

    @Test
    public void containsKey_emptyManager_returnFalse() throws Exception {
        forSimpleManager(manager -> {
            Assertions.assertFalse(manager.containsKey("Item1"));
        });
    }

    @Test
    public void containsKey_afterRemove_returnFalse() throws Exception {
        forSimpleManager(manager -> {
            manager.computeIfAbsent("Item1", SimpleTestItem::new);
            manager.remove("Item1");
            Assertions.assertFalse(manager.containsKey("Item1"));
        });
    }

    @Test
    public void remove_emptyManager_returnNull() throws Exception {
        forSimpleManager(manager -> {
            Assertions.assertNull(manager.remove("Item1"));
        });
    }

    @Test
    public void remove_afterComputeIfAbsent_areEquals() throws Exception {
        forSimpleManager(manager -> {
            SimpleTestItem item1 = manager.computeIfAbsent("Item1", SimpleTestItem::new);
            SimpleTestItem item1_1 = manager.get("Item1");
            Assertions.assertEquals(item1, item1_1);
        });
    }

    @Test
    public void removeAll_afterComputeIfAbsent_areEquals() throws Exception {
        forSimpleManager(manager -> {
            manager.computeIfAbsent("Item1", SimpleTestItem::new);
            manager.computeIfAbsent("Item2", SimpleTestItem::new);
            manager.computeIfAbsent("Item3", SimpleTestItem::new);
            Assertions.assertEquals(3, manager.removeAll().size());
            Assertions.assertTrue(manager.isEmpty());
        });
    }

    @Test
    public void values_areEquals() throws Exception {
        forSimpleManager(manager -> {
            manager.computeIfAbsent("Item1", SimpleTestItem::new);
            manager.computeIfAbsent("Item2", SimpleTestItem::new);
            manager.computeIfAbsent("Item3", SimpleTestItem::new);
            Assertions.assertFalse(manager.values().isEmpty());
            Assertions.assertEquals(3, manager.values().size());
        });
    }

    @Test
    public void isEmpty_emptyManager_true() throws Exception {
        forSimpleManager(manager -> {
            Assertions.assertTrue(manager.isEmpty());
        });
    }

    @Test
    public void isEmpty_nonEmptyManager_false() throws Exception {
        forSimpleManager(manager -> {
            manager.computeIfAbsent("Item1", SimpleTestItem::new);
            Assertions.assertFalse(manager.isEmpty());
        });
    }

    private static void forSimpleManager(FailableConsumer<Manager<String, SimpleTestItem>> consumer) throws Exception {
        Manager<String, SimpleTestItem> manager = new ConcurrentHashManager<>();
        try {
            consumer.accept(manager);
        } finally {
            manager.clear();
        }
    }

    private static void forCloseableManager(FailableConsumer<Manager<String, TestCloseableItem>> consumer) throws Exception {
        Manager<String, TestCloseableItem> manager = new ConcurrentHashManager<>();
        try {
            consumer.accept(manager);
        } finally {
            manager.clear();
        }
    }

    @Test
    public void lifeCycle_onManagerClear_allRequiredMethodsCalled() throws Exception {
        Manager<String, TestCloseableItem> manager = new ConcurrentHashManager<>(new TestCloseableItemToManagedAdapter());
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
        final Manager<String, TestCloseableItem> manager = new ConcurrentHashManager<>();
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
        final Manager<String, TestCloseableItem> manager = new ConcurrentHashManager<>();
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

    private static final class SimpleTestItem {

    }

}
