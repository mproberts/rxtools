package com.github.mproberts.rxtools.list;

import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subscriptions.CompositeSubscription;

import java.util.Collections;
import java.util.List;

class DifferentialObservableList<T> extends BaseObservableList<T>
{
    private final CompositeSubscription _subscription = new CompositeSubscription();
    private final Observable<List<T>> _list;
    private final boolean _alwaysReload;

    private static <T> List<Change> computeDiff(List<T> before, List<T> after)
    {
        return Collections.singletonList(Change.reloaded());
    }

    DifferentialObservableList(Observable<List<T>> list, boolean alwaysReload)
    {
        _list = list;
        _alwaysReload = alwaysReload;
    }

    DifferentialObservableList(Observable<List<T>> listStream)
    {
        _list = listStream;
        _alwaysReload = false;
    }

    @Override
    public Observable<Update<T>> updates()
    {
        return super.updates()
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call()
                    {
                        _subscription.add(_list.subscribe(new Action1<List<T>>() {
                            @Override
                            public void call(final List<T> updatedList)
                            {
                                applyUpdate(new Func1<List<T>, Update<T>>() {
                                    @Override
                                    public Update<T> call(List<T> list)
                                    {
                                        if (list == null || _alwaysReload) {
                                            return new Update<>(updatedList, Collections.singletonList(Change.reloaded()));
                                        }

                                        List<Change> changes = computeDiff(list, updatedList);

                                        return new Update<>(updatedList, changes);
                                    }
                                });
                            }
                        }));
                    }
                })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call()
                    {
                        _subscription.clear();
                    }
                });
    }
}
