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

import com.devives.commons.lang.ExceptionUtils;
import com.devives.commons.lang.function.FailableConsumer;
import com.devives.commons.lang.reflection.ProxyBuilder;
import com.devives.commons.lifecycle.Closeable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class ConcurrentManagedObjManagerTest {

    private final ServerContext serverContext = new ServerContextImpl();
    private final ActiveSessionFactory testSession1Factory = new ActiveSessionFactory(serverContext);
    private final PassiveSessionFactory testSession2Factory = new PassiveSessionFactory(serverContext);

    @Test
    public void create_ActiveSession_NoExceptions() throws Exception {
        forTestManager(manager -> {
            AbstractSession session1 = manager.create(testSession1Factory);
            Assertions.assertTrue(session1 instanceof ActiveSession);
            Assertions.assertEquals("A-1", session1.getId());
            Assertions.assertEquals(session1, manager.get(session1.getId()));
            Assertions.assertEquals(1, manager.size());
        });
    }

    @Test
    public void isStarted_ActiveSession_exceptionThrow() throws Exception {
        forTestManager(manager -> {
            AbstractSession session1 = manager.create(testSession1Factory);
            Assertions.assertTrue(session1.isStarted());
        });
    }

    @Test
    public void isStopped_ActiveSession_exceptionThrow() throws Exception {
        forTestManager(manager -> {
            AbstractSession session1 = manager.create(testSession1Factory);
            session1.close();
            Assertions.assertTrue(session1.isStopped());
        });
    }

    @Test
    public void runInSessionThread_ActiveSession_exceptionThrow() throws Exception {
        forTestManager(manager -> {
            AbstractSession session1 = manager.create(testSession1Factory);
            session1.runInSessionThread(() ->
                    Logger.getLogger(ConcurrentManagedObjManagerTest.class.getCanonicalName()).info("Some work in Thread = " + Thread.currentThread().getName())
            ).get();
        });
    }

    @Test
    public void runInSessionThread_PassiveSession_exceptionThrow() throws Exception {
        forTestManager(manager -> {
            AbstractSession session1 = manager.create(testSession2Factory);
            session1.runInSessionThread(() ->
                    Logger.getLogger(ConcurrentManagedObjManagerTest.class.getCanonicalName()).info("Some work in Thread = " + Thread.currentThread().getName())
            ).get();
        });
    }


    @Test
    public void create_PassiveSession_NoExceptions() throws Exception {
        forTestManager(manager -> {
            AbstractSession session1 = manager.create(testSession2Factory);
            Assertions.assertTrue(session1 instanceof PassiveSession);
            Assertions.assertEquals("P-1", session1.getId());
            Assertions.assertEquals(session1, manager.get(session1.getId()));
            Assertions.assertEquals(1, manager.size());
        });
    }

    @Test
    public void isActive_PassiveSession_exceptionThrow() throws Exception {
        forTestManager(manager -> {
            AbstractSession session2 = manager.create(testSession2Factory);
            Assertions.assertTrue(session2.isStarted());
        });
    }

    @Test
    public void close_PassiveSession_exceptionThrow() throws Exception {
        forTestManager(manager -> {
            AbstractSession session2 = manager.create(testSession2Factory);
            session2.close();
            Assertions.assertTrue(session2.isStopped());
            Assertions.assertTrue(manager.isEmpty());
        });
    }

    private static void forTestManager(FailableConsumer<ConcurrentManagedObjManager<AbstractSession>, Exception> consumer) throws Exception {
        ConcurrentManagedObjManager<AbstractSession> manager = new ConcurrentManagedObjManager<>();
        try {
            consumer.accept(manager);
        } finally {
            manager.close();
        }
    }

    private interface ServerContext {

        DataSource acquireDataSource();

        void releaseDataSource(DataSource dataSource);

    }

    private static class ServerContextImpl implements ServerContext {

        private final AtomicLong referenceCounter_ = new AtomicLong(0);
        private final DataSource dataSource_;

        public ServerContextImpl() {
            dataSource_ = ProxyBuilder.build(DataSource.class, new Object() {
                public Connection getConnection() throws SQLException {
                    return ProxyBuilder.build(Connection.class, new AutoCloseable() {
                        @Override
                        public void close() throws Exception {

                        }
                    });
                }
            });
        }

        @Override
        public DataSource acquireDataSource() {
            referenceCounter_.incrementAndGet();
            return dataSource_;
        }

        @Override
        public void releaseDataSource(DataSource dataSource) {
            referenceCounter_.decrementAndGet();
        }

    }

    /**
     *
     */
    private static abstract class SessionFactoryAbst implements ManagedObjFactory<String, AbstractSession, ConcurrentManagedObjManager<AbstractSession>> {

        protected final String keyPrefix_;
        protected final ServerContext serverContext_;
        protected volatile DataSource dataSource_;

        public SessionFactoryAbst(String keyPrefix, ServerContext serverContext) {
            keyPrefix_ = Objects.requireNonNull(keyPrefix);
            serverContext_ = Objects.requireNonNull(serverContext);
        }

        public String buildKey(long sequence) {
            return keyPrefix_ + sequence;
        }

        protected void acquireResources() {
            dataSource_ = serverContext_.acquireDataSource();
        }

        protected void releaseResources() {
            Optional.ofNullable(dataSource_).ifPresent(serverContext_::releaseDataSource);
        }
    }

    /**
     *
     */
    private static class ActiveSessionFactory extends SessionFactoryAbst {

        private ExecutorService executorService_;

        public ActiveSessionFactory(ServerContext serverContext) {
            super("A-", serverContext);
        }

        @Override
        public AbstractSession createObject(String key, ConcurrentManagedObjManager<AbstractSession> manager) throws Exception {
            acquireResources();
            executorService_ = Executors.newSingleThreadExecutor();
            return new ActiveSession(key, dataSource_, executorService_, manager::remove);
        }

        @Override
        public void startObject(AbstractSession session) throws Exception {
            Future<?> future = executorService_.submit(() -> {
                ExceptionUtils.passChecked(session::start);
            });
            future.get();
        }

        @Override
        public void stopObject(AbstractSession session) throws Exception {
            Future<?> future = executorService_.submit(() -> {
                ExceptionUtils.passChecked(session::stop);
            });
            future.get();
        }

        @Override
        public void destroyObject(AbstractSession object) throws Exception {
            releaseResources();
            executorService_.shutdown();
            super.destroyObject(object);
        }

    }

    /**
     *
     */
    private static class PassiveSessionFactory extends SessionFactoryAbst {

        public PassiveSessionFactory(ServerContext serverContext) {
            super("P-", serverContext);
        }

        @Override
        public AbstractSession createObject(String key, ConcurrentManagedObjManager<AbstractSession> manager) throws Exception {
            acquireResources();
            return new PassiveSession(key, dataSource_, manager::remove);
        }

        @Override
        public void startObject(AbstractSession session) throws Exception {
            session.start();
        }

        @Override
        public void stopObject(AbstractSession session) throws Exception {
            session.stop();
        }

        @Override
        public void destroyObject(AbstractSession object) throws Exception {
            releaseResources();
            super.destroyObject(object);
        }
    }

    private interface Session extends Closeable {

        String getId();

        String getSecret();
    }

    /**
     *
     */
    private static abstract class AbstractSession extends SynchronizedManagedObjAbst<AbstractSession> implements Session {

        private final String id_;
        private final String secret_ = UUID.randomUUID().toString();
        protected final DataSource dataSource_;

        public AbstractSession(String id, DataSource dataSource, FailableConsumer<AbstractSession, Exception> removeCallback) {
            super(removeCallback);
            id_ = Objects.requireNonNull(id);
            dataSource_ = Objects.requireNonNull(dataSource);
        }

        public String getId() {
            return id_;
        }

        public String getSecret() {
            return secret_;
        }

        public abstract Future<?> runInSessionThread(Runnable runnable);
    }

    /**
     *
     */
    private static final class ActiveSession extends AbstractSession {

        private final ExecutorService executorService_;

        public ActiveSession(String id,
                             DataSource dataSource,
                             ExecutorService executorService,
                             FailableConsumer<AbstractSession, Exception> closer) {
            super(id, dataSource, closer);
            executorService_ = Objects.requireNonNull(executorService);
        }

        @Override
        protected void onStart() throws Exception {
            Logger.getLogger(getClass().getCanonicalName()).info("Thread = " + Thread.currentThread().getName());
        }

        @Override
        protected void onStop() throws Exception {
            Logger.getLogger(getClass().getCanonicalName()).info("Thread = " + Thread.currentThread().getName());
        }

        public Future<?> runInSessionThread(Runnable runnable) {
            return executorService_.submit(runnable);
        }
    }

    /**
     *
     */
    private static final class PassiveSession extends AbstractSession {

        public PassiveSession(String id, DataSource dataSource, FailableConsumer<AbstractSession, Exception> removeCallback) {
            super(id, dataSource, removeCallback);
        }

        public Future<?> runInSessionThread(Runnable runnable) {
            CompletableFuture<?> future = new CompletableFuture<>();
            try {
                runnable.run();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
            return future;
        }

        @Override
        protected void onStart() throws Exception {
            Logger.getLogger(getClass().getCanonicalName()).info("Thread = " + Thread.currentThread().getName());
        }

        @Override
        protected void onStop() throws Exception {
            Logger.getLogger(getClass().getCanonicalName()).info("Thread = " + Thread.currentThread().getName());
        }
    }

}
