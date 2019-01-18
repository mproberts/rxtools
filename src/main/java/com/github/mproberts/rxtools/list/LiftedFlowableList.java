package com.github.mproberts.rxtools.list;

import io.reactivex.Flowable;
import io.reactivex.FlowableOperator;

public class LiftedFlowableList<T> extends FlowableList<T> {

    private Flowable<Update<T>> _updates;
    private FlowableOperator<Update<T>, Update<T>> _operator;

    LiftedFlowableList(Flowable<Update<T>> updates, FlowableOperator<Update<T>, Update<T>> operator) {
        _updates = updates;
        _operator = operator;
    }

    @Override
    public Flowable<Update<T>> updates() {
        return _updates.lift(_operator);
    }
}
