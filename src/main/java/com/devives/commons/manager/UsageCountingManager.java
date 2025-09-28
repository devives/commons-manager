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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Object manager with usage counting.
 *
 * @param <K> key type.
 * @param <O> managed object type.
 */
public class UsageCountingManager<K, O> implements Serializable {

    private static final long serialVersionUID = 8389806804677309190L;
    private final InternalManager<K, O> internalManager_;

    public UsageCountingManager() {
        this(new InternalManager<K, O>(Publishers
                .<Listener>builder()
                .listeners(b -> b.setSynchronized())
                .build()));
    }

    public UsageCountingManager(ManagedAdapter<O> defaultAdapter) {
        this(new InternalManager<K, O>(Publishers
                .<Listener>builder()
                .listeners(b -> b.setSynchronized())
                .build(), defaultAdapter));
    }

    public UsageCountingManager(Publisher<Listener> publisher, ManagedAdapter<O> defaultAdapter) {
        this(new InternalManager<K, O>(publisher, defaultAdapter));
    }

    protected UsageCountingManager(InternalManager<K, O> internalManager) {
        internalManager_ = Objects.requireNonNull(internalManager, "internalManager");
    }

    /**
     * Возвращает объект соответствующий указанному ключу. Перед возвращением объекта увеличивает счётчик использований объекта.
     *
     * @param key ключ объекта
     * @return объект соответсвующий ключу
     * @throws ManagerException Если объект с указанным ключом отсутствует в менеджере.
     */
    public O acquire(K key) throws ManagerException {
        return internalManager_.get(key);
    }

    /**
     * Возвращает существующий или новый объект, соответствующий указанному ключу.
     *
     * @param key     ключ объекта.
     * @param factory фабрика объекта.
     * @return объект соответсвующий ключу.
     */
    public O acquire(K key, Function<K, O> factory) {
        return internalManager_.computeIfAbsent(key, factory);
    }

    /**
     * Возвращает существующий или новый объект, соответствующий указанному ключу.
     *
     * @param key     ключ объекта.
     * @param factory фабрика объекта.
     * @return объект соответсвующий ключу.
     */
    public O acquire(K key, ObjectFactory<O> factory) {
        return internalManager_.computeIfAbsent(key, factory);
    }

    /**
     * Возвращает существующий или новый объект, соответствующий указанному ключу.
     *
     * @param key     ключ объекта.
     * @param factory фабрика объекта.
     * @return объект соответсвующий ключу.
     */
    public O acquire(K key, ManagedFactory<O> factory) {
        return internalManager_.computeIfAbsent(key, factory);
    }

    /**
     * Возвращает существующий или новый объект, соответствующий указанному ключу.
     *
     * @param key     ключ объекта.
     * @param factory фабрика объекта.
     * @param adapter адаптер жизненного цикла объекта к жизненному циклу управляемых объектов менеджера.
     * @return объект соответсвующий ключу.
     */
    public O acquire(K key, ObjectFactory<O> factory, ManagedAdapter<O> adapter) {
        return internalManager_.computeIfAbsent(key, factory, adapter);
    }

    /**
     * Возвращает существующий или новый объект, соответствующий указанному ключу.
     *
     * @param key     ключ объекта.
     * @param factory фабрика объекта.
     * @param adapter адаптер жизненного цикла объекта к жизненному циклу управляемых объектов менеджера.
     * @return объект соответсвующий ключу.
     */
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

    /**
     * Проверяет, является ли менеджер пустым.
     *
     * @return {@code true}, если менеджер не содержит объектов, иначе {@code false}.
     */
    public boolean isEmpty() {
        return internalManager_.isEmpty();
    }

    /**
     * Возвращает количество объектов, содержащихся в менеджере.
     *
     * @return количество объектов.
     */
    public int size() {
        return internalManager_.size();
    }

    /**
     * Флаг указывает, что менеджер удаляет объект при обнулении числа его использований.
     *
     * @return {@code true}, если объект удаляется, иначе {@code false}
     */
    public boolean isRemoveUnusedObjects() {
        return internalManager_.isRemoveUnusedObjects();
    }

    /**
     * Устанавливает значение свойства {@link #isRemoveUnusedObjects()}.
     *
     * @param value новое значение.
     */
    public void setRemoveUnusedObjects(boolean value) {
        internalManager_.setRemoveUnusedObjects(value);
    }

    /**
     * Добавляет слушателя в коллекцию слушателей событий менеджера.
     *
     * @param listener слушатель.
     */
    public void addListener(Listener listener) {
        internalManager_.addListener(listener);
    }

    /**
     * Удаляет слушателя из коллекции слушателей событий менеджера.
     *
     * @param listener слушатель.
     */
    public void removeListener(Listener listener) {
        internalManager_.removeListener(listener);
    }

    /**
     * Интерфейс слушателя событий менеджера
     *
     * @param <O> тип объектов менеджера.
     */
    public interface Listener<O> extends java.util.EventListener {

        /**
         * Вызывается после добавления объекта в менеджер и запуска объекта.
         *
         * @param object добавленный объект
         */
        void afterAddItem(O object);

        /**
         * Вызывается перед остановкой объекта и удаления объекта из менеджера.
         *
         * @param object удаляемый объект
         */
        void beforeRemoveItem(O object);
    }

    protected static class InternalManager<K, O> extends ConcurrentKeyedManager<K, O> {
        private static final long serialVersionUID = 242380967546124799L;
        private volatile boolean removeUnusedObjects_ = true;
        private final Publisher<Listener> publisher_;

        public InternalManager(Publisher<Listener> publisher) {
            publisher_ = Objects.requireNonNull(publisher, "publisher");
        }

        public InternalManager(Publisher<Listener> publisher, ManagedAdapter<O> defaultAdapter) {
            super(defaultAdapter);
            publisher_ = Objects.requireNonNull(publisher, "publisher");
        }

        public boolean isRemoveUnusedObjects() {
            return removeUnusedObjects_;
        }

        public void setRemoveUnusedObjects(boolean removeUnusedObjects) {
            removeUnusedObjects_ = removeUnusedObjects;
        }

        public void addListener(Listener listener) {
            publisher_.getListeners().add(listener);
        }

        public void removeListener(Listener listener) {
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

        /**
         * Метод увеличивает счётчик использований объекта.
         * <p>
         * Метод выполняется внутри блока с установленной блокировкой на чтение.
         *
         * @param entry the record
         */
        @Override
        protected void onEntryGot(Entry<O> entry) {
            ((CountingEntry<O>) entry).incUsages();
        }

        /**
         * Уменьшает число использований.
         * <p>
         * Если установлено свойство {@link #isRemoveUnusedObjects}, при уменьшении счётчика до "0", удаляет объект из
         * менеджера.
         *
         * @param key ключ
         */
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
            private final AtomicInteger usageCounter = new AtomicInteger();

            public int getUsages() {
                return usageCounter.get();
            }

            public int incUsages() {
                return usageCounter.incrementAndGet();
            }

            public int decUsages() {
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
