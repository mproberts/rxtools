package com.github.mproberts.rxtools.list;

import org.junit.Test;
import rx.Subscription;
import rx.exceptions.MissingBackpressureException;
import rx.functions.Action1;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

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
                .doOnNext(new Action1<ObservableList.Update<Integer>>() {
                    @Override
                    public void call(ObservableList.Update<Integer> integerUpdate)
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

        assertTrue(testSubscriber.awaitValueCount(iterations, 10000, TimeUnit.MILLISECONDS));
    }

    TestSubscriber runBackPressureTest(final int iterations, SimpleObservableList<Integer> originalList, ObservableList<Integer> list) throws InterruptedException
    {
        final AtomicInteger insertions = new AtomicInteger(0);
        final AtomicBoolean crashed = new AtomicBoolean();

        TestSubscriber testSubscriber = new TestSubscriber();

        Subscription subscribe = list.updates()
                .observeOn(Schedulers.computation())
                .doOnNext(new Action1<ObservableList.Update<Integer>>() {
                    @Override
                    public void call(ObservableList.Update<Integer> list)
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
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable)
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

        testSubscriber.add(subscribe);

        return testSubscriber;
    }

    @Test
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

        for (ObservableList.Update<Integer> update : (List<ObservableList.Update<Integer>>)testSubscriber.getOnNextEvents()) {
            count += update.changes.size();
        }

        assert(testSubscriber.getOnNextEvents().size() < 1001);

        assertEquals(1001, count);
    }

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
        testSubscriber.awaitValueCount(1, 100, TimeUnit.MILLISECONDS);

        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);

        for (int i = 0; i < 500; ++i) {
            list.add(i);
        }

        testScheduler.advanceTimeBy(50, TimeUnit.MILLISECONDS);
        testScheduler.triggerActions();
        testSubscriber.awaitValueCount(2, 100, TimeUnit.MILLISECONDS);

        // should collapse initial reload + 100 inserts into a reload
        ObservableList.Update<Integer> update1 = (ObservableList.Update<Integer>) testSubscriber.getOnNextEvents().get(0);

        // should collapse 500 inserts into one changeset
        ObservableList.Update<Integer> update2 = (ObservableList.Update<Integer>) testSubscriber.getOnNextEvents().get(1);

        ObservableList.Change firstChange = update1.changes.get(0);

        assertEquals(ObservableList.Change.Type.Reloaded, firstChange.type);
        assertEquals(500, update2.changes.size());

        testSubscriber.unsubscribe();
    }
}
