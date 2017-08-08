package com.github.mproberts.rxtools.list;

import java.util.ArrayList;
import java.util.List;

public class TestTools
{
    private TestTools()
    {
    }

    public static <T> List<T> applyChanges(List<T> before, List<T> after, List<Change> changes)
    {
        final List<T> target = new ArrayList<>();

        target.addAll(before);

        for (Change change : changes) {
            switch (change.type) {
                case Inserted:
                    target.add(change.to, null);
                    break;
                case Moved:
                    T item = target.remove(change.from);
                    target.add(change.to, item);
                    break;
                case Removed:
                    target.remove(change.from);
                    break;
            }
        }

        // repair nulls
        for (int i = 0; i < target.size(); ++i) {
            if (target.get(i) == null) {
                target.set(i, after.get(i));
            }
        }

        return target;
    }
}
