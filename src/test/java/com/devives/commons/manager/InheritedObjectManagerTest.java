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

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InheritedObjectManagerTest {

    @Test
    public void getOrCreate_subClass_expectedClasses() throws Exception {
        TestCloseableItemManager manager = new TestCloseableItemManager();
        try {
            TestCloseableItem item1 = manager.computeIfAbsent("item1", () ->
                    new AutoCloseableFactory<TestCloseableItem>() {
                        @Override
                        public TestCloseableItem createObject() {
                            return new TestCloseableItem("item1-1");
                        }
                    });

            TestCloseableItem item1_1 = manager.getByAdditionalField("item1-1");
            Assertions.assertEquals(item1, item1_1);
        } finally {
            manager.close();
        }
    }

    private static class TestCloseableItemManager extends ConcurrentManagerImpl<String, TestCloseableItem> {
        private static final long serialVersionUID = -9117715476640371271L;
        private final ConcurrentMap<String, TestCloseableItem> additionalIndex_ = new ConcurrentHashMap<>();

        public TestCloseableItemManager() {
            super();
        }

        public TestCloseableItem getByAdditionalField(String additionalField) {
            return additionalIndex_.get(additionalField);
        }

        @Override
        protected void onObjectCreated(String key, TestCloseableItem object) throws Exception {
            additionalIndex_.put(object.getAdditionalField(), object);
        }

        @Override
        protected void onObjectDestroying(TestCloseableItem object) throws Exception {
            additionalIndex_.remove(object.getAdditionalField());
        }

    }

    public static class TestCloseableItem implements AutoCloseable {

        private final String additionalField_;

        public TestCloseableItem(String additionalField) {
            additionalField_ = Objects.requireNonNull(additionalField);
        }

        public String getAdditionalField() {
            return additionalField_;
        }

        @Override
        public final void close() throws Exception {

        }

    }

}
