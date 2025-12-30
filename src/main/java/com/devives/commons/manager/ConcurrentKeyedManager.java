package com.devives.commons.manager;

import java.io.Serializable;

/**
 * Thread-safe concurrent implementation of {@link Manager}.
 *
 * @param <K> type of key
 * @param <O> type of managed object
 * @author Vladimir Ivanov {@code <ivvlev@devives.com>}
 * @deprecated Use {@link ConcurrentHashManager} instead.
 */
@Deprecated
public class ConcurrentKeyedManager<K, O> extends ConcurrentHashManager<K, O> implements Serializable {

    private static final long serialVersionUID = 2387557309207392164L;

    public ConcurrentKeyedManager() {
    }

    public ConcurrentKeyedManager(ManagedAdapter<O> defaultAdapter) {
        super(defaultAdapter);
    }
}
