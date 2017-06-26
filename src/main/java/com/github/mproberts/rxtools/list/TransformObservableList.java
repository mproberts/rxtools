package com.github.mproberts.rxtools.list;

import java.util.List;

import rx.Observable;
import rx.functions.Func1;

class TransformObservableList<K, V> implements ObservableList<V>
{
    private final ObservableList<K> _list;
    private final Func1<List<K>, List<V>> _mappingMethod;

    TransformObservableList(ObservableList<K> list, Func1<List<K>, List<V>> mapping)
    {
        _list = list;
        _mappingMethod = mapping;
    }

    @Override
    public Observable<Update<V>> updates()
    {
        return _list.updates().map(new Func1<Update<K>, Update<V>>() {

            @Override
            public Update<V> call(Update<K> key)
            {
                return new Update<>(_mappingMethod.call(key.list), key.changes);
            }
        });
    }
}
