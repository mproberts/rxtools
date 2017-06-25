package com.github.mproberts.rxtools.list;

import java.util.Collections;
import java.util.List;
import org.junit.Test;

public abstract class ListImplementationTest
{
    protected abstract List getList();

    @Test(expected = UnsupportedOperationException.class)
    public void testAdd()
    {
        getList().add(new Object());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRemove()
    {
        getList().remove(new Object());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAddAll()
    {
        getList().addAll(Collections.emptyList());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAddAllIndex()
    {
        getList().addAll(0, Collections.emptyList());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRemoveAll()
    {
        getList().removeAll(Collections.emptyList());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRetainAll()
    {
        getList().retainAll(Collections.emptyList());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testClear()
    {
        getList().clear();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSet()
    {
        getList().set(0, new Object());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAddIndex()
    {
        getList().add(0, new Object());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRemoveIndex()
    {
        getList().remove(0);
    }

}
