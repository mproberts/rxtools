package com.github.mproberts.rxtools.list;

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
import java.util.concurrent.atomic.AtomicLong;

class BaseObservableList<T> implements ObservableList<T>
{
    private List<T> _previousList = null;

    private final PublishSubject<Update<T>> _subject = PublishSubject.create();

    private final AtomicLong _nowServing = new AtomicLong();
    private final AtomicLong _nextTicket = new AtomicLong();

    private final ThreadLocal<Boolean> _isApplyingUpdate = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue()
        {
            return Boolean.FALSE;
        }
    };

    BaseObservableList()
    {
        _previousList = null;
    }

    BaseObservableList(List<T> initialState)
    {
        _previousList = initialState;
    }

    /**
     * Note: this is a dangerous call, make sure you know exactly what you are doing,
     * you probably don't need to use this. Think about it for a while
     * @param previousList
     */
    List<T> setPreviousList(List<T> previousList)
    {
        List<T> oldPreviousList = _previousList;

        _previousList = previousList;

        return oldPreviousList;
    }

    final void applyUpdate(final Func1<List<T>, Update<T>> change)
    {
        onNext(new Action0() {
            @Override
            public void call()
            {
                Update<T> update = null;
                Exception updateError = null;

                List<T> currentList = _previousList;

                try {
                    update = change.call(currentList);

                    if (update != null) {
                        if (_previousList == null) {
                            update = new Update<>(update.list, Change.reloaded());
                        }

                        _previousList = update.list;
                    }
                }
                catch (Exception e) {
                    updateError = e;
                }

                if (updateError != null) {
                    _subject.onError(updateError);
                }
                else if (update != null) {
                    _subject.onNext(update);
                }
            }
        });
    }

    private void onNext(Action0 doNotify)
    {
        boolean applyingUpdate = _isApplyingUpdate.get();

        _isApplyingUpdate.set(true);

        if (!applyingUpdate) {
            long ticket = _nextTicket.getAndIncrement();

            // ensure ordered
            while (_nowServing.get() != ticket) {
                Thread.yield();
            }
        }

        doNotify.call();

        if (!applyingUpdate) {
            // allow the next update to take hold
            _nowServing.incrementAndGet();

            _isApplyingUpdate.set(false);
        }
    }

    @Override
    public Observable<Update<T>> updates()
    {
        // starts the observable with whatever the present state is
        return _subject
                .startWith(Observable.unsafeCreate(new OnSubscribeCreate<>(new Action1<Emitter<Update<T>>>() {
                    @Override
                    public void call(final Emitter<Update<T>> updateEmitter)
                    {
                        onNext(new Action0() {
                            @Override
                            public void call()
                            {
                                if (_previousList != null) {
                                    updateEmitter.onNext(new Update<>(new ArrayList<>(_previousList), Collections.singletonList(Change.reloaded())));
                                }

                                updateEmitter.onCompleted();
                            }
                        });
                    }
                }, Emitter.BackpressureMode.LATEST)));
    }
}