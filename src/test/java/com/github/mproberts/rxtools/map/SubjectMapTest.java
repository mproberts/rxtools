package com.github.mproberts.rxtools.map;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.observers.TestObserver;
import io.reactivex.subjects.SingleSubject;
import io.reactivex.subscribers.DisposableSubscriber;
import io.reactivex.subscribers.TestSubscriber;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class SubjectMapTest
{
    private static final int runs = 100000;
    private static final int loop = 50;
    private static final String[] keys = new String[loop];

    static {
        for (int i = 0; i < loop; ++i) {
            keys[i] = "run-" + i;
        }
    }

    private CompositeDisposable _subscription;
    private SubjectMap<String, Integer> source;

    private static class IncrementingFaultSatisfier<K> implements Consumer<K>
    {
        private final AtomicInteger _counter;
        private final SubjectMap<K, Integer> _source;

        IncrementingFaultSatisfier(SubjectMap<K, Integer> source, AtomicInteger counter)
        {
            _source = source;
            _counter = counter;
        }

        @Override
        public void accept(K key)
        {
            _source.onNext(key, _counter.incrementAndGet());
        }
    }

    private <T> void subscribe(Flowable<T> observable, DisposableSubscriber<T> action)
    {
        _subscription.add(observable.subscribeWith(action));
    }

    private <T> void subscribeAll(List<Flowable<T>> observables, TestSubscriber<T> action)
    {
        for (int i = 0, l = observables.size(); i < l; ++i) {
            Flowable<T> observable = observables.get(i);

            _subscription.add(observable.subscribeWith(action));
        }
    }

    private <T> void subscribe(Flowable<T> observable, TestSubscriber<T> action)
    {
        _subscription.add(observable.subscribeWith(action));
    }

    private <T> void subscribe(Flowable<T> observable, Consumer<T> action)
    {
        _subscription.add(observable.subscribe(action));
    }

    private void unsubscribeAll()
    {
        _subscription.clear();
    }

    @Before
    public void setup()
    {
        source = new SubjectMap<>();
        _subscription = new CompositeDisposable();
    }

    @After
    public void teardown()
    {
        unsubscribeAll();
    }

    @Test
    public void testQueryAndIncrementOnFault()
    {
        // setup
        AtomicInteger counter = new AtomicInteger(0);
        Disposable faultSubscription = source.faults()
                .subscribe(new IncrementingFaultSatisfier<>(source, counter));

        TestSubscriber<Integer> testSubscriber1 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber2 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber3 = new TestSubscriber<>();

        subscribe(source.get("hello"), testSubscriber1);
        System.gc();

        testSubscriber1.assertValues(1);

        subscribe(source.get("hello"), testSubscriber2);
        System.gc();

        testSubscriber1.assertValues(1);
        testSubscriber2.assertValues(1);

        unsubscribeAll();
        System.gc();

        subscribe(source.get("hello"), testSubscriber3);

        testSubscriber3.assertValues(2);

        // cleanup
        faultSubscription.dispose();
    }

    @Test
    public void testQueryAndIncrementOnFaultWithHandler()
    {
        // setup
        final AtomicInteger counter = new AtomicInteger(0);

        source.setFaultHandler(new Function<String, Single<Integer>>() {
            @Override
            public Single<Integer> apply(String s) throws Exception {
                return Single.just(counter.incrementAndGet());
            }
        });

        TestSubscriber<Integer> testSubscriber1 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber2 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber3 = new TestSubscriber<>();

        subscribe(source.get("hello"), testSubscriber1);
        System.gc();

        testSubscriber1.assertValues(1);

        subscribe(source.get("hello"), testSubscriber2);
        System.gc();

        testSubscriber1.assertValues(1);
        testSubscriber2.assertValues(1);

        unsubscribeAll();
        System.gc();

        subscribe(source.get("hello"), testSubscriber3);

        testSubscriber3.assertValues(2);
    }

    @Test
    public void testDelayedFaultHandling()
    {
        source.setFaultHandler(new Function<String, Single<Integer>>() {
            @Override
            public Single<Integer> apply(String s) throws Exception {
                return Single.timer(100, TimeUnit.MILLISECONDS).map(new Function<Long, Integer>() {
                    @Override
                    public Integer apply(Long aLong) throws Exception {
                        return 23;
                    }
                });
            }
        });

        TestSubscriber<Integer> testSubscriber1 = new TestSubscriber<>();

        subscribe(source.get("hello"), testSubscriber1);

        testSubscriber1.awaitCount(1);
        testSubscriber1.assertValues(23);
    }

    @Test
    public void testDelayedFaultHandlingCancel()
    {
        final AtomicBoolean wasAttached = new AtomicBoolean(false);
        final AtomicBoolean isDisposed = new AtomicBoolean(false);

        source.setFaultHandler(new Function<String, Single<Integer>>() {
            @Override
            public Single<Integer> apply(String s) throws Exception {
                return Single.create(new SingleOnSubscribe<Integer>() {
                    @Override
                    public void subscribe(SingleEmitter<Integer> singleEmitter) throws Exception {
                        wasAttached.set(true);

                        Disposable disposable = new Disposable() {

                            @Override
                            public void dispose() {
                                isDisposed.set(true);
                            }

                            @Override
                            public boolean isDisposed() {
                                return isDisposed.get();
                            }
                        };

                        singleEmitter.setDisposable(disposable);
                    }
                });
            }
        });

        TestSubscriber<Integer> testSubscriber1 = new TestSubscriber<>();

        subscribe(source.get("hello"), testSubscriber1);

        assertTrue(wasAttached.get());
        assertFalse(isDisposed.get());

        testSubscriber1.dispose();

        assertTrue(isDisposed.get());
    }

    @Test
    public void testBattlingSubscribers1() throws InterruptedException
    {
        TestSubscriber<Integer> testSubscriber1 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber2 = new TestSubscriber<>();

        Flowable<Integer> value1 = source.get("hello");
        Disposable s1 = value1.subscribeWith(testSubscriber1);

        source.onNext("hello", 3);

        s1.dispose();

        testSubscriber1.assertValues(3);

        Flowable<Integer> value2 = source.get("hello");
        Disposable s2 = value2.subscribeWith(testSubscriber2);
        Disposable s3 = value1.subscribeWith(testSubscriber1);

        source.onNext("hello", 4);

        s2.dispose();
        s3.dispose();

        testSubscriber2.assertValues(4);
    }

    @Test
    public void testBattlingSubscribers() throws InterruptedException
    {
        // setup
        AtomicInteger counter = new AtomicInteger(0);
        Disposable faultSubscription = source.faults()
                .subscribe(new IncrementingFaultSatisfier<>(source, counter));

        Flowable<Integer> retainedObservable = source.get("hello");
        TestSubscriber<Integer> testSubscriber1 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber2 = new TestSubscriber<>();

        Disposable s1 = retainedObservable.subscribeWith(testSubscriber1);
        Disposable s2;

        testSubscriber1.assertValues(1);

        s1.dispose();

        testSubscriber1.assertValues(1);

        Flowable<Integer> retainedObservable2 = source.get("hello");

        s1 = retainedObservable.subscribeWith(testSubscriber1);

        testSubscriber1.assertValues(1);

        s2 = retainedObservable.subscribeWith(testSubscriber2);

        testSubscriber1.assertValues(1);

        s1.dispose();
        s2.dispose();

        // cleanup
        faultSubscription.dispose();
    }

    public void testMissHandling()
    {
        // setup
        final AtomicBoolean missHandlerCalled = new AtomicBoolean(false);
        AtomicInteger counter = new AtomicInteger(0);
        Disposable faultSubscription = source.faults()
                .subscribe(new IncrementingFaultSatisfier<>(source, counter));

        TestSubscriber<Integer> testSubscriber1 = new TestSubscriber<>();

        subscribe(source.get("hello"), testSubscriber1);

        source.onNext("hello2", new Callable<Integer>() {
            @Override
            public Integer call() throws Exception
            {
                fail("Value should not be accessed");
                return 13;
            }
        }, new Action() {
            @Override
            public void run()
            {
                missHandlerCalled.set(true);
            }
        });

        assertTrue(missHandlerCalled.get());

        testSubscriber1.assertValues(1);

        // cleanup
        faultSubscription.dispose();
    }

    @Test
    public void testErrorHandlingInValueProvider()
    {
        // setup
        final AtomicBoolean missHandlerCalled = new AtomicBoolean(false);
        TestSubscriber<Integer> testSubscriber1 = new TestSubscriber<>();

        subscribe(source.get("hello"), testSubscriber1);

        source.onNext("hello", new Callable<Integer>() {
            @Override
            public Integer call() throws Exception
            {
                throw new RuntimeException("Boom");
            }
        }, new Action() {
            @Override
            public void run()
            {
                missHandlerCalled.set(true);
            }
        });

        testSubscriber1.assertError(RuntimeException.class);
    }

    @Test
    public void testQueryAndUpdate()
    {
        // setup
        AtomicInteger counter = new AtomicInteger(0);
        Disposable faultSubscription = source.faults()
                .subscribe(new IncrementingFaultSatisfier<>(source, counter));

        TestSubscriber<Integer> testSubscriber1 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber2 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber3 = new TestSubscriber<>();

        subscribe(source.get("hello"), testSubscriber1);
        System.gc();

        testSubscriber1.assertValues(1);

        subscribe(source.get("hello"), testSubscriber2);
        System.gc();

        testSubscriber1.assertValues(1);
        testSubscriber2.assertValues(1);

        // send 10 to 2 already bound subscribers
        source.onNext("hello", 10);

        subscribe(source.get("hello"), testSubscriber3);

        // new subscriber 3 should only received the latest value of 10
        testSubscriber1.assertValues(1, 10);
        testSubscriber2.assertValues(1, 10);
        testSubscriber3.assertValues(10);

        // all 3 subscribers should receive the new value of 11
        source.onNext("hello", 11);

        testSubscriber1.assertValues(1, 10, 11);
        testSubscriber2.assertValues(1, 10, 11);
        testSubscriber3.assertValues(10, 11);

        // cleanup
        faultSubscription.dispose();
    }

    @Test
    public void testExceptionHandlingFault()
    {
        // setup
        final AtomicBoolean exceptionEncountered = new AtomicBoolean(false);
        final AtomicInteger counter = new AtomicInteger(0);
        Disposable faultSubscription = source.faults()
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String key) {
                        if (counter.incrementAndGet() <= 1) {
                            throw new RuntimeException("Explosions!");
                        }

                        source.onNext(key, counter.get());
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        assertTrue(throwable instanceof RuntimeException);

                        exceptionEncountered.set(true);
                    }
                });

        TestSubscriber<Integer> testSubscriber1 = new TestSubscriber<>();
        Flowable<Integer> helloValue = source.get("hello");

        subscribe(helloValue, testSubscriber1);

        testSubscriber1.assertNoValues();

        assertTrue(exceptionEncountered.get());

        // cleanup
        faultSubscription.dispose();
    }

    @Test
    public void testPruningUnsubscribedObservables()
    {
        // setup
        AtomicInteger counter = new AtomicInteger(0);
        Disposable faultSubscription = source.faults()
                .subscribe(new IncrementingFaultSatisfier<>(source, counter));

        TestSubscriber<Integer> testSubscriber = new TestSubscriber<>();

        @SuppressWarnings("unused")
        Flowable<Integer> helloValue = source.get("hello");

        helloValue = null;

        System.gc();

        subscribe(source.get("hello"), testSubscriber);

        testSubscriber.assertValues(1);

        source.onNext("hello", 11);

        testSubscriber.assertValues(1, 11);

        // cleanup
        faultSubscription.dispose();
    }

    @Test
    public void testSendBatchOfNoopsForUnobservedKey()
    {
        for (int i = 0; i < runs; ++i) {
            source.onNext(keys[i % 10], i);
        }
    }

    @Test
    public void testQueryBatchOfKeys()
    {
        final AtomicInteger counter = new AtomicInteger(0);

        for (int i = 0; i < loop; ++i) {
            final int index = i;

            keys[i] = "run-" + i;

            subscribe(source.get(keys[i]), new DisposableSubscriber<Integer>() {
                @Override
                public void onComplete()
                {
                    fail("Unexpected completion on observable");
                }

                @Override
                public void onError(Throwable e)
                {
                    fail("Unexpected error on observable");
                }

                @Override
                public void onNext(Integer value)
                {
                    assertEquals(index, value % 10);

                    counter.incrementAndGet();
                }
            });
        }

        for (int i = 0; i < runs; ++i) {
            source.onNext(keys[i % 10], i);
        }

        assertEquals(runs, counter.get());
    }

    @Test
    public void testThrashSubscriptions() throws InterruptedException, ExecutionException
    {
        final AtomicInteger globalCounter = new AtomicInteger(0);
        final int subscriberCount = 25;
        ExecutorService executorService = Executors.newFixedThreadPool(subscriberCount);

        for (int j = 0; j < subscriberCount; ++j) {
            System.gc();

            final AtomicInteger counter = new AtomicInteger(0);
            final Flowable<Integer> valueObservable = source.get("test");

            final Callable<Disposable> queryCallable = new Callable<Disposable>() {

                final int index = globalCounter.incrementAndGet();

                @Override
                public Disposable call() throws Exception
                {
                    return valueObservable.subscribe(new Consumer<Integer>() {
                        @Override
                        public void accept(Integer integer)
                        {
                            counter.incrementAndGet();
                        }
                    });
                }
            };
            List<Callable<Disposable>> callables = new ArrayList<>();

            for (int i = 0; i < subscriberCount; ++i) {
                callables.add(queryCallable);
            }

            List<Future<Disposable>> futures = executorService.invokeAll(callables);
            List<Disposable> subscriptions = new ArrayList<>();

            for (int i = 0; i < subscriberCount; ++i) {
                subscriptions.add(futures.get(i).get());
            }

            source.onNext("test", 1);

            for (int i = 0; i < 10; ++i) {
                if (counter.get() != subscriberCount) {
                    Thread.sleep(10);
                }
            }

            assertEquals(subscriberCount, counter.get());

            for (int i = 0; i < subscriberCount; ++i) {
                Disposable subscription = subscriptions.get(i);

                subscription.dispose();
            }
        }
    }

    @Test
    public void testThrashQuery() throws InterruptedException, ExecutionException
    {
        final int subscriberCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(subscriberCount);

        for (int j = 0; j < 10; ++j) {
            System.gc();

            final AtomicInteger counter = new AtomicInteger(0);

            Callable<Flowable<Integer>> queryCallable = new Callable<Flowable<Integer>>() {
                @Override
                public Flowable<Integer> call() throws Exception
                {
                    return source.get("test");
                }
            };
            List<Callable<Flowable<Integer>>> callables = new ArrayList<>();

            for (int i = 0; i < subscriberCount; ++i) {
                callables.add(queryCallable);
            }

            List<Future<Flowable<Integer>>> futures = executorService.invokeAll(callables);

            for (int i = 0; i < subscriberCount; ++i) {
                Flowable<Integer> observable = futures.get(i).get();

                subscribe(observable, new Consumer<Integer>() {
                    @Override
                    public void accept(Integer value)
                    {
                        counter.incrementAndGet();
                    }
                });
            }

            source.onNext("test", 1);

            for (int i = 0; i < 10; ++i) {
                if (counter.get() != subscriberCount) {
                    Thread.sleep(10);
                }
            }

            assertEquals(subscriberCount, counter.get());

            unsubscribeAll();
        }
    }

    @Test
    public void testErrorEmission()
    {
        // setup
        AtomicInteger counter = new AtomicInteger(0);
        Disposable faultSubscription = source.faults()
                .subscribe(new IncrementingFaultSatisfier<>(source, counter));

        TestSubscriber<Integer> testSubscriber1 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber2 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber3 = new TestSubscriber<>();

        subscribe(source.get("hello"), testSubscriber1);
        System.gc();

        testSubscriber1.assertValues(1);

        subscribe(source.get("hello"), testSubscriber2);
        System.gc();

        testSubscriber1.assertValues(1);
        testSubscriber2.assertValues(1);

        // send error to 2 already bound subscribers
        RuntimeException error = new RuntimeException("whoops");
        source.onError("hello", error);

        subscribe(source.get("hello"), testSubscriber3);

        testSubscriber1.assertError(error);
        testSubscriber2.assertError(error);

        // new subscriber 3 should fault in a new value after the error
        testSubscriber3.assertValues(2);

        // cleanup
        faultSubscription.dispose();
    }

    @Test
    public void testClearingCacheDetachesSubscriber()
    {
        // setup
        AtomicInteger counter = new AtomicInteger(0);
        Disposable faultSubscription = source.faults()
                .subscribe(new IncrementingFaultSatisfier<>(source, counter));

        TestSubscriber<Integer> testSubscriber = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber2 = new TestSubscriber<>();


        @SuppressWarnings("unused")
        Flowable<Integer> helloValue = source.get("hello");

        helloValue = null;

        System.gc();

        subscribe(source.get("hello"), testSubscriber);

        testSubscriber.assertValues(1);

        source.onNext("hello", 11);

        source.clearAndDetachAll();

        subscribe(source.get("hello"), testSubscriber2);
        source.onNext("hello", 22);

        testSubscriber.assertValues(1, 11);
        testSubscriber2.assertValues(2, 22);

        // cleanup
        faultSubscription.dispose();
    }

    @Test
    public void testClearingCacheDetachesSubscriberWithFaults()
    {
        // setup
        final SingleSubject<Integer> singleSource = SingleSubject.create();

        source.setFaultHandler(new Function<String, Single<Integer>>() {
            @Override
            public Single<Integer> apply(String s) throws Exception {
                return singleSource;
            }
        });

        TestSubscriber<Integer> testSubscriber = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber2 = new TestSubscriber<>();

        @SuppressWarnings("unused")
        Flowable<Integer> helloValue = source.get("hello");

        helloValue = null;

        System.gc();

        subscribe(source.get("hello"), testSubscriber);
        testSubscriber.assertNoValues();

        source.clearAndDetachAll();

        singleSource.onSuccess(11);

        subscribe(source.get("hello"), testSubscriber2);
        source.onNext("hello", 22);

        testSubscriber.assertNoValues();
        testSubscriber2.assertValues(11, 22);
    }

    @Test
    public void testErrorPropagationInFaultHandler()
    {
        source.setFaultHandler(new Function<String, Single<Integer>>() {
            @Override
            public Single<Integer> apply(String s) {
                return Single.just(123).map(new Function<Integer, Integer>() {
                    @Override
                    public Integer apply(Integer integer) throws Exception {
                        throw new IllegalStateException("whoops");
                    }
                });
            }
        });

        TestSubscriber<Integer> value = source.get("key").test();

        value.awaitTerminalEvent();
        value.assertError(IllegalStateException.class);
    }

    @Test
    public void testFaultIfBoundWhenNotBound()
    {
        source.setFaultHandler(new Function<String, Single<Integer>>() {
            @Override
            public Single<Integer> apply(String s) {
                fail("Nothing should be bound so no fault should occur.");
                return Single.error(new Exception());
            }
        });

        // No test() call so there will be no listeners bound
        source.get("key");

        source.faultIfBound("key").test().assertComplete();
        source.faultAllBound().test().assertComplete();
    }

    @Test
    public void testMultifaultIfBoundWhenNotBound()
    {
        source.setMultiFaultHandler(new Function<List<String>, Single<List<Integer>>>() {
            @Override
            public Single<List<Integer>> apply(List<String> strings) throws Exception {
                fail("Nothing should be bound so no fault should occur.");
                return Single.error(new Exception());
            }
        });

        // No test() call so there will be no listeners bound
        source.get("key");

        source.faultIfBound("key").test().assertComplete();
        source.faultAllBound().test().assertComplete();
    }

    @Test
    public void testFaultIfBoundOnlyFaultsBound()
    {
        source.setFaultHandler(new Function<String, Single<Integer>>() {
            boolean didFaultOnce = false;
            @Override
            public Single<Integer> apply(String s) {
                if (didFaultOnce) {
                    // We only want "faultKey to fault a second time"
                    assertEquals("faultKey", s);
                }
                else {
                    didFaultOnce = true;
                }
                return Single.just(1);
            }
        });

        TestSubscriber<Integer> boundNoFault = source.get("key").test();
        TestSubscriber<Integer> boundFault = source.get("faultKey").test();

        source.faultIfBound("faultKey").test().assertComplete();
        boundNoFault.assertValueCount(1);
        boundFault.assertValueCount(2);
    }

    @Test
    public void testMultiFaultIfBoundOnlyFaultsBound()
    {
        source.setMultiFaultHandler(new Function<List<String>, Single<List<Integer>>>() {
            boolean didFaultOnce = false;

            public Single<List<Integer>> apply(List<String> strings) throws Exception {
                if (didFaultOnce) {
                    // We only want "faultKey to fault a second time"
                    assertEquals("faultKey", strings.get(0));
                }
                else {
                    didFaultOnce = true;
                }
                return Single.just(Arrays.asList(1));
            }
        });

        TestSubscriber<Integer> boundNoFault = source.get("key").test();
        TestSubscriber<Integer> boundFault = source.get("faultKey").test();

        source.faultIfBound("faultKey").test().assertComplete();
        boundNoFault.assertValues(1);
        boundFault.assertValues(1, 1);
    }

    @Test
    public void testFaultIfBoundWhenBound()
    {
        final SingleSubject<Integer> singleSource = SingleSubject.create();

        final boolean[] didFault = {false, false, false};
        source.setFaultHandler(new Function<String, Single<Integer>>() {
            int faultNum = 0;
            @Override
            public Single<Integer> apply(String s) {
                didFault[faultNum] = true;
                faultNum += 1;
                return singleSource;
            }
        });

        TestSubscriber<Integer> keySubscription = source.get("key").test();

        singleSource.onSuccess(1234);
        keySubscription.assertValue(1234);

        source.faultIfBound("key").test().awaitCount(1);
        singleSource.onSuccess(123);

        source.clearAndDetachAll();

        // Nothing should be faulted since it should no longer be bound
        source.faultAllBound();
        singleSource.onSuccess(124);

        assertTrue(didFault[0]);
        assertTrue(didFault[1]);
        assertFalse(didFault[2]);
    }

    @Test
    public void testMultiFaultIfBoundWhenBound()
    {
        final SingleSubject<List<Integer>> singleSource = SingleSubject.create();

        final boolean[] didFault = {false, false, false};
        source.setMultiFaultHandler(new Function<List<String>, Single<List<Integer>>>() {
            int faultNum = 0;
            @Override
            public Single<List<Integer>> apply(List<String> s) {
                didFault[faultNum] = true;
                faultNum += 1;
                return singleSource;
            }
        });

        TestSubscriber<Integer> keySubscription = source.get("key").test();

        singleSource.onSuccess(Arrays.asList(1234));
        keySubscription.assertValue(1234);

        source.faultIfBound("key").test().awaitCount(1);
        singleSource.onSuccess(Arrays.asList(123));

        source.clearAndDetachAll();

        // Nothing should be faulted since it should no longer be bound
        source.faultAllBound();
        singleSource.onSuccess(Arrays.asList(124));

        assertTrue(didFault[0]);
        assertTrue(didFault[1]);
        assertFalse(didFault[2]);
    }

    @Test
    public void testFaultAllBoundWhenBound()
    {
        final SingleSubject<Integer> singleSource = SingleSubject.create();

        final boolean[] didFault = {false, false, false};
        source.setFaultHandler(new Function<String, Single<Integer>>() {
            int faultNum = 0;
            @Override
            public Single<Integer> apply(String s) {
                didFault[faultNum] = true;
                faultNum += 1;
                return singleSource;
            }
        });

        TestSubscriber<Integer> keySubscription = source.get("key").test();

        singleSource.onSuccess(1234);
        keySubscription.assertValue(1234);

        source.faultAllBound().test().awaitCount(1);
        singleSource.onSuccess(123);

        source.clearAndDetachAll();

        // Nothing should be faulted since it should no longer be bound
        source.faultAllBound();
        singleSource.onSuccess(124);

        assertTrue(didFault[0]);
        assertTrue(didFault[1]);
        assertFalse(didFault[2]);
    }

    @Test
    public void testMultiFaultAllBoundWhenBound()
    {
        final SingleSubject<List<Integer>> singleSource = SingleSubject.create();

        final boolean[] didFault = {false, false, false, false};
        source.setMultiFaultHandler(new Function<List<String>, Single<List<Integer>>>() {
            int faultNum = 0;
            @Override
            public Single<List<Integer>> apply(List<String> s) {
                didFault[faultNum] = true;

                if (faultNum == 0) {
                    assertEquals(s, Arrays.asList("key"));
                } else if (faultNum == 1) {
                    assertEquals(s, Arrays.asList("key2"));
                } else if (faultNum == 2) {
                    Collections.sort(s);
                    assertTrue(s.equals(Arrays.asList("key", "key2")));
                }

                faultNum += 1;

                return singleSource;
            }
        });

        TestSubscriber<Integer> keySubscription = source.get("key").test();
        TestSubscriber<Integer> keySubscription2 = source.get("key2").test();

        singleSource.onSuccess(Arrays.asList(1234));
        keySubscription.assertValue(1234);
        keySubscription2.assertValue(1234);

        source.faultAllBound().test().awaitCount(1);
        singleSource.onSuccess(Arrays.asList(123));

        source.clearAndDetachAll();

        // Nothing should be faulted since it should no longer be bound
        source.faultAllBound();
        singleSource.onSuccess(Arrays.asList(124));

        assertTrue(didFault[0]);
        assertTrue(didFault[1]);
        assertTrue(didFault[2]);
        assertFalse(didFault[3]);
    }

    @Test
    public void faultIfBoundWithNoFaultHandlerDoesNotThrow()
    {
        TestSubscriber<Integer> keySubscription = source.get("key").test();
        TestObserver<Void> observer = source.faultIfBound("key").test().awaitCount(1);
        keySubscription.assertNoValues();
        keySubscription.assertNoErrors();
        observer.assertNoErrors();
    }

    @Test
    public void faultAllBoundWithNoFaultHandlerDoesNotThrow()
    {
        TestSubscriber<Integer> keySubscriber = source.get("key").test();
        TestObserver<Void> observer = source.faultAllBound().test().awaitCount(1);
        keySubscriber.assertNoValues();
        keySubscriber.assertNoErrors();
        observer.assertNoErrors();
    }

    @Test
    public void testErrorHandlingInFaultMethods()
    {
        final Exception faultException = new Exception("Something went wrong the second time");
        source.setFaultHandler(new Function<String, Single<Integer>>() {
            boolean shouldThrow = false;
            @Override
            public Single<Integer> apply(String s) throws Exception {
                if (shouldThrow) {
                    throw faultException;
                }
                else {
                    shouldThrow = true;
                }
                return Single.just(1);
            }
        });

        TestSubscriber<Integer> keySubscriber = source.get("key").test();

        TestObserver<Void> allBoundObserver = source.faultAllBound().test();
        TestObserver<Void> keyBoundObserver = source.faultIfBound("key").test();

        // Initial subscription should fault a value correctly
        keySubscriber.assertNoErrors();

        // Subsequent faults should throw an error in the fault handler
        allBoundObserver.assertError(faultException);
        keyBoundObserver.assertError(faultException);
    }

    @Test
    public void testQueryAndIncrementOnMultiFaultWithHandler()
    {
        // setup
        final AtomicInteger counter = new AtomicInteger(0);

        source.setMultiFaultHandler(new Function<List<String>, Single<List<Integer>>>() {
            @Override
            public Single<List<Integer>> apply(List<String> s) throws Exception {
                List<Integer> results = new ArrayList<>();

                for (int i = 0, l = s.size(); i < l; ++i) {
                    results.add(counter.incrementAndGet());
                }

                return Single.just(results);
            }
        });

        TestSubscriber<Integer> testSubscriber1 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber2 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber3 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber4 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber5 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber6 = new TestSubscriber<>();

        List<String> keys = Arrays.asList("a", "b", "c");

        subscribeAll(source.getAll(keys), testSubscriber1);

        testSubscriber1.assertValues(1);

        subscribe(source.get("a"), testSubscriber2);
        subscribe(source.get("b"), testSubscriber4);
        subscribe(source.get("c"), testSubscriber5);
        System.gc();

        testSubscriber1.assertValues(1);
        testSubscriber2.assertValues(1);
        testSubscriber4.assertValues(2);
        testSubscriber5.assertValues(3);

        unsubscribeAll();
        System.gc();

        subscribe(source.get("a"), testSubscriber3);
        subscribe(source.get("b"), testSubscriber6);

        source.onNext("a", 12);
        source.onNext("b", 13);
        source.onNext("b", 14);

        testSubscriber3.assertValues(4, 12);
        testSubscriber6.assertValues(5, 13, 14);
    }

    @Test
    public void testRestartingSingleFaultBeforeEmission() throws InterruptedException
    {
        source.setFaultHandler(new Function<String, Single<Integer>>() {
            int attemptCount = 0;

            @Override
            public Single<Integer> apply(final String s) throws Exception {
                return Single.timer(10, TimeUnit.MILLISECONDS)
                        .map(new Function<Long, Integer>() {

                            @Override
                            public Integer apply(Long aLong) throws Exception {
                                ++attemptCount;

                                return Integer.parseInt(s) * 10 + attemptCount;
                            }
                        });
            }
        });

        TestSubscriber<Integer> testSubscriber10 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber11 = new TestSubscriber<>();

        subscribe(source.get("1"), testSubscriber10);

        testSubscriber10.dispose();

        subscribe(source.get("1"), testSubscriber11);

        testSubscriber11.awaitCount(1);

        testSubscriber10.assertValues();
        testSubscriber11.assertValues(11);
    }

    @Test
    public void testRestartingSingleFaultAfterEmission() throws InterruptedException
    {
        source.setFaultHandler(new Function<String, Single<Integer>>() {
            int attemptCount = 0;

            @Override
            public Single<Integer> apply(final String s) throws Exception {
                return Single.timer(10, TimeUnit.MILLISECONDS)
                        .map(new Function<Long, Integer>() {

                            @Override
                            public Integer apply(Long aLong) throws Exception {
                                ++attemptCount;

                                return Integer.parseInt(s) * 10 + attemptCount;
                            }
                        });
            }
        });

        TestSubscriber<Integer> testSubscriber10 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber11 = new TestSubscriber<>();

        subscribe(source.get("1"), testSubscriber10);

        testSubscriber10.awaitCount(1);

        testSubscriber10.dispose();

        subscribe(source.get("1"), testSubscriber11);

        testSubscriber11.awaitCount(1);

        testSubscriber10.assertValues(11);
        testSubscriber11.assertValues(12);
    }

    @Test
    public void testRetainedSingleFaultEmission() throws InterruptedException
    {
        source.setFaultHandler(new Function<String, Single<Integer>>() {
            int attemptCount = 0;

            @Override
            public Single<Integer> apply(final String s) throws Exception {
                return Single.timer(10, TimeUnit.MILLISECONDS)
                        .map(new Function<Long, Integer>() {

                            @Override
                            public Integer apply(Long aLong) throws Exception {
                                ++attemptCount;

                                return Integer.parseInt(s) * 10 + attemptCount;
                            }
                        });
            }
        });

        TestSubscriber<Integer> testSubscriber10 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber11 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber12 = new TestSubscriber<>();

        subscribe(source.get("1"), testSubscriber10);
        subscribe(source.get("1"), testSubscriber11);

        testSubscriber10.awaitCount(1);

        testSubscriber10.dispose();

        subscribe(source.get("1"), testSubscriber12);

        testSubscriber12.awaitCount(1);

        testSubscriber10.assertValues(11);
        testSubscriber11.assertValues(11);
        testSubscriber12.assertValues(11);
    }

    @Test
    public void testStackingSubscriptionOnSingleFault() throws InterruptedException
    {
        source.setFaultHandler(new Function<String, Single<Integer>>() {
            int attemptCount = 0;

            @Override
            public Single<Integer> apply(final String s) throws Exception {
                return Single.timer(10, TimeUnit.MILLISECONDS)
                        .map(new Function<Long, Integer>() {

                            @Override
                            public Integer apply(Long aLong) throws Exception {
                                ++attemptCount;

                                return Integer.parseInt(s) * 10 + attemptCount;
                            }
                        });
            }
        });

        TestSubscriber<Integer> testSubscriber10 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber11 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber12 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber13 = new TestSubscriber<>();

        subscribe(source.get("1"), testSubscriber10);
        subscribe(source.get("1"), testSubscriber11);
        subscribe(source.get("1"), testSubscriber12);

        testSubscriber10.awaitCount(1);

        testSubscriber11.dispose();
        testSubscriber10.dispose();
        testSubscriber12.dispose();

        subscribe(source.get("1"), testSubscriber13);

        testSubscriber13.awaitCount(1);

        testSubscriber10.assertValues(11);
        testSubscriber11.assertValues(11);
        testSubscriber12.assertValues(11);
        testSubscriber13.assertValues(12);
    }
}
