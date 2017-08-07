package com.github.mproberts.rxtools.list;

import io.reactivex.processors.BehaviorProcessor;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class DiffFlowableListTest
{
    @Test
    public void testBasicTransform()
    {
        BehaviorProcessor<List<Integer>> processor = BehaviorProcessor.create();
        FlowableList<Integer> list = FlowableList.diff(processor);
        TestSubscriber<Update<Integer>> test = list.updates().test();

        processor.onNext(Arrays.asList(1, 2, 3, 4));

        Update<Integer> firstUpdate = test.values().get(0);
        assertEquals(Collections.singletonList(Change.reloaded()), firstUpdate.changes);

        processor.onNext(Arrays.asList(2, 4));

        Update<Integer> secondUpdate = test.values().get(1);
        assertEquals(Change.removed(0), secondUpdate.changes.get(0));
        assertEquals(Change.removed(2), secondUpdate.changes.get(1));
        assertEquals(Arrays.asList(2, 4), secondUpdate.list);
    }
}
