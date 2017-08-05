package com.github.mproberts.rxtools.list;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import org.reactivestreams.Publisher;

class FlatMapFlowableList<T> extends FlowableList<T>
{
    private final Flowable<? extends FlowableList<T>> _list;

    public FlatMapFlowableList(Flowable<? extends FlowableList<T>> list)
    {
        _list = list;
    }

    @Override
    public Flowable<Update<T>> updates()
    {
        return _list.flatMap(new Function<FlowableList<T>, Publisher<Update<T>>>() {
            @Override
            public Publisher<Update<T>> apply(FlowableList<T> flowableList) throws Exception
            {
                return flowableList.updates();
            }
        });
    }
}
