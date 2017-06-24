package com.github.mproberts.rxtools.list;

import com.github.mproberts.rxtools.list.ObservableList;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Func1;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

class BufferedObservableList<T> implements ObservableList<T>
{
    private final ObservableList<T> _list;
    private final long _timespan;
    private final TimeUnit _timeUnit;
    private final Scheduler _scheduler;

    public BufferedObservableList(ObservableList<T> list, long timespan, TimeUnit timeUnit, Scheduler scheduler)
    {
        _list = list;
        _timespan = timespan;
        _timeUnit = timeUnit;
        _scheduler = scheduler;
    }

    @Override
    public Observable<Update<T>> updates()
    {
        return _list.updates()
                .buffer(_timespan, _timeUnit, _scheduler)
                .map(new Func1<List<Update<T>>, Update<T>>() {
                    @Override
                    public Update<T> call(List<Update<T>> updates) {
                        Update<T> lastUpdate = updates.get(updates.size() - 1);
                        List<Change> allChanges = new ArrayList<>();

                        for (Update<T> update : updates) {
                            allChanges.addAll(update.changes);
                        }

                        return new Update<>(lastUpdate.list, allChanges);
                    }
                });
    }
}
