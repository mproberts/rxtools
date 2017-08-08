package com.github.mproberts.rxtools.list;

/**
 * A change is a single modification to a list which transforms it from one state to the next
 */
public class Change
{
    /**
     * The type of the change dictates which behaviour to apply to the list
     */
    public enum Type
    {
        Moved(0),
        Inserted(1),
        Removed(2),
        Reloaded(3);

        final int value;

        Type(int value)
        {
            this.value = value;
        }
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
        return (type.value << 3) | (from) | (to << 16);
    }
}
