package com.github.mproberts.rxtools.list;

import io.reactivex.functions.Function;
import io.reactivex.processors.BehaviorProcessor;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

public class CachedFlowableListTest
{
    @Test
    public void testBasicCaching()
    {
        final List<Integer> fetches = new ArrayList<>();
        TestSubscriber<Update<Integer>> testSubscriber = new TestSubscriber<>();

        SimpleFlowableList<Integer> list = new SimpleFlowableList<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        FlowableList<Integer> transformedList = list.map(new Function<Integer, Integer>() {
            @Override
            public Integer apply(Integer value) {
                fetches.add(value);

                return value;
            }
        });

        FlowableList<Integer> cachedList = transformedList.cache(5, 0);

        cachedList.updates().subscribe(testSubscriber);

        testSubscriber.assertValueCount(1);

        List<Update<Integer>> onNextEvents = testSubscriber.values();

        assertEquals(Arrays.asList(Change.reloaded()), onNextEvents.get(0).changes);

        List<Integer> list1 = onNextEvents.get(0).list;

        int value1 = list1.get(3);
        int value2 = list1.get(3);
        int value3 = list1.get(3);
        int value4 = list1.get(4);

        assertEquals(4, value1);
        assertEquals(4, value2);
        assertEquals(4, value3);
        assertEquals(5, value4);
        assertEquals(Arrays.asList(4, 5), fetches);
    }

    @Test
    public void testCachingWithChanges()
    {
        final List<Integer> fetches = new ArrayList<>();
        TestSubscriber<Update<Integer>> testSubscriber = new TestSubscriber<>();

        SimpleFlowableList<Integer> list = new SimpleFlowableList<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        FlowableList<Integer> transformedList = list.map(new Function<Integer, Integer>() {
            @Override
            public Integer apply(Integer value) {
                fetches.add(value);

                return value;
            }
        });

        FlowableList<Integer> cachedList = transformedList.cache(5, 5);

        cachedList.updates().subscribe(testSubscriber);
        {
            List<Integer> list1 = testSubscriber.values().get(0).list;

            int value1 = list1.get(3);
            int value2 = list1.get(3);
            int value3 = list1.get(4);

            // 1, 2, 3, 4, 5, 6, 7, 8, 9, 10

            assertEquals(4, value1);
            assertEquals(4, value2);
            assertEquals(5, value3);
            assertEquals(Arrays.asList(4, 5), fetches);
        }

        list.add(4, 11);
        {
            List<Integer> list2 = testSubscriber.values().get(1).list;

            int value1 = list2.get(3);
            int value2 = list2.get(3);
            int value3 = list2.get(4);
            int value4 = list2.get(5);

            // 1, 2, 3, 4, 11, 5, 6, 7, 8, 9, 10

            assertEquals(4, value1);
            assertEquals(4, value2);
            assertEquals(11, value3);
            assertEquals(5, value4);
            assertEquals(Arrays.asList(4, 5, 11), fetches);
        }

        list.add(1, 12);
        {
            List<Integer> list3 = testSubscriber.values().get(2).list;

            int value1 = list3.get(4);
            int value2 = list3.get(6);
            int value3 = list3.get(5);
            int value4 = list3.get(1);
            int value5 = list3.get(3);

            // 1, 12, 2, 3, 4, 11, 5, 6, 7, 8, 9, 10

            assertEquals(4, value1);
            assertEquals(5, value2);
            assertEquals(11, value3);
            assertEquals(12, value4);
            assertEquals(3, value5);
            assertEquals(Arrays.asList(4, 5, 11, 12, 3), fetches);
        }
    }

    @Test
    public void testCachingWithRemovals()
    {
        final List<Integer> fetches = new ArrayList<>();
        TestSubscriber<Update<Integer>> testSubscriber = new TestSubscriber<>();

        SimpleFlowableList<Integer> list = new SimpleFlowableList<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        FlowableList<Integer> transformedList = list.map(new Function<Integer, Integer>() {
            @Override
            public Integer apply(Integer value) {
                fetches.add(value);

                return value;
            }
        });

        FlowableList<Integer> cachedList = transformedList.cache(5, 5);

        cachedList.updates().subscribe(testSubscriber);
        {
            List<Integer> list1 = testSubscriber.values().get(0).list;

            int value1 = list1.get(3);
            int value2 = list1.get(3);
            int value3 = list1.get(4);

            // 1, 2, 3, 4, 5, 6, 7, 8, 9, 10

            assertEquals(4, value1);
            assertEquals(4, value2);
            assertEquals(5, value3);
            assertEquals(Arrays.asList(4, 5), fetches);
        }

        list.remove(1);
        {
            List<Integer> list2 = testSubscriber.values().get(1).list;

            int value1 = list2.get(2);
            int value2 = list2.get(3);

            // 1, 3, 4, 5, 6, 7, 8, 9, 10

            assertEquals(4, value1);
            assertEquals(5, value2);
            assertEquals(Arrays.asList(4, 5), fetches);
        }
    }

    @Test
    public void testCachingWithMoves()
    {
        final List<Integer> fetches = new ArrayList<>();
        TestSubscriber<Update<Integer>> testSubscriber = new TestSubscriber<>();

        SimpleFlowableList<Integer> list = new SimpleFlowableList<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        FlowableList<Integer> transformedList = list.map(new Function<Integer, Integer>() {
            @Override
            public Integer apply(Integer value) {
                fetches.add(value);

                return value;
            }
        });

        FlowableList<Integer> cachedList = transformedList.cache(5, 5);

        cachedList.updates().subscribe(testSubscriber);
        {
            List<Integer> list1 = testSubscriber.values().get(0).list;

            int value1 = list1.get(3);
            int value2 = list1.get(3);
            int value3 = list1.get(4);

            // 1, 2, 3, 4, 5, 6, 7, 8, 9, 10

            assertEquals(4, value1);
            assertEquals(4, value2);
            assertEquals(5, value3);
            assertEquals(Arrays.asList(4, 5), fetches);
        }

        list.move(8, 1);
        {
            List<Integer> list2 = testSubscriber.values().get(1).list;

            int value1 = list2.get(4);
            int value2 = list2.get(5);

            // 1, 9, 2, 3, 4, 5, 6, 7, 8, 10

            assertEquals(4, value1);
            assertEquals(5, value2);
            assertEquals(Arrays.asList(4, 5), fetches);
        }

        list.move(8, 5);
        {
            List<Integer> list3 = testSubscriber.values().get(2).list;

            int value1 = list3.get(4);
            int value2 = list3.get(5);
            int value3 = list3.get(6);

            // 1, 9, 2, 3, 4, 8, 5, 6, 7, 10

            assertEquals(4, value1);
            assertEquals(8, value2);
            assertEquals(5, value3);
            assertEquals(Arrays.asList(4, 5, 8), fetches);
        }

        list.move(1, 7);
        {
            List<Integer> list4 = testSubscriber.values().get(3).list;

            int value1 = list4.get(3);
            int value2 = list4.get(4);
            int value3 = list4.get(5);

            // 1, 2, 3, 4, 8, 5, 6, 7, 9, 10

            assertEquals(4, value1);
            assertEquals(8, value2);
            assertEquals(5, value3);
            assertEquals(Arrays.asList(4, 5, 8), fetches);
        }
    }
}
