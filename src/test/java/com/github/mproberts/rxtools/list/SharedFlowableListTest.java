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

class TestFlowableOnSubscribe implements FlowableOnSubscribe<List<String>> {
    private int _subscriptionCount = 0;
    private FlowableEmitter<List<String>> _latestEmitter;

    @Override
    public void subscribe(FlowableEmitter<List<String>> flowableEmitter) {
        _subscriptionCount++;
        _latestEmitter = flowableEmitter;
    }

    int subscriptionCount() {
        return _subscriptionCount;
    }

    FlowableEmitter latestEmitter() {
        return _latestEmitter;
    }
}

public class SharedFlowableListTest {

    @Test
    public void testWithoutShareOperator() {
        TestFlowableOnSubscribe subscriptionTracker = new TestFlowableOnSubscribe();
        Flowable<List<String>> source = Flowable.create(subscriptionTracker, BackpressureStrategy.BUFFER);

        FlowableList<String> list = FlowableList.diff(source);

        Assert.assertEquals(0, subscriptionTracker.subscriptionCount());

        list.updates().test();
        Assert.assertEquals(1, subscriptionTracker.subscriptionCount());

        list.updates().test();
        Assert.assertEquals(2, subscriptionTracker.subscriptionCount());
    }

    @Test
    public void testWithShareOperator() {
        TestFlowableOnSubscribe subscriptionTracker = new TestFlowableOnSubscribe();
        Flowable<List<String>> source = Flowable.create(subscriptionTracker, BackpressureStrategy.BUFFER);

        FlowableList<String> list = FlowableList.diff(source).share();

        Assert.assertEquals(0, subscriptionTracker.subscriptionCount());

        list.updates().test();
        Assert.assertEquals(1, subscriptionTracker.subscriptionCount());

        list.updates().test();
        Assert.assertEquals(1, subscriptionTracker.subscriptionCount());
    }

    @Test
    public void testDoesNotReplay() {
        TestFlowableOnSubscribe subscriptionTracker = new TestFlowableOnSubscribe();
        Flowable<List<String>> source = Flowable.create(subscriptionTracker, BackpressureStrategy.BUFFER);

        FlowableList<String> list = FlowableList.diff(source).share();

        TestSubscriber sub1 = list.updates().test();
        subscriptionTracker.latestEmitter().onNext(new ArrayList<String>() {{
            add("un");
            add("deux");
        }});
        TestSubscriber sub2 = list.updates().test();

        Assert.assertEquals(1, sub1.valueCount());
        Assert.assertEquals(0, sub2.valueCount());

        subscriptionTracker.latestEmitter().onNext(new ArrayList<String>() {{
            add("trois");
            add("quatre");
        }});

        Assert.assertEquals(2, sub1.valueCount());
        Assert.assertEquals(1, sub2.valueCount());
    }

    @Test
    public void testDisposeThenSubscribeTriggersNewSubscription() {
        TestFlowableOnSubscribe subscriptionTracker = new TestFlowableOnSubscribe();
        Flowable<List<String>> source = Flowable.create(subscriptionTracker, BackpressureStrategy.BUFFER);

        FlowableList<String> list = FlowableList.diff(source).share();

        Assert.assertEquals(0, subscriptionTracker.subscriptionCount());

        TestSubscriber subscriber1 = list.updates().test();
        TestSubscriber subscriber2 = list.updates().test();
        Assert.assertEquals(1, subscriptionTracker.subscriptionCount());
        Assert.assertEquals(1, subscriptionTracker.subscriptionCount());
        subscriber1.dispose();
        subscriber2.dispose();

        list.updates().test();
        Assert.assertEquals(2, subscriptionTracker.subscriptionCount());
    }

    @Test
    public void testForwardsAllUpdates() {
        TestFlowableOnSubscribe subscriptionTracker = new TestFlowableOnSubscribe();
        Flowable<List<String>> source = Flowable.create(subscriptionTracker, BackpressureStrategy.BUFFER);

        FlowableList<String> list = FlowableList.diff(source).share();

        TestSubscriber sub1 = list.updates().test();
        TestSubscriber sub2 = list.updates().test();

        Assert.assertEquals(1, subscriptionTracker.subscriptionCount());

        Assert.assertEquals(0, sub1.values().size());
        Assert.assertEquals(0, sub2.values().size());

        // Emit something
        List<String> testList = new ArrayList<String>() {{
            add("un");
            add("deux");
        }};
        subscriptionTracker.latestEmitter().onNext(testList);

        Assert.assertEquals(1, sub1.values().size());
        Assert.assertEquals(1, sub2.values().size());

        Assert.assertEquals(testList, ((Update<String>) sub1.values().get(0)).list);
        Assert.assertEquals(testList, ((Update<String>) sub2.values().get(0)).list);
    }

}
