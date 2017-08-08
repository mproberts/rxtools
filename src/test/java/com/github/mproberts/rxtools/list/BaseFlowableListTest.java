package com.github.mproberts.rxtools.list;

import io.reactivex.functions.Consumer;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertTrue;

public class BaseFlowableListTest
{
    private SimpleFlowableList<Integer> list;

    @Before
    public void setup()
    {
        list = new SimpleFlowableList<>();
    }

    @Test
    public void testBasicAdd()
    {
        final AtomicInteger counter = new AtomicInteger(0);
        TestSubscriber<Update<Integer>> testSubscriber = new TestSubscriber<>();

        list.updates().subscribe(testSubscriber);

        list.add(counter.incrementAndGet());
        list.add(counter.incrementAndGet());

        testSubscriber.assertValues(
                new Update<>(Arrays.<Integer>asList(), Change.reloaded()),
                new Update<>(Arrays.asList(1), Change.inserted(0)),
                new Update<>(Arrays.asList(1, 2), Change.inserted(1)));
    }

    @Test
    public void testBasicAddAll()
    {
        final AtomicInteger counter = new AtomicInteger(0);
        TestSubscriber<Update<Integer>> testSubscriber = new TestSubscriber<>();

        list.updates().subscribe(testSubscriber);

        list.addAll(Arrays.asList(counter.incrementAndGet(), counter.incrementAndGet()));

        testSubscriber.assertValues(
                new Update<>(Arrays.<Integer>asList(), Change.reloaded()),
                new Update<>(Arrays.asList(1, 2), Arrays.asList(Change.inserted(0), Change.inserted(1))));
    }

    @Test
    public void testMoveNoop()
    {
        final AtomicInteger counter = new AtomicInteger(0);
        TestSubscriber<Update<Integer>> testSubscriber = new TestSubscriber<>();

        list.updates().subscribe(testSubscriber);

        list.addAll(Arrays.asList(counter.incrementAndGet(), counter.incrementAndGet()));
        list.move(1, 1);

        testSubscriber.assertValues(
                new Update<>(Arrays.<Integer>asList(), Change.reloaded()),
                new Update<>(Arrays.asList(1, 2), Arrays.asList(Change.inserted(0), Change.inserted(1))));
    }

    @Test
    public void testBatchAddRemove()
    {
        final AtomicInteger counter = new AtomicInteger(0);
        TestSubscriber<Update<Integer>> testSubscriber = new TestSubscriber<>();

        list.updates().subscribe(testSubscriber);

        list.batch(new Consumer<SimpleFlowableList<Integer>>() {
            @Override
            public void accept(SimpleFlowableList<Integer> integerBaseObservableList)
            {
                list.add(counter.incrementAndGet());
                list.add(counter.incrementAndGet());
                list.remove(1);
                list.add(counter.incrementAndGet());
            }
        });

        testSubscriber.assertValues(
                new Update<>(Arrays.<Integer>asList(), Change.reloaded()),
                new Update<>(Arrays.asList(1, 3), Arrays.asList(
                        Change.inserted(0),
                        Change.inserted(1),
                        Change.removed(1),
                        Change.inserted(1)
                )));
    }

    @Test
    public void testOrderedInOrderedOut() throws InterruptedException
    {
        final int iterations = 100;
        final AtomicInteger counter = new AtomicInteger(0);
        TestSubscriber<Update<Integer>> testSubscriber = new TestSubscriber<>();
        ExecutorService executorService = Executors.newFixedThreadPool(iterations / 4);

        List<Callable<Object>> callbales = new ArrayList<>();
        List<Integer> allEntries = new ArrayList<>();

        for (int i = 0; i < iterations; ++i) {
            allEntries.add(i + 1);

            callbales.add(new Callable<Object>() {
                @Override
                public Object call() throws Exception
                {
                    synchronized (counter) {
                        list.add(counter.incrementAndGet());
                    }

                    return null;
                }
            });
        }

        list.updates().skip(iterations).subscribe(testSubscriber);

        executorService.invokeAll(callbales);

        testSubscriber.assertValue(new Update<>(allEntries, Change.inserted(iterations - 1)));
    }

    @Test
    public void testThrashAddition() throws InterruptedException
    {
        final int iterations = 1000;
        final AtomicInteger counter = new AtomicInteger(0);
        TestSubscriber<Update<Integer>> testSubscriber = new TestSubscriber<>();
        ExecutorService executorService = Executors.newFixedThreadPool(25);

        List<Callable<Object>> callbales = new ArrayList<>();

        for (int i = 0; i < iterations; ++i) {
            callbales.add(new Callable<Object>() {
                @Override
                public Object call() throws Exception
                {
                    list.add(counter.getAndIncrement());

                    return null;
                }
            });
        }

        list.updates().skip(iterations).subscribe(testSubscriber);

        executorService.invokeAll(callbales);

        List<Update<Integer>> events = testSubscriber.values();
        Update<Integer> lastEvent = events.get(events.size() - 1);
        List<Integer> list = lastEvent.list;

        for (int i = 0; i < iterations; ++i) {
            assertTrue(list.contains(i));
        }
    }
}
