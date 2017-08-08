package com.github.mproberts.rxtools.list;

import io.reactivex.functions.Function;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TransformListTest extends ListImplementationTest
{
    @Override
    protected List getList()
    {
        return new TransformList.SimpleTransformList<>(Arrays.asList(1, 2, 3, 4), new Function<Integer, String>() {
            @Override
            public String apply(Integer o)
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
        return new TransformList.SimpleTransformList<>(Collections.<Integer>emptyList(), new Function<Integer, String>() {
            @Override
            public String apply(Integer o)
            {
                return o.toString();
            }
        });
    }
}
