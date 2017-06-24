package com.github.mproberts.rxtools.list;

import com.github.mproberts.rxtools.SubjectMap;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Func1;

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

    public static <T, R> ObservableList<R> transform(ObservableList<T> list, Func1<T, R> transform)
    {
        return new TransformObservableList<>(list, new Func1<List<T>, List<R>>() {
            @Override
            public List<R> call(List<T> list)
            {
                return new TransformList<>(list, transform);
            }
        });
    }

    public static <T, R> ObservableList<Observable<R>> transform(ObservableList<T> list, SubjectMap<T, R> mapping)
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
                return list.updates().lift(OperatorOnBackpressureMerge.instance());
            }
        };
    }
}
