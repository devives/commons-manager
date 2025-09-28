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


import com.devives.commons.lang.ExceptionUtils;
import com.devives.commons.publisher.Publisher;
import com.devives.commons.publisher.Publishers;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Object manager with usage counting.
 *
 * @param <K> key type.
 * @param <O> managed object type.
 */
public class UsageCountingManager<K, O> implements Serializable {

    private static final long serialVersionUID = 8389806804677309189L;
    private final InternalManager<K, O> internalManager_;

    public UsageCountingManager() {
        this(new InternalManager<K, O>(Publishers
                .<EventListener>builder()
                .listeners(b -> b.setSynchronized())
                .build()));
    }

    protected UsageCountingManager(InternalManager<K, O> internalManager) {
        internalManager_ = Objects.requireNonNull(internalManager, "internalManager");
    }

    public O acquire(K key) throws ManagerException {
        return internalManager_.get(key);
    }

    public O acquire(K key, Function<K, O> factory) {
        return internalManager_.computeIfAbsent(key, factory);
    }

    public O acquire(K key, ObjectFactory<O> factory) {
        return internalManager_.computeIfAbsent(key, factory);
    }

    public O acquire(K key, ManagedFactory<O> factory) {
        return internalManager_.computeIfAbsent(key, factory);
    }

    public O acquire(K key, ObjectFactory<O> factory, ManagedAdapter<O> adapter) {
        return internalManager_.computeIfAbsent(key, factory, adapter);
    }

    public O acquire(K key, ManagedFactory<O> factory, ManagedAdapter<O> adapter) {
        return internalManager_.computeIfAbsent(key, factory, adapter);
    }

    /**
     * Метод перебора всех объектов менеджера.
     * <p>
     * На время выполнения метода {@code visitor} увеличивается счётчик использований объекта. За счёт этого гарантируется,
     * что объект не будет остановлен до завершения {@code visitor}.
     *
     * @param visitor визитёр, вызываемый для каждого объекта в менеджере.
     */
    public void forEach(Consumer<O> visitor) {
        internalManager_.keySet().forEach((key) -> {
            O object = internalManager_.getIfPresent(key);
            if (object != null) {
                // Usage count was increased if object returned.
                try {
                    visitor.accept(object);
                } finally {
                    internalManager_.release(key);
                }
            }
        });
    }

    /**
     * Возвращает множество объектов, содержащихся в менеджере.
     * <p>
     * Множество не изменяемое.
     *
     * @return множество ключей менеджера.
     */
    public Set<K> keySet() {
        return internalManager_.keySet();
    }

    /**
     * Возвращает коллекцию объектов, содержащихся в менеджере.
     * <p>
     * Обращение к элементам коллекции не увеличивает счётчик использования. Состояние объекта не гарантируется.
     * Объект может быть остановлен и/или выброшен из менеджера сразу после получения ссылки на объект.
     * <p>
     * Коллекция не изменяемая.
     *
     * @return коллекция объектов, содержащихся в менеджере.
     */
    public Collection<O> values() {
        return internalManager_.values();
    }

    /**
     * Уменьшает счётчик использований объекта, соответствующего ключу {@code key}.
     * <p>
     * Если
     *
     * @param key ключ
     */
    public void release(K key) {
        internalManager_.release(key);
    }

    /**
     * Removes a key and its corresponding object from the manager.
     *
     * @param key key
     * @return instance of {@code O} or {@code null}.
     */
    public O remove(K key) {
        return internalManager_.remove(key);
    }

    /**
     * Remove all instances from manager.
     *
     * @return a list containing all removed instances.
     */
    public List<O> removeAll() {
        return internalManager_.removeAll();
    }

    /**
     * Remove all instances from manager.
     */
    public void clear() {
        internalManager_.clear();
    }

    public boolean isEmpty() {
        return internalManager_.isEmpty();
    }

    public long size() {
        return internalManager_.size();
    }

