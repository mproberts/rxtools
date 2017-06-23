package com.github.mproberts.rxtools;

import rx.functions.Func1;

import java.util.Collections;
import java.util.List;

public class DifferentialObservableList<T> extends BaseObservableList<T>
{
    private static <T> List<Change> computeDiff(List<T> before, List<T> after)
    {
        return Collections.singletonList(Change.reloaded());
    }

    public void update(List<T> updatedList)
    {
        applyUpdate(new Func1<List<T>, Update<T>>() {
            @Override
            public Update<T> call(List<T> list)
            {
                if (list == null) {
                    return new Update<>(updatedList, Collections.singletonList(Change.reloaded()));
                }

                List<Change> changes = computeDiff(list, updatedList);

                return new Update<>(updatedList, changes);
            }
        });
    }
}
