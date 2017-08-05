package com.github.mproberts.rxtools.list;

import com.github.mproberts.rxtools.types.Item;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.functions.Function;
import io.reactivex.functions.Function3;
import io.reactivex.processors.PublishProcessor;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class IndexedFlowableList<T, R> extends FlowableList<R>
{
    private final FlowableList<T> _list;
    private final Function3<T, Flowable<Item<T>>, Flowable<Item<T>>, R> _transform;
    private List<IndexHolder<T>> _indexList = new ArrayList<>();

    private static class IndexHolder<T>
    {
        private final List<T> _internalList;
        private int _index;
        private WeakReference<PublishProcessor<Item<T>>> _previous;
        private WeakReference<PublishProcessor<Item<T>>> _next;
        private boolean _previousDirty;
        private boolean _nextDirty;

        private IndexHolder(int index, List<T> internalList)
        {
            this(index, internalList, null, null);
        }

        private IndexHolder(int index, List<T> internalList, WeakReference<PublishProcessor<Item<T>>> previous, WeakReference<PublishProcessor<Item<T>>> next)
        {
            _index = index;
            _internalList = internalList;
            _previous = previous;
            _next = next;
        }

        public IndexHolder copy(List<T> updatedList)
        {
            return new IndexHolder<>(getIndex(), updatedList, _previous, _next);
        }

        public int getIndex()
        {
            return _index;
        }

        public void setIndex(int index)
        {
            _index = index;
        }

        public void setPreviousDirty()
        {
            _previousDirty = true;
        }

        public void setNextDirty()
        {
            _nextDirty = true;
        }

        public PublishProcessor<Item<T>> getPrevious()
        {
            PublishProcessor<Item<T>> processor = _previous != null ? _previous.get() : null;

            if (processor == null) {
                processor = PublishProcessor.create();
                _previous = new WeakReference<>(processor);
            }

            return processor;
        }

        public PublishProcessor<Item<T>> getNext()
        {
            PublishProcessor<Item<T>> processor = _next != null ? _next.get() : null;

            if (processor == null) {
                processor = PublishProcessor.create();
                _next = new WeakReference<>(processor);
            }

            return processor;
        }

        public boolean isActive()
        {
            PublishProcessor<Item<T>> next = _next.get();

            if (next != null) {
                return true;
            }

            PublishProcessor<Item<T>> previous = _previous.get();

            return previous != null;
        }

        public void postUpdate()
        {
            boolean nextDirty = _nextDirty;
            boolean previousDirty = _previousDirty;
            int index = _index;

            _nextDirty = false;
            _previousDirty = false;

            PublishProcessor<Item<T>> next = _next.get();
            PublishProcessor<Item<T>> previous = _previous.get();

            if (previousDirty) {
                if (previous != null) {
                    Item<T> item;

                    if (index > 0) {
                        item = new Item<>(_internalList.get(index - 1));
                    }
                    else {
                        item = Item.invalid();
                    }

                    previous.onNext(item);
                }
            }

            if (nextDirty) {
                if (next != null) {
                    Item<T> item;

                    if (index < _internalList.size() - 1) {
                        item = new Item<>(_internalList.get(index + 1));
                    }
                    else {
                        item = Item.invalid();
                    }

                    next.onNext(item);
                }
            }
        }
    }

    private static <T> IndexHolder<T> getIndexHolder(int index, boolean create, List<T> list, List<IndexHolder<T>> indexList)
    {
        IndexHolder<T> mappedIndex = null;

        for (IndexHolder<T> indexHolder : indexList) {
            if (indexHolder.getIndex() == index) {
                mappedIndex = indexHolder;
                break;
            }
        }

        if (mappedIndex == null && create) {
            mappedIndex = new IndexHolder<>(index, list);

            indexList.add(mappedIndex);
        }

        return mappedIndex;
    }

    private class IndexedTransformList extends TransformList<T, R>
    {
        IndexedTransformList(List<T> list)
        {
            super(list);
        }

        @Override
        protected R transform(T value, final int index)
        {
            final List<T> list = getList();

            try {
                final IndexHolder<T> mappedIndex = getIndexHolder(index, true, list, _indexList);

                PublishProcessor<Item<T>> previous = mappedIndex.getPrevious();
                PublishProcessor<Item<T>> next = mappedIndex.getNext();

                R result = _transform.apply(
                        value,
                        previous.startWith(Flowable.create(new FlowableOnSubscribe<Item<T>>() {
                            @Override
                            public void subscribe(FlowableEmitter<Item<T>> e) throws Exception {
                                int index = mappedIndex.getIndex();

                                if (index > 0) {
                                    e.onNext(new Item<>(list.get(index - 1)));
                                } else {
                                    e.onNext(Item.<T>invalid());
                                }

                                e.onComplete();
                            }
                        }, BackpressureStrategy.LATEST)).replay(1).autoConnect(),
                        next.startWith(Flowable.create(new FlowableOnSubscribe<Item<T>>() {
                            @Override
                            public void subscribe(FlowableEmitter<Item<T>> e) throws Exception {
                                int index = mappedIndex.getIndex();

                                if (index < list.size() - 1) {
                                    e.onNext(new Item<>(list.get(index + 1)));
                                } else {
                                    e.onNext(Item.<T>invalid());
                                }

                                e.onComplete();
                            }
                        }, BackpressureStrategy.LATEST)).replay(1).autoConnect());

                return result;
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public List<R> subList(int fromIndex, int toIndex)
        {
            return new IndexedTransformList(getList().subList(fromIndex, toIndex));
        }
    }

    public IndexedFlowableList(FlowableList<T> list, Function3<T, Flowable<Item<T>>, Flowable<Item<T>>, R> transform)
    {
        _list = list;
        _transform = transform;
    }

    @Override
    public Flowable<Update<R>> updates()
    {
        return _list.updates().map(new Function<Update<T>, Update<R>>() {
            @Override
            public Update<R> apply(Update<T> update) throws Exception
            {
                List<IndexHolder<T>> updatedIndex = new ArrayList<>(_indexList.size());

                for (IndexHolder<T> holder : _indexList) {
                    updatedIndex.add(holder.copy(update.list));
                }

                for (Change change : update.changes) {
                    switch (change.type) {
                        case Moved: {
                            IndexHolder<T> previousIndexHolder;
                            IndexHolder<T> nextIndexHolder;

                            IndexHolder<T> toPreviousIndexHolder;
                            IndexHolder<T> toNextIndexHolder;

                            if (change.to < change.from) {
                                previousIndexHolder = getIndexHolder(change.from - 1, false, update.list, updatedIndex);
                                nextIndexHolder = getIndexHolder(change.from + 1, false, update.list, updatedIndex);

                                toPreviousIndexHolder = getIndexHolder(change.to - 1, false, update.list, updatedIndex);
                                toNextIndexHolder = getIndexHolder(change.to, false, update.list, updatedIndex);

                                adjustIndexes(change.to, change.from, +1, updatedIndex);
                            }
                            else {
                                previousIndexHolder = getIndexHolder(change.from - 1, false, update.list, updatedIndex);
                                nextIndexHolder = getIndexHolder(change.from + 1, false, update.list, updatedIndex);

                                toPreviousIndexHolder = getIndexHolder(change.to, false, update.list, updatedIndex);
                                toNextIndexHolder = getIndexHolder(change.to - 1, false, update.list, updatedIndex);

                                adjustIndexes(change.from + 1, change.to, -1, updatedIndex);
                            }

                            if (previousIndexHolder != null) {
                                previousIndexHolder.setNextDirty();
                            }

                            if (nextIndexHolder != null) {
                                nextIndexHolder.setPreviousDirty();
                            }

                            if (toPreviousIndexHolder != null) {
                                toPreviousIndexHolder.setNextDirty();
                            }

                            if (toNextIndexHolder != null) {
                                toNextIndexHolder.setPreviousDirty();
                            }

                            break;
                        }
                        case Removed: {
                            IndexHolder<T> previousIndexHolder = getIndexHolder(change.from - 1, false, update.list, updatedIndex);
                            IndexHolder<T> nextIndexHolder = getIndexHolder(change.from + 1, false, update.list, updatedIndex);

                            adjustIndexes(change.from, update.list.size(), - 1, updatedIndex);

                            if (previousIndexHolder != null) {
                                previousIndexHolder.setNextDirty();
                            }

                            if (nextIndexHolder != null) {
                                nextIndexHolder.setPreviousDirty();
                            }

                            break;
                        }
                        case Inserted: {
                            IndexHolder<T> previousIndexHolder = getIndexHolder(change.to - 1, false, update.list, updatedIndex);
                            IndexHolder<T> nextIndexHolder = getIndexHolder(change.to, false, update.list, updatedIndex);

                            adjustIndexes(change.to, update.list.size() - 1, 1, updatedIndex);

                            if (previousIndexHolder != null) {
                                previousIndexHolder.setNextDirty();
                            }

                            if (nextIndexHolder != null) {
                                nextIndexHolder.setPreviousDirty();
                            }

                            break;
                        }
                        case Reloaded: {
                            updatedIndex.clear();
                            break;
                        }
                    }
                }

                List<IndexHolder<T>> finalIndexList = new ArrayList<>(updatedIndex.size());

                for (IndexHolder<T> index : updatedIndex) {
                    index.postUpdate();

                    if (index.isActive()) {
                        // the index is referenced still, it must be preserved
                        finalIndexList.add(index);
                    }
                }

                _indexList = finalIndexList;

                return new Update<>(new IndexedTransformList(update.list), update.changes);
            }
        });
    }

    private void adjustIndexes(int start, int end, int adjustment, List<IndexHolder<T>> indexList)
    {
        List<IndexHolder<T>> toUpdate = new ArrayList<>();

        for (int i = start; i <= end; ++i) {
            IndexHolder<T> indexHolder = getIndexHolder(i, false, null, indexList);

            if (indexHolder != null) {
                toUpdate.add(indexHolder);
            }
        }

        for (IndexHolder<T> indexHolder : toUpdate) {
            indexHolder.setIndex(indexHolder.getIndex() + adjustment);
        }
    }
}
