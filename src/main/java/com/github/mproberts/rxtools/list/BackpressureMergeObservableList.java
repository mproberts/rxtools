package com.github.mproberts.rxtools.list;

import rx.Observable;

class BackpressureMergeObservableList<T> implements ObservableList<T>
{
    private final ObservableList<T> _list;

    public BackpressureMergeObservableList(ObservableList<T> list)
    {
        _list = list;
    }

    @Override
    public Observable<Update<T>> updates()
    {
        return _list.updates().lift(OperatorOnBackpressureMerge.<T>instance());
    }
}
