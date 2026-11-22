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

import com.devives.commons.manager.lock.NoopLockSource;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Object manager interface.
 * <p>
 * The manager manages the life cycle of objects accessed via key.
 * </p>
 *
 * @param <K> type of key
 * @param <O> type of managed object
 * @author Vladimir Ivanov {@code <ivvlev@devives.com>}
 */
public interface Manager<K, O> {

    /**
     * Return instance of the class {@code O} corresponding to the key.
     * <p>
     * При получении ссылки на объект выполняется блокировка на чтение. Если в этот момент другой поток установил
     * блокировку на запись для выполняет операции запуска {@link ManagedAdapter#startObject(Object)} или
     * остановки {@link ManagedAdapter#stopObject(Object)} запрашиваемого объекта, метод будет ждать снятия блокировки
     * на запись.
     *
     * @param key key
     * @return instance of {@code O}
     * @throws ManagerException if {@code key} is not present in manager.
     */
    O get(K key) throws ManagerException;

    /**
     * Return instance of the class {@code O} corresponding to the key if it presents in manager.
     *
     * @param key key
     * @return instance of {@code O} or {@code null}.
     */
    O getIfPresent(K key);

    /**
     * If the specified key is not already associated with a value,
     * attempts to compute its value using the given mapping function
     * and enters it into this manager.
     *
     * @param key     key
     * @param factory the supplier to compute an objects factory.
     * @return instance of {@code O}
     * @see ConcurrentHashMap#computeIfAbsent(Object, Function)
     */
    O computeIfAbsent(K key, Function<K, O> factory);

    /**
     * If the specified key is not already associated with a value,
     * attempts to compute its value using the given mapping function
     * and enters it into this manager.
     *
     * @param key     key
     * @param factory the supplier to compute an objects factory.
     * @param adapter adapter of {@code <O>} lifecycle to the managed object lifecycle.
     * @return instance of {@code O}
     */
    O computeIfAbsent(K key, Function<K, O> factory, ManagedAdapter<O> adapter);

    /**
     * If the specified key is not already associated with a value,
     * attempts to compute its value using the given mapping function
     * and enters it into this manager.
     *
     * @param key     key
     * @param factory the supplier to compute an objects factory.
     * @return instance of {@code O}
     * @see ConcurrentHashMap#computeIfAbsent(Object, Function)
     */
    O computeIfAbsent(K key, ObjectFactory<O> factory);

    /**
     * If the specified key is not already associated with a value,
     * attempts to compute its value using the given mapping function
     * and enters it into this manager.
     *
     * @param key     key
     * @param factory the supplier to compute an objects factory.
     * @param adapter adapter of {@code <O>} lifecycle to the managed object lifecycle.
     * @return instance of {@code O}
     * @see ConcurrentHashMap#computeIfAbsent(Object, Function)
     */
    O computeIfAbsent(K key, ObjectFactory<O> factory, ManagedAdapter<O> adapter);

    /**
     * If the specified key is not already associated with a value,
     * attempts to compute its value using the given mapping function
     * and enters it into this manager.
     *
     * @param key            key
     * @param managedFactory the supplier to compute an objects factory.
     * @return instance of {@code O}
     * @see ConcurrentHashMap#computeIfAbsent(Object, Function)
     */
    O computeIfAbsent(K key, ManagedFactory<O> managedFactory);

    /**
     * Put new instance of {@code O} in to manager.
     * <p>
     * If the {@code key} is already present, the previous instance is stopped, destroyed and replaced by the new one.
     * <p>
     * Replacement is not atomic: the previous instance is deinitialized (stopped, removed, destroyed)
     * before the new one is created and started. If creation or startup of the new instance fails,
     * the exception propagates, the previous instance is <em>not</em> restored, and the {@code key}
     * is no longer associated with a value.
     *
     * @param key     key
     * @param factory the object factory.
     * @return the previous instance of {@code O} if it was present, otherwise {@code null}.
     */
    O put(K key, Function<K, O> factory);

