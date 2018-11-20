package com.github.mproberts.rxtools.list;

import io.reactivex.*;
import io.reactivex.functions.Function;
import org.reactivestreams.Publisher;

import java.util.ArrayList;
import java.util.List;

class ConcatFlowableList extends FlowableList
{
    private final FlowableList<FlowableList<?>> _lists;

    ConcatFlowableList(FlowableList<FlowableList<?>> lists)
    {
        _lists = lists;
    }

    private class PositionedUpdate {
        final int position;
        final Update update;
        boolean seen = false;

        private PositionedUpdate(int position, Update update) {
            this.position = position;
            this.update = update;
        }
    }

    @Override
    @SuppressWarnings("unchecked cast")
    public Flowable<Update> updates()
    {
        return _lists.updates().switchMap(new Function<Update<FlowableList<?>>, Publisher<Update>>() {
            @Override
            public Publisher<Update> apply(Update<FlowableList<?>> rootUpdates) throws Exception {
                Flowable[] sublistUpdates = new Flowable[rootUpdates.list.size()];

                for (int i = 0, s = rootUpdates.list.size(); i < s; ++i) {
                    final int index = i;

                    sublistUpdates[i] = rootUpdates.list.get(i).updates().map(new Function<Update, PositionedUpdate>() {
                        @Override
                        public PositionedUpdate apply(Update update) throws Exception {
                            return new PositionedUpdate(index, update);
                        }
                    });
                }

                return Flowable.combineLatest(sublistUpdates, new Function<Object[], Update<?>>() {

                        @Override
                        public Update<?> apply(Object[] positionedUpdates) throws Exception {
                            int offset = 0;
                            boolean reloaded = false;
                            List[] allLists = new List[positionedUpdates.length];
                            List<Change> changes = new ArrayList<>();

                            for (int i = 0; i < positionedUpdates.length; ++i) {
                                PositionedUpdate positionedUpdate = (PositionedUpdate) positionedUpdates[i];

                                allLists[i] = positionedUpdate.update.list;

                                if (!positionedUpdate.seen) {
                                    positionedUpdate.seen = true;

                                    if (!reloaded) {
                                        for (Change change : (List<Change>) positionedUpdate.update.changes) {
                                            if (change.type == Change.Type.Reloaded) {
                                                reloaded = true;
                                                break;
                                            }

                                            switch (change.type) {
                                                case Inserted:
                                                    changes.add(Change.inserted(change.to + offset));
                                                    break;
                                                case Removed:
                                                    changes.add(Change.removed(change.from + offset));
                                                    break;
                                                case Moved:
                                                    changes.add(Change.moved(change.from + offset, change.to + offset));
                                                    break;
                                            }
                                        }
                                    }
                                }

                                offset += positionedUpdate.update.list.size();
                            }

                            if (reloaded) {
                                changes.clear();
                                changes.add(Change.reloaded());
                            }

                            return new Update(new ConcatList(allLists), changes);
                        }
                    });
            }
        });
    }
}
