package com.github.mproberts.rxtools.list;

/**
 * A helper class for implementing VisibilityState items which return themselves as the
 * backing object
 * @param <T> Refers to the type of the implementing class
 */
public abstract class IdentityVisibilityState<T extends IdentityVisibilityState<T>> implements VisibilityState<T>
{
    @Override
    public final T get()
    {
        return (T) this;
    }
}
