package com.github.mproberts.rxtools.list;

import io.reactivex.functions.Consumer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class PrefetchList<T> extends TransformList<T, T>
{
    private final Consumer<Collection<T>> _prefetchCallback;
    private final int _prefetchBeforeCount;
    private final int _prefetchAfterCount;

    PrefetchList(List<T> list, int prefetchBeforeCount, int prefetchAfterCount, Consumer<Collection<T>> prefetchCallback)
    {
        super(list);

        _prefetchCallback = prefetchCallback;
        _prefetchBeforeCount = prefetchBeforeCount;
        _prefetchAfterCount = prefetchAfterCount;
    }

    @Override
    protected T transform(T value, int index)
    {
        try {
            List<T> prefetchList = new ArrayList<>();

            // before the index, in newest-first order
            for (int i = index - 1, e = index - _prefetchBeforeCount - 1; i > e && i >= 0; --i) {
                prefetchList.add(getInternal(i));
            }

            // after the index, in oldest-first order
            for (int i = index + 1, s = size(), e = index + _prefetchAfterCount + 1; i < s && i < e; ++i) {
                prefetchList.add(getInternal(i));
            }

            _prefetchCallback.accept(prefetchList);
        }
        catch (RuntimeException re) {
            throw re;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        return value;
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex)
    {
        return new PrefetchList<>(getList().subList(fromIndex, toIndex), _prefetchBeforeCount, _prefetchAfterCount, _prefetchCallback);
    }
}