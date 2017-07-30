package com.github.mproberts.rxtools.list;


import io.reactivex.Flowable;

class BackpressureMergeObservableList<T> implements ObservableList<T>
{
    private final ObservableList<T> _list;

    public BackpressureMergeObservableList(ObservableList<T> list)
    {
        _list = list;
    }

    @Override
    public Flowable<Update<T>> updates()
    {
        return _list.updates().lift(OperatorOnBackpressureMerge.<T>instance());
    }
}
