package com.github.mproberts.rxtools.list;

import io.reactivex.Flowable;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Function;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by mproberts on 2018-12-06.
 */
public class HeaderFlowableList extends FlowableList {

    private final FlowableList _list;
    private final Object _header;

    public HeaderFlowableList(FlowableList list, Object header) {
        _list = list;
        _header = header;
    }

    @Override
    public Flowable<Update> updates() {
        return _list.updates()
                .startWith(new Update(new ArrayList(), Change.reloaded()))
                .scan(new BiFunction<Update, Update, Update>() {
            @Override
            public Update apply(Update previous, Update update) throws Exception {
                if (update.list.isEmpty()) {
                    return new Update(new ArrayList(), Change.reloaded());
                }

                ArrayList<Change> changes = new ArrayList<>();

                for (Change change : (List<Change>) update.changes) {
                    switch (change.type) {
                        case Inserted:
                            changes.add(Change.inserted(change.to + 1));
                            break;
                        case Removed:
                            changes.add(Change.removed(change.from + 1));
                            break;
                        case Moved:
                            changes.add(Change.moved(change.from + 1, change.to + 1));
                            break;
                        default:
                            changes.add(Change.reloaded());
                            break;
                    }
                }

                if (previous.list.isEmpty()) {
                    changes.add(0, Change.inserted(0));
                }

                return new Update(new ConcatList(Arrays.asList(_header), update.list), changes);
            }
        })
                .skip(1); // Always skip the first emission (the startsWith) since we are guaranteeing that to be not of any use.
    }
}
