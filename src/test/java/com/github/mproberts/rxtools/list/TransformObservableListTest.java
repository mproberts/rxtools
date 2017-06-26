package com.github.mproberts.rxtools.list;

import com.github.mproberts.rxtools.SubjectMap;
import org.junit.Test;
import rx.Observable;
import rx.functions.Func1;
import rx.observers.TestSubscriber;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class TransformObservableListTest
{
    @Test
    public void testBasicTransform()
    {
        TestSubscriber<ObservableList.Update<Integer>> testSubscriber = new TestSubscriber<>();

        SimpleObservableList<Integer> list = new SimpleObservableList<>(Arrays.asList(1, 2, 3));
        ObservableList<Integer> transformedList = ObservableLists.transform(list, new Func1<Integer, Integer>() {
            @Override
            public Integer call(Integer integer) {
                return integer + 12;
            }
        });

        transformedList.updates().subscribe(testSubscriber);

        testSubscriber.assertValueCount(1);

        List<ObservableList.Update<Integer>> onNextEvents = testSubscriber.getOnNextEvents();

        assertEquals(Arrays.asList(ObservableList.Change.reloaded()), onNextEvents.get(0).changes);
        assertEquals(Arrays.asList(13, 14, 15), onNextEvents.get(0).list);
    }

    @Test
    public void testSubjectMapTransform()
    {
        TestSubscriber<ObservableList.Update<Observable<String>>> testSubscriber = new TestSubscriber<>();

        TestSubscriber<String> subscriber0 = new TestSubscriber<>();
        TestSubscriber<String> subscriber1 = new TestSubscriber<>();
        TestSubscriber<String> subscriber2 = new TestSubscriber<>();

        SubjectMap<Integer, String> subjectMap = new SubjectMap<>();
        SimpleObservableList<Integer> list = new SimpleObservableList<>(Arrays.asList(1, 2, 3));
        ObservableList<Observable<String>> transformedList = ObservableLists.transform(list, subjectMap);

        transformedList.updates().subscribe(testSubscriber);

        testSubscriber.assertValueCount(1);

        List<ObservableList.Update<Observable<String>>> onNextEvents = testSubscriber.getOnNextEvents();
        ObservableList.Update<Observable<String>> update = onNextEvents.get(0);

        Observable<String> value0 = update.list.get(0);
        Observable<String> value1 = update.list.get(1);
        Observable<String> value2 = update.list.get(2);

        value0.subscribe(subscriber0);
        value1.subscribe(subscriber1);
        value2.subscribe(subscriber2);

        subjectMap.onNext(1, "A");
        subjectMap.onNext(2, "B");
        subjectMap.onNext(3, "C");

        assertEquals(Arrays.asList(ObservableList.Change.reloaded()), update.changes);
        assertEquals("A", subscriber0.getOnNextEvents().get(0));
        assertEquals("B", subscriber1.getOnNextEvents().get(0));
        assertEquals("C", subscriber2.getOnNextEvents().get(0));
    }
}