    public boolean isRemoveUnusedObjects() {
        return internalManager_.isRemoveUnusedObjects();
    }

    public void setRemoveUnusedObjects(boolean value) {
        internalManager_.setRemoveUnusedObjects(value);
    }

    public void addListener(EventListener listener) {
        internalManager_.addListener(listener);
    }

    public void removeListener(EventListener listener) {
        internalManager_.removeListener(listener);
    }


    public interface EventListener<O> {

        void afterAddItem(O object);

        void beforeRemoveItem(O object);
    }

    protected static class InternalManager<K, O> extends ConcurrentKeyedManager<K, O> {
        private static final long serialVersionUID = 242380967546124799L;
        private volatile boolean removeUnusedObjects_ = true;
        private final Publisher<EventListener> publisher_;

        public InternalManager(Publisher<EventListener> publisher) {
            publisher_ = Objects.requireNonNull(publisher, "publisher");
        }

        public InternalManager(Publisher<EventListener> publisher, ManagedAdapter<O> defaultAdapter) {
            super(defaultAdapter);
            publisher_ = Objects.requireNonNull(publisher, "publisher");
        }

        public boolean isRemoveUnusedObjects() {
            return removeUnusedObjects_;
        }

        public void setRemoveUnusedObjects(boolean removeUnusedObjects) {
            removeUnusedObjects_ = removeUnusedObjects;
        }

        public void addListener(EventListener listener) {
            publisher_.getListeners().add(listener);
        }

        public void removeListener(EventListener listener) {
            publisher_.getListeners().remove(listener);
        }

        @Override
        protected <E extends Entry<?>> E newEntry(K key) {
            return (E) new CountingEntry<O>();
        }

        @Override
        protected void onEntryAdded(Entry<O> entry) {
            publisher_.publish(listener -> listener.afterAddItem(entry.getObject()));
        }

        @Override
        protected void onEntryRemoving(Entry<O> entry) {
            publisher_.publish(listener -> listener.beforeRemoveItem(entry.getObject()));
        }

        @Override
        protected void onEntryGot(Entry<O> entry) {
            ((CountingEntry<O>) entry).incUsages();
        }

        public final void release(K key) {
            final EntryLock entryLock = acquireEntryLock(key);
            try {
                long usages;
                CountingEntry<O> entry;
                entryLock.readLock().lock();
                try {
                    entry = (CountingEntry<O>) internalGetEntryIfPresent(key);
                    Objects.requireNonNull(entry, String.format("Key '%s' not present in manager.", key));
                    usages = entry.decUsages();
                } finally {
                    entryLock.readLock().unlock();
                }
                if (usages == 0 && isRemoveUnusedObjects()) {
                    entryLock.writeLock().lock();
                    try {
                        if (canRemoveUnused(entry)) {
                            doRemoveEntry(key, entry);
                        }
                    } finally {
                        entryLock.writeLock().unlock();
                    }
                }
            } catch (Exception e) {
                throw ExceptionUtils.asUnchecked(e);
            } finally {
                releaseEntryLock(key);
            }
        }

        /**
         * Выполняет проверку условия, при котором производится удаление не используемого элемента.
         *
         * @param entry запись элемента.
         * @return true, если необходимо удалить, иначе false.
         */
        protected boolean canRemoveUnused(CountingEntry<O> entry) {
            return entry.getUsages() == 0;
        }

        protected static class CountingEntry<I> extends Entry<I> {
            private static final long serialVersionUID = 1626761723478454362L;
            private final AtomicLong usageCounter = new AtomicLong();

            public long getUsages() {
                return usageCounter.get();
            }

            public long incUsages() {
                return usageCounter.incrementAndGet();
            }

            public long decUsages() {
                // Счетчик не может уйти в минус. Если ушел - где-то проглядели
                return usageCounter.accumulateAndGet(-1, (x, dx) -> {
                    if (x == 0) {
                        throw new RuntimeException("Counter can not be lower zero.");
                    }
                    return x + dx;
                });
            }

        }
    }
}
