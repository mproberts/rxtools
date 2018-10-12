package com.github.mproberts.rxtools.list;

import io.reactivex.*;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class ConcatFlowableList extends BaseFlowableList
{
    private final List<ListSubscription> _subscriptions = new ArrayList<>();

    private final FlowableList<FlowableList<?>> _lists;
    private Flowable<Update> _updateObservable;

    private class ListSubscription implements Consumer<Update>
    {
        private final FlowableList<?> _flowableList;
        private final List<Change> _initialChanges;
        private Disposable _subscription;
        private AtomicBoolean _alreadyRunning = new AtomicBoolean(true);
        private List<?> _latest;
        private int _index;

        public ListSubscription(int index, List<Change> initialChanges, FlowableList<?> flowableList)
        {
            _initialChanges = initialChanges;
            _flowableList = flowableList;
            _subscription = _flowableList
                    .updates()
                    .subscribe(this);

            _index = index;
            _alreadyRunning.set(false);
            _latest = new ArrayList<>();
        }

        public int size()
        {
            return _latest.size();
        }

        public void setIndex(int index)
        {
            _index = index;
        }

        public void unsubscribe()
        {
            _subscription.dispose();
        }

        @Override
        public void accept(final Update update)
        {
            _latest = update.list;

            if (_alreadyRunning.get()) {
                // enqueue changes immediately
                _initialChanges.addAll(update.changes);
            }
            else {
                applyUpdate(new Function<List, Update>() {
                    @Override
                    public Update apply(List list)
                    {
                        int offset = 0;

                        for (int i = 0; i < _index; ++i) {
                            offset += _subscriptions.get(i).size();
                        }

                        _latest = update.list;

                        List<Change> changes = adjustChanges(offset, offset, update.changes);

                        return new Update(getCurrentList(), changes);
                    }
                });
            }
        }

        public List<?> list()
        {
            return _latest;
        }
    }
    class ConcatUpdateSubscription implements Consumer<Update<FlowableList<?>>>, Disposable
    {
        final AtomicBoolean _isFirst = new AtomicBoolean(true);
        private Emitter<Update> _firstEmitter;
        private AtomicInteger _refCount = new AtomicInteger(0);
        private Disposable _subscription;

        ConcatUpdateSubscription(Emitter<Update> firstEmitter)
        {
            _firstEmitter = firstEmitter;
        }

        @Override
        public void accept(final Update<FlowableList<?>> listsUpdate)
        {
            boolean isFirstEmission = _isFirst.getAndSet(false);

            if (isFirstEmission) {
                int i = 0;
                for (FlowableList<?> flowableList : listsUpdate.list) {
                    addSubscription(i++, listsUpdate.list);
                }

                ConcatList currentList = getCurrentList();

                List oldPreviousList = setPreviousList(currentList);

                // if this is the very first emission, we are responsible for forcing
                // the emission of the reload, otherwise, the base observable
                // will step in and emit on subscribe
                if (oldPreviousList == null) {
                    _firstEmitter.onNext(new Update(currentList, Change.reloaded()));
                }
            }
            else {
                List<Change> changes = new ArrayList<>();

                for (Change change : listsUpdate.changes) {
                    int fromOffset = 0;
                    int toOffset = 0;

                    for (int i = 0; i < change.from; ++i) {
                        fromOffset += _subscriptions.get(i).size();
                    }

                    for (int i = 0; i < change.to; ++i) {
                        toOffset += _subscriptions.get(i).size();
                    }

                    switch (change.type) {
                        case Inserted: {
                            addSubscription(change.to, listsUpdate.list);

                            for (int i = change.to + 1; i < _subscriptions.size(); ++i) {
                                ListSubscription subscription = _subscriptions.get(i);

                                subscription.setIndex(i + 1);
                            }

                            ListSubscription subscription = _subscriptions.get(change.to);

                            for (int i = 0; i < subscription.list().size(); ++i) {
                                changes.add(Change.inserted(i + toOffset));
                            }
                            break;
                        }
                        case Moved: {
                            ListSubscription subscription = _subscriptions.remove(change.from);

                            _subscriptions.add(change.to, subscription);
                            subscription.setIndex(change.to);

                            if (change.from < change.to) {
                                for (int i = change.from; i < change.to; ++i) {
                                    ListSubscription movedSubscription = _subscriptions.get(i);

                                    movedSubscription.setIndex(i);
                                }
                            }
                            else {
                                for (int i = change.to + 1; i <= change.from; ++i) {
                                    ListSubscription movedSubscription = _subscriptions.get(i);

                                    movedSubscription.setIndex(i);
                                }
                            }

                            toOffset = 0;

                            for (int i = 0; i < change.to; ++i) {
                                toOffset += _subscriptions.get(i).size();
                            }

                            for (int i = 0; i < subscription.list().size(); ++i) {
                                changes.add(Change.moved(i + fromOffset, i + toOffset));
                            }
                            break;
                        }
                        case Removed: {
                            ListSubscription subscription = _subscriptions.remove(change.from);
                            subscription.unsubscribe();

                            for (int i = 0; i < subscription.list().size(); ++i) {
                                changes.add(Change.removed(i + fromOffset));
                            }
                            break;
                        }
                        case Reloaded: {
                            changes.add(Change.reloaded());

                            for (ListSubscription subscription : _subscriptions) {
                                subscription.unsubscribe();
                            }

                            _subscriptions.clear();

                            for (int i = 0; i < listsUpdate.list.size(); ++i) {
                                addSubscription(i++, listsUpdate.list);
                            }
                            break;
                        }
                    }
                }

                ConcatList currentList = getCurrentList();

                setPreviousList(currentList);

                _firstEmitter.onNext(new Update(currentList, changes));
            }
        }

        @Override
        public void dispose()
        {
            int subscriptions = _refCount.decrementAndGet();

            if (subscriptions == 0) {
                for (ListSubscription subscription : _subscriptions) {
                    subscription.unsubscribe();
                }

                _subscriptions.clear();

                _updateObservable = null;
                _subscription.dispose();
            }
        }

        @Override
        public boolean isDisposed()
        {
            return _refCount.get() == 0;
        }

        public void setSubscription(Disposable subscription)
        {
            _subscription = subscription;
        }

        public void subscribe()
        {
            _refCount.incrementAndGet();
        }
    }

    class ConcatOnSubscribeAction implements FlowableOnSubscribe<Update>
    {
        ConcatUpdateSubscription _subscriber;

        @Override
        public void subscribe(final FlowableEmitter<Update> updateEmitter)
        {
            if (_subscriber == null) {
                _subscriber = new ConcatUpdateSubscription(updateEmitter);

                final Disposable subscribe = _lists.updates()
                        .subscribe(_subscriber);

                _subscriber.setSubscription(subscribe);
            }

            _subscriber.subscribe();

            final AtomicBoolean isUnsubscribed = new AtomicBoolean();

            updateEmitter.setDisposable(new Disposable() {
                @Override
                public void dispose()
                {
                    _subscriber.dispose();

                    isUnsubscribed.set(true);
                }

                @Override
                public boolean isDisposed()
                {
                    return isUnsubscribed.get();
                }
            });
        }
    }

    public Flowable<Update> createUpdater()
    {
        if (_updateObservable == null) {
            _updateObservable = Flowable.create(new ConcatOnSubscribeAction(), BackpressureStrategy.LATEST);
        }


        return _updateObservable;
    }

    ConcatFlowableList(FlowableList<FlowableList<?>> lists)
    {
        _lists = lists;
    }

    private ConcatList getCurrentList()
    {
        List<List> lists = new ArrayList<>();

        for (ListSubscription subscription : _subscriptions) {
            List<?> list = subscription.list();

            if (list != null) {
                lists.add(list);
            }
        }

        return new ConcatList(lists.toArray(new List[lists.size()]));
    }

    private List<Change> adjustChanges(int fromOffset, int toOffset, List<Change> changes)
    {
        List<Change> updatedChanges = new ArrayList<>(changes.size());

        for (Change change : changes) {
            switch (change.type) {
                case Inserted:
                    updatedChanges.add(Change.inserted(change.to + toOffset));
                    break;
                case Removed:
                    updatedChanges.add(Change.removed(change.from + fromOffset));
                    break;
                case Moved:
                    updatedChanges.add(Change.moved(
                            change.from + fromOffset,
                            change.to + toOffset));
                    break;
                case Reloaded:
                    updatedChanges.add(Change.reloaded());
                    break;
            }
        }

        return updatedChanges;
    }

    private List<Change> addSubscription(int position, List<FlowableList<?>> lists)
    {
        int offset = 0;

        for (int i = 0; i < position; ++i) {
            offset += _subscriptions.get(i).size();
        }

        FlowableList<?> flowableList = lists.get(position);
        List<Change> changes = new ArrayList<>();

        _subscriptions.add(position, new ListSubscription(position, changes, flowableList));

        // populate initial adds if there is an emission from
        // the list already
        changes = adjustChanges(offset, offset, changes);

        return changes;
    }

    @Override
    public Flowable updates()
    {
        return super.updates().mergeWith(createUpdater());
    }
}
