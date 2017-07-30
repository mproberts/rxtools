package com.github.mproberts.rxtools.list;

import io.reactivex.FlowableOperator;
import io.reactivex.FlowableSubscriber;
import io.reactivex.internal.util.BackpressureHelper;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.subscribers.DisposableSubscriber;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.atomic.AtomicLong;

class OperatorOnBackpressureMerge<T> implements FlowableOperator<ObservableList.Update<T>, ObservableList.Update<T>>
{
    static final class Holder
    {
        static final OperatorOnBackpressureMerge<?> INSTANCE = new OperatorOnBackpressureMerge<>();
    }

    /*
    static class BackpressureMergeSubscriber<T> extends DisposableSubscriber<ObservableList.Update<T>>
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

                BackpressureHelper.add(_outstandingRequests, n);

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
        public void onError(Throwable e)
        {
            if (!_isDone) {
                _isDone = true;
                _child.onError(e);
            }
            else {
                RxJavaPlugins.onError(e);
            }
        }

        @Override
        public void onComplete()
        {
            if (!_isDone) {
                _isDone = true;
                _child.onComplete();
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
    */

    @SuppressWarnings("unchecked")
    static <T> OperatorOnBackpressureMerge<T> instance()
    {
        return (OperatorOnBackpressureMerge<T>) OperatorOnBackpressureMerge.Holder.INSTANCE;
    }

    OperatorOnBackpressureMerge()
    {
    }

    @Override
    public Subscriber<? super ObservableList.Update<T>> apply(Subscriber<? super ObservableList.Update<T>> observer) throws Exception
    {
        return observer;
    }
}
