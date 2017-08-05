package com.github.mproberts.rxtools.types;

public class Item<T>
{
    private static final Item<?> INVALID = new Item<>(null, false);

    private final boolean _exists;
    private final T _value;

    @SuppressWarnings("unchecked")
    public static <T> Item<T> invalid()
    {
        return (Item<T>) INVALID;
    }

    public Item(T value)
    {
        this(value, true);
    }

    private Item(T value, boolean exists) {
        _exists = exists;
        _value = value;
    }

    public boolean exists()
    {
        return _exists;
    }

    public T getValue()
    {
        return _value;
    }
}
