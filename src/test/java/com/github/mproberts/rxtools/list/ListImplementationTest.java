package com.github.mproberts.rxtools.list;

import java.util.*;

import org.junit.Test;

import static org.junit.Assert.*;

public abstract class ListImplementationTest
{
    protected abstract List getList();

    protected abstract List getComparison();

    protected abstract List getEmpty();

    @Test
    public void testSize()
    {
        assertEquals(0, getEmpty().size());
        assertEquals(getComparison().size(), getList().size());
    }

    @Test
    public void testToArray()
    {
        assertArrayEquals(getComparison().toArray(), getList().toArray());
    }

    @Test
    public void testEquals()
    {
        List list = getList();

        assertTrue(list.equals(list));
        assertTrue(getList().equals(getList()));
        assertTrue(getComparison().equals(getList()));
        assertTrue(getList().equals(getComparison()));

        List<String> equalLengthList = new ArrayList<>(list.size());

        for (Object o : list) {
            equalLengthList.add(o.toString() + "t");
        }

        assertFalse(getList().equals(equalLengthList));
        assertFalse(getList().equals(getEmpty()));
        assertFalse(getList().equals(Arrays.asList("this", "is", "fake")));
    }

    @Test
    public void testSublist()
    {
        List list = getList();

        assertEquals(
                getComparison().subList(1, list.size() - 1),
                list.subList(1, list.size() - 1));
        assertEquals(
                getComparison().subList(1, list.size() - 2),
                list.subList(1, list.size() - 2));
        assertEquals(
                getComparison().subList(0, list.size() - 4),
                list.subList(0, list.size() - 4));
    }

    @Test
    public void testIsEmpty()
    {
        assertFalse(getList().isEmpty());
        assertTrue(getEmpty().isEmpty());
    }

    @Test
    public void testGet()
    {
        for (int i = 0; i < getList().size(); ++i) {
            assertEquals(getComparison().get(i), getList().get(i));
        }
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetOobUnder()
    {
        getList().get(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetOobOver()
    {
        getList().get(getList().size());
    }

    @Test
    public void testIterator()
    {
        Iterator iterator1 = getList().iterator();
        Iterator iterator2 = getComparison().iterator();

        while (iterator1.hasNext()) {
            if (!iterator2.hasNext()) {
                fail();
            }

            assertEquals(iterator1.next(), iterator2.next());
        }
    }

    @Test
    public void testListIterator()
    {
        ListIterator iterator1 = getComparison().listIterator();
        ListIterator iterator2 = getList().listIterator();

        // run it forwards
        while (iterator1.hasNext()) {
            if (!iterator2.hasNext()) {
                fail();
            }

            assertEquals(iterator1.next(), iterator2.next());
            assertEquals(iterator1.nextIndex(), iterator2.nextIndex());
        }

        // run it backwards
        while (iterator1.hasPrevious()) {
            if (!iterator2.hasPrevious()) {
                fail();
            }

            assertEquals(iterator1.previous(), iterator2.previous());
            assertEquals(iterator1.previousIndex(), iterator2.previousIndex());
        }
    }

    @Test
    public void testListIteratorOffset()
    {
        ListIterator iterator1 = getList().listIterator(getList().size() / 2);
        ListIterator iterator2 = getComparison().listIterator(getList().size() / 2);

        // run it forwards
        while (iterator1.hasNext()) {
            if (!iterator2.hasNext()) {
                fail();
            }

            assertEquals(iterator1.next(), iterator2.next());
        }
    }

    @Test
    public void testEmptyIterator()
    {
        Iterator iterator = getEmpty().iterator();

        assertFalse(iterator.hasNext());
    }

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
    public void testIteratorRemove()
    {
        getList().iterator().remove();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testIteratorSet()
    {
        getList().listIterator().set(new Object());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testIteratorAdd()
    {
        getList().listIterator().add(new Object());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testListIteratorRemove()
    {
        getList().listIterator().remove();
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
