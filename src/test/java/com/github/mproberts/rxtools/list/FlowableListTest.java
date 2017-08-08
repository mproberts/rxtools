package com.github.mproberts.rxtools.list;

import io.reactivex.functions.Consumer;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FlowableListTest
{
    @Test
    public void testChangeAndUpdate()
    {
        final SimpleFlowableList<Integer> list = new SimpleFlowableList<>();
        TestSubscriber<Update<Integer>> test = list.updates().test();

        assertEquals("list=[0], changes={reloaded}", test.values().get(0).toString());

        list.batch(new Consumer<SimpleFlowableList<Integer>>() {
            @Override
            public void accept(SimpleFlowableList<Integer> integerSimpleFlowableList) throws Exception {
                list.add(1);
                list.add(2);
                list.add(3);
            }
        });
        assertEquals("list=[3], changes={inserted(0), inserted(1), inserted(2)}", test.values().get(1).toString());

        list.remove(1);
        assertEquals("list=[2], changes={removed(1)}", test.values().get(2).toString());

        list.move(0, 1);
        assertEquals("list=[2], changes={moved(0 -> 1)}", test.values().get(3).toString());
    }
}
