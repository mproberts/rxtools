package com.github.mproberts.rxtools.list;

import com.github.mproberts.rxtools.types.Optional;
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
    private final Flowable<Update<R>> _updates;
    private final FlowableList<T> _list;
    private final Function3<T, Flowable<Optional<T>>, Flowable<Optional<T>>, R> _transform;
    private List<IndexHolder<T>> _indexList = new ArrayList<>();

    private static class IndexHolder<T>
    {
        private final List<T> _internalList;
        private int _index;
        private WeakReference<PublishProcessor<Optional<T>>> _previous;
        private WeakReference<PublishProcessor<Optional<T>>> _next;
        private boolean _previousDirty;
        private boolean _nextDirty;

        private IndexHolder(int index, List<T> internalList)
        {
            this(index, internalList, null, null);
        }

        private IndexHolder(int index, List<T> internalList, WeakReference<PublishProcessor<Optional<T>>> previous, WeakReference<PublishProcessor<Optional<T>>> next)
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

        public PublishProcessor<Optional<T>> getPrevious()
        {
            PublishProcessor<Optional<T>> processor = _previous != null ? _previous.get() : null;

            if (processor == null) {
                processor = PublishProcessor.create();
                _previous = new WeakReference<>(processor);
            }

            return processor;
        }

        public PublishProcessor<Optional<T>> getNext()
        {
            PublishProcessor<Optional<T>> processor = _next != null ? _next.get() : null;

            if (processor == null) {
                processor = PublishProcessor.create();
                _next = new WeakReference<>(processor);
            }

            return processor;
        }

        public boolean isActive()
        {
            PublishProcessor<Optional<T>> next = null;

            if (_next != null) {
                next = _next.get();
            }

            if (next != null) {
                return true;
            }

            PublishProcessor<Optional<T>> previous = null;

            if (_previous != null) {
                previous = _previous.get();
            }

            return previous != null;
        }

        public void postUpdate()
        {
            boolean nextDirty = _nextDirty;
            boolean previousDirty = _previousDirty;
            int index = _index;

            _nextDirty = false;
            _previousDirty = false;

            PublishProcessor<Optional<T>> next = null;
            PublishProcessor<Optional<T>> previous = null;

            if (_next != null) {
                next = _next.get();
            }

            if (_previous != null) {
                previous = _previous.get();
            }

            if (previousDirty) {
                if (previous != null) {
                    Optional<T> item;

                    if (index > 0) {
                        item = Optional.ofNullable(_internalList.get(index - 1));
                    }
                    else {
                        item = Optional.empty();
                    }

                    previous.onNext(item);
                }
            }

            if (nextDirty) {
                if (next != null) {
                    Optional<T> item;

                    synchronized (_internalList) {
                        if (index < _internalList.size() - 1) {
                            item = Optional.ofNullable(_internalList.get(index + 1));
                        }
                        else {
                            item = Optional.empty();
                        }
                    }

                    next.onNext(item);
                }
            }
        }
    }

    private static <T> IndexHolder<T> getIndexHolder(int index, boolean create, List<T> list, List<IndexHolder<T>> indexList)
    {
        IndexHolder<T> mappedIndex = null;

        synchronized (indexList) {
            for (int i = 0, l = indexList.size(); i < l; ++i) {
                IndexHolder<T> indexHolder = indexList.get(i);

                if (indexHolder.getIndex() == index) {
                    mappedIndex = indexHolder;
                    break;
                }
            }

            if (mappedIndex == null && create) {
                mappedIndex = new IndexHolder<>(index, list);

                indexList.add(mappedIndex);
            }
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

                PublishProcessor<Optional<T>> previous = mappedIndex.getPrevious();
                PublishProcessor<Optional<T>> next = mappedIndex.getNext();

                R result = _transform.apply(
                        value,
                        previous.startWith(Flowable.create(new FlowableOnSubscribe<Optional<T>>() {
                            @Override
                            public void subscribe(FlowableEmitter<Optional<T>> e) throws Exception {
                                int index = mappedIndex.getIndex();

                                if (index > 0) {
                                    e.onNext(Optional.ofNullable(list.get(index - 1)));
                                } else {
                                    e.onNext(Optional.<T>empty());
                                }

                                e.onComplete();
                            }
                        }, BackpressureStrategy.LATEST)).replay(1).autoConnect(),
                        next.startWith(Flowable.create(new FlowableOnSubscribe<Optional<T>>() {
                            @Override
                            public void subscribe(FlowableEmitter<Optional<T>> e) throws Exception {
                                int index = mappedIndex.getIndex();

                                if (index < list.size() - 1) {
                                    e.onNext(Optional.ofNullable(list.get(index + 1)));
                                } else {
                                    e.onNext(Optional.<T>empty());
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

    public IndexedFlowableList(FlowableList<T> list, Function3<T, Flowable<Optional<T>>, Flowable<Optional<T>>, R> transform)
    {
        _list = list;
        _transform = transform;
        _updates = _list.updates().map(new Function<Update<T>, Update<R>>() {
            @Override
            public Update<R> apply(Update<T> update) throws Exception {
                List<IndexHolder<T>> updatedIndex = new ArrayList<>(_indexList.size());

                synchronized (_indexList) {
                    for (int i = 0, l = _indexList.size(); i < l; ++i) {
                        IndexHolder<T> holder = _indexList.get(i);

                        updatedIndex.add(holder.copy(update.list));
                    }
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
                            } else {
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

                            adjustIndexes(change.from, update.list.size(), -1, updatedIndex);

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

    @Override
    public Flowable<Update<R>> updates()
    {
        return _updates;
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
