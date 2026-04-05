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
package com.devives.commons.manager.lifecycle;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DefaultLifeCycleContainerTest {

    @Test
    public void startStop_itemsStartedInInsertionOrderAndStoppedInReverseOrder() throws Exception {
        final List<String> events = new ArrayList<>();
        final DefaultLifeCycleContainer<RecordingLifeCycle> container = new DefaultLifeCycleContainer<>();
        final RecordingLifeCycle item1 = new RecordingLifeCycle("item1", events);
        final RecordingLifeCycle item2 = new RecordingLifeCycle("item2", events);
        final RecordingLifeCycle item3 = new RecordingLifeCycle("item3", events);

        container.addItem(item1);
        container.addItem(item2);
        container.addItem(item3);

        container.start();
        container.stop();

        Assertions.assertEquals(6, events.size());
        Assertions.assertEquals("start:item1", events.get(0));
        Assertions.assertEquals("start:item2", events.get(1));
        Assertions.assertEquals("start:item3", events.get(2));
        Assertions.assertEquals("stop:item3", events.get(3));
        Assertions.assertEquals("stop:item2", events.get(4));
        Assertions.assertEquals("stop:item1", events.get(5));
        Assertions.assertTrue(item1.isStopped());
        Assertions.assertTrue(item2.isStopped());
        Assertions.assertTrue(item3.isStopped());
    }

    @Test
    public void addItem_startedContainer_itemStartedImmediately() throws Exception {
        final List<String> events = new ArrayList<>();
        final DefaultLifeCycleContainer<RecordingLifeCycle> container = new DefaultLifeCycleContainer<>();

        container.start();

        final RecordingLifeCycle item = new RecordingLifeCycle("item1", events);
        container.addItem(item);

        Assertions.assertTrue(item.isStarted());
        Assertions.assertEquals(1, events.size());
        Assertions.assertEquals("start:item1", events.get(0));
        Assertions.assertTrue(container.getItems().contains(item));
    }

    @Test
    public void removeItem_startedContainer_itemStoppedAndRemoved() throws Exception {
        final List<String> events = new ArrayList<>();
        final DefaultLifeCycleContainer<RecordingLifeCycle> container = new DefaultLifeCycleContainer<>();
        final RecordingLifeCycle item = new RecordingLifeCycle("item1", events);
        container.addItem(item);
        container.start();

        container.removeItem(item);

        Assertions.assertFalse(container.getItems().contains(item));
        Assertions.assertEquals(2, events.size());
        Assertions.assertEquals("start:item1", events.get(0));
        Assertions.assertEquals("stop:item1", events.get(1));
        Assertions.assertTrue(item.isStopped());
    }

    @Test
    public void removeItem_stoppedContainer_itemRemovedWithoutLifecycleCalls() {
        final List<String> events = new ArrayList<>();
        final DefaultLifeCycleContainer<RecordingLifeCycle> container = new DefaultLifeCycleContainer<>();
        final RecordingLifeCycle item = new RecordingLifeCycle("item1", events);
        container.addItem(item);

        container.removeItem(item);

        Assertions.assertTrue(events.isEmpty());
        Assertions.assertFalse(container.getItems().contains(item));
        Assertions.assertTrue(item.isStopped());
    }

    @Test
    public void addItem_sameInstanceTwice_throwIllegalArgumentException() {
        final DefaultLifeCycleContainer<RecordingLifeCycle> container = new DefaultLifeCycleContainer<>();
        final RecordingLifeCycle item = new RecordingLifeCycle("item1", new ArrayList<>());
        container.addItem(item);

        Assertions.assertThrows(IllegalArgumentException.class, () -> container.addItem(item));
    }

    @Test
    public void getItems_returnUnmodifiableCollection() {
        final DefaultLifeCycleContainer<RecordingLifeCycle> container = new DefaultLifeCycleContainer<>();
        final RecordingLifeCycle item = new RecordingLifeCycle("item1", new ArrayList<>());
        container.addItem(item);

        final Collection<RecordingLifeCycle> items = container.getItems();

        Assertions.assertThrows(UnsupportedOperationException.class, () -> items.remove(item));
    }

    private static final class RecordingLifeCycle extends AbstractLifeCycle {

        private final String name;
        private final List<String> events;

        private RecordingLifeCycle(String name, List<String> events) {
            this.name = name;
            this.events = events;
        }

        @Override
        protected void onStart() {
            events.add("start:" + name);
        }

        @Override
        protected void onStop() {
            events.add("stop:" + name);
        }
    }

}