    /**
     * Put new instance of {@code O} in to manager.
     * <p>
     * If the {@code key} is already present, the previous instance is stopped, destroyed and replaced by the new one.
     * <p>
     * Replacement is not atomic: the previous instance is deinitialized (stopped, removed, destroyed)
     * before the new one is created and started. If creation or startup of the new instance fails,
     * the exception propagates, the previous instance is <em>not</em> restored, and the {@code key}
     * is no longer associated with a value.
     *
     * @param key     key
     * @param factory the supplier to compute an objects factory.
     * @return the previous instance of {@code O} if it was present, otherwise {@code null}.
     */
    O put(K key, ObjectFactory<O> factory);

    /**
     * Put new instance of {@code O} in to manager.
     * <p>
     * If the {@code key} is already present, the previous instance is stopped, destroyed and replaced by the new one.
     * <p>
     * Replacement is not atomic: the previous instance is deinitialized (stopped, removed, destroyed)
     * before the new one is created and started. If creation or startup of the new instance fails,
     * the exception propagates, the previous instance is <em>not</em> restored, and the {@code key}
     * is no longer associated with a value.
     *
     * @param key     key
     * @param factory the supplier to compute an objects factory.
     * @param adapter adapter of {@code <O>} lifecycle to the managed object lifecycle.
     * @return the previous instance of {@code O} if it was present, otherwise {@code null}.
     */
    O put(K key, ObjectFactory<O> factory, ManagedAdapter<O> adapter);

    /**
     * Put new instance of {@code O} in to manager.
     * <p>
     * If the {@code key} is already present, the previous instance is stopped, destroyed and replaced by the new one.
     * <p>
     * Replacement is not atomic: the previous instance is deinitialized (stopped, removed, destroyed)
     * before the new one is created and started. If creation or startup of the new instance fails,
     * the exception propagates, the previous instance is <em>not</em> restored, and the {@code key}
     * is no longer associated with a value.
     *
     * @param key            key
     * @param managedFactory the supplier to compute an objects factory.
     * @return the previous instance of {@code O} if it was present, otherwise {@code null}.
     */
    O put(K key, ManagedFactory<O> managedFactory);

    /**
     * Removes a key and its corresponding object from the manager.
     *
     * @param key key
     * @return instance of {@code O} or {@code null}.
     */
    O remove(K key);

    /**
     * Remove all instances from manager.
     *
     * @return a list containing all removed instances.
     */
    List<O> removeAll();

    /**
     * Remove all instances from manager.
     */
    void clear();

    /**
     * Check presence of {@code key} in the manager.
     *
     * @param key key
     * @return {@code true} if present, else {@code false}.
     * @see ConcurrentHashMap#containsKey(Object)
     */
    boolean containsKey(K key);

    /**
     * Returns a {@link Set} view of the keys contained in this manager.
     * <p>
     * The set is unmodifiable.
     *
     * @return a set view of the keys contained in this manager.
     */
    Set<K> keySet();

    /**
     * Returns a {@link Collection} view of the values contained in this manager.
     * The collection is backed by the manager, so changes to the manager are
     * reflected in the collection, and vice-versa.  If the manager is
     * modified while an iteration over the collection is in progress,
     * the results of the iteration are undefined.
     * <p>
     * The collection is unmodifiable.
     *
     * @return a collection view of the values contained in this manager.
     */
    Collection<O> values();

    /**
     * Returns <code>true</code> if this manages contains no objects.
     *
     * @return {@code true} if manager is empty, else {@code false}.
     */
    boolean isEmpty();

    /**
     * Return the count of objects in manager.
     *
     * @return count of object
     */
    int size();

    /**
     * Call an {@code action} for each key-value pair.
     * <p>
     * Iteration starts from the keys present at the call start. Non-concurrent implementations
     * traverse a snapshot of the key set: a key added during the call is not processed.
     * Concurrent implementations traverse a live weakly-consistent view: a key added during the
     * call may or may not be processed. A key removed before it is reached is skipped either way.
     * Modification of the manager from {@code action} (for example, {@code remove}) is safe — no
     * {@code ConcurrentModificationException} is thrown.
     * <p>
     * The value is passed to {@code action} without retention: a concurrent {@code remove} may
     * stop and destroy the object before or during {@code action} — liveness checks are the
     * action's responsibility. A retaining counterpart is
     * {@link UsageCountingManager#forEach(java.util.function.Consumer)} (its retention is limited,
     * see that method's contract).
     *
     * @param action action
     */
    void forEach(BiConsumer<? super K, ? super O> action);

