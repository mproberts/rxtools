package com.github.mproberts.rxtools.map;

import io.reactivex.*;
import io.reactivex.functions.Function;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscription;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.processors.BehaviorProcessor;

/**
 * SubjectMap manages the connection between an entity store and subscribers who are
 * interested in the updates to those entities.
 * <p>
 * The SubjectMap depends on the garbage collector to clean up unreferenced observables
 * when the weak references are automatically cleared. Subjects will be retained strongly
 * so long as a subscriber is subscribed and are weakly retained outside of that lifecycle.
 *
 * @param <K> key type for the collection
 * @param <V> value type for the emissions from the observables of the collection
 */
public class SubjectMap<K, V>
{
    private static final Action EMPTY_ACTION = new Action() {
        @Override
        public void run()
        {
        }
    };

    private final Lock _writeLock;
    private final Lock _readLock;

    private final HashMap<K, WeakReference<Flowable<V>>> _weakCache;
    private final HashMap<K, WeakReference<Processor<V, V>>> _weakSources;

    private final HashMap<K, Flowable<V>> _cache;

    private final BehaviorProcessor<K> _faults;

    private Function<K, Single<V>> _faultHandler;

    private class OnSubscribeAttach implements FlowableOnSubscribe<V>
    {
        private final AtomicBoolean _isFirstFault = new AtomicBoolean(true);
        private final K _key;
        private volatile Processor<V, V> _valueObservable;

        OnSubscribeAttach(K key)
        {
            _key = key;
        }

