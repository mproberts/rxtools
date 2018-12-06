package com.github.mproberts.rxtools.types;

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import io.reactivex.functions.Function;

public class Optional<T>
{
    private static final int OPTIONAL_HASHCODE_MIX = 0xbf206d77;
    private static final Optional<?> INVALID = new Optional<>(null);

    private final T _value;

    @SuppressWarnings("unchecked")
    public static <T> Optional<T> empty()
    {
        return (Optional<T>) INVALID;
    }

    public static <T> Optional<T> of(@NonNull T value)
    {
        return new Optional<>(value);
    }

    @SuppressWarnings("unchecked")
    public static <T> Optional<T> ofNullable(@Nullable T value)
    {
        return value == null ? (Optional<T>) empty() : of(value);
    }

    private Optional(T value)
    {
        _value = value;
    }

    public T get()
    {
        return _value;
    }

    public <S> Optional<S> map(Function<T, S> map)
    {
        try {
            return _value == null ? Optional.<S>empty() : Optional.of(map.apply(_value));
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isPresent()
    {
        return _value != null;
    }

    public T orElse(@NonNull T other)
    {
        return _value == null ? other : _value;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Optional)) {
            return false;
        }

        Optional<?> other = (Optional<?>) obj;

        if (_value == other._value) {
            return true;
        } else if (_value == null || other._value == null) {
            return false;
        }

        return _value.equals(other._value);
    }

    @Override
    public int hashCode() {
        if (_value == null) {
            return 0;
        }

        return _value.hashCode() ^ OPTIONAL_HASHCODE_MIX;
    }

    @Override
    public String toString() {
        if (_value == null) {
            return "Optional(null)";
        }

        return "Optional(" + _value.toString() + ")";
    }
}
