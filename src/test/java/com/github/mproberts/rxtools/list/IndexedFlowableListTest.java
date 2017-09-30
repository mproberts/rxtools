package com.github.mproberts.rxtools.list;

import com.github.mproberts.rxtools.types.Optional;
import io.reactivex.Flowable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Function3;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;

public class IndexedFlowableListTest
{
    class Result {
        private final Flowable<Optional<Integer>> _previous;
        private final Flowable<Optional<Integer>> _next;
        private final Integer _key;
        private CompositeDisposable _disposable = new CompositeDisposable();;

        public Result(Integer key, Flowable<Optional<Integer>> previous, Flowable<Optional<Integer>> next)
        {
            _key = key;
            _previous = previous;
            _next = next;
        }

        void subscribe()
        {
            Disposable previousSubscription = _previous.subscribe(new Consumer<Optional<Integer>>() {
                @Override
                public void accept(Optional<Integer> integerOptional) throws Exception {
                }
            });
            Disposable nextSubscription = _next.subscribe(new Consumer<Optional<Integer>>() {
                @Override
                public void accept(Optional<Integer> integerOptional) throws Exception {
                }
            });

            _disposable.add(previousSubscription);
            _disposable.add(nextSubscription);
        }

        void unsubscribe()
        {
            _disposable.clear();
        }
    }

    protected TestSubscriber<Update<Flowable<String>>> createIndexedList(FlowableList<Integer> list)
    {
        FlowableList<Flowable<String>> transformedList = list.indexedMap(new Function3<Integer, Flowable<Optional<Integer>>, Flowable<Optional<Integer>>, Flowable<String>>() {
            @Override
            public Flowable<String> apply(final Integer item, Flowable<Optional<Integer>> previousItem, Flowable<Optional<Integer>> nextItem) throws Exception
            {
                return Flowable.combineLatest(previousItem, nextItem, new BiFunction<Optional<Integer>, Optional<Integer>, String>() {
                    @Override
                    public String apply(Optional<Integer> previous, Optional<Integer> next) throws Exception
                    {
                        String previousString = previous.map(new Function<Integer, String>() {
                            @Override
                            public String apply(Integer integer) throws Exception {
                                return integer.toString();
                            }
                        }).orElse("?");
                        String nextString = next.map(new Function<Integer, String>() {
                            @Override
                            public String apply(Integer integer) throws Exception {
                                return integer.toString();
                            }
                        }).orElse("?");

                        return previousString + " < " + item + " > " + nextString;
                    }
                });
            }
        });

        return transformedList.updates().test();
    }

