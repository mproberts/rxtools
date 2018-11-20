package com.github.mproberts.rxtools.list;

import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class ConcatFlowableListTest
{
    @Test
    public void testSingleList()
    {
        List<FlowableList<Integer>> flowableLists = Arrays.asList(FlowableList.of(1, 2, 3));
        FlowableList<?> list = FlowableList.concat(flowableLists);
        TestSubscriber testSubscriber = new TestSubscriber();

        list.updates().subscribe(testSubscriber);

        testSubscriber.assertValueCount(1);

        List<Update> onNextEvents = testSubscriber.values();

        Update update = onNextEvents.get(0);

        assertEquals(Change.reloaded(), update.changes.get(0));
        assertEquals(Arrays.asList(1, 2, 3), update.list);
    }

    @Test
    public void testSingleConcatListInsertions()
    {
        SimpleFlowableList<Integer> a = new SimpleFlowableList<>();
        SimpleFlowableList<Integer> b = new SimpleFlowableList<>();
        SimpleFlowableList<Integer> c = new SimpleFlowableList<>();

        a.add(1);
        a.add(2);
        a.add(3);

        b.add(4);
        b.add(5);

        c.add(6);
        c.add(7);
        c.add(8);

        FlowableList<?> list = FlowableList.concat(Arrays.<FlowableList<Integer>>asList(a, b, c));
        TestSubscriber testSubscriber = new TestSubscriber();

        list.updates().subscribe(testSubscriber);

        b.add(9);
        c.add(1, 10);

        testSubscriber.assertValueCount(3);

        List<Update> onNextEvents = testSubscriber.values();

        Update reload = onNextEvents.get(0);
        Update insert9 = onNextEvents.get(1);
        Update insert10 = onNextEvents.get(2);

        assertEquals(Arrays.asList(Change.reloaded()), reload.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8), reload.list);

        assertEquals(Arrays.asList(Change.inserted(5)), insert9.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 9, 6, 7, 8), insert9.list);

        assertEquals(Arrays.asList(Change.inserted(7)), insert10.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 9, 6, 10, 7, 8), insert10.list);
    }

    @Test
    public void testHeterogeneousConcat()
    {
        SimpleFlowableList<Integer> a = new SimpleFlowableList<>();
        SimpleFlowableList<String> b = new SimpleFlowableList<>();
        SimpleFlowableList<Integer> c = new SimpleFlowableList<>();

        a.add(1);
        a.add(2);
        a.add(3);

        b.add("4");
        b.add("5");

        c.add(6);
        c.add(7);
        c.add(8);

        FlowableList list = FlowableList.concatGeneric(FlowableList.of(a, b, c));
        TestSubscriber testSubscriber = new TestSubscriber();

        list.updates().subscribe(testSubscriber);

        b.add("9");
        c.add(1, 10);

        testSubscriber.assertValueCount(3);

        List<Update> onNextEvents = testSubscriber.values();

        Update reload = onNextEvents.get(0);
        Update insert9 = onNextEvents.get(1);
        Update insert10 = onNextEvents.get(2);

        assertEquals(Arrays.asList(Change.reloaded()), reload.changes);
        assertEquals(Arrays.asList(1, 2, 3, "4", "5", 6, 7, 8), reload.list);

        assertEquals(Arrays.asList(Change.inserted(5)), insert9.changes);
        assertEquals(Arrays.asList(1, 2, 3, "4", "5", "9", 6, 7, 8), insert9.list);

        assertEquals(Arrays.asList(Change.inserted(7)), insert10.changes);
        assertEquals(Arrays.asList(1, 2, 3, "4", "5", "9", 6, 10, 7, 8), insert10.list);
    }

    @Test
    public void testRemovePropagation()
    {
        SimpleFlowableList<Integer> a = new SimpleFlowableList<>();
        SimpleFlowableList<Integer> b = new SimpleFlowableList<>();
        SimpleFlowableList<Integer> c = new SimpleFlowableList<>();

        a.add(1);
        a.add(2);

        b.add(3);

        c.add(4);

        FlowableList<?> list = FlowableList.concat(Arrays.<FlowableList<Integer>>asList(a, b, c));
        TestSubscriber testSubscriber = new TestSubscriber();

        list.updates().subscribe(testSubscriber);

        b.remove(0);
        a.remove(1);

        testSubscriber.assertValueCount(3);

        List<Update> onNextEvents = testSubscriber.values();

        Update reload = onNextEvents.get(0);
        Update remove3 = onNextEvents.get(1);
        Update remove2 = onNextEvents.get(2);

        assertEquals(Arrays.asList(Change.reloaded()), reload.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4), reload.list);

        assertEquals(Arrays.asList(Change.removed(2)), remove3.changes);
        assertEquals(Arrays.asList(1, 2, 4), remove3.list);

        assertEquals(Arrays.asList(Change.removed(1)), remove2.changes);
        assertEquals(Arrays.asList(1, 4), remove2.list);
    }

    @Test
    public void testMovePropagation()
    {
        SimpleFlowableList<Integer> a = new SimpleFlowableList<>();
        SimpleFlowableList<Integer> b = new SimpleFlowableList<>();
        SimpleFlowableList<Integer> c = new SimpleFlowableList<>();

        a.add(1);
        a.add(2);

        b.add(3);
        b.add(4);
        b.add(5);
        b.add(6);

        c.add(7);

        FlowableList<?> list = FlowableList.concat(Arrays.<FlowableList<Integer>>asList(a, b, c));
        TestSubscriber testSubscriber = new TestSubscriber();

        list.updates().subscribe(testSubscriber);

        b.move(0, 2);

        testSubscriber.assertValueCount(2);

        List<Update> onNextEvents = testSubscriber.values();

        Update reload = onNextEvents.get(0);
        Update move3 = onNextEvents.get(1);

        assertEquals(Arrays.asList(Change.reloaded()), reload.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7), reload.list);

        assertEquals(Arrays.asList(Change.moved(2, 4)), move3.changes);
        assertEquals(Arrays.asList(1, 2, 4, 5, 3, 6, 7), move3.list);
    }

    @Test
    public void testInsertAndRemoveListPropagation()
    {
        SimpleFlowableList<Integer> a = new SimpleFlowableList<>();
        SimpleFlowableList<Integer> b = new SimpleFlowableList<>();
        SimpleFlowableList<Integer> c = new SimpleFlowableList<>();
        SimpleFlowableList<Integer> d = new SimpleFlowableList<>();

        a.add(1);
        a.add(2);

        b.add(3);
        b.add(4);

        c.add(5);

        d.add(6);

        SimpleFlowableList<FlowableList<Integer>> combinedList = new SimpleFlowableList<>();

        combinedList.add(a);
        combinedList.add(b);
        combinedList.add(c);

        FlowableList<?> list = FlowableList.concat(combinedList);
        TestSubscriber testSubscriber = new TestSubscriber();

        list.updates().subscribe(testSubscriber);

        combinedList.add(1, d);
        combinedList.remove(b);

        List<Update> onNextEvents = testSubscriber.values();

        testSubscriber.assertValueCount(3);

        Update reload = onNextEvents.get(0);
        Update insertd = onNextEvents.get(1);
        Update removeb = onNextEvents.get(2);

        assertEquals(Arrays.asList(Change.reloaded()), reload.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), reload.list);

        assertEquals(Arrays.asList(Change.reloaded()), insertd.changes);
        assertEquals(Arrays.asList(1, 2, 6, 3, 4, 5), insertd.list);

        assertEquals(
                Arrays.asList(
                        Change.reloaded()),
                removeb.changes);
        assertEquals(Arrays.asList(1, 2, 6, 5), removeb.list);
    }

    @Test
    public void testMoveListPropagation()
    {
        SimpleFlowableList<Integer> a = new SimpleFlowableList<>();
        SimpleFlowableList<Integer> b = new SimpleFlowableList<>();
        SimpleFlowableList<Integer> c = new SimpleFlowableList<>();

        a.add(1);
        a.add(2);

        b.add(3);
        b.add(4);

        c.add(5);
        c.add(6);

        SimpleFlowableList<FlowableList<Integer>> combinedList = new SimpleFlowableList<>();

        combinedList.add(a);
        combinedList.add(b);
        combinedList.add(c);

        FlowableList<?> list = FlowableList.concat(combinedList);
        TestSubscriber testSubscriber = new TestSubscriber();

        list.updates().subscribe(testSubscriber);

        combinedList.move(1, 2);
        c.add(7);
        combinedList.move(2, 1);

        List<Update> onNextEvents = testSubscriber.values();

        testSubscriber.assertValueCount(4);

        Update reload = onNextEvents.get(0);
        Update move = onNextEvents.get(1);
        Update insert7 = onNextEvents.get(2);
        Update move2 = onNextEvents.get(3);

        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), reload.list);
        assertEquals(Arrays.asList(Change.reloaded()), reload.changes);

        assertEquals(Arrays.asList(1, 2, 5, 6, 3, 4), move.list);
        assertEquals(
                Arrays.asList(
                        Change.reloaded()),
                move.changes);

        assertEquals(Arrays.asList(1, 2, 5, 6, 7, 3, 4), new ArrayList<Integer>(insert7.list));
        assertEquals(Arrays.asList(Change.inserted(4)), insert7.changes);

        // move it back to the original order
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7), move2.list);
        assertEquals(
                Arrays.asList(
                        Change.reloaded()),
                move2.changes);
    }

    @Test
    public void testBindAndRebind()
    {
        SimpleFlowableList<Integer> a = new SimpleFlowableList<>();
        SimpleFlowableList<Integer> b = new SimpleFlowableList<>();

        a.add(1);
        a.add(2);

        b.add(3);
        b.add(4);

        FlowableList<?> list = FlowableList.concat(Arrays.<FlowableList<Integer>>asList(a, b));
        TestSubscriber testSubscriber1 = new TestSubscriber();
        TestSubscriber testSubscriber2 = new TestSubscriber();
        TestSubscriber testSubscriber3 = new TestSubscriber();

        list.updates().subscribe(testSubscriber1);

        b.add(5);

        list.updates().subscribe(testSubscriber2);

        b.add(6);

        list.updates().subscribe(testSubscriber3);

        testSubscriber1.dispose();

        b.add(7);

        List<Update> onNextEvents1 = testSubscriber1.values();
        List<Update> onNextEvents2 = testSubscriber2.values();
        List<Update> onNextEvents3 = testSubscriber3.values();

        // test subscriber 1
        testSubscriber1.assertValueCount(3);

        Update reload1 = onNextEvents1.get(0);
        Update insert5 = onNextEvents1.get(1);
        Update insert61 = onNextEvents1.get(2);

        assertEquals(Arrays.asList(Change.reloaded()), reload1.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4), reload1.list);

        assertEquals(Arrays.asList(Change.inserted(4)), insert5.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), insert5.list);

        assertEquals(Arrays.asList(Change.inserted(5)), insert61.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), insert61.list);

        // test subscriber 2
        testSubscriber2.assertValueCount(3);

        Update reload2 = onNextEvents2.get(0);
        Update insert62 = onNextEvents2.get(1);
        Update insert72 = onNextEvents2.get(2);

        assertEquals(Arrays.asList(Change.reloaded()), reload2.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), reload2.list);

        assertEquals(Arrays.asList(Change.inserted(5)), insert62.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), insert62.list);

        assertEquals(Arrays.asList(Change.inserted(6)), insert72.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7), insert72.list);

        // test subscriber 3
        testSubscriber3.assertValueCount(2);

        Update reload3 = onNextEvents3.get(0);
        Update insert73 = onNextEvents3.get(1);

        assertEquals(Arrays.asList(Change.reloaded()), reload3.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), reload3.list);

        assertEquals(Arrays.asList(Change.inserted(6)), insert73.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7), insert73.list);
    }

    @Test
    public void testUnbindAndRebind()
    {
        SimpleFlowableList<Integer> a = new SimpleFlowableList<>();
        SimpleFlowableList<Integer> b = new SimpleFlowableList<>();

        a.add(1);
        a.add(2);

        b.add(3);
        b.add(4);

        FlowableList<?> list = FlowableList.concat(Arrays.<FlowableList<Integer>>asList(a, b));
        TestSubscriber testSubscriber1 = new TestSubscriber();
        TestSubscriber testSubscriber2 = new TestSubscriber();

        list.updates().subscribe(testSubscriber1);

        b.add(5);
        b.add(6);

        testSubscriber1.dispose();
        list.updates().subscribe(testSubscriber2);

        b.add(7);

        List<Update> onNextEvents1 = testSubscriber1.values();
        List<Update> onNextEvents2 = testSubscriber2.values();

        // test subscriber 1
        testSubscriber1.assertValueCount(3);

        Update reload1 = onNextEvents1.get(0);
        Update insert5 = onNextEvents1.get(1);
        Update insert6 = onNextEvents1.get(2);

        assertEquals(Arrays.asList(Change.reloaded()), reload1.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4), reload1.list);

        assertEquals(Arrays.asList(Change.inserted(4)), insert5.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), insert5.list);

        assertEquals(Arrays.asList(Change.inserted(5)), insert6.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), insert6.list);

        // test subscriber 2
        testSubscriber2.assertValueCount(2);

        Update reload2 = onNextEvents2.get(0);
        Update insert72 = onNextEvents2.get(1);

        assertEquals(Arrays.asList(Change.reloaded()), reload2.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), reload2.list);

        assertEquals(Arrays.asList(Change.inserted(6)), insert72.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7), insert72.list);
    }

    @Test
    public void testConcatListEquals()
    {
        SimpleFlowableList<Integer> a = new SimpleFlowableList<>();
        SimpleFlowableList<Integer> b = new SimpleFlowableList<>();
        SimpleFlowableList<Integer> c = new SimpleFlowableList<>();

        a.add(1);
        a.add(2);
        a.add(3);

        b.add(4);
        b.add(5);

        c.add(6);
        c.add(7);
        c.add(8);

        FlowableList<?> list = FlowableList.concat(Arrays.<FlowableList<Integer>>asList(a, b, c));
        TestSubscriber testSubscriber = new TestSubscriber();

        list.updates().subscribe(testSubscriber);

        testSubscriber.assertValueCount(1);

        List<Update> onNextEvents = testSubscriber.values();

        Update reload = onNextEvents.get(0);

        assertEquals(reload.list, Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8));
        assertNotEquals(reload.list, Arrays.asList(1, 2, 3, 4, 5, 6, 7));
        assertNotEquals(reload.list, Arrays.asList(1, 2, 3, 4, 5, 8, 7, 6));
    }
}
