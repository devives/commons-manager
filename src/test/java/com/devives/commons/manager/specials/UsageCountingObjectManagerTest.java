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
package com.devives.commons.manager.specials;

import com.devives.commons.lang.function.FailableConsumer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class UsageCountingObjectManagerTest {

    @Test
    public void acquire_twiceCall_oneObject() throws Exception {
        forTestManager(manager -> {
            SimpleTestItem item1 = manager.acquire("Item1", () -> SimpleTestItem::new);
            SimpleTestItem item1_1 = manager.acquire("Item1", () -> SimpleTestItem::new);
            Assertions.assertEquals(item1, item1_1);
        });
    }

    @Test
    public void acquire_afterAcquire_areEquals() throws Exception {
        forTestManager(manager -> {
            SimpleTestItem item1 = manager.acquire("Item1", () -> SimpleTestItem::new);
            SimpleTestItem item1_1 = manager.acquire("Item1");
            Assertions.assertEquals(item1, item1_1);
        });
    }

    @Test
    public void setRemoveUnusedObjects_false_areEquals() throws Exception {
        forTestManager(manager -> {
            manager.setRemoveUnusedObjects(false);
            SimpleTestItem item1 = manager.acquire("Item1", () -> SimpleTestItem::new);
            manager.release("Item1");
            SimpleTestItem item1_1 = manager.acquire("Item1");
            Assertions.assertEquals(item1, item1_1);
        });
    }

    @Test
    public void isEmpty_afterRelease_true() throws Exception {
        forTestManager(manager -> {
            manager.acquire("Item1", () -> SimpleTestItem::new);
            manager.acquire("Item1", () -> SimpleTestItem::new);
            manager.release("Item1");
            Assertions.assertFalse(manager.isEmpty());
            manager.release("Item1");
            Assertions.assertTrue(manager.isEmpty());
        });
    }

    private static void forTestManager(FailableConsumer<UsageCountingManager<String, SimpleTestItem>, Exception> consumer) throws Exception {
        UsageCountingManager<String, SimpleTestItem> manager = new UsageCountingManager<>();
        try {
            consumer.accept(manager);
        } finally {
            manager.close();
        }
    }

    private static final class SimpleTestItem {
    }

}
