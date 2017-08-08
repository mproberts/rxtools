package com.github.mproberts.rxtools.list;

import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PrefetchListTest extends ListImplementationTest
{
    @Override
    protected List getList()
    {
        return new PrefetchList<>(Arrays.asList(1, 2, 3, 4), 1, 2, new Consumer<Collection<Integer>>() {

            @Override
            public void accept(Collection<Integer> integer) throws Exception
            {
            }
        });
    }

    @Override
    protected List getComparison()
    {
        return Arrays.asList(1, 2, 3, 4);
    }

    @Override
    protected List getEmpty()
    {
        return new PrefetchList<>(Collections.<Integer>emptyList(), 1, 2, new Consumer<Collection<Integer>>() {

            @Override
            public void accept(Collection<Integer> integer) throws Exception
            {
            }
        });
    }
}
