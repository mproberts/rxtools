package com.github.mproberts.rxtools.list;

import io.reactivex.processors.BehaviorProcessor;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

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

        processor.onNext(Arrays.asList(2, 4, 5));

        Update<Integer> secondUpdate = test.values().get(1);

        assertEquals(Arrays.asList(2, 4, 5), secondUpdate.list);
        assertEquals(Arrays.asList(
                2, 4, 5),
                TestTools.applyChanges(firstUpdate.list, secondUpdate.list, secondUpdate.changes));
    }

    @Test
    public void testSortedMoveOnly()
    {
        final List<String> list1 = Arrays.asList("C", "B", "J", "D", "G", "H", "A", "I", "E", "F");
        final List<String> list2 = Arrays.asList("A", "B", "C", "D", "E", "F", "G", "H", "I", "J");

        BehaviorProcessor<List<String>> processor = BehaviorProcessor.create();
        FlowableList<String> list = FlowableList.diff(processor);
        TestSubscriber<Update<String>> test = list.updates().test();

        processor.onNext(list1);

        Update<String> firstUpdate = test.values().get(0);
        assertEquals(Collections.singletonList(Change.reloaded()), firstUpdate.changes);

        processor.onNext(list2);

        Update<String> secondUpdate = test.values().get(1);

        assertEquals(
                Arrays.asList("A", "B", "C", "D", "E", "F", "G", "H", "I", "J"),
                secondUpdate.list);

        assertEquals(
                Arrays.asList("A", "B", "C", "D", "E", "F", "G", "H", "I", "J"),
                TestTools.applyChanges(firstUpdate.list, secondUpdate.list, secondUpdate.changes));

        for (Change change : secondUpdate.changes) {
            assertEquals(change.type, Change.Type.Moved);
        }
    }

    @Test
    public void testSortedIgnoreMoves()
    {
        final List<String> list1 = Arrays.asList("C", "B", "J", "D", "G", "H", "A", "I", "E", "F");
        final List<String> list2 = Arrays.asList("A", "B", "C", "D", "E", "F", "G", "H", "I", "J");

        BehaviorProcessor<List<String>> processor = BehaviorProcessor.create();
        FlowableList<String> list = FlowableList.diff(processor, false);
        TestSubscriber<Update<String>> test = list.updates().test();

        processor.onNext(list1);

        Update<String> firstUpdate = test.values().get(0);
        assertEquals(Collections.singletonList(Change.reloaded()), firstUpdate.changes);

        processor.onNext(list2);

        Update<String> secondUpdate = test.values().get(1);

        assertEquals(
                Arrays.asList("A", "B", "C", "D", "E", "F", "G", "H", "I", "J"),
                secondUpdate.list);

        assertEquals(
                Arrays.asList("A", "B", "C", "D", "E", "F", "G", "H", "I", "J"),
                TestTools.applyChanges(firstUpdate.list, secondUpdate.list, secondUpdate.changes));

        for (Change change : secondUpdate.changes) {
            assertNotEquals(change.type, Change.Type.Moved);
        }
    }
}
