package com.github.mproberts.rxtools.list;

import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class VisibilityStateObservableList<T> extends BaseObservableList<T>
{
    private final ObservableList<VisibilityState<T>> _list;
    private final List<ItemSubscription> _listVisibility;
    private Disposable _subscription;

    VisibilityStateObservableList(ObservableList<VisibilityState<T>> list)
    {
        _list = list;
        _listVisibility = new ArrayList<>();
    }

    private void addItem(final int index, List<VisibilityState<T>> list)
    {
        final VisibilityState<T> insertedItem = list.get(index);

        ItemSubscription itemSubscription = new ItemSubscription(index, insertedItem);

        _listVisibility.add(index, itemSubscription);

        Disposable subscribe = insertedItem.isVisible().subscribe(itemSubscription);

        itemSubscription.setSubscription(subscribe);
    }

    @Override
    public Flowable<Update<T>> updates()
    {
        if (_subscription == null) {
            _subscription = _list.updates().subscribe(new Consumer<Update<VisibilityState<T>>>() {
                @Override
                public void accept(final Update<VisibilityState<T>> update) {
                    for (final Change change : update.changes) {
                        switch (change.type) {
                            case Moved:
                                ItemSubscription moved;
                                final int j, jj;

                                synchronized (_listVisibility) {
                                    moved = _listVisibility.get(change.from);
                                    ItemSubscription target = _listVisibility.get(change.to);
                                    j = moved._currentVirtualIndex.get();
                                    jj = target._currentVirtualIndex.get();
                                    int ci = moved._currentIndex.get();
                                    int cii = target._currentIndex.get();

                                    if (change.from < change.to) {
                                        for (int i = change.from + 1; i < change.to; ++i) {
                                            ItemSubscription itemSubscription = _listVisibility.get(i);
                                            int currentVirtualIndex = itemSubscription._currentVirtualIndex.get();

                                            itemSubscription.updateVirtualIndex(currentVirtualIndex - 1);
                                            itemSubscription.updateIndex(i - 1);
                                        }

                                        moved.updateVirtualIndex(jj);
                                        moved.updateIndex(cii);

                                        target.updateVirtualIndex(j);
                                        target.updateIndex(ci);
                                    }
                                    else {
                                        for (int i = change.to; i < change.from; ++i) {
                                            ItemSubscription itemSubscription = _listVisibility.get(i);
                                            int currentVirtualIndex = itemSubscription._currentVirtualIndex.get();

                                            itemSubscription.updateVirtualIndex(currentVirtualIndex + 1);
                                            itemSubscription.updateIndex(i + 1);
                                        }

                                        moved.updateVirtualIndex(jj);
                                        moved.updateIndex(cii);

                                        target.updateVirtualIndex(j);
                                        target.updateIndex(ci);
                                    }

                                    ItemSubscription movedItem = _listVisibility.remove(change.from);

                                    _listVisibility.add(change.to, movedItem);
                                }

                                if (moved._isVisible.get()) {
                                    applyUpdate(new Function<List<T>, Update<T>>() {
                                        @Override
                                        public Update<T> apply(List<T> currentList) {
                                            ArrayList<T> listToUpdate = new ArrayList<>(currentList);

                                            listToUpdate.add(change.to, listToUpdate.remove(change.from));

                                            return new Update<>(listToUpdate, Change.moved(j, jj));
                                        }
                                    });
                                }
                                break;
                            case Inserted:
                                synchronized (_listVisibility) {
                                    addItem(change.to, update.list);

                                    for (int i = change.to + 1, length = _listVisibility.size(); i < length; ++i) {
                                        _listVisibility.get(i).updateIndex(i);
                                    }
                                }
                                break;
                            case Removed:
                                ItemSubscription removed;
                                synchronized (_listVisibility) {
                                    removed = _listVisibility.remove(change.from);
                                }

                                if (removed != null) {
                                    removed.unsubscribe();
                                }
                                break;
                            case Reloaded:
                                List<ItemSubscription> listVisibility;

                                synchronized (_listVisibility) {
                                    listVisibility = new ArrayList<>(_listVisibility);

                                    _listVisibility.clear();
                                }

                                for (ItemSubscription itemSubscription : listVisibility) {
                                    itemSubscription.unsubscribe();
                                }

                                synchronized (_listVisibility) {
                                    for (int i = 0, length = update.list.size(); i < length; ++i) {
                                        addItem(i, update.list);
                                    }
                                }
                                break;
                        }
                    }
                }
            });
        }

        return super.updates();
    }

    private class ItemSubscription implements Consumer<Boolean>
    {
        private final VisibilityState<T> _insertedItem;
        private final AtomicInteger _currentVirtualIndex;
        private final AtomicInteger _currentIndex;
        private final AtomicBoolean _isVisible;
        private Disposable _subscription;

        public ItemSubscription(int index, VisibilityState<T> insertedItem)
        {
            _insertedItem = insertedItem;
            _currentIndex = new AtomicInteger(index);
            _currentVirtualIndex = new AtomicInteger(index);
            _isVisible = new AtomicBoolean();
        }

        public void updateIndex(int index)
        {
            _currentIndex.set(index);
        }

        public void updateVirtualIndex(int index)
        {
            _currentVirtualIndex.set(index);
        }

        @Override
        public void accept(final Boolean updatedVisibility)
        {
            if (_isVisible.getAndSet(updatedVisibility) == updatedVisibility) {
                return;
            }

            applyUpdate(new Function<List<T>, Update<T>>() {
                @Override
                public Update<T> apply(List<T> currentList) {
                    ArrayList<T> listToUpdate;

                    if (currentList == null) {
                        listToUpdate = new ArrayList<>();
                    }
                    else {
                        listToUpdate = new ArrayList<>(currentList);
                    }

                    int index = _currentIndex.get();
                    int virtualIndex = _currentVirtualIndex.get();
                    Update<T> update;
                    int j;

                    if (updatedVisibility) {
                        listToUpdate.add(index, _insertedItem.get());

                        j = virtualIndex + 1;
                        update = new Update<>(listToUpdate, Change.inserted(index));
                    }
                    else {
                        listToUpdate.remove(virtualIndex);

                        j = virtualIndex;
                        update = new Update<>(listToUpdate, Change.removed(virtualIndex));
                    }

                    for (int i = index + 1, length = _listVisibility.size(); i < length; ++i) {
                        ItemSubscription itemSubscription = _listVisibility.get(i);

                        itemSubscription.updateVirtualIndex(j);

                        if (itemSubscription._isVisible.get()) {
                            ++j;
                        }
                    }

                    return update;
                }
            });
        }

        public void setSubscription(Disposable subscription)
        {
            _subscription = subscription;
        }

        public void unsubscribe()
        {
            _subscription.dispose();

            accept(false);
        }
    }
}
