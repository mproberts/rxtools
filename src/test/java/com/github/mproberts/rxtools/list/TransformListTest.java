package com.github.mproberts.rxtools.list;

import rx.functions.Func1;

import java.util.Arrays;
import java.util.List;

public class TransformListTest extends ListImplementationTest
{
    @Override
    protected List getList()
    {
        return new TransformList(Arrays.asList(new Object()), new Func1<Object, Object>() {
            @Override
            public Object call(Object o)
            {
                return o;
            }
        });
    }
}
