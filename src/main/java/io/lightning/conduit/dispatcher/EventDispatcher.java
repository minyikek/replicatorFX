package io.lightning.conduit.dispatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public interface EventDispatcher<E> extends Dispatcher<E> {


    /**
     * Get the list of listeners for this dispatcher.
     */
    List<Listener<E>> getListeners();

    /**
     * Default logger for the EventDispatcher interface.
     */
    default Logger getLogger() {
        // Use the implementing class as the logger name
        return LoggerFactory.getLogger(this.getClass());
    }

    /**
     * Register a listener/callback.
     */
    default void register(Listener<E> listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        getListeners().add(listener);
    }

    /**
     * Remove a previously registered listener.
     * Returns true if the listener was found and removed.
     */
    @Override
    default boolean unregister(Listener<E> listener) {
        return getListeners().remove(listener);
    }

    /**
     * Fire the event to every registered listener.
     */
    default void dispatch(E event) {
        for (Listener<E> listener : getListeners()) {
            try {
                listener.onEvent(event);
            } catch (Exception ex) {
                getLogger().warn("Exception in event listener: {}", ex.getMessage(), ex);
            }
        }
    }

    /**
     * Convenience: clear all listeners at once.
     */
    @Override
    default void clear() {
        getListeners().clear();
    }

    /**
     * Static factory method to create an EventDispatcher instance.
     */
    static <E> EventDispatcher<E> create() {
        List<Listener<E>> listeners = new CopyOnWriteArrayList<>();
        return new EventDispatcher<>() {
            @Override
            public List<Listener<E>> getListeners() {
                return listeners;
            }

            @Override
            public Logger getLogger() {
                return LoggerFactory.getLogger(EventDispatcher.class);
            }
        };
    }
}