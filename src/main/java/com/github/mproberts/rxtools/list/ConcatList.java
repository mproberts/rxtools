package com.github.mproberts.rxtools.list;

import java.util.*;

class ConcatList implements List
{
    private final List[] _lists;
    private int _totalSize;

    private class ConcatIterator implements Iterator
    {
        private final Iterator[] _iterators;
        private int _currentIteratorIndex;

        public ConcatIterator(List[] _lists)
        {
            _iterators = new Iterator[_lists.length];

            for (int i = 0; i < _lists.length; ++i) {
                _iterators[i] = _lists[i].iterator();
            }
        }

        private Iterator currentIterator()
        {
            if (_iterators.length == 0) {
                return null;
            }

            return _iterators[_currentIteratorIndex];
        }

        private boolean nextIterator()
        {
            ++_currentIteratorIndex;

            if (_currentIteratorIndex >= _iterators.length) {
                _currentIteratorIndex = _iterators.length;
                return false;
            }

            return true;
        }

        @Override
        public boolean hasNext()
        {
            if (currentIterator() == null) {
                return false;
            }

            if (currentIterator().hasNext()) {
                return true;
            }

            while (nextIterator()) {
                if (currentIterator().hasNext()) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public Object next()
        {
            while (!currentIterator().hasNext() && nextIterator()) {
                if (currentIterator().hasNext()) {
                    break;
                }
            }

            return currentIterator().next();
        }

        @Override
        public void remove()
        {
            throw new UnsupportedOperationException("Not modifiable");
        }
    }

    private class ConcatListIterator implements ListIterator
    {
        private final ListIterator[] _iterators;
        private int _currentIteratorIndex;
        private int _offset;

        public ConcatListIterator(List[] lists)
        {
            _iterators = new ListIterator[lists.length];

            for (int i = 0; i < lists.length; ++i) {
                _iterators[i] = lists[i].listIterator();
            }
        }

        private ListIterator currentIterator()
        {
            if (_iterators.length == 0) {
                return null;
            }

            return _iterators[_currentIteratorIndex];
        }

        private boolean previousIterator()
        {
            --_currentIteratorIndex;

            if (_currentIteratorIndex < 0) {
                _currentIteratorIndex = 0;
                return false;
            }

            return true;
        }

        private boolean nextIterator()
        {
            ++_currentIteratorIndex;

            if (_currentIteratorIndex >= _iterators.length) {
                _currentIteratorIndex = _iterators.length - 1;
                return false;
            }

            return true;
        }

        @Override
        public boolean hasNext()
        {
            if (currentIterator() == null) {
                return false;
            }

            if (currentIterator().hasNext()) {
                return true;
            }

            while (nextIterator()) {
                if (currentIterator().hasNext()) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public Object next()
        {
            ++_offset;

            return currentIterator().next();
        }

        @Override
        public boolean hasPrevious()
        {
            if (currentIterator() == null) {
                return false;
            }

            if (currentIterator().hasPrevious()) {
                return true;
            }

            while (previousIterator()) {
                if (currentIterator().hasPrevious()) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public Object previous()
        {
            --_offset;

            return currentIterator().previous();
        }

        @Override
        public int nextIndex()
        {
            return _offset;
        }

        @Override
        public int previousIndex()
        {
            return _offset - 1;
        }

        @Override
        public void remove()
        {
            throw new UnsupportedOperationException("Not modifiable");
        }

        @Override
        public void set(Object v)
        {
            throw new UnsupportedOperationException("Not modifiable");
        }

        @Override
        public void add(Object v)
        {
            throw new UnsupportedOperationException("Not modifiable");
        }
    }

    ConcatList(List... lists)
    {
        _lists = lists;
    }

    @Override
    public int size()
    {
        if (_totalSize == 0) {
            int totalSize = 0;

            for (List list : _lists) {
                totalSize += list.size();
            }

            _totalSize = totalSize;
        }

        return _totalSize;
    }

    @Override
    public boolean isEmpty()
    {
        return size() == 0;
    }

    @Override
    public Iterator iterator()
    {
        return new ConcatIterator(_lists);
    }

    @Override
    public Object[] toArray()
    {
        @SuppressWarnings("unchecked cast")
        Object[] values = (Object[]) new Object[size()];

        return toArray(values);
    }

    @Override
    public Object[] toArray(Object[] target)
    {
        int i = 0;
        for (Object value : this) {
            target[i++] = value;
        }

        return target;
    }

    @Override
    public Object get(int index)
    {
        int offset = 0;
        Object result = null;

        if (index < 0) {
            throw new IndexOutOfBoundsException(index + " < 0");
        }
        else if (index >= size()) {
            throw new IndexOutOfBoundsException(index + " >= " + size());
        }

        for (List list : _lists) {
            int size = list.size();

            if (offset + size > index) {
                result = list.get(index - offset);
                break;
            }

            offset += size;
        }

        return result;
    }

    @Override
    public ListIterator listIterator()
    {
        return new ConcatListIterator(_lists);
    }

    @Override
    public ListIterator listIterator(int index)
    {
        ConcatListIterator concatListIterator = new ConcatListIterator(_lists);

        if (index >= size()) {
            throw new IndexOutOfBoundsException(index + " >= " + size());
        }

        int offset = 0;

        while (concatListIterator.hasNext() && offset < index) {
            offset++;
            concatListIterator.next();
        }

        return concatListIterator;
    }

    @Override
    public List subList(int fromIndex, int toIndex)
    {
        List<List> sublists = new ArrayList<>();
        int offset = 0;

        for (List list : _lists) {
            int tailIndex = offset + list.size();
            int headIndex = offset;

            if (tailIndex < fromIndex) {
                // skip entirely, excluded by lower bounds
            }
            else if (headIndex > toIndex) {
                // skip entirely, excluded by upper bounds
            }
            else {
                int headCutIndex = 0;
                int tailCutIndex = list.size();

                // include at least some subset of this list
                if (tailIndex > fromIndex && headIndex < fromIndex) {
                    headCutIndex = fromIndex - offset;
                }

                if (tailIndex > toIndex && headIndex < tailIndex) {
                    tailCutIndex = toIndex - offset;
                }

                // create a sublist within the bounded range, if necessary
                if (headCutIndex != 0 || tailCutIndex != list.size()) {
                    list = list.subList(headCutIndex, tailCutIndex);
                }

                sublists.add(list);
            }

            // advance to the next position in the range
            offset = tailIndex;

            // give up if we've already reached the end of our request
            if (offset > toIndex) {
                break;
            }
        }

        return new ConcatList(sublists.toArray(new List[sublists.size()]));
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == this) {
            return true;
        }

        if (obj instanceof List) {
            List other = (List) obj;

            if (size() != other.size()) {
                return false;
            }

            Iterator iterator = iterator();
            Iterator otherIterator = other.iterator();

            while (iterator.hasNext()) {
                // other and this have the same number of elements so we can co-iterate
                Object e1 = iterator.next();
                Object e2 = otherIterator.next();

                boolean isEqual = e1 == null ? e2 == null : e1.equals(e2);

                if (!isEqual) {
                    return false;
                }
            }

            return true;
        }

        return false;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder("[");

        for (int i = 0; i < size(); ++i) {
            Object o = get(i);

            if (builder.length() > 1) {
                builder.append(", ");
            }

            builder.append(o.toString());
        }

        builder.append("]");

        return builder.toString();
    }

    @Override
    public boolean contains(Object o)
    {
        throw new UnsupportedOperationException("Search operations not available");
    }

    @Override
    public boolean containsAll(Collection c)
    {
        throw new UnsupportedOperationException("Search operations not available");
    }

    @Override
    public int indexOf(Object o)
    {
        throw new UnsupportedOperationException("Search operations not available");
    }

    @Override
    public int lastIndexOf(Object o)
    {
        throw new UnsupportedOperationException("Search operations not available");
    }

    @Override
    public boolean add(Object v)
    {
        throw new UnsupportedOperationException("Not modifiable");
    }

    @Override
    public boolean remove(Object o)
    {
        throw new UnsupportedOperationException("Not modifiable");
    }

    @Override
    public boolean addAll(Collection c)
    {
        throw new UnsupportedOperationException("Not modifiable");
    }

    @Override
    public boolean addAll(int index, Collection c)
    {
        throw new UnsupportedOperationException("Not modifiable");
    }

    @Override
    public boolean removeAll(Collection c)
    {
        throw new UnsupportedOperationException("Not modifiable");
    }

    @Override
    public boolean retainAll(Collection c)
    {
        throw new UnsupportedOperationException("Not modifiable");
    }

    @Override
    public void clear()
    {
        throw new UnsupportedOperationException("Not modifiable");
    }

    @Override
    public Object set(int index, Object element)
    {
        throw new UnsupportedOperationException("Not modifiable");
    }

    @Override
    public void add(int index, Object element)
    {
        throw new UnsupportedOperationException("Not modifiable");
    }

    @Override
    public Object remove(int index)
    {
        throw new UnsupportedOperationException("Not modifiable");
    }
}
