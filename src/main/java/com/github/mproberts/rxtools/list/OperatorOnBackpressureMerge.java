package com.github.mproberts.rxtools.list;

import java.util.ArrayList;
import java.util.List;

import rx.Observable;
import rx.Producer;
import rx.Subscriber;

import java.util.concurrent.atomic.AtomicLong;

import rx.internal.operators.BackpressureUtils;
import rx.plugins.RxJavaHooks;

class OperatorOnBackpressureMerge<T> implements Observable.Operator<ObservableList.Update<T>, ObservableList.Update<T>>
{
    static final class Holder
    {
        static final OperatorOnBackpressureMerge<?> INSTANCE = new OperatorOnBackpressureMerge<>();
    }

    static class BackpressureMergeSubscriber<T> extends Subscriber<ObservableList.Update<T>>
    {
        private volatile boolean _isDone;
        private AtomicLong _outstandingRequests = new AtomicLong();
        private Subscriber<? super ObservableList.Update<T>> _child;

        private List<ObservableList.Change> _queuedChanges = new ArrayList<>();
        private List<T> _lastList;

        private Producer _producer = new Producer() {
            @Override
            public void request(long n)
            {
                List<ObservableList.Change> queuedChanges = new ArrayList<>(_queuedChanges);
                List<T> lastList = _lastList;

                if (lastList != null) {
                    _queuedChanges.clear();
                    _lastList = null;
                }

                BackpressureUtils.getAndAddRequest(_outstandingRequests, n);

                if (lastList != null) {
                    onNext(new ObservableList.Update<>(lastList, queuedChanges));
                }
            }
        };

        BackpressureMergeSubscriber(Subscriber<? super ObservableList.Update<T>> child)
        {
            _child = child;

            child.setProducer(_producer);
        }

        @Override
        public void onStart()
        {
            request(Long.MAX_VALUE);
        }

        @Override
        public void onCompleted()
        {
            if (!_isDone) {
                _isDone = true;
                _child.onCompleted();
            }
        }

        @Override
        public void onError(Throwable e)
        {
            if (!_isDone) {
                _isDone = true;
                _child.onError(e);
            }
            else {
                RxJavaHooks.onError(e);
            }
        }

        @Override
        public void onNext(ObservableList.Update<T> update)
        {
            if (_isDone) {
                return;
            }

            long requests = _outstandingRequests.get();

            if (requests > 0) {
                _child.onNext(update);

                _outstandingRequests.decrementAndGet();
            }
            else {
                _lastList = update.list;

                for (ObservableList.Change change : update.changes) {
                    if (change.type == ObservableList.Change.Type.Reloaded) {
                        _queuedChanges.clear();
                        break;
                    }
                    else {
                        _queuedChanges.add(change);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    static <T> OperatorOnBackpressureMerge<T> instance()
    {
        return (OperatorOnBackpressureMerge<T>) OperatorOnBackpressureMerge.Holder.INSTANCE;
    }

    OperatorOnBackpressureMerge()
    {
    }

    @Override
    public Subscriber<? super ObservableList.Update<T>> call(Subscriber<? super ObservableList.Update<T>> subscriber)
    {
        return new BackpressureMergeSubscriber<>(subscriber);
    }
}
