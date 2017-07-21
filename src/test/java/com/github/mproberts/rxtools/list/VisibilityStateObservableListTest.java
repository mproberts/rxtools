package com.github.mproberts.rxtools.list;

import org.junit.Test;
import rx.Observable;
import rx.observers.TestSubscriber;
import rx.subjects.BehaviorSubject;
import rx.subjects.Subject;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class VisibilityStateObservableListTest
{
    class VisibleItem<T> implements VisibilityState<T>
    {
        public final T value;
        public final Subject<Boolean, Boolean> visibility;

        VisibleItem(T value)
        {
            this(value, false);
        }

        VisibleItem(T value, boolean initialVisibility)
        {
            this.value = value;
            this.visibility = BehaviorSubject.create(initialVisibility);
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
        public Observable<Boolean> isVisible()
        {
            return visibility;
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

        List<ObservableList.Update> onNextEvents = testSubscriber.getOnNextEvents();
        testSubscriber.assertValueCount(1);

        ObservableList.Update update = onNextEvents.get(0);

        assertEquals(Arrays.asList(ObservableList.Change.reloaded()), update.changes);
        assertEquals(Arrays.asList(1, 2, 3, 4), update.list);
    }

    @Test
    public void testBasicHideAndShowVisibility()
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

        List<ObservableList.Update> onNextEvents = testSubscriber.getOnNextEvents();
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
}
