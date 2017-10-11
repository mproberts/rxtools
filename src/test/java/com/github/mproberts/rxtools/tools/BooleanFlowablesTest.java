package com.github.mproberts.rxtools.tools;

import io.reactivex.Flowable;
import io.reactivex.processors.BehaviorProcessor;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;

public class BooleanFlowablesTest
{
    @Test
    public void testAndShortCircuiting()
    {
        BehaviorProcessor<Boolean> bool1 = BehaviorProcessor.create();
        BehaviorProcessor<Boolean> bool2 = BehaviorProcessor.create();
        BehaviorProcessor<Boolean> bool3 = BehaviorProcessor.create();
        BehaviorProcessor<Boolean> bool4 = BehaviorProcessor.create();

        bool1.onNext(true);
        bool2.onNext(true);
        bool3.onNext(true);
        bool4.onNext(true);

        Flowable<Boolean> joined = BooleanFlowables.and(bool1, bool2, bool3, bool4);

        TestSubscriber<Boolean> results = new TestSubscriber<>();

        joined.subscribe(results);

        results.assertValues(true);

        bool2.onNext(false);

        results.assertValues(true, false);

        bool3.onNext(false);

        results.assertValues(true, false);

        bool3.onNext(true);

        results.assertValues(true, false);

        bool2.onNext(true);

        results.assertValues(true, false, true);

        bool1.onNext(false);

        results.assertValues(true, false, true, false);

        bool4.onNext(false);

        results.assertValues(true, false, true, false);
    }

    @Test
    public void testEmptyAnd()
    {
        Flowable<Boolean> joined = BooleanFlowables.and();

        TestSubscriber<Boolean> results = new TestSubscriber<>();

        joined.subscribe(results);

        results.assertValues(false);
    }

    @Test
    public void testOrShortCircuiting()
    {
        BehaviorProcessor<Boolean> bool1 = BehaviorProcessor.create();
        BehaviorProcessor<Boolean> bool2 = BehaviorProcessor.create();
        BehaviorProcessor<Boolean> bool3 = BehaviorProcessor.create();
        BehaviorProcessor<Boolean> bool4 = BehaviorProcessor.create();

        bool1.onNext(true);
        bool2.onNext(false);
        bool3.onNext(false);
        bool4.onNext(false);

        Flowable<Boolean> joined = BooleanFlowables.or(bool1, bool2, bool3, bool4);

        TestSubscriber<Boolean> results = new TestSubscriber<>();

        joined.subscribe(results);

        results.assertValues(true);

        bool2.onNext(true);

        results.assertValues(true);

        bool1.onNext(false);

        results.assertValues(true);

        bool2.onNext(false);

        results.assertValues(true, false);

        bool3.onNext(true);

        results.assertValues(true, false, true);

        bool4.onNext(true);

        results.assertValues(true, false, true);

        bool4.onNext(false);

        results.assertValues(true, false, true);
    }

    @Test
    public void testEmptyOr()
    {
        Flowable<Boolean> joined = BooleanFlowables.or();

        TestSubscriber<Boolean> results = new TestSubscriber<>();

        joined.subscribe(results);

        results.assertValues(false);
    }
}