    /**
     * Hook methods invoked by a manager during managed object lifecycle operations.
     * <p>
     * Hooks run synchronously inside a lifecycle transition of the key, under the lock for this key.
     * Throwing is a supported way to influence the transition; exceptions propagate to the caller of
     * the public operation as-is, per the module exception policy (checked exceptions from user
     * callbacks are not wrapped).
     * <p>
     * Reentrant modification of the same key from a hook is rejected: the key is in a lifecycle
     * transition, and {@link #computeIfAbsent(Object, ObjectFactory)}, {@link #put(Object, ObjectFactory)}
     * and {@link #remove(Object)} (as well as {@code release} of a usage-counting manager) throw
     * {@link ManagerException} for it. To cancel or roll back the ongoing transition, throw from the
     * hook instead. Reentrant reads ({@link #get(Object)}, {@link #getIfPresent(Object)}) are allowed.
     * The same rules apply to the {@link ManagedAdapter} callbacks invoked around these hooks.
     *
     * @param <K> type of key
     * @param <O> type of managed object
     */
    interface Hooks<K, O> {

        /**
         * Called after the factory has successfully created the object, before the object is bound
         * to the entry and published in the manager.
         * <p>
         * If this method throws, the object is destroyed via {@link ManagedAdapter#destroyObject(Object)},
         * the creation fails, nothing is published under the key, and the exception propagates to the caller.
         *
         * @param key    key of the object being created
         * @param object the newly created object
         */
        default void onObjectCreated(K key, O object) throws Exception {

        }

        /**
         * Called before {@link ManagedAdapter#startObject(Object)}. At this moment the object is already
         * bound to the entry and the entry is published in the manager, so that a reentrant read from
         * the current thread (see {@link #get(Object)}) can obtain the object.
         * <p>
         * If this method throws, {@link #onObjectFailure(Object, Throwable)} is notified with this
         * exception, the entry is removed from the manager, the object is destroyed, and the exception
         * propagates to the caller — the transition is rolled back.
         *
         * @param object the object being started
         */
        default void onObjectStarting(O object) throws Exception {

        }

        /**
         * Called after {@link ManagedAdapter#startObject(Object)} completed successfully.
         * <p>
         * If this method throws, the object is stopped (stop hooks and
         * {@link ManagedAdapter#stopObject(Object)} run), {@link #onObjectFailure(Object, Throwable)}
         * is notified, the entry is removed from the manager, the object is destroyed, and the exception
         * propagates to the caller — the transition is rolled back.
         *
         * @param object the started object
         */
        default void onObjectStarted(O object) throws Exception {

        }

        /**
         * Called when starting or stopping the object failed, with the failure cause.
         * <p>
         * If this method throws, its exception becomes the primary one and the original failure is
         * attached to it as suppressed.
         *
         * @param object     the object whose start or stop failed
         * @param throwable  the failure cause
         */
        default void onObjectFailure(O object, Throwable throwable) throws Exception {

        }

        /**
         * Called before {@link ManagedAdapter#stopObject(Object)}. Both run in a collecting scope: if
         * this method throws, the adapter stop and {@link #onObjectStopped(Object)} are still attempted
         * and the exceptions are aggregated.
         * <p>
         * A stop-phase exception does not cancel the removal: during deinitialization the entry is
         * removed and the object is destroyed regardless, and the aggregated exception propagates to
         * the caller. During rollback of a failed initialization a stop failure additionally notifies
         * {@link #onObjectFailure(Object, Throwable)}.
         *
         * @param object the object being stopped
         */
        default void onObjectStopping(O object) throws Exception {

        }

