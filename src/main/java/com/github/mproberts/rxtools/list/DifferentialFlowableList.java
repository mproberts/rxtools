package com.github.mproberts.rxtools.list;

import difflib.Chunk;
import difflib.Delta;
import difflib.DiffAlgorithm;
import difflib.Patch;
import difflib.myers.MyersDiff;
import io.reactivex.*;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Function;

import java.util.ArrayList;
import java.util.List;

class DifferentialFlowableList<T> extends FlowableList<T>
{
    private final Flowable<Update<T>> _diffTransform;
    private List<T> _previousList;
    private DiffAlgorithm<T> _diffAlgorithm;

    private List<Change> computeDiff(List<T> before, List<T> after)
    {
        Patch<T> patch = _diffAlgorithm.diff(before, after);
        List<Delta<T>> deltas = patch.getDeltas();
        List<Change> changes = new ArrayList<>(deltas.size());

        for (Delta<T> delta : deltas) {
            switch (delta.getType()) {
                case CHANGE: {
                    Chunk<T> original = delta.getOriginal();
                    List<T> originalLines = original.getLines();
                    int originalPosition = original.getPosition();

                    Chunk<T> revised = delta.getRevised();
                    List<T> lines = revised.getLines();
                    int position = revised.getPosition();

                    for (int i = 0; i < originalLines.size(); ++i) {
                        changes.add(Change.removed(originalPosition));
                    }

                    for (int i = 0; i < lines.size(); ++i) {
                        changes.add(Change.inserted(position + i));
                    }
                    break;
                }
                case INSERT: {
                    Chunk<T> revised = delta.getRevised();
                    List<T> lines = revised.getLines();
                    int position = revised.getPosition();

                    for (int i = 0; i < lines.size(); ++i) {
                        changes.add(Change.inserted(position + i));
                    }
                    break;
                }
                case DELETE: {
                    Chunk<T> original = delta.getOriginal();
                    List<T> lines = original.getLines();
                    int position = original.getPosition();

                    for (int i = 0; i < lines.size(); ++i) {
                        changes.add(Change.removed(position));
                    }
                    break;
                }
            }
        }

        return changes;
    }

    DifferentialFlowableList(Flowable<List<T>> list)
    {
        _diffAlgorithm = new MyersDiff<>();

        _diffTransform = list
                .map(new Function<List<T>, Update<T>>() {
                    @Override
                    public Update<T> apply(List<T> ts) {
                        return new Update<>(ts, Change.reloaded());
                    }
                })
                .scan(new BiFunction<Update<T>, Update<T>, Update<T>>() {
                    @Override
                    public Update<T> apply(Update<T> previous, Update<T> next) {
                        if (previous == null) {
                            return next;
                        }

                        List<Change> changes = computeDiff(previous.list, next.list);

                        _previousList = next.list;

                        return new Update<>(next.list, changes);
                    }
                });
    }

    @Override
    public Flowable<Update<T>> updates()
    {
        return _diffTransform
                .startWith(Flowable.create(new FlowableOnSubscribe<Update<T>>() {
                    @Override
                    public void subscribe(FlowableEmitter<Update<T>> updateEmitter) throws Exception {
                        if (_previousList != null) {
                            Update<T> update = new Update<>(new ArrayList<>(_previousList), Change.reloaded());
                            updateEmitter.onNext(update);
                        }

                        updateEmitter.onComplete();
                    }
                }, BackpressureStrategy.LATEST));
    }
}
