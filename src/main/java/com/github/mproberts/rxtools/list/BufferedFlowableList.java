package com.github.mproberts.rxtools.list;

import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.functions.Function;
import org.reactivestreams.Publisher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

class BufferedFlowableList<T> extends FlowableList<T>
{
    private final FlowableList<T> _list;
    private final long _timeSpan;
    private final TimeUnit _timeUnit;
    private final Scheduler _scheduler;

    public BufferedFlowableList(FlowableList<T> list, long timeSpan, TimeUnit timeUnit, Scheduler scheduler)
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
                .switchMap(new Function<List<Update<T>>, Publisher<Update<T>>>() {
                    @Override
                    public Publisher<Update<T>> apply(List<Update<T>> updates) throws Exception {
                        if (updates.size() == 0) {
                            return Flowable.empty();
                        }

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

                        return Flowable.just(new Update<>(lastUpdate.list, allChanges));
                    }
                });
    }
}
