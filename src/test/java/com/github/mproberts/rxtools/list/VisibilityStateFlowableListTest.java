package com.github.mproberts.rxtools.list;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class VisibilityStateObservableListTest
{
    class VisibleItem<T> implements VisibilityState<T>
    {
        public final T value;
        public final Subject<Boolean> visibility;

        VisibleItem(T value)
        {
            this(value, false);
        }

        VisibleItem(T value, boolean initialVisibility)
        {
            this.value = value;
            this.visibility = BehaviorSubject.create();
            this.visibility.onNext(initialVisibility);
        }

        @Override
        public T get()
        {
            return value;
        }

        public void setIsVisible(boolean isVisible)
        {
            visibility.onNext(isVisible);
        }

        @Override
        public Flowable<Boolean> isVisible()
        {
            return visibility.toFlowable(BackpressureStrategy.BUFFER);
        }
    }

    @Test
    public void testBasicVisibility()
    {
        VisibleItem<Integer> item1 = new VisibleItem<>(1, true);
        VisibleItem<Integer> item2 = new VisibleItem<>(2, true);
        VisibleItem<Integer> item3 = new VisibleItem<>(3, true);
        VisibleItem<Integer> item4 = new VisibleItem<>(4, true);

        SimpleObservableList<VisibleItem<Integer>> simpleList = new SimpleObservableList<>();
        ObservableList<Integer> list = ObservableLists.collapseVisibility(simpleList);
        TestSubscriber testSubscriber = new TestSubscriber();

        simpleList.add(item1);
        simpleList.add(item2);
        simpleList.add(item3);
        simpleList.add(item4);

        list.updates().subscribe(testSubscriber);

        List<ObservableList.Update> onNextEvents = testSubscriber.values();
        testSubscriber.assertValueCount(1);

        ObservableList.Update update = onNextEvents.get(0);

        assertEquals(Arrays.asList(ObservableList.Change.reloaded()), update.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4), update.list);
    }

    @Test
    public void testBasicHideVisibility()
    {
        VisibleItem<Integer> item1 = new VisibleItem<>(1, true);
        VisibleItem<Integer> item2 = new VisibleItem<>(2, true);
        VisibleItem<Integer> item3 = new VisibleItem<>(3, true);
        VisibleItem<Integer> item4 = new VisibleItem<>(4, true);
        VisibleItem<Integer> item5 = new VisibleItem<>(5, true);

        SimpleObservableList<VisibleItem<Integer>> simpleList = new SimpleObservableList<>();
        ObservableList<Integer> list = ObservableLists.collapseVisibility(simpleList);
        TestSubscriber testSubscriber = new TestSubscriber();

        simpleList.add(item1);
        simpleList.add(item2);
        simpleList.add(item3);
        simpleList.add(item4);
        simpleList.add(item5);

        list.updates().subscribe(testSubscriber);

        item2.setIsVisible(false);
        item3.setIsVisible(false);
        item5.setIsVisible(false);

        List<ObservableList.Update> onNextEvents = testSubscriber.values();
        testSubscriber.assertValueCount(4);

        ObservableList.Update update1 = onNextEvents.get(0);
        ObservableList.Update update2 = onNextEvents.get(1);
        ObservableList.Update update3 = onNextEvents.get(2);
        ObservableList.Update update4 = onNextEvents.get(3);

        assertEquals(Arrays.asList(ObservableList.Change.reloaded()), update1.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), update1.list);

        assertEquals(Arrays.asList(ObservableList.Change.removed(1)), update2.changes);
        assertEquals(Arrays.asList(1, 3, 4, 5), update2.list);

        assertEquals(Arrays.asList(ObservableList.Change.removed(1)), update3.changes);
        assertEquals(Arrays.asList(1, 4, 5), update3.list);

        assertEquals(Arrays.asList(ObservableList.Change.removed(2)), update4.changes);
        assertEquals(Arrays.asList(1, 4), update4.list);
    }

    @Test
    public void testBasicHideAndShowVisibility()
    {
        VisibleItem<Integer> item1 = new VisibleItem<>(1, true);
        VisibleItem<Integer> item2 = new VisibleItem<>(2, true);
        VisibleItem<Integer> item3 = new VisibleItem<>(3, true);

        SimpleObservableList<VisibleItem<Integer>> simpleList = new SimpleObservableList<>();
        ObservableList<Integer> list = ObservableLists.collapseVisibility(simpleList);
        TestSubscriber testSubscriber = new TestSubscriber();

        simpleList.add(item1);
        simpleList.add(item2);
        simpleList.add(item3);

        list.updates().subscribe(testSubscriber);

        item2.setIsVisible(false);
        item2.setIsVisible(true);
        item3.setIsVisible(false);

        List<ObservableList.Update> onNextEvents = testSubscriber.values();
        testSubscriber.assertValueCount(4);

        ObservableList.Update update1 = onNextEvents.get(0);
        ObservableList.Update update2 = onNextEvents.get(1);
        ObservableList.Update update3 = onNextEvents.get(2);
        ObservableList.Update update4 = onNextEvents.get(3);

        assertEquals(Arrays.asList(ObservableList.Change.reloaded()), update1.changes);
        assertEquals(Arrays.asList(1, 2, 3), update1.list);

        assertEquals(Arrays.asList(ObservableList.Change.removed(1)), update2.changes);
        assertEquals(Arrays.asList(1, 3), update2.list);

        assertEquals(Arrays.asList(ObservableList.Change.inserted(1)), update3.changes);
        assertEquals(Arrays.asList(1, 2, 3), update3.list);

        assertEquals(Arrays.asList(ObservableList.Change.removed(2)), update4.changes);
        assertEquals(Arrays.asList(1, 2), update4.list);
    }

    @Test
    public void testItemRemoval()
    {
        VisibleItem<Integer> item1 = new VisibleItem<>(1, true);
        VisibleItem<Integer> item2 = new VisibleItem<>(2, true);
        VisibleItem<Integer> item3 = new VisibleItem<>(3, true);

        SimpleObservableList<VisibleItem<Integer>> simpleList = new SimpleObservableList<>();
        ObservableList<Integer> list = ObservableLists.collapseVisibility(simpleList);
        TestSubscriber testSubscriber = new TestSubscriber();

        simpleList.add(item1);
        simpleList.add(item2);
        simpleList.add(item3);

        list.updates().subscribe(testSubscriber);

        simpleList.remove(1);

        List<ObservableList.Update> onNextEvents = testSubscriber.values();
        testSubscriber.assertValueCount(2);

        ObservableList.Update update1 = onNextEvents.get(0);
        ObservableList.Update update2 = onNextEvents.get(1);

        assertEquals(Arrays.asList(ObservableList.Change.reloaded()), update1.changes);
        assertEquals(Arrays.asList(1, 2, 3), update1.list);

        assertEquals(Arrays.asList(ObservableList.Change.removed(1)), update2.changes);
        assertEquals(Arrays.asList(1, 3), update2.list);
    }

    @Test
    public void testItemAddition()
    {
        VisibleItem<Integer> item1 = new VisibleItem<>(1, true);
        VisibleItem<Integer> item2 = new VisibleItem<>(2, true);
        VisibleItem<Integer> item3 = new VisibleItem<>(3, true);
        VisibleItem<Integer> hidden1 = new VisibleItem<>(4, false);

        SimpleObservableList<VisibleItem<Integer>> simpleList = new SimpleObservableList<>();
        ObservableList<Integer> list = ObservableLists.collapseVisibility(simpleList);
        TestSubscriber testSubscriber = new TestSubscriber();

        simpleList.add(item3);

        list.updates().subscribe(testSubscriber);

        simpleList.add(0, item1);
        simpleList.add(1, item2);
        simpleList.add(1, hidden1);

        List<ObservableList.Update> onNextEvents = testSubscriber.values();
        testSubscriber.assertValueCount(3);

        ObservableList.Update update1 = onNextEvents.get(0);
        ObservableList.Update update2 = onNextEvents.get(1);
        ObservableList.Update update3 = onNextEvents.get(2);

        assertEquals(Arrays.asList(ObservableList.Change.reloaded()), update1.changes);
        assertEquals(Arrays.asList(3), update1.list);

        assertEquals(Arrays.asList(ObservableList.Change.inserted(0)), update2.changes);
        assertEquals(Arrays.asList(1, 3), update2.list);

        assertEquals(Arrays.asList(ObservableList.Change.inserted(1)), update3.changes);
        assertEquals(Arrays.asList(1, 2, 3), update3.list);
    }

    @Test
    public void testItemMove()
    {
        VisibleItem<Integer> item1 = new VisibleItem<>(1, true);
        VisibleItem<Integer> item2 = new VisibleItem<>(2, true);
        VisibleItem<Integer> item3 = new VisibleItem<>(3, true);
        VisibleItem<Integer> item4 = new VisibleItem<>(4, true);

        SimpleObservableList<VisibleItem<Integer>> simpleList = new SimpleObservableList<>();
        ObservableList<Integer> list = ObservableLists.collapseVisibility(simpleList);
        TestSubscriber testSubscriber = new TestSubscriber();

        simpleList.add(item1);
        simpleList.add(item2);
        simpleList.add(item3);
        simpleList.add(item4);

        list.updates().subscribe(testSubscriber);

        simpleList.move(0, 1);
        simpleList.move(2, 0);
        simpleList.move(0, 3);

        List<ObservableList.Update> onNextEvents = testSubscriber.values();
        testSubscriber.assertValueCount(4);

        ObservableList.Update update1 = onNextEvents.get(0);
        ObservableList.Update update2 = onNextEvents.get(1);
        ObservableList.Update update3 = onNextEvents.get(2);
        ObservableList.Update update4 = onNextEvents.get(3);

        assertEquals(Arrays.asList(ObservableList.Change.reloaded()), update1.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4), update1.list);

        assertEquals(Arrays.asList(ObservableList.Change.moved(0, 1)), update2.changes);
        assertEquals(Arrays.asList(2, 1, 3, 4), update2.list);

        assertEquals(Arrays.asList(ObservableList.Change.moved(2, 0)), update3.changes);
        assertEquals(Arrays.asList(3, 2, 1, 4), update3.list);

        assertEquals(Arrays.asList(ObservableList.Change.moved(0, 3)), update4.changes);
        assertEquals(Arrays.asList(2, 1, 4, 3), update4.list);
    }
}
