package com.github.mproberts.rxtools.tools;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import org.reactivestreams.Publisher;

/**
 * A utility class for acting on boolean Flowables
 */
public final class BooleanFlowables {

    private BooleanFlowables()
    {
        // intentionally blank
    }

    private static class AndSwitchMap implements Function<Boolean, Publisher<Boolean>> {
        private final Flowable<Boolean> _bool2;

        public AndSwitchMap(Flowable<Boolean> bool2)
        {
            _bool2 = bool2;
        }

        @Override
        public Publisher<Boolean> apply(Boolean aBoolean) throws Exception
        {
            if (aBoolean) {
                return _bool2;
            }
            return Flowable.just(aBoolean);
        }
    }

    private static class OrSwitchMap implements Function<Boolean, Publisher<Boolean>> {
        private final Flowable<Boolean> _bool2;

        public OrSwitchMap(Flowable<Boolean> bool2) {
            _bool2 = bool2;
        }

        @Override
        public Publisher<Boolean> apply(Boolean aBoolean) throws Exception {
            if (!aBoolean) {
                return _bool2;
            }
            return Flowable.just(aBoolean);
        }
    }

    /**
     * Creates a Flowable which is true iff all bools provided to the call are also true.
     * This method short circuits and only evaluates until the first false value.
     * @param bools
     * @return A Flowable which emits true iff all bools are true
     */
    @SafeVarargs
    public static Flowable<Boolean> and(Flowable<Boolean>... bools)
    {
        Flowable<Boolean> previousBool = null;

        for (Flowable<Boolean> bool : bools) {
            if (previousBool == null) {
                previousBool = bool;
                continue;
            }

            previousBool = previousBool.switchMap(new AndSwitchMap(bool));
        }

        if (previousBool == null) {
            return Flowable.just(false);
        }

        return previousBool.distinctUntilChanged();
    }

    /**
     * Creates a Flowable which is true iff at least one bool provided to the call is true.
     * This method short circuits and only evaluates until the first true value.
     * @param bools
     * @return A Flowable which emits true iff at least one bools is true
     */
    @SafeVarargs
    public static Flowable<Boolean> or(Flowable<Boolean>... bools)
    {
        Flowable<Boolean> previousBool = null;

        for (Flowable<Boolean> bool : bools) {
            if (previousBool == null) {
                previousBool = bool;
                continue;
            }

            previousBool = previousBool.switchMap(new OrSwitchMap(bool));
        }

        if (previousBool == null) {
            return Flowable.just(false);
        }

        return previousBool.distinctUntilChanged();
    }
}