        @Override
        public void subscribe(final FlowableEmitter<V> emitter) throws Exception
        {
            boolean isFirst = _isFirstFault.getAndSet(false);
            Completable attachedFetch = null;

            if (isFirst) {
                _valueObservable = attachSource(_key);

                // since this is the first fetch of the observable, go grab the first emission
                attachedFetch = Completable.defer(new Callable<CompletableSource>() {
                    @Override
                    public CompletableSource call() throws Exception {
                        return new CompletableSource() {
                            @Override
                            public void subscribe(CompletableObserver completableObserver) {
                                Function<K, Single<V>> faultHandler = _faultHandler;

                                emitFault(_key);

                                if (faultHandler != null) {
                                    try {
                                        Single<V> fault = faultHandler.apply(_key);

                                        fault.doOnSuccess(new Consumer<V>() {
                                            @Override
                                            public void accept(V v) throws Exception {
                                                _valueObservable.onNext(v);
                                            }
                                        }).toCompletable().subscribe(completableObserver);
                                    } catch (Exception e) {
                                        completableObserver.onError(e);
                                    }
                                } else {
                                    completableObserver.onComplete();
                                }
                            }
                        };
                    }
                });
            }

            // in case you raced into this block but someone else won the coin toss
            // and is still setting up the value observable
            while (_valueObservable == null) {
                // just chill out and let the other thread do the setup
                Thread.yield();
            }

            final Completable initialValueFetch = attachedFetch;
            final AtomicReference<Subscription> disposableTarget = new AtomicReference<>();
            final AtomicReference<Disposable> initialValueFetchDisposable = new AtomicReference<>();

            _valueObservable.subscribe(new FlowableSubscriber<V>() {
                @Override
                public void onSubscribe(Subscription s)
                {
                    if (initialValueFetch != null) {
                        Disposable subscription = initialValueFetch.subscribe(new Action() {
                            @Override
                            public void run() throws Exception {
                            }
                        }, new Consumer<Throwable>() {
                            @Override
                            public void accept(Throwable throwable) throws Exception {
                                _valueObservable.onError(throwable);
                            }
                        });
                        initialValueFetchDisposable.set(subscription);
                    }

                    disposableTarget.set(s);

                    s.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(V v) {
                    emitter.onNext(v);
                }

                @Override
                public void onError(Throwable e) {
                    emitter.onError(e);
                }

                @Override
                public void onComplete() {
                    emitter.onComplete();
                }
            });

            emitter.setDisposable(new Disposable() {
                public boolean _isDisposed;

                @Override
                public void dispose() {
                    Disposable disposable = initialValueFetchDisposable.get();

                    if (disposable != null && !disposable.isDisposed()) {
                        disposable.dispose();
                    }

                    disposableTarget.get().cancel();
                    detachSource(_key);
                }

                @Override
                public boolean isDisposed() {
                    return _isDisposed;
                }
            });
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
        _faults = BehaviorProcessor.create();

        _weakSources = new HashMap<>();
    }

    private Processor<V, V> attachSource(K key)
    {
        _writeLock.lock();
        try {
            // if our source is being attached, we expect that all existing sources have been
            // cleaned up properly. If not, this is a serious issue
            assert(!_weakSources.containsKey(key));

            Processor<V, V> value = BehaviorProcessor.create();

            WeakReference<Flowable<V>> weakConnector = _weakCache.get(key);

            // if an observable is being attached then it must have been added to the weak cache
            // and it must still be referenced
            Flowable<V> connector = weakConnector.get();

            // the observable must be retained by someone since it is being attached
            assert(connector != null);

            // strongly retain the observable and add the subject so future next
            // calls will be piped through the subject
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

    private void emitUpdate(K key, Consumer<Processor<V, V>> updater, Action missHandler)
    {
        emitUpdate(key, updater, missHandler, false);
    }

    private void emitUpdate(K key, Consumer<Processor<V, V>> updater, Action missHandler, boolean disconnect)
    {
        Processor<V, V> subject = null;

        if (disconnect) {
            _writeLock.lock();
        }
        else {
            _readLock.lock();
        }

        try {
            // if we have a subject, we will emit the new value on the subject
            if (_weakSources.containsKey(key)) {
                WeakReference<Processor<V, V>> weakSource = _weakSources.get(key);

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

        try {
            if (subject != null) {
                updater.accept(subject);
            }
            else {
                missHandler.run();
            }
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void emitFault(K key)
    {
        _faults.onNext(key);
    }

    public void setFaultHandler(Function<K, Single<V>> faultHandler)
    {
        _faultHandler = faultHandler;
    }

    /**
     * Returns a stream of keys indicating which values need to be faulted in to satisfy
     * the observables which have been requested through the system
     *
     * @return an observable stream of keys
     */
    public Flowable<K> faults()
    {
        return _faults;
    }

    /**
     * Re-emits a fault for the specified key if there is someone bound
     */
    public Completable faultIfBound(final K key)
    {
        return Completable.defer(new Callable<CompletableSource>() {
            @Override
            public CompletableSource call() throws Exception {
                return new CompletableSource() {
                    @Override
                    public void subscribe(CompletableObserver completableObserver) {
                        _readLock.lock();
                        List<K> retainedKeys = new ArrayList<>(1);
                        try {
                            WeakReference<Processor<V, V>> weakSource = _weakSources.get(key);
                            if (weakSource != null && weakSource.get() != null) {
                                retainedKeys.add(key);
                            }
                        }
                        finally {
                            _readLock.unlock();
                        }
                        processFaultForRetainedKeys(retainedKeys, completableObserver);
                    }
                };
            }
        });
    }

    /**
     * Re-emits a fault for all bound keys
     */
    public Completable faultAllBound()
    {
        return Completable.defer(new Callable<CompletableSource>() {
            @Override
            public CompletableSource call() throws Exception {
                return new CompletableSource() {
                    @Override
                    public void subscribe(CompletableObserver completableObserver) {
                        List<K> retainedKeys = new ArrayList<>(_weakSources.size());
                        _readLock.lock();

                        try {
                            for (K key : _weakSources.keySet()) {
                                WeakReference<Processor<V, V>> weakSource = _weakSources.get(key);
                                if (weakSource != null &&  weakSource.get() != null) {
                                    retainedKeys.add(key);
                                }
                            }
                        }
                        finally {
                            _readLock.unlock();
                        }
                        processFaultForRetainedKeys(retainedKeys, completableObserver);
                    }
                };
            }
        });
    }

    /**
     * For all keys passed in, emit faults and fetch faulted value from a fault handler (if one is set)
     * and emit the new value for all processors that are still listening.
     *
     * @param retainedKeys keys that should be faulted if a faultHandler is set
     * @param completableObserver observer on which faults should be subscribed with
     */
    private void processFaultForRetainedKeys(List<K> retainedKeys, CompletableObserver completableObserver) {
        // Only process the fault if there are any keys bound
        if (retainedKeys.isEmpty()) {
            Completable.complete().subscribe(completableObserver);
            return;
        }

        Function<K, Single<V>> faultHandler = _faultHandler;

        // Emit all faults for old fault handlers
        for (K key : retainedKeys) {
            emitFault(key);
        }

        // Only process faults if there is a handler explicitly set
        if (faultHandler == null) {
            Completable.complete().subscribe(completableObserver);
            return;
        }

        try {
            List<Completable> faultCompletables = new ArrayList<>(retainedKeys.size());
            for (final K key : retainedKeys) {
                Single<V> fault = faultHandler.apply(key);

                faultCompletables.add(fault.doOnSuccess(new Consumer<V>() {
                    @Override
                    public void accept(V v) throws Exception {
                        _readLock.lock();
                        WeakReference<Processor<V, V>> weakSource = _weakSources.get(key);
                        _readLock.unlock();
                        Processor<V, V> processor;
                        if (weakSource != null && (processor = weakSource.get()) != null) {
                            processor.onNext(v);
                        }
                    }
                }).toCompletable());
            }
            Completable.merge(faultCompletables).subscribe(completableObserver);
        }
        catch (Exception e) {
            Completable.error(e).subscribe(completableObserver);
        }
    }

    /**
     * Emits the specified value from the observable associated with the specified key
     * if there is an associated observable. If no observable has subscribed to the key,
     * this operation is a noop. If no value is not emitted it will be faulted in later
     * should another query request it
     *
     * @param key key with which the specified value is to be associated
     * @param valueProvider the method to be called to create the new value in the case of a hit
     * @param missHandler the callback for when a subscriber has not been bound
     */
    public void onNext(K key, final Callable<V> valueProvider, Action missHandler)
    {
        emitUpdate(key, new Consumer<Processor<V, V>>() {
            @Override
            public void accept(Processor<V, V> subject)
            {
                try {
                    subject.onNext(valueProvider.call());
                }
                catch (Exception error) {
                    subject.onError(error);
                }
            }
        }, missHandler);
    }

    /**
     * Emits the specified value from the observable associated with the specified key
     * if there is an associated observable. If no observable has subscribed to the key,
     * this operation is a noop. If no value is not emitted it will be faulted in later
     * should another query request it
     *
     * @param key key with which the specified value is to be associated
     * @param valueCreator the method to be called to create the new value in the case of a hit
     */
    public void onNext(K key, final Callable<V> valueCreator)
    {
        onNext(key, valueCreator, EMPTY_ACTION);
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
        onNext(key, new Callable<V>() {
            @Override
            public V call() throws Exception
            {
                return value;
            }
        });
    }

    /**
     * Emits the error from the observable associated with the specified key. After the
     * error is emitted, the observable will be automatically unbound, subsequent calls
     * to get will return a new observable and attempt to fault the value in
     *
     * @param key key with which the specified value is to be associated
     * @param error exception to be sent to the specified observable
     */
    public void onError(K key, final Throwable error)
    {
        emitUpdate(key, new Consumer<Processor<V, V>>() {
            @Override
            public void accept(Processor<V, V> subject)
            {
                subject.onError(error);
            }
        }, EMPTY_ACTION, true);
    }

    /**
     * Returns an observable associated with the specified key. The observable will
     * request that a value be supplied when the observable is bound and automatically
     * manage the lifecycle of the observable internally
     *
     * @param key the key whose associated observable is to be returned
     *
     * @return an observable which, when subscribed, will be bound to the specified key
     * and will receive all emissions and errors for the specified key
     */
    public Flowable<V> get(K key)
    {
        WeakReference<Flowable<V>> weakObservable;

        _readLock.lock();

        try {
            Flowable<V> observable;

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
                observable = Flowable.create(new OnSubscribeAttach(key), BackpressureStrategy.LATEST);

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

    /**
     * Clear all internal caches for this map.
     * onComplete() will be called for all sources that are still alive.
     * The map will be empty after this call returns.
     */
    public void clearAndDetachAll()
    {
        List<Processor<V, V>> lingeringProcessors = new ArrayList<>(_weakSources.size());
        _writeLock.lock();
        try {
            _cache.clear();
            for (WeakReference<Processor<V, V>> weakProcessors : _weakSources.values()) {
                Processor<V,V> processor = weakProcessors.get();
                if (processor != null) {
                    lingeringProcessors.add(processor);
                }
            }
            _weakSources.clear();
            _weakCache.clear();
        }
        finally {
            _writeLock.unlock();
            for (Processor<V,V> processor : lingeringProcessors) {
                processor.onComplete();
            }
        }
    }
}
