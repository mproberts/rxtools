package com.github.mproberts.rxtools.list;

import io.reactivex.Flowable;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class AttachDetachListTest
{
    @Test
    public void testBasicAdd()
    {
        final AtomicInteger attachCount = new AtomicInteger();
        final AtomicInteger detachCount = new AtomicInteger();

        SimpleFlowableList list = new SimpleFlowableList() {
            @Override
            protected void onAttached() {
                super.onAttached();

                attachCount.incrementAndGet();
            }

            @Override
            protected void onDetached() {
                super.onDetached();

                detachCount.incrementAndGet();
            }
        };

        TestSubscriber testSubscriber1 = new TestSubscriber();
        TestSubscriber testSubscriber2 = new TestSubscriber();
        TestSubscriber testSubscriber3 = new TestSubscriber();

        list.add("test 1");
        list.add("test 2");
        list.add("test 3");

        Flowable updates = list.updates();

        assertEquals(0, attachCount.get());
        assertEquals(0, detachCount.get());

        updates.subscribe(testSubscriber1);

        assertEquals(1, attachCount.get());
        assertEquals(0, detachCount.get());

        updates.subscribe(testSubscriber2);

        assertEquals(1, attachCount.get());
        assertEquals(0, detachCount.get());

        testSubscriber1.dispose();

        assertEquals(1, attachCount.get());
        assertEquals(0, detachCount.get());

        testSubscriber2.dispose();

        assertEquals(1, attachCount.get());
        assertEquals(1, detachCount.get());

        updates.subscribe(testSubscriber3);

        assertEquals(2, attachCount.get());
        assertEquals(1, detachCount.get());

        testSubscriber3.dispose();

        assertEquals(2, attachCount.get());
        assertEquals(2, detachCount.get());
    }
    @Test
    public void testResetOnDispose()
    {
        final AtomicInteger attachCount = new AtomicInteger();

        SimpleFlowableList list = new SimpleFlowableList() {
            @Override
            protected void onAttached() {
                super.onAttached();

                add(attachCount.incrementAndGet());
            }

            @Override
            protected void onDetached() {
                super.onDetached();

                clear();
            }
        };

        TestSubscriber<Update> testSubscriber1 = new TestSubscriber<>();
        TestSubscriber<Update> testSubscriber2 = new TestSubscriber<>();
        TestSubscriber<Update> testSubscriber3 = new TestSubscriber<>();

        Flowable updates = list.updates();

        updates.subscribe(testSubscriber1);

        testSubscriber1.assertValue(new Update<>(Arrays.asList(1), Change.reloaded()));

        updates.subscribe(testSubscriber2);

        testSubscriber2.assertValue(new Update<>(Arrays.asList(1), Change.reloaded()));

        testSubscriber1.dispose();
        testSubscriber2.dispose();

        updates.subscribe(testSubscriber3);

        testSubscriber3.assertValue(new Update<>(Arrays.asList(2), Change.reloaded()));
    }
}
