package com.github.mproberts.rxtools.list;

import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class HeaderFlowableListTest
{
    @Test
    public void testSingleList()
    {
        SimpleFlowableList<Integer> flowableList = new SimpleFlowableList<>();

        FlowableList headerList = flowableList.withHeader(0);

        TestSubscriber testSubscriber = new TestSubscriber();

        headerList.updates().subscribe(testSubscriber);

        testSubscriber.assertValueCount(1);

        List<Update> onNextEvents = testSubscriber.values();

        Update update = onNextEvents.get(0);

        assertEquals(Change.reloaded(), update.changes.get(0));
        assertEquals(Arrays.asList(), update.list);

        flowableList.add(1);

        Update update1 = onNextEvents.get(1);

        assertEquals(Arrays.asList(0, 1), update1.list);
        assertEquals(Change.inserted(0), update1.changes.get(0));
        assertEquals(Change.inserted(1), update1.changes.get(1));

        flowableList.add(2);

        Update update2 = onNextEvents.get(2);

        assertEquals(Arrays.asList(0, 1, 2), update2.list);
        assertEquals(Change.inserted(2), update2.changes.get(0));

        flowableList.remove(0);

        Update update3 = onNextEvents.get(3);

        assertEquals(Arrays.asList(0, 2), update3.list);
        assertEquals(Change.removed(1), update3.changes.get(0));

        flowableList.clear();

        Update update4 = onNextEvents.get(4);

        assertEquals(Arrays.asList(), update4.list);
        assertEquals(Change.reloaded(), update4.changes.get(0));
    }
}
