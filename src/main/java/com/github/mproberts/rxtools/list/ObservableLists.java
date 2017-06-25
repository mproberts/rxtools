package com.github.mproberts.rxtools.list;

import com.github.mproberts.rxtools.SubjectMap;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Func1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ObservableLists
{
    private ObservableLists()
    {
        // utility class
    }

    public static <T> ObservableList<T> singletonList(List<T> list)
    {
        return new SingletonObservableList<>(list);
    }

    public static <T> ObservableList<T> singletonList(T... list)
    {
        return singletonList(Arrays.asList(list));
    }

    public static <T> ObservableList<T> diff(Observable<List<T>> list, boolean alwaysReload)
    {
        return new DifferentialObservableList<>(list, alwaysReload);
    }

    public static <T> ObservableList<T> diff(Observable<List<T>> list)
    {
        return new DifferentialObservableList<>(list);
    }

    public static ObservableList concatGeneric(ObservableList<? extends ObservableList> list)
    {
        return new ConcatObservableList((ObservableList) list);
    }

    public static <T> ObservableList<T> concat(ObservableList<? extends ObservableList<T>> list)
    {
        return new ConcatObservableList((ObservableList) list);
    }

    public static <T> ObservableList concat(List<? extends ObservableList<T>> lists)
    {
        return concat(singletonList(lists));
    }

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

    public static <T> ObservableList<T> buffer(ObservableList<T> list, long timespan, TimeUnit timeUnit, Scheduler scheduler)
    {
        return new BufferedObservableList<>(list, timespan, timeUnit, scheduler);
    }

    public static <T> ObservableList<T> onBackpressureMerge(final ObservableList<T> list)
    {
        return new ObservableList<T>() {

            @Override
            public Observable<Update<T>> updates()
            {
                return list.updates().lift(OperatorOnBackpressureMerge.<T>instance());
            }
        };
    }
}
