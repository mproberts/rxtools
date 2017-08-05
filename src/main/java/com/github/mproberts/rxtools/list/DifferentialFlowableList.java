package com.github.mproberts.rxtools.list;

import io.reactivex.*;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Function;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class DifferentialFlowableList<T> extends FlowableList<T>
{
    private final boolean _alwaysReload;
    private final Flowable<Update<T>> _diffTransform;
    private List<T> _previousList;

    private static <T> List<Change> computeDiff(List<T> before, List<T> after)
    {
        return Collections.singletonList(Change.reloaded());
    }

    DifferentialFlowableList(Flowable<List<T>> listStream)
    {
        this(listStream, false);
    }

    DifferentialFlowableList(Flowable<List<T>> list, boolean alwaysReload)
    {
        _alwaysReload = alwaysReload;
        _diffTransform = list
                .map(new Function<List<T>, Update<T>>() {
                    @Override
                    public Update<T> apply(List<T> ts) {
                        return new Update<T>(ts, Change.reloaded());
                    }
                })
                .scan(new BiFunction<Update<T>, Update<T>, Update<T>>() {
                    @Override
                    public Update<T> apply(Update<T> previous, Update<T> next) {
                        if (previous == null) {
                            return next;
                        }

                        List<Change> changes = computeDiff(previous.list, next.list);

                        _previousList = next.list;

                        return new Update<>(next.list, changes);
                    }
                });
    }

    @Override
    public Flowable<Update<T>> updates()
    {
        return _diffTransform
                .startWith(Flowable.create(new FlowableOnSubscribe<Update<T>>() {
                    @Override
                    public void subscribe(FlowableEmitter<Update<T>> updateEmitter) throws Exception {
                        if (_previousList != null) {
                            Update<T> update = new Update<>(new ArrayList<>(_previousList), Change.reloaded());
                            updateEmitter.onNext(update);
                        }

                        updateEmitter.onComplete();
                    }
                }, BackpressureStrategy.LATEST));
    }
}