    @Test
    public void testThrashingAdditions()
    {
        final int total = 5000;
        final List<Result> toDispose = new ArrayList<>();
        final SimpleFlowableList<Integer> list = new SimpleFlowableList<>(Arrays.asList(1, 2, 3));

        list
                .indexedMap(new Function3<Integer, Flowable<Optional<Integer>>, Flowable<Optional<Integer>>, Result>() {

                    @Override
                    public Result apply(Integer integer, Flowable<Optional<Integer>> optionalFlowable, Flowable<Optional<Integer>> optionalFlowable2) throws Exception {
                        Result result = new Result(integer, optionalFlowable, optionalFlowable2);

                        return result;
                    }
                })
                .updates()
                .observeOn(Schedulers.computation())
                .subscribe(new Consumer<Update<Result>>() {
                    @Override
                    public void accept(Update<Result> resultUpdate) throws Exception {

                        for (Result result : resultUpdate.list) {
                            result.subscribe();

                            toDispose.add(result);
                        }
                    }
                });

        final Thread addThread = new Thread() {

            @Override
            public void run() {
                int count = 0;
                int size = 0;
                Random rand = new Random();

                while (count++ < total) {
                    if (rand.nextInt(3) == 1 && size > 0) {
                        --size;
                        list.remove(0);
                    } else {
                        ++size;
                        list.add(1, rand.nextInt());
                    }
                }
            }
        };

        Thread disposeThread = new Thread() {

            @Override
            public void run() {
                try {
                    sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                while (toDispose.size() > 0) {
                    Result result = toDispose.remove(0);

                    yield();

                    if (result == null) {
                        continue;
                    }
                    result.unsubscribe();
                }
            }
        };

        addThread.start();
        disposeThread.start();

        try {
            addThread.join();
            disposeThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testAddTransform()
    {
        SimpleFlowableList<Integer> list = new SimpleFlowableList<>(Arrays.asList(1, 2, 3));

        TestSubscriber<Update<Flowable<String>>> testSubscriber = createIndexedList(list);

        testSubscriber.assertValueCount(1);

        List<Update<Flowable<String>>> onNextEvents = testSubscriber.values();

        assertEquals(Arrays.asList(Change.reloaded()), onNextEvents.get(0).changes);

        List<Flowable<String>> list1 = onNextEvents.get(0).list;

        Flowable<String> item1 = list1.get(0);
        Flowable<String> item2 = list1.get(1);
        Flowable<String> item3 = list1.get(2);

        TestSubscriber<String> test1 = item1.test();
        TestSubscriber<String> test2 = item2.test();
        TestSubscriber<String> test3 = item3.test();

        test1.assertValue("? < 1 > 2");
        test2.assertValue("1 < 2 > 3");
        test3.assertValue("2 < 3 > ?");

        list.add(1, 4);

        test1.assertValues("? < 1 > 2", "? < 1 > 4");
        test2.assertValues("1 < 2 > 3", "4 < 2 > 3");
        test3.assertValueCount(1);

        list.add(5);

        test1.assertValues("? < 1 > 2", "? < 1 > 4");
        test2.assertValues("1 < 2 > 3", "4 < 2 > 3");
        test3.assertValues("2 < 3 > ?", "2 < 3 > 5");
    }

    @Test
    public void testRemoveTransform()
    {
        SimpleFlowableList<Integer> list = new SimpleFlowableList<>(Arrays.asList(1, 2, 3, 4));

        TestSubscriber<Update<Flowable<String>>> testSubscriber = createIndexedList(list);

        testSubscriber.assertValueCount(1);

        List<Update<Flowable<String>>> onNextEvents = testSubscriber.values();

        assertEquals(Arrays.asList(Change.reloaded()), onNextEvents.get(0).changes);

        List<Flowable<String>> list1 = onNextEvents.get(0).list;

        Flowable<String> item1 = list1.get(0);
        Flowable<String> item2 = list1.get(1);
        Flowable<String> item3 = list1.get(2);
        Flowable<String> item4 = list1.get(3);

        TestSubscriber<String> test1 = item1.test();
        TestSubscriber<String> test2 = item2.test();
        TestSubscriber<String> test3 = item3.test();
        TestSubscriber<String> test4 = item4.test();

        test1.assertValue("? < 1 > 2");
        test2.assertValue("1 < 2 > 3");
        test3.assertValue("2 < 3 > 4");
        test4.assertValue("3 < 4 > ?");

        list.remove(3);

        test3.assertValues("2 < 3 > 4", "2 < 3 > ?");

        list.remove(1);

        test1.assertValues("? < 1 > 2", "? < 1 > 3");
        test3.assertValues("2 < 3 > 4", "2 < 3 > ?", "1 < 3 > ?");
    }

    @Test
    public void testMoveTransform()
    {
        SimpleFlowableList<Integer> list = new SimpleFlowableList<>(Arrays.asList(1, 2, 3, 4));

        TestSubscriber<Update<Flowable<String>>> testSubscriber = createIndexedList(list);

        testSubscriber.assertValueCount(1);

        List<Update<Flowable<String>>> onNextEvents = testSubscriber.values();

        assertEquals(Arrays.asList(Change.reloaded()), onNextEvents.get(0).changes);

        List<Flowable<String>> list1 = onNextEvents.get(0).list;

        Flowable<String> item1 = list1.get(0);
        Flowable<String> item2 = list1.get(1);
        Flowable<String> item3 = list1.get(2);
        Flowable<String> item4 = list1.get(3);

        TestSubscriber<String> test1 = item1.test();
        TestSubscriber<String> test2 = item2.test();
        TestSubscriber<String> test3 = item3.test();
        TestSubscriber<String> test4 = item4.test();

        test1.assertValue("? < 1 > 2");
        test2.assertValue("1 < 2 > 3");
        test3.assertValue("2 < 3 > 4");
        test4.assertValue("3 < 4 > ?");

        list.move(3, 1);

        test1.assertValues("? < 1 > 2", "? < 1 > 4");
        test2.assertValues("1 < 2 > 3", "4 < 2 > 3");
        test3.assertValues("2 < 3 > 4", "2 < 3 > ?");

        list.move(1, 3);

        test1.assertValues("? < 1 > 2", "? < 1 > 4", "? < 1 > 2");
        test2.assertValues("1 < 2 > 3", "4 < 2 > 3", "1 < 2 > 3");
        test3.assertValues("2 < 3 > 4", "2 < 3 > ?", "2 < 3 > 4");
    }
}
