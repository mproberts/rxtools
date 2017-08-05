package com.github.mproberts.rxtools.list;

import com.github.mproberts.rxtools.map.SubjectMap;
import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.functions.Function;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A utility class providing access to wrapping methods for working with FlowableList
 * instances. This class provides methods for creating, transforming, merging and managing
 * ObservableLists.
 */
public final class ObservableLists
{
    private ObservableLists()
    {
        // utility class
    }

    /**
     * Creates an FlowableList which will contain all and only the provided list items
     * @param list The list of items to wrap
     * @param <T> The type of elements
     * @return The wrapped FlowableList
     */
    public static <T> FlowableList<T> singletonList(List<T> list)
    {
        return new SingletonFlowableList<>(list);
    }

    /**
     * See {@link #singletonList(List) singletonList}.
     */
    public static <T> FlowableList<T> singletonList(T... list)
    {
        return singletonList(Arrays.asList(list));
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
    public static <T> FlowableList<T> concat(FlowableList<? extends FlowableList<T>> list)
    {
        return new ConcatFlowableList((FlowableList) list);
    }

    /**
     * See {@link #concat(FlowableList) concat}.
     */
    public static <T> FlowableList concat(List<? extends FlowableList<T>> lists)
    {
        return concat(singletonList(lists));
    }

    /**
     * Wraps the supplied list, calling the transform method when the get method is called for a specific index.
     * @param list The list to wrap
     * @param transform A function transforming the source to the target type
     * @param <T> The type of the source
     * @param <R> The type of the mapped value
     * @return A new FlowableList which has values mapped via the supplied transform
     */
    public static <T, R> FlowableList<R> transform(final FlowableList<T> list, final Function<T, R> transform)
    {
        return new TransformFlowableList<>(list, new Function<List<T>, List<R>>() {
            @Override
            public List<R> apply(List<T> list)
            {
                return new TransformList<>(list, transform);
            }
        });
    }

    /**
     * See {@link #transform(FlowableList, Function transform}.
     * @param list The list to wrap
     * @param mapping A SubjectMap used to maps input keys onto observable values
     * @param <T> The type of the source
     * @param <R> The type of the mapped value
     * @return A new FlowableList which has values mapped via the supplied SubjectMap
     */
    public static <T, R> FlowableList<Flowable<R>> transform(FlowableList<T> list, final SubjectMap<T, R> mapping)
    {
        return transform(list, new Function<T, Flowable<R>>() {
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
     * @param list The list to wrap
     * @param timespan
     *            the period of time each buffer collects items before it is emitted and replaced with a new
     *            buffer
     * @param unit
     *            the unit of time which applies to the {@code timespan} argument
     * @param scheduler
     *            the {@link Scheduler} to use when determining the end and start of a buffer
     * @param <T> The type of elements
     * @return A new observable with the buffer window applied to all updates
     */
    public static <T> FlowableList<T> buffer(FlowableList<T> list, long timespan, TimeUnit unit, Scheduler scheduler)
    {
        return new BufferedFlowableList<>(list, timespan, unit, scheduler);
    }

    /**
     * Wraps the supplied list with the ability to merge changesets into a single changeset
     * when the stream comes under pressure. A sequence of inserts, removals and moves will
     * be translated into a single changeset caching the most recent list state only
     * @param list The list to wrap
     * @param <T> The type of elements
     * @return A new FlowableList which has an updates method capable of handling backpressure
     */
    public static <T> FlowableList<T> onBackpressureMerge(final FlowableList<T> list)
    {
        return new BackpressureMergeFlowableList<>(list);
    }

    /**
     * Transforms an FlowableList containing VisibilityState items into an FlowableList
     * which includes in its emissions the changes in visibility status of the items within the
     * original list
     * @param list The list to wrap
     * @param <T> The type of elements
     * @return A new FlowableList
     */
    public static <T, S extends FlowableList<? extends VisibilityState<T>>> FlowableList<T> collapseVisibility(S list)
    {
        return new VisibilityStateFlowableList<>((FlowableList<VisibilityState<T>>) list);
    }

    /**
     * Observes a stream of type List and computes the diff between successive emissions. The
     * wrapped FlowableList will emit the new list state when new emissions are available
     * along with the diff which transforms the previous into the next state.
     * @param list The list to wrap
     * @param alwaysReload If true, the diff between emissions will not be computed, instead,
     *                     a reload will be emitted for every change
     * @param <T> The type of elements
     * @return A new FlowableList
     */
    public static <T> FlowableList<T> diff(Flowable<List<T>> list, boolean alwaysReload)
    {
        return new DifferentialFlowableList<>(list, alwaysReload);
    }

    /**
     * See {@link #diff(Flowable<List>) diff}.
     */
    public static <T> FlowableList<T> diff(Flowable<List<T>> list)
    {
        return new DifferentialFlowableList<>(list);
    }

}
