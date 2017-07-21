package com.github.mproberts.rxtools.list;

import com.github.mproberts.rxtools.SubjectMap;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Func1;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A utility class providing access to wrapping methods for working with ObservableList
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
     * Creates an ObservableList which will contain all and only the provided list items
     * @param list The list of items to wrap
     * @param <T> The type of elements
     * @return The wrapped ObservableList
     */
    public static <T> ObservableList<T> singletonList(List<T> list)
    {
        return new SingletonObservableList<>(list);
    }

    /**
     * See {@link #singletonList(List) singletonList}.
     */
    public static <T> ObservableList<T> singletonList(T... list)
    {
        return singletonList(Arrays.asList(list));
    }

    public static ObservableList concatGeneric(ObservableList<? extends ObservableList> list)
    {
        return new ConcatObservableList((ObservableList) list);
    }

    /**
     * Joins the list of lists into an ObservableList of all values contained within the list of lists. Change
     * events for individual and lists of items are automatically propagated to subscribers to the concatenated
     * result list.
     * @param list The list of items to wrap
     * @param <T> The type of elements
     * @return A new ObservableList with the contents of all supplied lists
     */
    public static <T> ObservableList<T> concat(ObservableList<? extends ObservableList<T>> list)
    {
        return new ConcatObservableList((ObservableList) list);
    }

    /**
     * See {@link #concat(ObservableList) concat}.
     */
    public static <T> ObservableList concat(List<? extends ObservableList<T>> lists)
    {
        return concat(singletonList(lists));
    }

    /**
     * Wraps the supplied list, calling the transform method when the get method is called for a specific index.
     * @param list The list to wrap
     * @param transform A function transforming the source to the target type
     * @param <T> The type of the source
     * @param <R> The type of the mapped value
     * @return A new ObservableList which has values mapped via the supplied transform
     */
    public static <T, R> ObservableList<R> transform(final ObservableList<T> list, final Func1<T, R> transform)
    {
        return new TransformObservableList<>(list, new Func1<List<T>, List<R>>() {
            @Override
            public List<R> call(List<T> list)
            {
                return new TransformList<>(list, transform);
            }
        });
    }

    /**
     * See {@link #transform(ObservableList, Func1 transform}.
     * @param list The list to wrap
     * @param mapping A SubjectMap used to maps input keys onto observable values
     * @param <T> The type of the source
     * @param <R> The type of the mapped value
     * @return A new ObservableList which has values mapped via the supplied SubjectMap
     */
    public static <T, R> ObservableList<Observable<R>> transform(ObservableList<T> list, final SubjectMap<T, R> mapping)
    {
        return transform(list, new Func1<T, Observable<R>>() {
            @Override
            public Observable<R> call(T t)
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
    public static <T> ObservableList<T> buffer(ObservableList<T> list, long timespan, TimeUnit unit, Scheduler scheduler)
    {
        return new BufferedObservableList<>(list, timespan, unit, scheduler);
    }

    /**
     * Wraps the supplied list with the ability to merge changesets into a single changeset
     * when the stream comes under pressure. A sequence of inserts, removals and moves will
     * be translated into a single changeset caching the most recent list state only
     * @param list The list to wrap
     * @param <T> The type of elements
     * @return A new ObservableList which has an updates method capable of handling backpressure
     */
    public static <T> ObservableList<T> onBackpressureMerge(final ObservableList<T> list)
    {
        return new BackpressureMergeObservableList<>(list);
    }

    /**
     * Transforms an ObservableList containing VisibilityState items into an ObservableList
     * which includes in its emissions the changes in visibility status of the items within the
     * original list
     * @param list The list to wrap
     * @param <T> The type of elements
     * @return A new ObservableList
     */
    public static <T, S extends ObservableList<? extends VisibilityState<T>>> ObservableList<T> collapseVisibility(S list)
    {
        return new VisibilityStateObservableList<>((ObservableList<VisibilityState<T>>) list);
    }

    /**
     * Observes a stream of type List and computes the diff between successive emissions. The
     * wrapped ObservableList will emit the new list state when new emissions are available
     * along with the diff which transforms the previous into the next state.
     * @param list The list to wrap
     * @param alwaysReload If true, the diff between emissions will not be computed, instead,
     *                     a reload will be emitted for every change
     * @param <T> The type of elements
     * @return A new ObservableList
     */
    public static <T> ObservableList<T> diff(Observable<List<T>> list, boolean alwaysReload)
    {
        return new DifferentialObservableList<>(list, alwaysReload);
    }

    /**
     * See {@link #diff(Observable<List>) diff}.
     */
    public static <T> ObservableList<T> diff(Observable<List<T>> list)
    {
        return new DifferentialObservableList<>(list);
    }

}
