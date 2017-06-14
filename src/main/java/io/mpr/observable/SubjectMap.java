package io.mpr.observable;

import rx.Observable;
import rx.Subscriber;
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
 * @param <TKey> key type for the collection
 * @param <TValue> value type for the emissions from the observables of the collection
 */
public class SubjectMap<TKey, TValue>
{
    private class OnSubscribeAttach implements Observable.OnSubscribe<TValue>
    {
        private final AtomicBoolean _isFirstFault = new AtomicBoolean(true);
        private final TKey _key;
        private volatile Observable<TValue> _valueObservable;

        OnSubscribeAttach(TKey key)
        {
            _key = key;
        }

        @Override
        public void call(final Subscriber<? super TValue> subscriber)
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

            _valueObservable.subscribe(subscriber);

            subscriber.add(BooleanSubscription.create(() -> detachSource(_key)));
        }
    }

    private final Lock _writeLock;
    private final Lock _readLock;

    private HashMap<TKey, WeakReference<Observable<TValue>>> _weakCache;
    private HashMap<TKey, Observable<TValue>> _cache;

    private HashMap<TKey, Subject<TValue, TValue>> _sources;

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

        _sources = new HashMap<>();
    }

    private BehaviorSubject<TKey> _faults = BehaviorSubject.create();

    private Observable<TValue> attachSource(TKey key)
    {
        _writeLock.lock();
        try {
            BehaviorSubject<TValue> value = BehaviorSubject.create();
            WeakReference<Observable<TValue>> weakConnector = _weakCache.get(key);

            // if an observable is being attached then it must have been added to the weak cache
            // and it must still be referenced
            assert(weakConnector != null);

            Observable<TValue> connector = weakConnector.get();

            // the observable must be retained by someone since it is being attached
            assert(connector != null);

            // strongly retain the observable and add the subject so future next calls will be
            // piped through the subject
            _sources.put(key, value);
            _cache.put(key, connector);

            return value;
        }
        finally {
            _writeLock.unlock();
        }
    }

    private void detachSource(TKey key)
    {
        _writeLock.lock();
        try {
            _cache.remove(key);
            _weakCache.remove(key);
            _sources.remove(key);
        }
        finally {
            _writeLock.unlock();
        }
    }

    private void emitUpdate(TKey key, Action1<Subject<TValue, TValue>> updater)
    {
        Subject<TValue, TValue> subject = null;

        _readLock.lock();

        try {
            // if we have a subject, we will emit the new value on the subject
            if (_sources.containsKey(key)) {
                subject = _sources.get(key);
            }
        }
        finally {
            _readLock.unlock();
        }

        if (subject != null) {
            updater.call(subject);
        }
    }

    private void emitFault(TKey key)
    {
        _faults.onNext(key);
    }

    /**
     * Returns a stream of keys indicating which values need to be faulted in to satisfy
     * the observables which have been requested through the system
     *
     * @return an observable stream of keys
     */
    public Observable<TKey> faults()
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
    public void onNext(TKey key, TValue value)
    {
        emitUpdate(key, subject -> subject.onNext(value));
    }

    /**
     * Emits the error from the observable associated with the specified key. After the
     * error is emitted, the observable will be automatically unbound, subsequent calls
     * to get will return a new observable and attempt to fault the value in
     *
     * @param key key with which the specified value is to be associated
     * @param exception exception to be sent to the specified observable
     */
    public void onError(TKey key, Exception exception)
    {
        emitUpdate(key, subject -> subject.onError(exception));
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
    public Observable<TValue> get(TKey key)
    {
        WeakReference<Observable<TValue>> weakObservable;

        _readLock.lock();

        try {
            Observable<TValue> observable;

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
                }

                // no observable was found in the cache, create a new binding
                observable = Observable.unsafeCreate(new OnSubscribeAttach(key));

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
