package com.github.mproberts.rxtools.list;

import io.reactivex.Flowable;

/**
 * Defines a base interface for objects capable of appearing and disappearing from
 * ObservableList instances. The underlying object returned by {@link #get() get} will
 * be included in the parent ObservableList iff _isVisible has emitted true.
 * @param <T> The type of the backing object
 */
public interface VisibilityState<T>
{
    /**
     * @return The backing object
     */
    T get();

    /**
     * An observable which emits true whenever the backing data is to be inserted
     * into the containing ObservableList
     * @return An observable of visibility state
     */
    Flowable<Boolean> isVisible();
}
