package com.github.mproberts.rxtools.list;

import com.github.mproberts.rxtools.list.ObservableList;
import com.github.mproberts.rxtools.list.SimpleObservableList;
import org.junit.Before;
import org.junit.Test;
import rx.functions.Action1;
import rx.observers.TestSubscriber;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertTrue;

public class BaseObservableListTest
{
    private SimpleObservableList<Integer> list;

    @Before
    public void setup()
    {
        list = new SimpleObservableList<>();
    }

    @Test
    public void testBasicAdd()
    {
        AtomicInteger counter = new AtomicInteger(0);
        TestSubscriber<ObservableList.Update<Integer>> testSubscriber = new TestSubscriber<>();

        list.updates().subscribe(testSubscriber);

        list.add(counter.incrementAndGet());
        list.add(counter.incrementAndGet());

        testSubscriber.assertValues(
                new ObservableList.Update<>(Arrays.asList(), ObservableList.Change.reloaded()),
                new ObservableList.Update<>(Arrays.asList(1), ObservableList.Change.inserted(0)),
                new ObservableList.Update<>(Arrays.asList(1, 2), ObservableList.Change.inserted(1)));
    }

    @Test
    public void testBatchAddRemove()
    {
        AtomicInteger counter = new AtomicInteger(0);
        TestSubscriber<ObservableList.Update<Integer>> testSubscriber = new TestSubscriber<>();

        list.updates().subscribe(testSubscriber);

        list.batch(new Action1<SimpleObservableList<Integer>>() {
            @Override
            public void call(SimpleObservableList<Integer> integerBaseObservableList)
            {
                list.add(counter.incrementAndGet());
                list.add(counter.incrementAndGet());
                list.remove(1);
                list.add(counter.incrementAndGet());
            }
        });

        testSubscriber.assertValues(
                new ObservableList.Update<>(Arrays.asList(), ObservableList.Change.reloaded()),
                new ObservableList.Update<>(Arrays.asList(1, 3), Arrays.asList(
                        ObservableList.Change.inserted(0),
                        ObservableList.Change.inserted(1),
                        ObservableList.Change.removed(1),
                        ObservableList.Change.inserted(1)
                )));
    }

    @Test
    public void testOrderedInOrderedOut() throws InterruptedException
    {
        final int iterations = 100;
        AtomicInteger counter = new AtomicInteger(0);
        TestSubscriber<ObservableList.Update<Integer>> testSubscriber = new TestSubscriber<>();
        ExecutorService executorService = Executors.newWorkStealingPool(iterations / 4);

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

        testSubscriber.assertValue(new ObservableList.Update<>(allEntries, ObservableList.Change.inserted(iterations - 1)));
    }

    @Test
    public void testThrashAddition() throws InterruptedException
    {
        final int iterations = 1000;
        AtomicInteger counter = new AtomicInteger(0);
        TestSubscriber<ObservableList.Update<Integer>> testSubscriber = new TestSubscriber<>();
        ExecutorService executorService = Executors.newWorkStealingPool(iterations / 4);

        List<Callable<Object>> callbales = new ArrayList<>();
        List<Integer> allEntries = new ArrayList<>();

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

        List<ObservableList.Update<Integer>> events = testSubscriber.getOnNextEvents();
        ObservableList.Update<Integer> lastEvent = events.get(events.size() - 1);
        List<Integer> list = lastEvent.list;

        for (int i = 0; i < iterations; ++i) {
            assertTrue(list.contains(i));
        }
    }
}
