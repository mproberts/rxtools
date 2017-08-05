package com.github.mproberts.rxtools.list;

import io.reactivex.*;
import io.reactivex.functions.Action;
import io.reactivex.functions.Function;
import io.reactivex.subjects.PublishSubject;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

class BaseFlowableList<T> implements FlowableList<T>
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

    BaseFlowableList()
    {
        _previousList = null;
    }

    BaseFlowableList(List<T> initialState)
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

    final void applyUpdate(final Function<List<T>, Update<T>> change)
    {
        onNext(new Action() {
            @Override
            public void run()
            {
                Update<T> update = null;
                Exception updateError = null;

                List<T> currentList = _previousList;

                try {
                    update = change.apply(currentList);

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

    private void onNext(Action doNotify)
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

        try {
            doNotify.run();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (!applyingUpdate) {
            // allow the next update to take hold
            _nowServing.incrementAndGet();

            _isApplyingUpdate.set(false);
        }
    }

    @Override
    public Flowable<Update<T>> updates()
    {
        // starts the observable with whatever the present state is
        return _subject
                .startWith(new ObservableSource<Update<T>>() {
                    @Override
                    public void subscribe(final Observer<? super Update<T>> observer) {
                        onNext(new Action() {
                            @Override
                            public void run()
                            {
                                if (_previousList != null) {
                                    observer.onNext(
                                            new Update<>(new ArrayList<>(_previousList),
                                                    Collections.singletonList(Change.reloaded())));
                                }

                                observer.onComplete();
                            }
                        });
                    }
                })
                .toFlowable(BackpressureStrategy.BUFFER);
    }
}
