package com.github.mproberts.rxtools.list;

import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * A basic FlowableList implementation which behaves much like a generic List. Additions, removals,
 * and moves will be automatically applied and emitted via the updates Flowable.
 * @param <T> The value type of the list
 */
public class SimpleFlowableList<T> extends BaseFlowableList<T>
{
    private final Object _batchingLock = new Object();
    private List<Function<List<T>, Update<T>>> _batchedOperations;

    void applyOperation(final Function<List<T>, Update<T>> operation)
    {
        synchronized (_batchingLock) {
            if (_batchedOperations != null) {
                _batchedOperations.add(operation);
                return;
            }
        }

        applyUpdate(new Function<List<T>, Update<T>>() {
            @Override
            public Update<T> apply(List<T> list) throws Exception
            {
                list = new ArrayList<>(list);

                return operation.apply(list);
            }
        });
    }

    /**
     * Constructs a new, empty SimpleFlowableList
     */
    public SimpleFlowableList()
    {
        super(Collections.<T>emptyList());
    }

    /**
     * Constructs a new SimpleFlowableList starting from the predefined state
     * @param initialState The initial state of the list
     */
    public SimpleFlowableList(List<T> initialState)
    {
        super(initialState);
    }

    /**
     * Groups operations into a single emission. This reduces changes to a single change list
     * as well a only emitting a single immutable list.
     * @param changes An action to be called which will apply operations to the list
     */
    public void batch(final Consumer<SimpleFlowableList<T>> changes)
    {
        final SimpleFlowableList<T> target = this;

        applyUpdate(new Function<List<T>, Update<T>>() {
            @Override
            public Update<T> apply(List<T> list) throws Exception
            {
                List<Function<List<T>, Update<T>>> batchedOperations;

                synchronized (_batchingLock) {
                    _batchedOperations = new ArrayList<>();

                    changes.accept(target);

                    List<T> resultList = new ArrayList<>(list);
                    List<Change> allChanges = new ArrayList<>();

                    for (Function<List<T>, Update<T>> operation : _batchedOperations) {
                        Update<T> update = operation.apply(resultList);

                        allChanges.addAll(update.changes);
                        resultList = update.list;
                    }

                    _batchedOperations = null;

                    return new Update<>(resultList, allChanges);
                }
            }
        });
    }

    /**
     * Resets the flowable list to a 0-length state
     */
    public void clear()
    {
        applyOperation(new Function<List<T>, Update<T>>() {
            @Override
            public Update<T> apply(List<T> list)
            {
                return new Update<>(Arrays.<T>asList(), Change.reloaded());
            }
        });
    }

    /**
     * Adds a value to the end of the list
     * @param value The value to add
     */
    public void add(final T value)
    {
        applyOperation(new Function<List<T>, Update<T>>() {
            @Override
            public Update<T> apply(List<T> list)
            {
                int size = list.size();

                list.add(value);

                return new Update<>(list, Change.inserted(size));
            }
        });
    }

    /**
     * Adds a value at the specified position within the list
     * @param index The position in the list at which to add the new element
     * @param value The value to add
     */
    public void add(final int index, final T value)
    {
        applyOperation(new Function<List<T>, Update<T>>() {
            @Override
            public Update<T> apply(List<T> list)
            {
                int position = Math.min(list.size(), index);

                list.add(index, value);

                return new Update<>(list, Change.inserted(position));
            }
        });
    }

    /**
     * Adds all of the values contained in the collection to the end of the list
     * @param values The values to add
     */
    public void addAll(final Collection<? extends T> values)
    {
        applyOperation(new Function<List<T>, Update<T>>() {
            @Override
            public Update<T> apply(List<T> list)
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

    /**
     * Moves the value at the specified fromIndex to the specified toIndex
     * @param fromIndex The index to move from
     * @param toIndex The index to move to
     */
    public void move(final int fromIndex, final int toIndex)
    {
        applyOperation(new Function<List<T>, Update<T>>() {
            @Override
            public Update<T> apply(List<T> list)
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

    /**
     * Removes the value at the index
     * @param index The index of the value to remove
     */
    public void remove(final int index)
    {
        applyOperation(new Function<List<T>, Update<T>>() {
            @Override
            public Update<T> apply(List<T> list)
            {
                list.remove(index);

                return new Update<>(list, Change.removed(index));
            }
        });
    }

    /**
     * Finds and removes the first occurrence of the value from the list
     * @param value The value to remove from the list
     */
    public void remove(final T value)
    {
        applyOperation(new Function<List<T>, Update<T>>() {
            @Override
            public Update<T> apply(List<T> list)
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

    /**
     * Finds and removes the first occurrence from the list that matches the provided predicate
     * @param predicate The predicate indicating whether a value should be removed or not
     */
    public void remove(final Predicate<T> predicate)
    {
        applyOperation(new Function<List<T>, Update<T>>() {
            @Override
            public Update<T> apply(List<T> list)
            {
                for (int index = 0; index < list.size(); index++) {
                    if (predicate.test(list.get(index))) {
                        list.remove(index);
                        return new Update<>(list, Change.removed(index));
                    }
                }

                return null;
            }
        });
    }
}
