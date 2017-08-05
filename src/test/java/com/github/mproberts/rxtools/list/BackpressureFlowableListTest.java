package com.github.mproberts.rxtools.list;

import io.reactivex.functions.Consumer;
import io.reactivex.observers.BaseTestConsumer;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;

import java.util.concurrent.*;

import static org.junit.Assert.*;

public class BackpressureFlowableListTest
{
    @Test
    public void testBasicBackpressure() throws InterruptedException, ExecutionException
    {
        final int iterations = 100;
        final SimpleFlowableList<Integer> list = new SimpleFlowableList<>();
        TestSubscriber testSubscriber = new TestSubscriber();

        list.updates()
                .observeOn(Schedulers.computation())
                .doOnNext(new Consumer<Update<Integer>>() {
                    @Override
                    public void accept(Update<Integer> integerUpdate)
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

    @Test
    public void testBufferingObservableList() throws InterruptedException
    {
        TestScheduler testScheduler = new TestScheduler();
        SimpleFlowableList<Integer> list = new SimpleFlowableList<>();
        FlowableList<Integer> bufferedList = list.buffer(50, TimeUnit.MILLISECONDS, testScheduler);

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
        Update<Integer> update1 = (Update<Integer>) testSubscriber.values().get(0);

        // should collapse 500 inserts into one changeset
        Update<Integer> update2 = (Update<Integer>) testSubscriber.values().get(1);

        Change firstChange = update1.changes.get(0);

        assertEquals(Change.Type.Reloaded, firstChange.type);
        assertEquals(500, update2.changes.size());

        testSubscriber.dispose();
    }
}
