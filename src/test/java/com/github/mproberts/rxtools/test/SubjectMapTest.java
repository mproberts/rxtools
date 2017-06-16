package com.github.mproberts.rxtools.test;

import com.github.mproberts.rxtools.SubjectMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action1;
import rx.observers.TestSubscriber;
import rx.subscriptions.CompositeSubscription;

import java.util.ArrayList;
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

    private CompositeSubscription _subscription;
    private SubjectMap<String, Integer> source;

    private static class IncrementingFaultSatisfier<K> implements Action1<K>
    {
        private final AtomicInteger _counter;
        private final SubjectMap<K, Integer> _source;

        IncrementingFaultSatisfier(SubjectMap<K, Integer> source, AtomicInteger counter)
        {
            _source = source;
            _counter = counter;
        }

        @Override
        public void call(K key)
        {
            _source.onNext(key, _counter.incrementAndGet());
        }
    }

    private <T> void subscribe(Observable<T> observable, Action1<T> action)
    {
        _subscription.add(observable.subscribe(action));
    }

    private <T> void subscribe(Observable<T> observable, Subscriber<T> subscriber)
    {
        _subscription.add(observable.subscribe(subscriber));
    }

    private void unsubscribeAll()
    {
        _subscription.clear();
    }

    @Before
    public void setup()
    {
        source = new SubjectMap<>();
        _subscription = new CompositeSubscription();
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
        Subscription faultSubscription = source.faults()
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
        faultSubscription.unsubscribe();
    }

    @Test
    public void testBattlingSubscribers1() throws InterruptedException
    {
        TestSubscriber<Integer> testSubscriber1 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber2 = new TestSubscriber<>();

        Observable<Integer> value1 = source.get("hello");
        Subscription s1 = value1.subscribe(testSubscriber1);

        source.onNext("hello", 3);

        s1.unsubscribe();

        testSubscriber1.assertValues(3);

        Observable<Integer> value2 = source.get("hello");
        Subscription s2 = value2.subscribe(testSubscriber2);
        Subscription s3 = value1.subscribe(testSubscriber1);

        source.onNext("hello", 4);

        s2.unsubscribe();
        s3.unsubscribe();

        testSubscriber2.assertValues(3, 4);
    }

    @Test
    public void testBattlingSubscribers() throws InterruptedException
    {
        // setup
        AtomicInteger counter = new AtomicInteger(0);
        Subscription faultSubscription = source.faults()
                .subscribe(new IncrementingFaultSatisfier<>(source, counter));

        Observable<Integer> retainedObservable = source.get("hello");
        TestSubscriber<Integer> testSubscriber1 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber2 = new TestSubscriber<>();

        Subscription s1 = retainedObservable.subscribe(testSubscriber1);
        Subscription s2;

        testSubscriber1.assertValues(1);

        s1.unsubscribe();

        testSubscriber1.assertValues(1);

        Observable<Integer> retainedObservable2 = source.get("hello");

        s1 = retainedObservable.subscribe(testSubscriber1);

        testSubscriber1.assertValues(1);

        s2 = retainedObservable.subscribe(testSubscriber2);

        testSubscriber1.assertValues(1);

        s1.unsubscribe();
        s2.unsubscribe();
    }

    @Test
    public void testQueryAndUpdate()
    {
        // setup
        AtomicInteger counter = new AtomicInteger(0);
        Subscription faultSubscription = source.faults()
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
        faultSubscription.unsubscribe();
    }

    @Test
    public void testExceptionHandlingFault()
    {
        // setup
        final AtomicBoolean exceptionEncountered = new AtomicBoolean(false);
        final AtomicInteger counter = new AtomicInteger(0);
        Subscription faultSubscription = source.faults()
                .subscribe(new Action1<String>() {
                    @Override
                    public void call(String key) {
                        if (counter.incrementAndGet() <= 1) {
                            throw new RuntimeException("Explosions!");
                        }

                        source.onNext(key, counter.get());
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        assertTrue(throwable instanceof RuntimeException);

                        exceptionEncountered.set(true);
                    }
                });

        TestSubscriber<Integer> testSubscriber1 = new TestSubscriber<>();
        Observable<Integer> helloValue = source.get("hello");

        subscribe(helloValue, testSubscriber1);

        testSubscriber1.assertNoValues();

        assertTrue(exceptionEncountered.get());

        // cleanup
        faultSubscription.unsubscribe();
    }

    @Test
    public void testPruningUnsubscribedObservables()
    {
        // setup
        AtomicInteger counter = new AtomicInteger(0);
        Subscription faultSubscription = source.faults()
                .subscribe(new IncrementingFaultSatisfier<>(source, counter));

        TestSubscriber<Integer> testSubscriber = new TestSubscriber<>();

        @SuppressWarnings("unused")
        Observable<Integer> helloValue = source.get("hello");

        helloValue = null;

        System.gc();

        subscribe(source.get("hello"), testSubscriber);

        testSubscriber.assertValues(1);

        source.onNext("hello", 11);

        testSubscriber.assertValues(1, 11);

        // cleanup
        faultSubscription.unsubscribe();
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

            subscribe(source.get(keys[i]), new Subscriber<Integer>() {
                @Override
                public void onCompleted()
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
        final int subscriberCount = 50;
        ExecutorService executorService = Executors.newWorkStealingPool(subscriberCount);

        for (int j = 0; j < 10; ++j) {
            System.gc();

            final AtomicInteger counter = new AtomicInteger(0);
            final Observable<Integer> valueObservable = source.get("test");

            final Callable<Subscription> queryCallable = new Callable<Subscription>() {

                final int index = globalCounter.incrementAndGet();

                @Override
                public Subscription call() throws Exception
                {
                    return valueObservable.subscribe(new Action1<Integer>() {
                        @Override
                        public void call(Integer integer)
                        {
                            counter.incrementAndGet();
                        }
                    });
                }
            };
            List<Callable<Subscription>> callables = new ArrayList<>();

            for (int i = 0; i < subscriberCount; ++i) {
                callables.add(queryCallable);
            }

            List<Future<Subscription>> futures = executorService.invokeAll(callables);
            List<Subscription> subscriptions = new ArrayList<>();

            for (int i = 0; i < subscriberCount; ++i) {
                subscriptions.add(futures.get(i).get());
            }

            source.onNext("test", 1);

            assertEquals(subscriberCount, counter.get());

            for (int i = 0; i < subscriberCount; ++i) {
                Subscription subscription = subscriptions.get(i);

                subscription.unsubscribe();
            }
        }
    }

    @Test
    public void testThrashQuery() throws InterruptedException, ExecutionException
    {
        final int subscriberCount = 50;
        ExecutorService executorService = Executors.newWorkStealingPool(subscriberCount);

        for (int j = 0; j < 10; ++j) {
            System.gc();

            final AtomicInteger counter = new AtomicInteger(0);

            Callable<Observable<Integer>> queryCallable = new Callable<Observable<Integer>>() {
                @Override
                public Observable<Integer> call() throws Exception
                {
                    return source.get("test");
                }
            };
            List<Callable<Observable<Integer>>> callables = new ArrayList<>();

            for (int i = 0; i < subscriberCount; ++i) {
                callables.add(queryCallable);
            }

            List<Future<Observable<Integer>>> futures = executorService.invokeAll(callables);

            for (int i = 0; i < subscriberCount; ++i) {
                Observable<Integer> observable = futures.get(i).get();

                subscribe(observable, new Action1<Integer>() {
                    @Override
                    public void call(Integer value)
                    {
                        counter.incrementAndGet();
                    }
                });
            }

            source.onNext("test", 1);

            assertEquals(subscriberCount, counter.get());

            unsubscribeAll();
        }
    }

    @Test
    public void testErrorEmission()
    {
        // setup
        AtomicInteger counter = new AtomicInteger(0);
        Subscription faultSubscription = source.faults()
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
        faultSubscription.unsubscribe();
    }
}
