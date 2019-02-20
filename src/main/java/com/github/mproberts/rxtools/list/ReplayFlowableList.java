package com.github.mproberts.rxtools.list;

import io.reactivex.Flowable;

public class ReplayFlowableList<T> extends FlowableList<T> {

    private Flowable<Update<T>> _updates;

    ReplayFlowableList(Flowable<Update<T>> updates, int bufferSize) {
        _updates = updates.replay(bufferSize).autoConnect();
    }

    @Override
    public Flowable<Update<T>> updates() {
        return _updates;
    }
}
