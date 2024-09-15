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

public class SimpleObjectManagerTest {

    @Test
    public void get_emptyManager_exceptionThrow() throws Exception {
        forTestManager(manager -> {
            Assertions.assertThrows(ManagerException.class, () -> manager.get("Item1"));
        });
    }

    @Test
    public void computeIfAbsent_emptyManager_nonNull() throws Exception {
        forTestManager(manager -> {
            SimpleTestItem item1 = manager.computeIfAbsent("Item1", () -> SimpleTestItem::new);
            Assertions.assertNotNull(item1);
        });
    }

    @Test
    public void computeIfAbsent_twiceCall_oneObject() throws Exception {
        forTestManager(manager -> {
            SimpleTestItem item1 = manager.computeIfAbsent("Item1", () -> SimpleTestItem::new);
            SimpleTestItem item1_1 = manager.computeIfAbsent("Item1", () -> SimpleTestItem::new);
            Assertions.assertEquals(item1, item1_1);
        });
    }

    @Test
    public void get_afterComputeIfAbsent_areEquals() throws Exception {
        forTestManager(manager -> {
            SimpleTestItem item1 = manager.computeIfAbsent("Item1", () -> SimpleTestItem::new);
            SimpleTestItem item1_1 = manager.get("Item1");
            Assertions.assertEquals(item1, item1_1);
        });
    }

    @Test
    public void containsKey_emptyManager_returnFalse() throws Exception {
        forTestManager(manager -> {
            Assertions.assertFalse(manager.containsKey("Item1"));
        });
    }

    @Test
    public void containsKey_afterRemove_returnFalse() throws Exception {
        forTestManager(manager -> {
            manager.computeIfAbsent("Item1", () -> SimpleTestItem::new);
            manager.remove("Item1");
            Assertions.assertFalse(manager.containsKey("Item1"));
        });
    }

    @Test
    public void remove_emptyManager_returnNull() throws Exception {
        forTestManager(manager -> {
            Assertions.assertNull(manager.remove("Item1"));
        });
    }

    @Test
    public void remove_afterComputeIfAbsent_areEquals() throws Exception {
        forTestManager(manager -> {
            SimpleTestItem item1 = manager.computeIfAbsent("Item1", () -> SimpleTestItem::new);
            SimpleTestItem item1_1 = manager.get("Item1");
            Assertions.assertEquals(item1, item1_1);
        });
    }

    @Test
    public void removeAll_afterComputeIfAbsent_areEquals() throws Exception {
        forTestManager(manager -> {
            manager.computeIfAbsent("Item1", () -> SimpleTestItem::new);
            manager.computeIfAbsent("Item2", () -> SimpleTestItem::new);
            manager.computeIfAbsent("Item3", () -> SimpleTestItem::new);
            Assertions.assertEquals(3, manager.removeAll().size());
            Assertions.assertTrue(manager.isEmpty());
        });
    }

    @Test
    public void isEmpty_emptyManager_true() throws Exception {
        forTestManager(manager -> {
            Assertions.assertTrue(manager.isEmpty());
        });
    }

    @Test
    public void isEmpty_nonEmptyManager_false() throws Exception {
        forTestManager(manager -> {
            manager.computeIfAbsent("Item1", () -> SimpleTestItem::new);
            Assertions.assertFalse(manager.isEmpty());
        });
    }

    private static void forTestManager(FailableConsumer<Manager<String, SimpleTestItem>, Exception> consumer) throws Exception {
        Manager<String, SimpleTestItem> manager = new ConcurrentManagerImpl<>();
        try {
            consumer.accept(manager);
        } finally {
            manager.close();
        }
    }

    private static final class SimpleTestItem {

    }

}
