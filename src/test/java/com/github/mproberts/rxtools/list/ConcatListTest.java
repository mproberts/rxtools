package com.github.mproberts.rxtools.list;

import java.util.Arrays;
import java.util.List;

public class ConcatListTest extends ListImplementationTest
{
    @Override
    protected List getList()
    {
        return new ConcatList(Arrays.asList(1, 2, 3), Arrays.asList(4), Arrays.asList(5, 6, 7));
    }

    @Override
    protected List getComparison()
    {
        return Arrays.asList(1, 2, 3, 4, 5, 6, 7);
    }

    @Override
    protected List getEmpty()
    {
        return new ConcatList();
    }
}
