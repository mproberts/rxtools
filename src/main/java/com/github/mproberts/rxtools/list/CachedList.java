package com.github.mproberts.rxtools.list;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;

class CachedList<T> extends TransformList<T, T>
{
    private final Map<Integer, WeakReference<T>> _weakCache;
    private final Map<Integer, T> _strongCache;
    private final int _offset;

    CachedList(List<T> list, int offset, Map<Integer, WeakReference<T>> weakCache, Map<Integer, T> strongCache)
    {
        super(list);

        _offset = offset;
        _weakCache = weakCache;
        _strongCache = strongCache;
    }

    @Override
    protected T getInternal(int index)
    {
        WeakReference<T> cachedRef = null;
        int offsetIndex = index;
        T value;

        synchronized (_strongCache) {
            value = _strongCache.get(offsetIndex);
        }

        if (value == null) {
            synchronized (_weakCache) {
                cachedRef = _weakCache.get(offsetIndex);

                if (cachedRef != null) {
                    value = cachedRef.get();
                }
            }
        }

        if (value == null) {
            value = super.getInternal(index);
        }

        if (value != null) {
            synchronized (_strongCache) {
                _strongCache.put(offsetIndex, value);
            }

            if (cachedRef == null) {
                cachedRef = new WeakReference<>(value);
            }

            synchronized (_weakCache) {
                _weakCache.put(offsetIndex, cachedRef);
            }
        }

        return value;
    }

    @Override
    protected T transform(T value, int index)
    {
        return value;
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex)
    {
        return new CachedList<>(getList().subList(fromIndex, toIndex), _offset + fromIndex, _weakCache, _strongCache);
    }
}