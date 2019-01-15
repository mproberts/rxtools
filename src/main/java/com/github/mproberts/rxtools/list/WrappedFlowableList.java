package com.github.mproberts.rxtools.list;

import io.reactivex.Flowable;

class WrappedFlowableList<T> extends FlowableList<T>
{
    private final Flowable<Update<T>> _wrappedFlowable;

    public WrappedFlowableList(Flowable<Update<T>> flowable)
    {
        _wrappedFlowable = flowable;
    }

    @Override
    public Flowable<Update<T>> updates()
    {
        return _wrappedFlowable;
    }
}
