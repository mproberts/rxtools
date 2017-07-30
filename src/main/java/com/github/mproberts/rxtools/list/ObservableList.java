package com.github.mproberts.rxtools.list;

import io.reactivex.Flowable;

import java.util.Collections;
import java.util.List;

/**
 * Represents a list with changes to the underlying dataset being emitted as updates including
 * a copy of the data and a changeset to transform from one list state to the next.
 * @param <T> The value type of the list
 */
public interface ObservableList<T>
{
    /**
     * A change is a single modification to a list which transforms it from one state to the next
     */
    class Change
    {
        /**
         * The type of the change dictates which behaviour to apply to the list
         */
        public enum Type
        {
            Moved,
            Inserted,
            Removed,
            Reloaded
        }

        public final Type type;
        public final int from;
        public final int to;

        /**
         *
         * @param from
         * @param to
         * @return
         */
        public static Change moved(int from, int to)
        {
            return new Change(Type.Moved, from, to);
        }

        /**
         *
         * @param to
         * @return
         */
        public static Change inserted(int to)
        {
            return new Change(Type.Inserted, to, to);
        }

        /**
         *
         * @param from
         * @return
         */
        public static Change removed(int from)
        {
            return new Change(Type.Removed, from, from);
        }

        /**
         *
         * @return
         */
        public static Change reloaded()
        {
            return new Change(Type.Reloaded, Integer.MIN_VALUE, Integer.MIN_VALUE);
        }

        Change(Type type, int from, int to)
        {
            this.type = type;
            this.from = from;
            this.to = to;
        }

        @Override
        public String toString()
        {
            switch (type) {
                case Moved:
                    return "moved(" + from + " -> " + to + ")";
                case Inserted:
                    return "inserted(" + to + ")";
                case Removed:
                    return "removed(" + from + ")";
                default:
                    return "reloaded";
            }
        }

        @Override
        public boolean equals(Object obj)
        {
            if (!(obj instanceof Change)) {
                return false;
            }

            Change other = (Change) obj;

            return other.type == type
                    && other.from == from
                    && other.to == to;
        }

        @Override
        public int hashCode()
        {
            int typeId = 0;

            switch (type) {
                case Moved:
                    typeId = 0;
                    break;
                case Inserted:
                    typeId = 1;
                    break;
                case Removed:
                    typeId = 2;
                    break;
                case Reloaded:
                    typeId = 3;
                    break;
            }

            return (typeId << 3) | (from) | (to << 16);
        }
    }

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
     * repository of some kind. A {@link com.github.mproberts.rxtools.SubjectMap} is a
     * useful candidate for applying this pattern.
     * @param <T> The type of values contained in the list
     */
    final class Update<T>
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
            return list.hashCode()
                    | (changes.hashCode() << 16);
        }
    }

    /**
     * A stream of updates that represent all changes to the underlying list. When first subscribed,
     * the list will emit a reload event immediately, if there is an existing state for the list
     * already available.
     * @return An Flowable bound to the update stream of the list
     */
    Flowable<Update<T>> updates();
}
