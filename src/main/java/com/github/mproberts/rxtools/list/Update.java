package com.github.mproberts.rxtools.list;

import com.github.mproberts.rxtools.map.SubjectMap;

import java.util.Collections;
import java.util.List;

/**
 * An update represents the delta between the previous list state and the new list
 * state, including an immutable copy of the new state.
 *
 * The change list may contain any number of changes and is designed such that:
 * L0 + C1 + C2 + C3 = L, where L0 is the previous update emitted from the
 * list and C1, ... are the changes contained within the update.
 *
 * The list contained within the update is immutable. A new list will be sent with
 * every update, this should be taken into consideration when using observable lists
 * as you may wish to use an ID as the value of your list and map the value using a
 * repository of some kind. A {@link SubjectMap} is a
 * useful candidate for applying this pattern.
 * @param <T> The type of values contained in the list
 */
public final class Update<T>
{
    /**
     * The current state of the underlying list
     */
    public final List<T> list;

    /**
     * The set of changes which, when applied to the prior state, produce the new list
     */
    public final List<Change> changes;

    Update(List<T> list, Change change)
    {
        this(list, Collections.singletonList(change));
    }

    Update(List<T> list, List<Change> changes)
    {
        this.list = list;
        this.changes = changes;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();

        for (Change change : changes) {
            if (builder.length() > 0) {
                builder.append(", ");
            }

            builder.append(change.toString());
        }

        return "list=[" + list.size() + "], changes={" + builder.toString() + "}";
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof Update)) {
            return false;
        }

        Update other = (Update) obj;

        return other.list.equals(list)
                && other.changes.equals(changes);
    }

    @Override
    public int hashCode()
    {
        return list.hashCode() | (changes.hashCode() << 16);
    }
}
