package com.github.mproberts.rxtools.list;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.FlowableOperator;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.subscribers.TestSubscriber;

class TestOperator implements FlowableOperator<Update<String>, Update<String>> {

    private boolean _didSubscribe = false;
    private Throwable _error;
    private Update<String> _nextUpdate;
    private boolean _didComplete = false;

    @Override
    public Subscriber<? super Update<String>> apply(final Subscriber<? super Update<String>> subscriber) throws Exception {
        return new Subscriber<Update<String>>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                _didSubscribe = true;
                subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(Update<String> stringUpdate) {
                _nextUpdate = stringUpdate;
                subscriber.onNext(stringUpdate);
            }

            @Override
            public void onError(Throwable throwable) {
                _error = throwable;
                subscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
                _didComplete = true;
                subscriber.onComplete();
            }
        };
    }

    boolean didSubscribe() {
        return _didSubscribe;
    }

    Throwable error() {
        return _error;
    }

    Update<String> nextUpdate() {
        return _nextUpdate;
    }

    boolean didComplete() {
        return _didComplete;
    }
}

public class LiftedFlowableListTest {

    private PublishProcessor<List<String>> _source;
    private FlowableList<String> _list;
    private TestOperator _operator;

    @Before
    public void setup()
    {
        _source = PublishProcessor.create();
        _list = FlowableList.diff(_source);
        _operator = new TestOperator();
    }

    @Test
    public void testInitial()
    {
        Assert.assertFalse(_operator.didSubscribe());

        TestSubscriber<Update<String>> observer = _list.lift(_operator).updates().test();

        Assert.assertTrue(_operator.didSubscribe());
        Assert.assertFalse(_operator.didComplete());
        Assert.assertNull(_operator.error());
        Assert.assertNull(_operator.nextUpdate());

        observer.dispose();
    }

    @Test
    public void testNextThenComplete() {
        TestSubscriber<Update<String>> observer = _list.lift(_operator).updates().test();

        List<String> testList = new ArrayList<String>() {{
            add("un");
            add("deux");
        }};

        _source.onNext(testList);

        Assert.assertTrue(_operator.didSubscribe());
        Assert.assertFalse(_operator.didComplete());
        Assert.assertNull(_operator.error());
        Assert.assertEquals(_operator.nextUpdate().list, testList);

        _source.onComplete();
        Assert.assertTrue(_operator.didComplete());

        observer.dispose();
    }

    @Test
    public void testError() {
        TestSubscriber<Update<String>> observer = _list.lift(_operator).updates().test();

        Throwable testError = new Exception("remi was here");
        _source.onError(testError);

        Assert.assertTrue(_operator.didSubscribe());
        Assert.assertFalse(_operator.didComplete());
        Assert.assertEquals(_operator.error(), testError);
        Assert.assertNull(_operator.nextUpdate());

        observer.dispose();
    }
}