        /**
         * Called after {@link ManagedAdapter#stopObject(Object)}, in the same collecting scope as
         * {@link #onObjectStopping(Object)}.
         *
         * @param object the stopped object
         */
        default void onObjectStopped(O object) throws Exception {

        }

        /**
         * Called before {@link ManagedAdapter#destroyObject(Object)}. The three destroy-phase callbacks
         * ({@code onObjectDestroying}, the adapter destroy, {@link #onObjectDestroyed(Object)}) all run
         * even if one of them throws; the exceptions are aggregated and thrown to the caller.
         * <p>
         * By this point the transition has passed the point of no return — throwing here does not
         * undo the removal of the entry.
         *
         * @param object the object being destroyed
         */
        default void onObjectDestroying(O object) throws Exception {

        }

        /**
         * Called after {@link ManagedAdapter#destroyObject(Object)}, in the same collecting scope as
         * {@link #onObjectDestroying(Object)}.
         *
         * @param object the destroyed object
         */
        default void onObjectDestroyed(O object) throws Exception {

        }
    }

    /**
     * Provider of per-key locks used by the manager to serialize lifecycle operations on the same key.
     *
     * @param <K> type of key
     */
    interface LockSource<K> {

        /**
         * Acquires the lock for the key. Every call must be paired with {@link #release(Object)}
         * in a {@code finally} block. Implementations may keep a single lock instance per key while
         * it is acquired (the standard sources do).
         *
         * @param key key
         * @return lock instance for the key
         */
        Lock acquire(K key);

        /**
         * Releases the lock instance previously returned by {@link #acquire(Object)} for the key.
         *
         * @param key key
         */
        void release(K key);
    }

    /**
     * Per-key lock used by the manager. Implementations must support the lock usage patterns
     * of the manager:
     * <ul>
     * <li>recursive {@link #lockRead()} acquisitions by the same thread;</li>
     * <li>{@link #lockRead()}/{@link #unlockRead()} by the thread that holds the write lock —
     * the manager performs reentrant reads (see {@link #get(Object)}, {@link #getIfPresent(Object)})
     * from user callbacks while holding the write lock;</li>
     * <li>balanced {@link #upgradeLock()}/{@link #downgradeLock()} transitions described in their
     * contracts.</li>
     * </ul>
     */
    interface Lock {

        void lockRead();

        void unlockRead();

        void lockWrite();

        void unlockWrite();

        /**
         * Upgrades the read lock held by the current thread to the write lock.
         * <p>
         * The method is called by a thread holding the read lock it gives up. On normal return the
         * thread must hold the write lock and must no longer hold that read acquisition: the manager
         * calls {@link #downgradeLock()} in a {@code finally} right after the upgraded section, and
         * {@link #unlockRead()} exactly once at the outer level.
         * <p>
         * <strong>Throwing contract.</strong> If this method throws, the manager skips the paired
         * {@code downgradeLock()}, but the enclosing {@code finally} block still calls
         * {@link #unlockRead()} exactly once. An implementation that drops the read acquisition
         * before a step that can throw must therefore be self-recovering: before rethrowing, it must
         * either keep the read acquisition held or re-acquire the read lock, so that this single
         * {@code unlockRead()} balances the state. Throwing after dropping the read lock without
         * restoring it silently corrupts the per-key mutual exclusion for all subsequent operations
         * (CM-21). The standard sources ({@code RWLockSource}, {@code SyncLockSource}) never throw
         * from here; third-party implementations must honor this balance rule.
         */
        void upgradeLock();

        /**
         * Downgrades the write lock acquired via {@link #upgradeLock()} back to the read lock.
         * <p>
         * The method is called by the thread holding that write lock, in a {@code finally} block.
         * On return the thread must hold exactly the read acquisition it had before the upgrade, so
         * that the manager's outer {@link #unlockRead()} balances it.
         */
        void downgradeLock();
    }

    static <K> LockSource<K> noopLockSource() {
        return new NoopLockSource<K>();
    }

    static <O> ManagedAdapter<O> noopManagedAdapter() {
        return new NoopManagedAdapter<O>();
    }

    static <K, O> Hooks<K, O> noopHooks() {
        return new NoopHooks<>();
    }

}
