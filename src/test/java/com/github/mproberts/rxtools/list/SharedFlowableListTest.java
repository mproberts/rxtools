package com.github.mproberts.rxtools.list;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.subscribers.TestSubscriber;

public class SharedFlowableListTest {

    private class TestFlowableOnSubscribe implements FlowableOnSubscribe<List<String>> {
        private int subscriptionCount = 0;
        private FlowableEmitter<List<String>> latestEmitter;

        @Override
        public void subscribe(FlowableEmitter<List<String>> flowableEmitter) throws Exception {
            subscriptionCount++;
            latestEmitter = flowableEmitter;
        }
    }

    @Test
    public void testWithoutShareOperator() {
        TestFlowableOnSubscribe subscriptionTracker = new TestFlowableOnSubscribe();
        Flowable<List<String>> source = Flowable.create(subscriptionTracker, BackpressureStrategy.BUFFER);

        FlowableList<String> list = FlowableList.diff(source);

        Assert.assertEquals(0, subscriptionTracker.subscriptionCount);

        list.updates().test();
        Assert.assertEquals(1, subscriptionTracker.subscriptionCount);

        list.updates().test();
        Assert.assertEquals(2, subscriptionTracker.subscriptionCount);
    }

    @Test
    public void testWithShareOperator() {
        TestFlowableOnSubscribe subscriptionTracker = new TestFlowableOnSubscribe();
        Flowable<List<String>> source = Flowable.create(subscriptionTracker, BackpressureStrategy.BUFFER);

        FlowableList<String> list = FlowableList.diff(source).share();

        Assert.assertEquals(0, subscriptionTracker.subscriptionCount);

        list.updates().test();
        Assert.assertEquals(1, subscriptionTracker.subscriptionCount);

        list.updates().test();
        Assert.assertEquals(1, subscriptionTracker.subscriptionCount);
    }

    @Test
    public void testForwardsAllUpdates() {
        TestFlowableOnSubscribe subscriptionTracker = new TestFlowableOnSubscribe();
        Flowable<List<String>> source = Flowable.create(subscriptionTracker, BackpressureStrategy.BUFFER);

        FlowableList<String> list = FlowableList.diff(source).share();

        TestSubscriber sub1 = list.updates().test();
        TestSubscriber sub2 = list.updates().test();

        Assert.assertEquals(1, subscriptionTracker.subscriptionCount);

        Assert.assertEquals(0, sub1.values().size());
        Assert.assertEquals(0, sub2.values().size());

        // Emit something
        List<String> testList = new ArrayList<String>() {{
            add("un");
            add("deux");
        }};
        subscriptionTracker.latestEmitter.onNext(testList);

        Assert.assertEquals(1, sub1.values().size());
        Assert.assertEquals(1, sub2.values().size());

        Assert.assertEquals(testList, ((Update<String>) sub1.values().get(0)).list);
        Assert.assertEquals(testList, ((Update<String>) sub2.values().get(0)).list);
    }

}
