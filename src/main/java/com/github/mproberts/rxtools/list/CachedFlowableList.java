package com.github.mproberts.rxtools.list;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class CachedFlowableList<V> extends FlowableList<V>
{
    private class SizeLimitedMap<T> extends LinkedHashMap<Integer, T>
    {
        private int _maxLimit;

        SizeLimitedMap(int maxLimit)
        {
            _maxLimit = maxLimit;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry eldest)
        {
            return size() > _maxLimit;
        }
    }

    private final FlowableList<V> _list;
    private final int _weakCacheSize;
    private final int _strongCacheSize;

    CachedFlowableList(FlowableList<V> list, int weakCacheSize, int strongCacheSize)
    {
        _list = list;
        _weakCacheSize = weakCacheSize;
        _strongCacheSize = strongCacheSize;
    }

    private <T> Map<Integer, T> remapCache(Map<Integer, T> priorCache, List<Change> changes, int size)
    {
        Map<Integer, T> updatedCache = new SizeLimitedMap<>(size);

        for (Map.Entry<Integer, T> cacheEntry : priorCache.entrySet()) {
            int index = cacheEntry.getKey();
            T value = cacheEntry.getValue();

            for (Change change : changes) {
                switch (change.type) {
                    case Removed:
                        if (change.from < index) {
                            --index;
                        }
                        else if (change.from == index) {
                            // skip the entry
                            continue;
                        }
                        break;
                    case Moved:
                        if (change.from > index && change.to <= index) {
                            ++index;
                        }
                        else if (change.from < index && change.to > index) {
                            --index;
                        }
                        else if (change.from == index) {
                            index = change.to;
                        }
                        break;
                    case Inserted:
                        if (change.to <= index) {
                            ++index;
                        }
                        break;
                    case Reloaded:
                        // give up and return
                        updatedCache.clear();

                        return updatedCache;
                }
            }

            updatedCache.put(index, value);
        }

        return updatedCache;
    }

    @Override
    public Flowable<Update<V>> updates()
    {
        return _list.updates().map(new Function<Update<V>, Update<V>>() {

            private Map<Integer, WeakReference<V>> _weakCache = new SizeLimitedMap<>(_weakCacheSize);
            private Map<Integer, V> _strongCache = new SizeLimitedMap<>(_strongCacheSize);

            @Override
            public Update<V> apply(Update<V> update) throws Exception
            {
                Map<Integer, WeakReference<V>> weakCache = _weakCache;
                Map<Integer, V> strongCache = _strongCache;

                synchronized (weakCache) {
                    weakCache = remapCache(weakCache, update.changes, _weakCacheSize);
                }

                synchronized (strongCache) {
                    strongCache = remapCache(strongCache, update.changes, _strongCacheSize);
                }

                _weakCache = weakCache;
                _strongCache = strongCache;

                return new Update<>(new CachedList<>(update.list, 0, weakCache, strongCache), update.changes);
            }
        });
    }
}
