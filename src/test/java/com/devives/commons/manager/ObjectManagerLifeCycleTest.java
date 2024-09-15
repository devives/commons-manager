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
import org.mockito.Mockito;

public class ObjectManagerLifeCycleTest {

    @Test
    public void lifeCycle_onManagerClose_allRequiredMethodsCalled() throws Exception {
        final TestCloseableItem item;
        final Manager<String, TestCloseableItem> manager = new ConcurrentManagerImpl<>();
        try {
            item = manager.computeIfAbsent("item1", () -> createFactory(manager));
            Mockito.verify(item, Mockito.atMostOnce()).start();
            Mockito.verify(item, Mockito.atLeast(0)).stop();
            Mockito.verify(item, Mockito.atLeast(0)).close();
        } finally {
            manager.close();
        }
        Mockito.verify(item, Mockito.atMostOnce()).stop();
        Mockito.verify(item, Mockito.atMostOnce()).close();
    }

    @Test
    public void lifeCycle_onItemRemove_allRequiredMethodsCalled() throws Exception {
        final TestCloseableItem item;
        final Manager<String, TestCloseableItem> manager = new ConcurrentManagerImpl<>();
        try {
            item = manager.computeIfAbsent("item1", () -> createFactory(manager));
            Mockito.verify(item, Mockito.atMostOnce()).start();
            manager.remove("item1");
            Mockito.verify(item, Mockito.atMostOnce()).stop();
            Mockito.verify(item, Mockito.atMostOnce()).close();
        } finally {
            manager.close();
        }
    }

    @Test
    public void recurrentItemAccess_onLifeCycleEvents_itemIsAvailableInManager() throws Exception {
        final String key_item1 = "item1";
        final Manager<String, TestCloseableItem> manager = new ConcurrentManagerImpl<>();
        try {
            manager.computeIfAbsent(key_item1, () -> new ObjectFactory<TestCloseableItem>() {
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
            manager.close();
        }
    }

    @Test
    private ObjectFactory<TestCloseableItem> createFactory(Manager<String, TestCloseableItem> manager) {
        return new ObjectFactory<TestCloseableItem>() {

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

    private static class TestCloseableItem {

        public void start() throws Exception {

        }

        public void stop() throws Exception {

        }

        protected void close() throws Exception {

        }
    }

}
