package com.github.mproberts.rxtools.list;

import org.junit.Test;

import java.util.Arrays;
import java.util.function.Predicate;

import io.reactivex.subscribers.TestSubscriber;

import static org.junit.Assert.assertEquals;

public class SimpleFlowableListTest {

    @Test
    public void testRemoveWithPredicate()
    {
        SimpleFlowableList<String> list = new SimpleFlowableList();
        list.add("str1");
        list.add("str2");
        list.add("str3");
        list.add("str4");
        list.add("str3");

        TestSubscriber<Update<String>> testSubscriber = new TestSubscriber<>();
        list.updates().skip(1).subscribe(testSubscriber);

        // Remove once
        list.remove(new Predicate<String>() {
            @Override
            public boolean test(String s) {
                return s.equals("str3");
            }
        });
        assertEquals(1, testSubscriber.valueCount());
        assertEquals(Arrays.asList(Change.removed(2)), testSubscriber.values().get(0).changes);
        assertEquals(Arrays.asList("str1", "str2", "str4", "str3"), testSubscriber.values().get(0).list);

        // Remove again
        list.remove(new Predicate<String>() {
            @Override
            public boolean test(String s) {
                return s.equals("str3");
            }
        });
        assertEquals(2, testSubscriber.valueCount());
        assertEquals(Arrays.asList(Change.removed(3)), testSubscriber.values().get(1).changes);
        assertEquals(Arrays.asList("str1", "str2", "str4"), testSubscriber.values().get(1).list);

        // Remove again again (no value this time)
        list.remove(new Predicate<String>() {
            @Override
            public boolean test(String s) {
                return s.equals("str3");
            }
        });
        assertEquals(2, testSubscriber.valueCount());
    }

}
