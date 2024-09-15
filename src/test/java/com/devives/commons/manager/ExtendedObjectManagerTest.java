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

public class ExtendedObjectManagerTest {

    @Test
    public void getOrCreate_subClass_expectedClasses() throws Exception {

        final ObjectFactory<TestCloseableItem> testItemFactory = new AutoCloseableFactory<TestCloseableItem>() {
            @Override
            public TestCloseableItem createObject() {
                return new CloseableItemBuilder()
                        .setTestResource1(new TestResource1())
                        .setTestResource2(new TestResource2())
                        .build();
            }
        };

        final ObjectFactory<TestCloseableItem> extTestItemFactory = new AutoCloseableFactory<TestCloseableItem>() {
            @Override
            public TestCloseableItem createObject() {
                return new ExtCloseableItemBuilder()
                        .setTestResource1(new TestResource1())
                        .setTestResource2(new TestResource2())
                        .build();
            }
        };

        Manager<String, TestCloseableItem> manager = new ConcurrentManagerImpl<>();
        try {
            TestCloseableItem item1 = manager.computeIfAbsent("item1", () -> testItemFactory);
            TestCloseableItem item2 = manager.computeIfAbsent("item2", () -> extTestItemFactory);
            Assertions.assertNotNull(item1);
            Assertions.assertTrue(item2 instanceof ExtTestCloseableItem);
        } finally {
            manager.close();
        }
    }

    public static class TestCloseableItem implements AutoCloseable {

        private final TestResource1 testResource1_;
        private final TestResource2 testResource2_;

        public TestCloseableItem(TestResource1 testResource1, TestResource2 testResource2) {
            testResource1_ = testResource1;
            testResource2_ = testResource2;
        }

        @Override
        public final void close() throws Exception {
            onClose();
        }

        protected void onClose() throws Exception {
            testResource2_.close();
        }

    }

    public static class ExtTestCloseableItem extends TestCloseableItem {

        public ExtTestCloseableItem(TestResource1 testResource1, TestResource2 testResource2) {
            super(testResource1, testResource2);
        }
    }

    private static class CloseableItemBuilder {

        protected TestResource1 testResource1_;
        protected TestResource2 testResource2_;

        public CloseableItemBuilder setTestResource1(TestResource1 testResource1) {
            testResource1_ = testResource1;
            return this;
        }

        public CloseableItemBuilder setTestResource2(TestResource2 testResource2) {
            testResource2_ = testResource2;
            return this;
        }

        public TestCloseableItem build() {
            return new TestCloseableItem(testResource1_, testResource2_);
        }
    }


    public static class TestResource1 {

    }

    public static class TestResource2 implements AutoCloseable {

        @Override
        public void close() throws Exception {

        }
    }

    private static class ExtCloseableItemBuilder extends CloseableItemBuilder {

        public ExtTestCloseableItem build() {
            return new ExtTestCloseableItem(testResource1_, testResource2_);
        }
    }


}
