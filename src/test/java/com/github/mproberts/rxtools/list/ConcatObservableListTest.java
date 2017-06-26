package com.github.mproberts.rxtools.list;

import org.junit.Test;
import rx.observers.TestSubscriber;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class ConcatObservableListTest
{
    @Test
    public void testSingleList()
    {
        List<ObservableList<Integer>> observableLists = Arrays.asList(ObservableLists.singletonList(1, 2, 3));
        ObservableList<?> list = ObservableLists.concat(observableLists);
        TestSubscriber testSubscriber = new TestSubscriber();

        list.updates().subscribe(testSubscriber);

        testSubscriber.assertValueCount(1);

        List<ObservableList.Update> onNextEvents = testSubscriber.getOnNextEvents();

        ObservableList.Update update = onNextEvents.get(0);

        assertEquals(ObservableList.Change.reloaded(), update.changes.get(0));
        assertEquals(Arrays.asList(1, 2, 3), update.list);
    }

    @Test
    public void testSingleConcatListInsertions()
    {
        SimpleObservableList<Integer> a = new SimpleObservableList<>();
        SimpleObservableList<Integer> b = new SimpleObservableList<>();
        SimpleObservableList<Integer> c = new SimpleObservableList<>();

        a.add(1);
        a.add(2);
        a.add(3);

        b.add(4);
        b.add(5);

        c.add(6);
        c.add(7);
        c.add(8);

        ObservableList<?> list = ObservableLists.concat(Arrays.<ObservableList<Integer>>asList(a, b, c));
        TestSubscriber testSubscriber = new TestSubscriber();

        list.updates().subscribe(testSubscriber);

        b.add(9);
        c.add(1, 10);

        testSubscriber.assertValueCount(3);

        List<ObservableList.Update> onNextEvents = testSubscriber.getOnNextEvents();

        ObservableList.Update reload = onNextEvents.get(0);
        ObservableList.Update insert9 = onNextEvents.get(1);
        ObservableList.Update insert10 = onNextEvents.get(2);

        assertEquals(Arrays.asList(ObservableList.Change.reloaded()), reload.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8), reload.list);

        assertEquals(Arrays.asList(ObservableList.Change.inserted(5)), insert9.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 9, 6, 7, 8), insert9.list);

        assertEquals(Arrays.asList(ObservableList.Change.inserted(7)), insert10.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 9, 6, 10, 7, 8), insert10.list);
    }

    @Test
    public void testHeterogeneousConcat()
    {
        SimpleObservableList<Integer> a = new SimpleObservableList<>();
        SimpleObservableList<String> b = new SimpleObservableList<>();
        SimpleObservableList<Integer> c = new SimpleObservableList<>();

        a.add(1);
        a.add(2);
        a.add(3);

        b.add("4");
        b.add("5");

        c.add(6);
        c.add(7);
        c.add(8);

        ObservableList list = ObservableLists.concatGeneric(ObservableLists.singletonList(a, b, c));
        TestSubscriber testSubscriber = new TestSubscriber();

        list.updates().subscribe(testSubscriber);

        b.add("9");
        c.add(1, 10);

        testSubscriber.assertValueCount(3);

        List<ObservableList.Update> onNextEvents = testSubscriber.getOnNextEvents();

        ObservableList.Update reload = onNextEvents.get(0);
        ObservableList.Update insert9 = onNextEvents.get(1);
        ObservableList.Update insert10 = onNextEvents.get(2);

        assertEquals(Arrays.asList(ObservableList.Change.reloaded()), reload.changes);
        assertEquals(Arrays.asList(1, 2, 3, "4", "5", 6, 7, 8), reload.list);

        assertEquals(Arrays.asList(ObservableList.Change.inserted(5)), insert9.changes);
        assertEquals(Arrays.asList(1, 2, 3, "4", "5", "9", 6, 7, 8), insert9.list);

        assertEquals(Arrays.asList(ObservableList.Change.inserted(7)), insert10.changes);
        assertEquals(Arrays.asList(1, 2, 3, "4", "5", "9", 6, 10, 7, 8), insert10.list);
    }

    @Test
    public void testRemovePropagation()
    {
        SimpleObservableList<Integer> a = new SimpleObservableList<>();
        SimpleObservableList<Integer> b = new SimpleObservableList<>();
        SimpleObservableList<Integer> c = new SimpleObservableList<>();

        a.add(1);
        a.add(2);

        b.add(3);

        c.add(4);

        ObservableList<?> list = ObservableLists.concat(Arrays.<ObservableList<Integer>>asList(a, b, c));
        TestSubscriber testSubscriber = new TestSubscriber();

        list.updates().subscribe(testSubscriber);

        b.remove(0);
        a.remove(1);

        testSubscriber.assertValueCount(3);

        List<ObservableList.Update> onNextEvents = testSubscriber.getOnNextEvents();

        ObservableList.Update reload = onNextEvents.get(0);
        ObservableList.Update remove3 = onNextEvents.get(1);
        ObservableList.Update remove2 = onNextEvents.get(2);

        assertEquals(Arrays.asList(ObservableList.Change.reloaded()), reload.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4), reload.list);

        assertEquals(Arrays.asList(ObservableList.Change.removed(2)), remove3.changes);
        assertEquals(Arrays.asList(1, 2, 4), remove3.list);

        assertEquals(Arrays.asList(ObservableList.Change.removed(1)), remove2.changes);
        assertEquals(Arrays.asList(1, 4), remove2.list);
    }

    @Test
    public void testMovePropagation()
    {
        SimpleObservableList<Integer> a = new SimpleObservableList<>();
        SimpleObservableList<Integer> b = new SimpleObservableList<>();
        SimpleObservableList<Integer> c = new SimpleObservableList<>();

        a.add(1);
        a.add(2);

        b.add(3);
        b.add(4);
        b.add(5);
        b.add(6);

        c.add(7);

        ObservableList<?> list = ObservableLists.concat(Arrays.<ObservableList<Integer>>asList(a, b, c));
        TestSubscriber testSubscriber = new TestSubscriber();

        list.updates().subscribe(testSubscriber);

        b.move(0, 2);

        testSubscriber.assertValueCount(2);

        List<ObservableList.Update> onNextEvents = testSubscriber.getOnNextEvents();

        ObservableList.Update reload = onNextEvents.get(0);
        ObservableList.Update move3 = onNextEvents.get(1);

        assertEquals(Arrays.asList(ObservableList.Change.reloaded()), reload.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7), reload.list);

        assertEquals(Arrays.asList(ObservableList.Change.moved(2, 4)), move3.changes);
        assertEquals(Arrays.asList(1, 2, 4, 5, 3, 6, 7), move3.list);
    }

    @Test
    public void testInsertAndRemoveListPropagation()
    {
        SimpleObservableList<Integer> a = new SimpleObservableList<>();
        SimpleObservableList<Integer> b = new SimpleObservableList<>();
        SimpleObservableList<Integer> c = new SimpleObservableList<>();
        SimpleObservableList<Integer> d = new SimpleObservableList<>();

        a.add(1);
        a.add(2);

        b.add(3);
        b.add(4);

        c.add(5);

        d.add(6);

        SimpleObservableList<ObservableList<Integer>> combinedList = new SimpleObservableList<>();

        combinedList.add(a);
        combinedList.add(b);
        combinedList.add(c);

        ObservableList<?> list = ObservableLists.concat(combinedList);
        TestSubscriber testSubscriber = new TestSubscriber();

        list.updates().subscribe(testSubscriber);

        combinedList.add(1, d);
        combinedList.remove(b);

        List<ObservableList.Update> onNextEvents = testSubscriber.getOnNextEvents();

        testSubscriber.assertValueCount(3);

        ObservableList.Update reload = onNextEvents.get(0);
        ObservableList.Update insertd = onNextEvents.get(1);
        ObservableList.Update removeb = onNextEvents.get(2);

        assertEquals(Arrays.asList(ObservableList.Change.reloaded()), reload.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), reload.list);

        assertEquals(Arrays.asList(ObservableList.Change.inserted(2)), insertd.changes);
        assertEquals(Arrays.asList(1, 2, 6, 3, 4, 5), insertd.list);

        assertEquals(
                Arrays.asList(
                        ObservableList.Change.removed(3),
                        ObservableList.Change.removed(4)),
                removeb.changes);
        assertEquals(Arrays.asList(1, 2, 6, 5), removeb.list);
    }

    @Test
    public void testMoveListPropagation()
    {
        SimpleObservableList<Integer> a = new SimpleObservableList<>();
        SimpleObservableList<Integer> b = new SimpleObservableList<>();
        SimpleObservableList<Integer> c = new SimpleObservableList<>();

        a.add(1);
        a.add(2);

        b.add(3);
        b.add(4);

        c.add(5);
        c.add(6);

        SimpleObservableList<ObservableList<Integer>> combinedList = new SimpleObservableList<>();

        combinedList.add(a);
        combinedList.add(b);
        combinedList.add(c);

        ObservableList<?> list = ObservableLists.concat(combinedList);
        TestSubscriber testSubscriber = new TestSubscriber();

        list.updates().subscribe(testSubscriber);

        combinedList.move(1, 2);
        c.add(7);
        combinedList.move(2, 1);

        List<ObservableList.Update> onNextEvents = testSubscriber.getOnNextEvents();

        testSubscriber.assertValueCount(4);

        ObservableList.Update reload = onNextEvents.get(0);
        ObservableList.Update move = onNextEvents.get(1);
        ObservableList.Update insert7 = onNextEvents.get(2);
        ObservableList.Update move2 = onNextEvents.get(3);

        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), reload.list);
        assertEquals(Arrays.asList(ObservableList.Change.reloaded()), reload.changes);

        assertEquals(Arrays.asList(1, 2, 5, 6, 3, 4), move.list);
        assertEquals(
                Arrays.asList(
                        ObservableList.Change.moved(2, 4),
                        ObservableList.Change.moved(3, 5)),
                move.changes);

        assertEquals(Arrays.asList(1, 2, 5, 6, 7, 3, 4), insert7.list);
        assertEquals(Arrays.asList(ObservableList.Change.inserted(4)), insert7.changes);

        // move it back to the original order
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7), move2.list);
        assertEquals(
                Arrays.asList(
                        ObservableList.Change.moved(5, 2),
                        ObservableList.Change.moved(6, 3)),
                move2.changes);
    }

    @Test
    public void testBindAndRebind()
    {
        SimpleObservableList<Integer> a = new SimpleObservableList<>();
        SimpleObservableList<Integer> b = new SimpleObservableList<>();

        a.add(1);
        a.add(2);

        b.add(3);
        b.add(4);

        ObservableList<?> list = ObservableLists.concat(Arrays.<ObservableList<Integer>>asList(a, b));
        TestSubscriber testSubscriber1 = new TestSubscriber();
        TestSubscriber testSubscriber2 = new TestSubscriber();
        TestSubscriber testSubscriber3 = new TestSubscriber();

        list.updates().subscribe(testSubscriber1);

        b.add(5);

        list.updates().subscribe(testSubscriber2);

        b.add(6);

        list.updates().subscribe(testSubscriber3);

        testSubscriber1.unsubscribe();

        b.add(7);

        List<ObservableList.Update> onNextEvents1 = testSubscriber1.getOnNextEvents();
        List<ObservableList.Update> onNextEvents2 = testSubscriber2.getOnNextEvents();
        List<ObservableList.Update> onNextEvents3 = testSubscriber3.getOnNextEvents();

        // test subscriber 1
        testSubscriber1.assertValueCount(3);

        ObservableList.Update reload1 = onNextEvents1.get(0);
        ObservableList.Update insert5 = onNextEvents1.get(1);
        ObservableList.Update insert61 = onNextEvents1.get(2);

        assertEquals(Arrays.asList(ObservableList.Change.reloaded()), reload1.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4), reload1.list);

        assertEquals(Arrays.asList(ObservableList.Change.inserted(4)), insert5.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), insert5.list);

        assertEquals(Arrays.asList(ObservableList.Change.inserted(5)), insert61.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), insert61.list);

        // test subscriber 2
        testSubscriber2.assertValueCount(3);

        ObservableList.Update reload2 = onNextEvents2.get(0);
        ObservableList.Update insert62 = onNextEvents2.get(1);
        ObservableList.Update insert72 = onNextEvents2.get(2);

        assertEquals(Arrays.asList(ObservableList.Change.reloaded()), reload2.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), reload2.list);

        assertEquals(Arrays.asList(ObservableList.Change.inserted(5)), insert62.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), insert62.list);

        assertEquals(Arrays.asList(ObservableList.Change.inserted(6)), insert72.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7), insert72.list);

        // test subscriber 3
        testSubscriber3.assertValueCount(2);

        ObservableList.Update reload3 = onNextEvents3.get(0);
        ObservableList.Update insert73 = onNextEvents3.get(1);

        assertEquals(Arrays.asList(ObservableList.Change.reloaded()), reload3.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), reload3.list);

        assertEquals(Arrays.asList(ObservableList.Change.inserted(6)), insert73.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7), insert73.list);
    }

    @Test
    public void testUnbindAndRebind()
    {
        SimpleObservableList<Integer> a = new SimpleObservableList<>();
        SimpleObservableList<Integer> b = new SimpleObservableList<>();

        a.add(1);
        a.add(2);

        b.add(3);
        b.add(4);

        ObservableList<?> list = ObservableLists.concat(Arrays.<ObservableList<Integer>>asList(a, b));
        TestSubscriber testSubscriber1 = new TestSubscriber();
        TestSubscriber testSubscriber2 = new TestSubscriber();

        list.updates().subscribe(testSubscriber1);

        b.add(5);
        b.add(6);

        testSubscriber1.unsubscribe();
        list.updates().subscribe(testSubscriber2);

        b.add(7);

        List<ObservableList.Update> onNextEvents1 = testSubscriber1.getOnNextEvents();
        List<ObservableList.Update> onNextEvents2 = testSubscriber2.getOnNextEvents();

        // test subscriber 1
        testSubscriber1.assertValueCount(3);

        ObservableList.Update reload1 = onNextEvents1.get(0);
        ObservableList.Update insert5 = onNextEvents1.get(1);
        ObservableList.Update insert6 = onNextEvents1.get(2);

        assertEquals(Arrays.asList(ObservableList.Change.reloaded()), reload1.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4), reload1.list);

        assertEquals(Arrays.asList(ObservableList.Change.inserted(4)), insert5.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), insert5.list);

        assertEquals(Arrays.asList(ObservableList.Change.inserted(5)), insert6.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), insert6.list);

        // test subscriber 2
        testSubscriber2.assertValueCount(2);

        ObservableList.Update reload2 = onNextEvents2.get(0);
        ObservableList.Update insert72 = onNextEvents2.get(1);

        assertEquals(Arrays.asList(ObservableList.Change.reloaded()), reload2.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), reload2.list);

        assertEquals(Arrays.asList(ObservableList.Change.inserted(6)), insert72.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7), insert72.list);
    }

    @Test
    public void testConcatListEquals()
    {
        SimpleObservableList<Integer> a = new SimpleObservableList<>();
        SimpleObservableList<Integer> b = new SimpleObservableList<>();
        SimpleObservableList<Integer> c = new SimpleObservableList<>();

        a.add(1);
        a.add(2);
        a.add(3);

        b.add(4);
        b.add(5);

        c.add(6);
        c.add(7);
        c.add(8);

        ObservableList<?> list = ObservableLists.concat(Arrays.<ObservableList<Integer>>asList(a, b, c));
        TestSubscriber testSubscriber = new TestSubscriber();

        list.updates().subscribe(testSubscriber);

        testSubscriber.assertValueCount(1);

        List<ObservableList.Update> onNextEvents = testSubscriber.getOnNextEvents();

        ObservableList.Update reload = onNextEvents.get(0);

        assertEquals(reload.list, Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8));
        assertNotEquals(reload.list, Arrays.asList(1, 2, 3, 4, 5, 6, 7));
        assertNotEquals(reload.list, Arrays.asList(1, 2, 3, 4, 5, 8, 7, 6));
    }
}
