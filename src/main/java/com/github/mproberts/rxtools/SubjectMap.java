package com.github.mproberts.rxtools;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.subjects.BehaviorSubject;
import rx.subjects.Subject;
import rx.subscriptions.BooleanSubscription;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * SubjectMap manages the connection between an entity store and subscribers who are
 * interested in the changes to those entities.
 *
 * The SubjectMap depends on the garbage collector to clean up unreferenced observables
 * when the weak references are automatically cleared. Subjects will be retained strongly
 * so long as a subscriber is subscribed and are weakly retained outside of that lifecycle.
 *
 * @param <K> key type for the collection
 * @param <V> value type for the emissions from the observables of the collection
 */
public class SubjectMap<K, V>
{
    private final Lock _writeLock;
    private final Lock _readLock;

    private final HashMap<K, WeakReference<Observable<V>>> _weakCache;
    private final HashMap<K, WeakReference<Subject<V, V>>> _weakSources;

    private final HashMap<K, Observable<V>> _cache;

    private final BehaviorSubject<K> _faults;

    private class OnSubscribeAttach implements Observable.OnSubscribe<V>
    {
        private final AtomicBoolean _isFirstFault = new AtomicBoolean(true);
        private final K _key;
        private volatile Subject<V, V> _valueObservable;

        OnSubscribeAttach(K key)
        {
            _key = key;
        }

        @Override
        public void call(final Subscriber<? super V> subscriber)
        {
            boolean isFirst = _isFirstFault.getAndSet(false);

            if (isFirst) {
                _valueObservable = attachSource(_key);

                // since this is the first fetch of the observable, go grab the first emission
                emitFault(_key);
            }

            // in case you raced into this block but someone else won the coin toss
            // and is still setting up the value observable
            while (_valueObservable == null) {
                // just chill out and let the other thread do the setup
                Thread.yield();
            }

            final Subscription subscription = _valueObservable.subscribe(subscriber);

            subscriber.add(BooleanSubscription.create(new Action0() {
                @Override
                public void call()
                {
                    subscription.unsubscribe();
                    detachSource(_key);
                }
            }));
        }
    }

    /**
     * Constructs a new, empty SubjectMap
     */
    public SubjectMap()
    {
        ReadWriteLock _readWriteLock = new ReentrantReadWriteLock();

        _readLock = _readWriteLock.readLock();
        _writeLock = _readWriteLock.writeLock();

        _weakCache = new HashMap<>();
        _cache = new HashMap<>();
        _faults = BehaviorSubject.create();

        _weakSources = new HashMap<>();
    }

    private Subject<V, V> attachSource(K key)
    {
        _writeLock.lock();
        try {
            // if our source is being attached, we expect that all existing sources have been
            // cleaned up properly. If not, this is a serious issue
            assert(!_weakSources.containsKey(key));

            Subject<V, V> value = BehaviorSubject.create();

            WeakReference<Observable<V>> weakConnector = _weakCache.get(key);

            // if an observable is being attached then it must have been added to the weak cache
            // and it must still be referenced
            assert(weakConnector != null);

            Observable<V> connector = weakConnector.get();

            // the observable must be retained by someone since it is being attached
            assert(connector != null);

            // strongly retain the observable and add the subject so future next calls will be
            // piped through the subject
            _weakSources.put(key, new WeakReference<>(value));
            _cache.put(key, connector);

            return value;
        }
        finally {
            _writeLock.unlock();
        }
    }

    private void detachSource(K key)
    {
        _writeLock.lock();
        try {
            _cache.remove(key);
        }
        finally {
            _writeLock.unlock();
        }
    }

    private void emitUpdate(K key, Action1<Subject<V, V>> updater)
    {
        emitUpdate(key, updater, false);
    }

    private void emitUpdate(K key, Action1<Subject<V, V>> updater, boolean disconnect)
    {
        Subject<V, V> subject = null;

        if (disconnect) {
            _writeLock.lock();
        }
        else {
            _readLock.lock();
        }

        try {
            // if we have a subject, we will emit the new value on the subject
            if (_weakSources.containsKey(key)) {
                WeakReference<Subject<V, V>> weakSource = _weakSources.get(key);

                subject = weakSource.get();
            }

            if (disconnect) {
                _weakSources.remove(key);
                _weakCache.remove(key);
                _cache.remove(key);
            }
        }
        finally {
            if (disconnect) {
                _writeLock.unlock();
            }
            else {
                _readLock.unlock();
            }

        }

        if (subject != null) {
            updater.call(subject);
        }
    }

    private void emitFault(K key)
    {
        _faults.onNext(key);
    }

    /**
     * Returns a stream of keys indicating which values need to be faulted in to satisfy
     * the observables which have been requested through the system
     *
     * @return an observable stream of keys
     */
    public Observable<K> faults()
    {
        return _faults;
    }

    /**
     * Emits the specified value from the observable associated with the specified key
     * if there is an associated observable. If no observable has subscribed to the key,
     * this operation is a noop. If no value is not emitted it will be faulted in later
     * should another query request it
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be send to the specified observable
     */
    public void onNext(K key, final V value)
    {
        emitUpdate(key, new Action1<Subject<V, V>>() {
            @Override
            public void call(Subject<V, V> subject)
            {
                subject.onNext(value);
            }
        });
    }

    /**
     * Emits the error from the observable associated with the specified key. After the
     * error is emitted, the observable will be automatically unbound, subsequent calls
     * to get will return a new observable and attempt to fault the value in
     *
     * @param key key with which the specified value is to be associated
     * @param exception exception to be sent to the specified observable
     */
    public void onError(K key, final Exception exception)
    {
        emitUpdate(key, new Action1<Subject<V, V>>() {
            @Override
            public void call(Subject<V, V> subject)
            {
                subject.onError(exception);
            }
        }, true);
    }

    /**
     * Returns an observable associated with the specified key. The observable will
     * request that a value be supplied when the observable is bound and automatically
     * manage the lifecycle of the observable internally
     *
     * @param key the key whose associated observable is to be returned
     * @return an observable which, when subscribed, will be bound to the specified key
     * and will receive all emissions and errors for the specified key
     */
    public Observable<V> get(K key)
    {
        WeakReference<Observable<V>> weakObservable;

        _readLock.lock();

        try {
            Observable<V> observable;

            // attempt to retrieve the weakly held observable
            if (_weakCache.containsKey(key)) {
                weakObservable = _weakCache.get(key);
                observable = weakObservable.get();

                if (observable != null) {
                    // we already have a cached observable bound to this key
                    return observable;
                }
            }

            // we do not have an observable for the key, escalate the lock
            _readLock.unlock();
            _writeLock.lock();

            try {
                // recheck the observable since we had to retake the lock
                if (_weakCache.containsKey(key)) {
                    weakObservable = _weakCache.get(key);
                    observable = weakObservable.get();

                    if (observable != null) {
                        // we found a hit this time around, return the hit
                        return observable;
                    }
                    else {
                        // the target of the weak source should have already been cleared by the
                        // garbage collector since the source is retained by the cached observable
                        _weakSources.remove(key);
                    }
                }

                // no observable was found in the cache, create a new binding
                observable = Observable.create(new OnSubscribeAttach(key));

                _weakCache.put(key, new WeakReference<>(observable));
            }
            finally {
                _readLock.lock();
                _writeLock.unlock();
            }

            return observable;
        }
        finally {
            _readLock.unlock();
        }
    }
}
