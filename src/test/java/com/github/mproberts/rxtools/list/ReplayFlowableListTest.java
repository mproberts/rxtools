package com.github.mproberts.rxtools.list;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.subscribers.TestSubscriber;

public class ReplayFlowableListTest {

    @Test
    public void testShareAndReplayForSecondSubscriber() {
        final List<String> testList = new ArrayList<String>() {{
            add("A");
            add("B");
            add("C");
        }};

        final AtomicInteger subCount = new AtomicInteger(0);

        final Flowable<Update<String>> source = Flowable.create(new FlowableOnSubscribe<Update<String>>() {
            @Override
            public void subscribe(FlowableEmitter<Update<String>> flowableEmitter) throws Exception {
                subCount.incrementAndGet();
                flowableEmitter.onNext(new Update<String>(
                    testList,
                    Change.reloaded()
                ));
            }
        }, BackpressureStrategy.BUFFER);

        FlowableList<String> list = new FlowableList<String>() {
            @Override
            public Flowable<Update<String>> updates() {
                return source;
            }
        }.share().replayAndAutoConnect(1);

        // Subscribe to the values once
        TestSubscriber sub1 = list.updates().test();
        Assert.assertEquals(1, sub1.values().size());
        Assert.assertEquals(testList, ((Update<String>) sub1.values().get(0)).list);
        Assert.assertEquals(1, subCount.get());

        // Subscribe to the values a second time
        TestSubscriber sub2 = list.updates().test();
        Assert.assertEquals(1, sub2.values().size());
        Assert.assertEquals(testList, ((Update<String>) sub2.values().get(0)).list);
        Assert.assertEquals(1, subCount.get());
    }
}
