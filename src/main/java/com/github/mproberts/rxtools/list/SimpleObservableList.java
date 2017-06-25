package com.github.mproberts.rxtools.list;

import rx.functions.Action1;
import rx.functions.Func1;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class SimpleObservableList<T> extends BaseObservableList<T>
{
    final Object _batchingLock = new Object();
    List<Func1<List<T>, Update<T>>> _batchedOperations;

    public SimpleObservableList()
    {
        super(Collections.<T>emptyList());
    }

    public SimpleObservableList(List<T> initialState)
    {
        super(initialState);
    }

    public void batch(final Action1<SimpleObservableList<T>> changes)
    {
        final SimpleObservableList<T> target = this;

        applyUpdate(new Func1<List<T>, Update<T>>() {
            @Override
            public Update<T> call(List<T> list)
            {
                List<Func1<List<T>, Update<T>>> batchedOperations;

                synchronized (_batchingLock) {
                    _batchedOperations = new ArrayList<>();

                    changes.call(target);

                    List<T> resultList = new ArrayList<>(list);
                    List<Change> allChanges = new ArrayList<>();

                    for (Func1<List<T>, Update<T>> operation : _batchedOperations) {
                        Update<T> update = operation.call(resultList);

                        allChanges.addAll(update.changes);
                        resultList = update.list;
                    }

                    _batchedOperations = null;

                    return new Update<>(resultList, allChanges);
                }
            }
        });
    }

    void applyOperation(final Func1<List<T>, Update<T>> operation)
    {
        synchronized (_batchingLock) {
            if (_batchedOperations != null) {
                _batchedOperations.add(operation);
                return;
            }
        }

        applyUpdate(new Func1<List<T>, Update<T>>() {
            @Override
            public Update<T> call(List<T> list)
            {
                list = new ArrayList<>(list);

                return operation.call(list);
            }
        });
    }

    public void add(final T value)
    {
        applyOperation(new Func1<List<T>, Update<T>>() {
            @Override
            public Update<T> call(List<T> list)
            {
                int size = list.size();

                list.add(value);

                return new Update<>(list, Change.inserted(size));
            }
        });
    }

    public void add(final int index, final T value)
    {
        applyOperation(new Func1<List<T>, Update<T>>() {
            @Override
            public Update<T> call(List<T> list)
            {
                int position = Math.min(list.size(), index);

                list.add(index, value);

                return new Update<>(list, Change.inserted(position));
            }
        });
    }

    public void move(final int fromIndex, final int toIndex)
    {
        applyOperation(new Func1<List<T>, Update<T>>() {
            @Override
            public Update<T> call(List<T> list)
            {
                int toPosition = Math.min(list.size() - 1, toIndex);

                if (toPosition == fromIndex) {
                    // do nothing
                    return null;
                }

                list.add(toPosition, list.remove(fromIndex));

                return new Update<>(list, Change.moved(fromIndex, toIndex));
            }
        });
    }

    public void addAll(final Collection<? extends T> values)
    {
        applyOperation(new Func1<List<T>, Update<T>>() {
            @Override
            public Update<T> call(List<T> list)
            {
                int size = list.size();

                list.addAll(values);

                List<Change> changes = new ArrayList<>();

                for (int i = 0; i < values.size(); ++i) {
                    changes.add(Change.inserted(size + i));
                }

                return new Update<>(list, changes);
            }
        });
    }

    public void remove(final int index)
    {
        applyOperation(new Func1<List<T>, Update<T>>() {
            @Override
            public Update<T> call(List<T> list)
            {
                list.remove(index);

                return new Update<>(list, Change.removed(index));
            }
        });
    }

    public void remove(final T value)
    {
        applyOperation(new Func1<List<T>, Update<T>>() {
            @Override
            public Update<T> call(List<T> list)
            {
                int index = list.indexOf(value);

                if (index < 0) {
                    return null;
                }

                list.remove(index);

                return new Update<>(list, Change.removed(index));
            }
        });
    }
}
