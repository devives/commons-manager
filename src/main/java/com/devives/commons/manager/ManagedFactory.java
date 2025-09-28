package com.devives.commons.manager;

/**
 * A managed object factory that is also an adapter.
 *
 * @param <O> type of objects to create adn managed.
 */
public interface ManagedFactory<O> extends ObjectFactory<O>, ManagedAdapter<O> {
}
