package com.github.mproberts.rxtools.list;

import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;

class TransformFlowableList<K, V> extends FlowableList<V>
{
    private final FlowableList<K> _list;
    private final Function<List<K>, List<V>> _mappingMethod;

    TransformFlowableList(FlowableList<K> list, Function<List<K>, List<V>> mapping)
    {
        _list = list;
        _mappingMethod = mapping;
    }

    @Override
    public Flowable<Update<V>> updates()
    {
        return _list.updates().map(new Function<Update<K>, Update<V>>() {

            @Override
            public Update<V> apply(Update<K> key) throws Exception
            {
                return new Update<>(_mappingMethod.apply(key.list), key.changes);
            }
        });
    }
}
