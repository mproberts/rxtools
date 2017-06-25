package com.github.mproberts.rxtools.list;

import java.util.List;

public class ConcatListTest extends ListImplementationTest
{
    @Override
    protected List getList()
    {
        return new ConcatList();
    }
}
