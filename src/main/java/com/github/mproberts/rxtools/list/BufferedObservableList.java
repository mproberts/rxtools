package com.github.mproberts.rxtools.list;

import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.functions.Function;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

class BufferedObservableList<T> implements ObservableList<T>
{
    private final ObservableList<T> _list;
    private final long _timeSpan;
    private final TimeUnit _timeUnit;
    private final Scheduler _scheduler;

    public BufferedObservableList(ObservableList<T> list, long timeSpan, TimeUnit timeUnit, Scheduler scheduler)
    {
        _list = list;
        _timeSpan = timeSpan;
        _timeUnit = timeUnit;
        _scheduler = scheduler;
    }

    @Override
    public Flowable<Update<T>> updates()
    {
        return _list.updates()
                .buffer(_timeSpan, _timeUnit, _scheduler)
                .map(new Function<List<Update<T>>, Update<T>>() {
                    @Override
                    public Update<T> apply(List<Update<T>> updates) {
                        Update<T> lastUpdate = updates.get(updates.size() - 1);
                        List<Change> allChanges = new ArrayList<>();

                        for (Update<T> update : updates) {
                            allChanges.addAll(update.changes);
                        }

                        for (Change allChange : allChanges) {
                            // it only takes one reload to force a reload
                            if (allChange.type == Change.Type.Reloaded) {
                                allChanges = Collections.singletonList(Change.reloaded());
                                break;
                            }
                        }

                        return new Update<>(lastUpdate.list, allChanges);
                    }
                });
    }
}
