package com.github.mproberts.rxtools;

import rx.Observable;
import rx.functions.Func1;

public class MappedObservableList<K, V> implements ObservableList<Observable<V>>
{
    private final ObservableList<K> _list;
    private final SubjectMap<K, V> _mapping;
    private final Func1<K, Observable<V>> _mappingMethod = new Func1<K, Observable<V>>() {
        @Override
        public Observable<V> call(K key)
        {
            return _mapping.get(key);
        }
    };

    public MappedObservableList(ObservableList<K> list, SubjectMap<K, V> mapping)
    {
        _list = list;
        _mapping = mapping;
    }

    @Override
    public Observable<Update<Observable<V>>> updates()
    {
        return _list.updates().map(new Func1<Update<K>, Update<Observable<V>>>() {

            @Override
            public Update<Observable<V>> call(Update<K> key)
            {
                return new Update<>(new TransformList<K, Observable<V>>(key.list(), _mappingMethod), key.changes());
            }
        });
    }
}
