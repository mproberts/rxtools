package com.github.mproberts.rxtools.list;

import io.reactivex.Flowable;

public class SharedFlowableList<T> extends FlowableList<T> {

    private Flowable<Update<T>> _updates;

    SharedFlowableList(Flowable<Update<T>> updates) {
        _updates = updates.share();
    }

    @Override
    public Flowable<Update<T>> updates() {
        return _updates;
    }
}
