package com.github.mproberts.rxtools.list;

import io.reactivex.disposables.Disposable;
import io.reactivex.exceptions.MissingBackpressureException;
import io.reactivex.functions.Consumer;
import io.reactivex.observers.BaseTestConsumer;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class BackpressureObservableListTest
{
    @Test
    public void testBasicBackpressure() throws InterruptedException, ExecutionException
    {
        final int iterations = 100;
        final SimpleObservableList<Integer> list = new SimpleObservableList<>();
        TestSubscriber testSubscriber = new TestSubscriber();

        list.updates()
                .observeOn(Schedulers.computation())
                .doOnNext(new Consumer<ObservableList.Update<Integer>>() {
                    @Override
                    public void accept(ObservableList.Update<Integer> integerUpdate)
                    {
                        if (integerUpdate.list.size() % 10 == 0) {
                            Thread.yield();
                        }
                    }
                })
                .subscribe(testSubscriber);

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<?> result = executorService.submit(new Runnable() {
            @Override
            public void run()
            {
                for (int i = 0; i < iterations; ++i) {
                    if (iterations % 9 == 0) {
                        Thread.yield();
                    }
                    list.add(i);
                }
            }
        });

        result.get();

        testSubscriber.awaitCount(iterations, BaseTestConsumer.TestWaitStrategy.SLEEP_100MS, 10000);
    }

    TestSubscriber runBackPressureTest(final int iterations, SimpleObservableList<Integer> originalList, ObservableList<Integer> list) throws InterruptedException
    {
        final AtomicInteger insertions = new AtomicInteger(0);
        final AtomicBoolean crashed = new AtomicBoolean();

        TestSubscriber testSubscriber = new TestSubscriber();

        list.updates()
                .observeOn(Schedulers.computation())
                .doOnNext(new Consumer<ObservableList.Update<Integer>>() {
                    @Override
                    public void accept(ObservableList.Update<Integer> list)
                    {
                        try {
                            Thread.sleep(2);
                        }
                        catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        for (ObservableList.Change change : list.changes) {
                            if (change.type == ObservableList.Change.Type.Inserted) {
                                insertions.incrementAndGet();
                            }
                        }

                        synchronized (insertions) {
                            if (insertions.get() >= iterations) {
                                insertions.notify();
                            }
                        }
                    }
                })
                .doOnError(new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable)
                    {
                        synchronized (insertions) {
                            crashed.set(true);
                            insertions.notify();
                        }
                    }
                })
                .subscribe(testSubscriber);

        for (int i = 0; i < iterations && !crashed.get(); ++i) {
            originalList.add(i);
        }

        return testSubscriber;
    }

    /*
    @Test(timeout = 2000)
    public void testExcessivePressure() throws InterruptedException
    {
        SimpleObservableList<Integer> list = new SimpleObservableList<>();
        TestSubscriber testSubscriber = runBackPressureTest(100000, list, list);

        testSubscriber.awaitTerminalEvent(500, TimeUnit.MILLISECONDS);

        testSubscriber.assertError(MissingBackpressureException.class);
    }

    @Test
    public void testExcessivePressureButHandled() throws InterruptedException
    {
        SimpleObservableList<Integer> list = new SimpleObservableList<>();
        TestSubscriber testSubscriber = runBackPressureTest(1000, list, ObservableLists.onBackpressureMerge(list));

        testSubscriber.awaitTerminalEvent(500, TimeUnit.MILLISECONDS);
        testSubscriber.assertNoErrors();

        int count = 0;

        for (ObservableList.Update<Integer> update : (List<ObservableList.Update<Integer>>)testSubscriber.values()) {
            count += update.changes.size();
        }

        assert(testSubscriber.values().size() < 1001);

        assertEquals(1001, count);
    }
    */

    @Test
    public void testBufferingObservableList() throws InterruptedException
    {
        TestScheduler testScheduler = new TestScheduler();
        SimpleObservableList<Integer> list = new SimpleObservableList<>();
        ObservableList<Integer> bufferedList = ObservableLists.buffer(list, 50, TimeUnit.MILLISECONDS, testScheduler);

        TestSubscriber testSubscriber = new TestSubscriber();

        bufferedList.updates().subscribe(testSubscriber);

        for (int i = 0; i < 50; ++i) {
            list.add(i);
        }

        testScheduler.advanceTimeBy(50, TimeUnit.MILLISECONDS);
        testScheduler.triggerActions();
        testSubscriber.awaitCount(1);

        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);

        for (int i = 0; i < 500; ++i) {
            list.add(i);
        }

        testScheduler.advanceTimeBy(50, TimeUnit.MILLISECONDS);
        testScheduler.triggerActions();
        testSubscriber.awaitCount(2);

        // should collapse initial reload + 100 inserts into a reload
        ObservableList.Update<Integer> update1 = (ObservableList.Update<Integer>) testSubscriber.values().get(0);

        // should collapse 500 inserts into one changeset
        ObservableList.Update<Integer> update2 = (ObservableList.Update<Integer>) testSubscriber.values().get(1);

        ObservableList.Change firstChange = update1.changes.get(0);

        assertEquals(ObservableList.Change.Type.Reloaded, firstChange.type);
        assertEquals(500, update2.changes.size());

        testSubscriber.dispose();
    }
}
