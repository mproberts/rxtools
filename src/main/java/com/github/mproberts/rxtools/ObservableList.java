package com.github.mproberts.rxtools;

import rx.Observable;

import java.util.Collections;
import java.util.List;

public interface ObservableList<T>
{
    class Change
    {
        enum Type
        {
            Moved,
            Inserted,
            Removed,
            Reloaded
        }

        public final Type type;
        public final int from;
        public final int to;

        public static Change moved(int from, int to)
        {
            return new Change(Type.Moved, from, to);
        }

        public static Change inserted(int to)
        {
            return new Change(Type.Inserted, to, to);
        }

        public static Change removed(int from)
        {
            return new Change(Type.Removed, from, from);
        }

        public static Change reloaded()
        {
            return new Change(Type.Reloaded, -1, -1);
        }

        Change(Type type, int from, int to)
        {
            this.type = type;
            this.from = from;
            this.to = to;
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

    final class Update<T>
    {
        private final List<T> _list;
        private final List<Change> _changes;

        Update(List<T> list, Change change)
        {
            this(list, Collections.singletonList(change));
        }

        Update(List<T> list, List<Change> changes)
        {
            _list = list;
            _changes = changes;
        }

        public List<Change> changes()
        {
            return _changes;
        }

        public List<T> list()
        {
            return _list;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (!(obj instanceof Update)) {
                return false;
            }

            Update other = (Update) obj;

            return other.list().equals(list())
                    && other.changes().equals(changes());
        }

        @Override
        public int hashCode()
        {
            return list().hashCode()
                    | (changes().hashCode() << 16);
        }
    }

    Observable<Update<T>> updates();
}
