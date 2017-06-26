package com.github.mproberts.rxtools.list;

import rx.Observable;
import rx.functions.Func1;

class VisibilityStateObservableList<T> implements ObservableList<T>
{
    private final ObservableList<VisibilityState<T>> _list;

    VisibilityStateObservableList(ObservableList<VisibilityState<T>> list)
    {
        _list = list;
    }

    @Override
    public Observable<Update<T>> updates()
    {
        return _list.updates().map(new Func1<Update<VisibilityState<T>>, Update<T>>() {
            @Override
            public Update<T> call(Update<VisibilityState<T>> visibilityStateUpdate)
            {
                return new Update<>(new TransformList<>(visibilityStateUpdate.list, new Func1<VisibilityState<T>, T>() {
                    @Override
                    public T call(VisibilityState<T> state)
                    {
                        return state.get();
                    }
                }), visibilityStateUpdate.changes);
            }
        });
    }
}
