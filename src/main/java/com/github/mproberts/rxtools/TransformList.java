package com.github.mproberts.rxtools;

import rx.functions.Func1;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class TransformList<K, V> implements List<V>
{
    private class TransformIterator implements Iterator<V>
    {
        private final Iterator<K> _iterator;

        public TransformIterator(Iterator<K> iterator) {
            _iterator = iterator;
        }

        @Override
        public boolean hasNext()
        {
            return _iterator.hasNext();
        }

        @Override
        public V next()
        {
            K next = _iterator.next();

            if (next == null) {
                return null;
            }

            return _transform.call(next);
        }
    }

    private class TransformListIterator implements ListIterator<V>
    {
        private final ListIterator<K> _iterator;

        private TransformListIterator(ListIterator<K> iterator)
        {
            _iterator = iterator;
        }

        @Override
        public boolean hasNext()
        {
            return _iterator.hasNext();
        }

        @Override
        public V next()
        {
            return _transform.call(_iterator.next());
        }

        @Override
        public boolean hasPrevious()
        {
            return _iterator.hasPrevious();
        }

        @Override
        public V previous()
        {
            return _transform.call(_iterator.previous());
        }

        @Override
        public int nextIndex()
        {
            return _iterator.nextIndex();
        }

        @Override
        public int previousIndex()
        {
            return _iterator.previousIndex();
        }

        @Override
        public void remove()
        {
            throw new UnsupportedOperationException("Not modifiable");
        }

        @Override
        public void set(V v)
        {
            throw new UnsupportedOperationException("Not modifiable");
        }

        @Override
        public void add(V v)
        {
            throw new UnsupportedOperationException("Not modifiable");
        }
    }

    private final List<K> _list;
    private final Func1<K, V> _transform;

    TransformList(List<K> list, Func1<K, V> transform)
    {
        _list = list;
        _transform = transform;
    }

    @Override
    public int size()
    {
        return _list.size();
    }

    @Override
    public boolean isEmpty()
    {
        return _list.isEmpty();
    }

    @Override
    public Iterator<V> iterator()
    {
        return new TransformIterator(_list.iterator());
    }

    @Override
    public Object[] toArray()
    {
        @SuppressWarnings("unchecked cast")
        V[] values = (V[]) new Object[size()];

        return toArray(values);
    }

    @Override
    public <T> T[] toArray(T[] target)
    {
        int i = 0;
        for (V value : this) {
            target[i++] = (T) value;
        }

        return target;
    }

    @Override
    public V get(int index)
    {
        return _transform.call(_list.get(index));
    }

    @Override
    public ListIterator<V> listIterator()
    {
        return new TransformListIterator(_list.listIterator());
    }

    @Override
    public ListIterator<V> listIterator(int index)
    {
        return new TransformListIterator(_list.listIterator(index));
    }

    @Override
    public List<V> subList(int fromIndex, int toIndex)
    {
        return new TransformList<>(_list.subList(fromIndex, toIndex), _transform);
    }

    @Override
    public boolean contains(Object o)
    {
        throw new UnsupportedOperationException("Search operations not available");
    }

    @Override
    public boolean containsAll(Collection<?> c)
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
    public boolean add(V v)
    {
        throw new UnsupportedOperationException("Not modifiable");
    }

    @Override
    public boolean remove(Object o)
    {
        throw new UnsupportedOperationException("Not modifiable");
    }

    @Override
    public boolean addAll(Collection<? extends V> c)
    {
        throw new UnsupportedOperationException("Not modifiable");
    }

    @Override
    public boolean addAll(int index, Collection<? extends V> c)
    {
        throw new UnsupportedOperationException("Not modifiable");
    }

    @Override
    public boolean removeAll(Collection<?> c)
    {
        throw new UnsupportedOperationException("Not modifiable");
    }

    @Override
    public boolean retainAll(Collection<?> c)
    {
        throw new UnsupportedOperationException("Not modifiable");
    }

    @Override
    public void clear()
    {
        throw new UnsupportedOperationException("Not modifiable");
    }

    @Override
    public V set(int index, V element)
    {
        throw new UnsupportedOperationException("Not modifiable");
    }

    @Override
    public void add(int index, V element)
    {
        throw new UnsupportedOperationException("Not modifiable");
    }

    @Override
    public V remove(int index)
    {
        throw new UnsupportedOperationException("Not modifiable");
    }
}
