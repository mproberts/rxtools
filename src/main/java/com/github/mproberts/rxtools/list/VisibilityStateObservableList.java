package com.github.mproberts.rxtools.list;

import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class VisibilityStateObservableList<T> extends BaseObservableList<T>
{
    private final ObservableList<VisibilityState<T>> _list;
    private final List<ItemSubscription> _listVisibility;
    private Subscription _subscription;

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

        insertedItem.isVisible().subscribe(itemSubscription);
    }

    @Override
    public Observable<Update<T>> updates() {
        if (_subscription == null) {
            Action1<Update<VisibilityState<T>>> action1 = new Action1<Update<VisibilityState<T>>>() {
                @Override
                public void call(final Update<VisibilityState<T>> update) {
                    for (final Change change : update.changes) {
                        switch (change.type) {
                            case Moved:
                                _listVisibility.add(change.to, _listVisibility.remove(change.from));
                                break;
                            case Inserted:
                                addItem(change.to, update.list);

                                for (int i = change.to + 1, length = _listVisibility.size(); i < length; ++i) {
                                    _listVisibility.get(i).updateIndex(i);
                                }
                                break;
                            case Removed:
                                //Subscription remove = _listVisibility.remove(change.from);
                                //remove.unsubscribe();
                                break;
                            case Reloaded:
                                //for (Subscription subscription : _listVisibility) {
                                //    subscription.unsubscribe();
                                //}

                                _listVisibility.clear();

                                for (int i = 0, length = update.list.size(); i < length; ++i) {
                                    addItem(i, update.list);
                                }
                                break;
                        }
                    }
                }
            };
            _subscription = _list.updates().subscribe(action1);
        }

        return super.updates();
    }

    private class ItemSubscription implements Action1<Boolean> {
        private final VisibilityState<T> _insertedItem;
        AtomicInteger _currentVirtualIndex;
        AtomicInteger _currentIndex;
        AtomicBoolean _isVisible;

        public ItemSubscription(int index, VisibilityState<T> insertedItem) {
            this._insertedItem = insertedItem;
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
        public void call(final Boolean updatedVisibility) {
            if (_isVisible.get() == updatedVisibility) {
                return;
            }

            _isVisible.set(updatedVisibility);

            applyUpdate(new Func1<List<T>, Update<T>>() {
                @Override
                public Update<T> call(List<T> currentList) {
                    ArrayList<T> listToUpdate;

                    if (currentList == null) {
                        listToUpdate = new ArrayList<>();
                    }
                    else {
                        listToUpdate = new ArrayList<>(currentList);
                    }

                    int index = _currentIndex.get();
                    int virtualIndex = _currentVirtualIndex.get();

                    if (updatedVisibility) {
                        listToUpdate.add(index, _insertedItem.get());

                        for (int i = index + 1, length = _listVisibility.size(); i < length; ++i) {
                            _listVisibility.get(i).updateIndex(i);
                        }

                        return new Update<>(listToUpdate, Change.inserted(index));
                    }
                    else {
                        listToUpdate.remove(virtualIndex);

                        int j = virtualIndex;

                        for (int i = index + 1, length = _listVisibility.size(); i < length; ++i) {
                            ItemSubscription itemSubscription = _listVisibility.get(i);

                            if (itemSubscription._isVisible.get()) {
                                itemSubscription.updateVirtualIndex(j);
                                ++j;
                            }
                        }

                        return new Update<>(listToUpdate, Change.removed(virtualIndex));
                    }
                }
            });
        }
    }
}
