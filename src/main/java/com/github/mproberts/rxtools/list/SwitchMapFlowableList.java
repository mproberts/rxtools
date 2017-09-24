package com.github.mproberts.rxtools.list;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import org.reactivestreams.Publisher;

class SwitchMapFlowableList<T> extends FlowableList<T>
{
    private final Flowable<? extends FlowableList<T>> _list;

    public SwitchMapFlowableList(Flowable<? extends FlowableList<T>> list)
    {
        _list = list;
    }

    @Override
    public Flowable<Update<T>> updates()
    {
        return _list.switchMap(new Function<FlowableList<T>, Publisher<Update<T>>>() {
            @Override
            public Publisher<Update<T>> apply(FlowableList<T> flowableList) throws Exception
            {
                return flowableList.updates();
            }
        });
    }
}
