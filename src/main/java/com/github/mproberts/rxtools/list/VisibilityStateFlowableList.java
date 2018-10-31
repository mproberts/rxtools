package com.github.mproberts.rxtools.list;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;

class VisibilityStateFlowableList<T> extends BaseFlowableList<T> {
    private final FlowableList<VisibilityState<T>> _list;

    VisibilityStateFlowableList(FlowableList<VisibilityState<T>> list) {
        _list = list;
    }

    @Override
    public Flowable<Update<T>> updates() {
        return FlowableList.diff(_list.updates()
                .switchMap(new Function<Update<VisibilityState<T>>, Flowable<List<T>>>() {
                    @Override
                    public Flowable<List<T>> apply(Update<VisibilityState<T>> update) {
                        List<Flowable<VisibilityPair<T>>> pairs = new ArrayList<>(update.list.size());
                        for (final VisibilityState<T> listItem : update.list) {
                            pairs.add(listItem.isVisible().map(new Function<Boolean, VisibilityPair<T>>() {
                                @Override
                                public VisibilityPair apply(Boolean visible) {
                                    return new VisibilityPair<>(listItem, visible);
                                }
                            }));
                        }

                        return Flowable.combineLatest(pairs, new Function<Object[], List<T>>() {
                            @Override
                            public List<T> apply(Object[] objects) {
                                List<T> items = new ArrayList<>(objects.length);
                                for (Object o : objects) {
                                    VisibilityPair<T> pair = (VisibilityPair) o;
                                    if (pair._visible) {
                                        items.add(pair._item.get());
                                    }
                                }
                                return items;
                            }
                        });
                    }
                })).updates();
    }

    private class VisibilityPair<T> {
        VisibilityState<T> _item;
        boolean _visible;

        VisibilityPair(VisibilityState<T> item, boolean visible) {
            _item = item;
            _visible = visible;
        }
    }
}
