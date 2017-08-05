package com.github.mproberts.rxtools.list;

import io.reactivex.Flowable;


import java.util.List;

class SingletonFlowableList<T> implements FlowableList<T>
{
    private final Flowable<Update<T>> _justReloadObservable;

    public SingletonFlowableList(List<T> list)
    {
        _justReloadObservable = Flowable.just(new Update<>(list, Change.reloaded()));
    }

    @Override
    public Flowable<Update<T>> updates()
    {
        return _justReloadObservable;
    }
}
