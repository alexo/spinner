package io.github.alexo.spinner;

import io.github.alexo.spinner.Spinner.Clock;

import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Holds Spinner slot related settings.
 *
 * @author Alex Objelean
 */
class SpinnerConfig<I, O> {
    private static final int DEFAULT_QUEUE_SIZE = 1;
    private static final long DEFAULT_DURATION = 1000;
    /**
     * {@link #setSlotsNumber(int)}
     */
    private int slotsNumber = DEFAULT_QUEUE_SIZE;
    /**
     * {@link #setDuration(long)}
     */
    private long duration = DEFAULT_DURATION;
    /**
     * {@link #setSlotSupplier(Supplier)}
     */
    private Supplier<I> slotSupplier;
    /**
     * {@link #setAggregator(BiFunction)}
     */
    private BiFunction<Stream<I>, I, O> aggregator;
    /**
     * {@link #setClock(Clock)}
     */
    private Clock clock = Clock.WALL;

    /**
     * Useful for unit testing slot holder behavior.
     *
     * @VisibleForTesting
     */
    public SpinnerConfig<I, O> setClock(final Clock clock) {
        this.clock = clock;
        return this;
    }

    /**
     * @VisibleForTesting
     */
    Clock getClock() {
        return clock;
    }

    public Supplier<I> getSlotSupplier() {
        return slotSupplier;
    }

    /**
     * Factory responsible for slot object creation. A slot can be anything, which can be used later for data aggregation.
     */
    public SpinnerConfig<I, O> setSlotSupplier(final Supplier<I> supplier) {
        this.slotSupplier = supplier;
        return this;
    }

    public BiFunction<Stream<I>, I, O> getAggregator() {
        return aggregator;
    }

    /**
     * Responsible for computing aggregated result for a collection of provided input slots. The implementation should return a non empty
     * result, even if the provided input is an empty iterator.
     */
    public SpinnerConfig<I, O> setAggregator(final BiFunction<Stream<I>, I, O> aggregator) {
        this.aggregator = aggregator;
        return this;
    }

    public int getSlotsNumber() {
        return slotsNumber;
    }

    /**
     * The number of slots to keep in the queue.
     */
    public SpinnerConfig<I, O> setSlotsNumber(final int slotsNumber) {
        this.slotsNumber = slotsNumber;
        return this;
    }

    public long getDuration() {
        return duration;
    }

    /**
     * @param durationMs the number of milliseconds a single slot lasts (1000 milliseconds by default).
     */
    public SpinnerConfig<I, O> setDuration(final long durationMs) {
        this.duration = durationMs;
        return this;
    }
}
