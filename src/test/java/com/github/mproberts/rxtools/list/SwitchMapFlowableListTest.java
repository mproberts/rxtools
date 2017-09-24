package com.github.mproberts.rxtools.list;

import io.reactivex.Flowable;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class SwitchMapFlowableListTest
{
    @Test
    public void testBasicTransform()
    {
        TestSubscriber<Update<Integer>> testSubscriber = new TestSubscriber<>();

        SimpleFlowableList<Integer> list1 = new SimpleFlowableList<>(Arrays.asList(1, 2, 3));
        SimpleFlowableList<Integer> list2 = new SimpleFlowableList<>(Arrays.asList(3, 4, 5));
        SimpleFlowableList<Integer> list3 = new SimpleFlowableList<>(Arrays.asList(6, 7, 8));

        Flowable<FlowableList<Integer>> fatListStream = Flowable.<FlowableList<Integer>>just(list1, list2, list3);

        FlowableList<Integer> flattened = FlowableList.flatten(fatListStream);

        flattened.updates().subscribe(testSubscriber);

        testSubscriber.assertValueCount(3);

        List<Update<Integer>> onNextEvents = testSubscriber.values();

        assertEquals(Arrays.asList(Change.reloaded()), onNextEvents.get(0).changes);
        assertEquals(Arrays.asList(1, 2, 3), onNextEvents.get(0).list);

        assertEquals(Arrays.asList(Change.reloaded()), onNextEvents.get(1).changes);
        assertEquals(Arrays.asList(3, 4, 5), onNextEvents.get(1).list);

        assertEquals(Arrays.asList(Change.reloaded()), onNextEvents.get(2).changes);
        assertEquals(Arrays.asList(6, 7, 8), onNextEvents.get(2).list);
    }
}
