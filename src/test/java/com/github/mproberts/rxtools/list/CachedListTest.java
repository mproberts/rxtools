package com.github.mproberts.rxtools.list;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class CachedListTest extends ListImplementationTest
{
    @Override
    protected List getList()
    {
        return new CachedList(Arrays.asList(1, 2, 3, 4, 5, 6, 7), 0, new HashMap<>(), new HashMap<>());
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
