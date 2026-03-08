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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.*;
import java.util.*;
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
    public void remove_emptyManager_returnNull() throws Exception {
        forManager(manager -> {
            Assertions.assertNull(manager.remove("Item1"));
        });
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
    public void serializeRoundTrip_nonEmptyManager_stateRestored() throws Exception {
        final Manager<String, Integer> manager = newManager();
        try {
            manager.put("1", () -> 1);
            manager.put("2", () -> 2);

            Assertions.assertTrue(manager instanceof Serializable);

            @SuppressWarnings("unchecked")
            final Manager<String, Integer> restored =
                    (Manager<String, Integer>) serializeRoundTrip((Serializable) manager);

            Assertions.assertEquals(2, restored.size());
            Assertions.assertEquals(Integer.valueOf(1), restored.get("1"));
            Assertions.assertEquals(Integer.valueOf(2), restored.get("2"));
        } finally {
            manager.clear();
        }
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
        private static final long serialVersionUID = 1L;

        private OrderedMapManager() {
            super(new LinkedHashMap<>(), new com.devives.commons.manager.lock.NoopLockSource<K>());
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

    private static <T extends Serializable> T serializeRoundTrip(T value) throws Exception {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }
        try (ObjectInputStream objectInputStream =
                     new ObjectInputStream(new ByteArrayInputStream(outputStream.toByteArray()))) {
            @SuppressWarnings("unchecked")
            final T result = (T) objectInputStream.readObject();
            return result;
        }
    }

    static final class SimpleTestItem {

    }

}
