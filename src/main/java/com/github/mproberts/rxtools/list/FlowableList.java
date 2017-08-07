package com.github.mproberts.rxtools.list;

import com.github.mproberts.rxtools.map.SubjectMap;
import com.github.mproberts.rxtools.types.Item;
import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Function3;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Represents a list with changes to the underlying dataset being emitted as updates including
 * a copy of the data and a changeset to transform from one list state to the next.
 * @param <T> The value type of the list
 */
public abstract class FlowableList<T>
{
    /**
     * A stream of updates that represent all changes to the underlying list. When first subscribed,
     * the list will emit a reload event immediately, if there is an existing state for the list
     * already available.
     * @return An Flowable bound to the update stream of the list
     */
    public abstract Flowable<Update<T>> updates();

    /**
     * Creates an FlowableList which will contain all and only the provided list items
     * @param list The list of items to wrap
     * @return The wrapped FlowableList
     */
    public static <T> FlowableList<T> of(List<T> list)
    {
        return new SingletonFlowableList<>(list);
    }

    /**
     * See {@link #of(List) of}.
     */
    public static <T> FlowableList<T> of(T... list)
    {
        return of(Arrays.asList(list));
    }

    public static FlowableList concatGeneric(FlowableList<? extends FlowableList> list)
    {
        return new ConcatFlowableList((FlowableList) list);
    }

    /**
     * Joins the list of lists into an FlowableList of all values contained within the list of lists. Change
     * events for individual and lists of items are automatically propagated to subscribers to the concatenated
     * result list.
     * @param list The list of items to wrap
     * @param <T> The type of elements
     * @return A new FlowableList with the contents of all supplied lists
     */
    @SuppressWarnings("unchecked cast")
    public static <T> FlowableList<T> concat(FlowableList<? extends FlowableList<T>> list)
    {
        return new ConcatFlowableList((FlowableList) list);
    }

    /**
     * Transforms an FlowableList containing VisibilityState items into an FlowableList
     * which includes in its emissions the changes in visibility status of the items within the
     * original list
     * @param list The list to wrap
     * @return A new FlowableList
     */
    public static <T> FlowableList<T> flatten(Flowable<? extends FlowableList<T>> list)
    {
        return new FlatMapFlowableList<>(list);
    }

    /**
     * Transforms an FlowableList containing VisibilityState items into an FlowableList
     * which includes in its emissions the changes in visibility status of the items within the
     * original list
     * @param list The list to wrap
     * @return A new FlowableList
     */
    @SuppressWarnings("unchecked cast")
    public static <T, S extends FlowableList<? extends VisibilityState<T>>> FlowableList<T> collapseVisibility(S list)
    {
        return new VisibilityStateFlowableList<>((FlowableList<VisibilityState<T>>) list);
    }

    /**
     * See {@link #diff(FlowableList, boolean) diff}.
     */
    public static <T> FlowableList<T> diff(Flowable<List<T>> listStream)
    {
        return diff(listStream, true);
    }

    /**
     * Observes a stream of type List and computes the diff between successive emissions. The
     * wrapped FlowableList will emit the new list state when new emissions are available
     * along with the diff which transforms the previous into the next state.
     * @param listStream The list to wrap
     * @param detectMoves Indicates whether to apply move calculation to the diff
     * @return A new FlowableList
     */
    public static <T> FlowableList<T> diff(Flowable<List<T>> listStream, boolean detectMoves)
    {
        return new DifferentialFlowableList<>(listStream, detectMoves);
    }

    /**
     * See {@link #concat(FlowableList) concat}.
     */
    public static <T> FlowableList concat(List<? extends FlowableList<T>> lists)
    {
        return concat(of(lists));
    }

    /**
     * Wraps the supplied list, calling the transform method when the get method is called for a specific index.
     * @param transform A function transforming the source to the target type
     * @param <R> The type of the mapped value
     * @return A new FlowableList which has values mapped via the supplied transform
     */
    public <R> FlowableList<R> transform(final Function<T, R> transform)
    {
        final FlowableList<T> list = this;

        return new TransformFlowableList<>(list, new Function<List<T>, List<R>>() {
            @Override
            public List<R> apply(List<T> list)
            {
                return new TransformList.SimpleTransformList<>(list, transform);
            }
        });
    }

    public <R> FlowableList<R> indexedTransform(final Function3<T, Flowable<Item<T>>, Flowable<Item<T>>, R> transform)
    {
        return new IndexedFlowableList<>(this, transform);
    }

    /**
     * Calls the supplied prefetch method on the amount before and after a requested index on every get call. This is
     * useful for flowable lists of indexes which can be prefetched before a scroll event or as a new batch of content
     * is requested.
     * @param fetcher The prefetching method to run on each requested item
     * @param beforeAmount The number of items to prefetch after the current index
     * @param afterAmount The number of items to prefetch after the current index
     * @return
     */
    public FlowableList<T> prefetch(final int beforeAmount, final int afterAmount, final Consumer<Collection<T>> fetcher)
    {
        final FlowableList<T> list = this;

        return new TransformFlowableList<>(list, new Function<List<T>, List<T>>() {
            @Override
            public List<T> apply(List<T> list) throws Exception {
                return new PrefetchList<>(list, beforeAmount, afterAmount, fetcher);
            }
        });
    }

    /**
     * See {@link #transform(Function transform}.
     * @param list The list to wrap
     * @param mapping A SubjectMap used to maps input keys onto observable values
     * @param <R> The type of the mapped value
     * @return A new FlowableList which has values mapped via the supplied SubjectMap
     */
    public <R> FlowableList<Flowable<R>> transform(final SubjectMap<T, R> mapping)
    {
        return transform(new Function<T, Flowable<R>>() {
            @Override
            public Flowable<R> apply(T t)
            {
                return mapping.get(t);
            }
        });
    }

    /**
     * Wraps the supplied list with the ability to buffer update to the underlying list for the provided timespan
     * emitting a single changeset when the buffer period elapses
     * @param timespan
     *            the period of time each buffer collects items before it is emitted and replaced with a new
     *            buffer
     * @param unit
     *            the unit of time which applies to the {@code timespan} argument
     * @param scheduler
     *            the {@link Scheduler} to use when determining the end and start of a buffer
     * @return A new observable with the buffer window applied to all updates
     */
    public FlowableList<T> buffer(long timespan, TimeUnit unit, Scheduler scheduler)
    {
        return new BufferedFlowableList<>(this, timespan, unit, scheduler);
    }
}
