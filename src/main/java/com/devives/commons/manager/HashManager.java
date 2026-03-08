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
import com.devives.commons.manager.lock.NoopLockSource;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Single-thread implementation of {@link Manager}.
 *
 * @param <K> type of key
 * @param <O> type of managed object
 * @author Vladimir Ivanov {@code <ivvlev@devives.com>}
 */
public class HashManager<K, O> extends AbstractManager<K, O> implements Serializable {

    private static final long serialVersionUID = 1L;

    public HashManager() {
        super(new HashMap<>(), new NoopLockSource<K>());
    }

    public HashManager(ManagedAdapter<O> defaultAdapter) {
        super(new HashMap<>(), new NoopLockSource<K>(), defaultAdapter);
    }

    protected final List<O> doRemoveAll() {
        final List<Throwable> exceptionList = new ArrayList<>();
        final List<O> list = new ArrayList<>();
        final List<K> keys = new ArrayList<>(keySet());
        keys.forEach(key -> {
            try {
                // The write lock will set in doRemove().
                O item = doRemove(key);
                if (item != null) {
                    list.add(item);
                }
            } catch (Exception e) {
                exceptionList.add(new ManagerException("Error while removing key = '" + key + "'", e));
            }
        });
        ExceptionUtils.throwCollected(exceptionList);
        return list;
    }

    protected final void doClear() {
        final List<Throwable> exceptionList = new ArrayList<>();
        final List<K> keys = new ArrayList<>(keySet());
        keys.forEach(key -> {
            try {
                // The write lock will set in doRemove().
                doRemove(key);
            } catch (Exception e) {
                exceptionList.add(new ManagerException("Error while removing key = '" + key + "'", e));
            }
        });
        ExceptionUtils.throwCollected(exceptionList);
    }

}
