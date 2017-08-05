package com.github.mproberts.rxtools.list;

import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

public class PrefetchFlowableListTest
{
    @Test
    public void testBasicPrefetch()
    {
        final List<Integer> fetches = new ArrayList<>();
        final List<Integer> prefetches = new ArrayList<>();
        TestSubscriber<FlowableList.Update<Integer>> testSubscriber = new TestSubscriber<>();

        SimpleFlowableList<Integer> list = new SimpleFlowableList<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        FlowableList<Integer> transformedList = FlowableLists.transform(list, new Function<Integer, Integer>() {
            @Override
            public Integer apply(Integer value) {
                fetches.add(value);

                return value;
            }
        });

        FlowableList<Integer> prefetchList = FlowableLists.prefetch(transformedList, 2, 3,
                new Consumer<Collection<Integer>>() {
                    @Override
                    public void accept(Collection<Integer> integer) throws Exception {
                        prefetches.addAll(integer);
                    }
                });

        prefetchList.updates().subscribe(testSubscriber);

        testSubscriber.assertValueCount(1);

        List<FlowableList.Update<Integer>> onNextEvents = testSubscriber.values();

        assertEquals(Arrays.asList(FlowableList.Change.reloaded()), onNextEvents.get(0).changes);

        List<Integer> list1 = onNextEvents.get(0).list;

        int value = list1.get(3);

        assertEquals(4, value);
        assertEquals(Arrays.asList(4, 3, 2, 5, 6, 7), fetches);
        assertEquals(Arrays.asList(3, 2, 5, 6, 7), prefetches);
    }

    @Test
    public void testEmptyPrefetch()
    {
        final List<Integer> fetches = new ArrayList<>();
        final List<Integer> prefetches = new ArrayList<>();
        TestSubscriber<FlowableList.Update<Integer>> testSubscriber = new TestSubscriber<>();

        SimpleFlowableList<Integer> list = new SimpleFlowableList<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        FlowableList<Integer> transformedList = FlowableLists.transform(list, new Function<Integer, Integer>() {
            @Override
            public Integer apply(Integer value) {
                fetches.add(value);

                return value;
            }
        });

        FlowableList<Integer> prefetchList = FlowableLists.prefetch(transformedList, 0, 0,
                new Consumer<Collection<Integer>>() {
                    @Override
                    public void accept(Collection<Integer> integer) throws Exception {
                        prefetches.addAll(integer);
                    }
                });

        prefetchList.updates().subscribe(testSubscriber);

        testSubscriber.assertValueCount(1);

        List<FlowableList.Update<Integer>> onNextEvents = testSubscriber.values();

        assertEquals(Arrays.asList(FlowableList.Change.reloaded()), onNextEvents.get(0).changes);

        List<Integer> list1 = onNextEvents.get(0).list;

        int value = list1.get(3);

        assertEquals(4, value);
        assertEquals(Collections.singletonList(4), fetches);
        assertEquals(Collections.emptyList(), prefetches);
    }

    @Test
    public void testLopSidedPrefetch()
    {
        final List<Integer> fetches = new ArrayList<>();
        final List<Integer> prefetches = new ArrayList<>();
        TestSubscriber<FlowableList.Update<Integer>> testSubscriber = new TestSubscriber<>();

        SimpleFlowableList<Integer> list = new SimpleFlowableList<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        FlowableList<Integer> transformedList = FlowableLists.transform(list, new Function<Integer, Integer>() {
            @Override
            public Integer apply(Integer value) {
                fetches.add(value);

                return value;
            }
        });

        FlowableList<Integer> prefetchList = FlowableLists.prefetch(transformedList, 0, 4,
                new Consumer<Collection<Integer>>() {
                    @Override
                    public void accept(Collection<Integer> integer) throws Exception {
                        prefetches.addAll(integer);
                    }
                });

        prefetchList.updates().subscribe(testSubscriber);

        testSubscriber.assertValueCount(1);

        List<FlowableList.Update<Integer>> onNextEvents = testSubscriber.values();

        assertEquals(Arrays.asList(FlowableList.Change.reloaded()), onNextEvents.get(0).changes);

        List<Integer> list1 = onNextEvents.get(0).list;

        assertEquals(Integer.valueOf(4), list1.get(3));
        assertEquals(Arrays.asList(4, 5, 6, 7, 8), fetches);
        assertEquals(Arrays.asList(5, 6, 7, 8), prefetches);

        assertEquals(Integer.valueOf(9), list1.get(8));
        assertEquals(Arrays.asList(4, 5, 6, 7, 8, 9, 10), fetches);
        assertEquals(Arrays.asList(5, 6, 7, 8, 10), prefetches);
    }
}
