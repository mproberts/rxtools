package com.github.mproberts.rxtools.list;

import rx.functions.Func1;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TransformListTest extends ListImplementationTest
{
    @Override
    protected List getList()
    {
        return new TransformList<>(Arrays.asList(1, 2, 3, 4), new Func1<Integer, String>() {
            @Override
            public String call(Integer o)
            {
                return o.toString();
            }
        });
    }

    @Override
    protected List getComparison()
    {
        return Arrays.asList("1", "2", "3", "4");
    }

    @Override
    protected List getEmpty()
    {
        return new TransformList<>(Collections.<Integer>emptyList(), new Func1<Integer, String>() {
            @Override
            public String call(Integer o)
            {
                return o.toString();
            }
        });
    }
}
