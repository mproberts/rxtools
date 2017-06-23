package com.github.mproberts.rxtools;

import rx.Emitter;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.internal.operators.OnSubscribeCreate;
import rx.subjects.PublishSubject;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

class BaseObservableList<T> implements ObservableList<T>
{
    private List<T> _previousList = null;

    private final Object _updateLock = new Object();
    private PublishSubject<Update<T>> _subject = PublishSubject.create();

    private AtomicInteger _nowServing = new AtomicInteger();
    private AtomicInteger _nextTicket = new AtomicInteger();

    BaseObservableList()
    {
        _previousList = null;
    }

    BaseObservableList(List<T> initialState)
    {
        _previousList = initialState;
    }

    final void applyUpdate(Func1<List<T>, Update<T>> change)
    {
        int ticket;
        Update<T> update = null;
        Exception updateError = null;

        synchronized (_updateLock) {
            ticket = _nextTicket.getAndIncrement();
            List<T> currentList = _previousList;

            try {
                update = change.call(currentList);

                if (update != null) {
                    if (_previousList == null) {
                        update = new Update<>(update.list(), Change.reloaded());
                    }

                    _previousList = update.list();
                }
            }
            catch (Exception e) {
                updateError = e;
            }
        }

        final Update<T> result = update;
        final Exception resultError = updateError;

        onNext(ticket, update, new Action0() {
            @Override
            public void call()
            {
                if (resultError != null) {
                    _subject.onError(resultError);
                }
                else if(result != null) {
                    _subject.onNext(result);
                }
            }
        });
    }

    private void onNext(int ticket, Update<T> update, Action0 doNotify)
    {
        // ensure ordered
        while (_nowServing.get() != ticket) {
            Thread.yield();
        }

        doNotify.call();

        // allow the next update to take hold
        _nowServing.incrementAndGet();
    }

    @Override
    public Observable<Update<T>> updates()
    {
        // starts the observable with whatever the present state is
        return _subject
                .startWith(Observable.unsafeCreate(new OnSubscribeCreate<>(new Action1<Emitter<Update<T>>>() {
                    @Override
                    public void call(Emitter<Update<T>> updateEmitter) {
                        int ticket = 0;
                        Update<T> update = null;

                        synchronized (_updateLock) {
                            if (_previousList != null) {
                                update = new Update<>(new ArrayList<>(_previousList), Collections.singletonList(Change.reloaded()));
                                ticket = _nextTicket.getAndIncrement();
                            }
                        }

                        if (update != null) {
                            final Update<T> result = update;

                            onNext(ticket, update, new Action0() {
                                @Override
                                public void call()
                                {
                                    updateEmitter.onNext(result);
                                    updateEmitter.onCompleted();
                                }
                            });
                        }
                        else {
                            updateEmitter.onCompleted();
                        }
                    }
                }, Emitter.BackpressureMode.LATEST)));
    }
}
