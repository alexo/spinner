package io.github.alexo.spinner;

import io.github.alexo.spinner.Spinner.Clock;

import java.util.Iterator;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Holds Spinner slot related settings.
 *
 * @author Alex Objelean
 */
public class SpinnerConfig<I, O> {
    private static final int DEFAULT_QUEUE_SIZE = 1;
    private static final long DEFAULT_TIME_SLOT_SPAN = 1000;
    /**
     * {@link #setSlotsNumber(int)}
     */
    private int slotsNumber = DEFAULT_QUEUE_SIZE;
    /**
     * {@link #setTimeSlotSpan(long)}
     */
    private long timeSlotSpan = DEFAULT_TIME_SLOT_SPAN;
    /**
     * {@link #setSlotSupplier(Supplier)}
     */
    private Supplier<I> slotSupplier;
    /**
     * {@link #setSlotsAggregator(BiFunction)}
     */
    private BiFunction<Iterator<I>, I, O> slotsAggregator;
    /**
     * {@link #setClock(Clock)}
     */
    private Clock clock = Clock.WALL;

    /**
     * Check if all provided configurations are valid.
     */
    public void validate() {
        notNull(slotSupplier);
        notNull(slotsAggregator);
        notNull(clock);
        isTrue(timeSlotSpan > 0, "timeSlotSpan must be a positive value");
        isTrue(slotsNumber > 0, "slotsNumber must be a positive value");
    }

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
    public SpinnerConfig<I, O> setSlotSupplier(final Supplier<I> slotSupplier) {
        this.slotSupplier = slotSupplier;
        return this;
    }

    public BiFunction<Iterator<I>, I, O> getSlotsAggregator() {
        return slotsAggregator;
    }

    /**
     * Responsible for computing aggregated result for a collection of provided input slots. The implementation should return a non empty
     * result, even if the provided input is an empty iterator.
     */
    public SpinnerConfig<I, O> setSlotsAggregator(final BiFunction<Iterator<I>, I, O> slotsAggregator) {
        this.slotsAggregator = slotsAggregator;
        return this;
    }

    public int getSlotsNumber() {
        return slotsNumber;
    }

    /**
     * The number of slots to keep in the queue.
     */
    public SpinnerConfig<I, O> setSlotsNumber(final int slotsNumber) {
        isTrue(slotsNumber > 0, "slotsNumber must be a positive value");
        this.slotsNumber = slotsNumber;
        return this;
    }

    public long getTimeSlotSpan() {
        return timeSlotSpan;
    }

    /**
     * @param timeSlotSpan the number of milliseconds a single slot lasts (1000 milliseconds by default).
     */
    public SpinnerConfig<I, O> setTimeSlotSpan(final long timeSlotSpan) {
        isTrue(timeSlotSpan > 0, "timeSlotSpan must be a positive value");
        this.timeSlotSpan = timeSlotSpan;
        return this;
    }

    /**
     * <p>
     * Validate that the argument condition is <code>true</code>; otherwise throwing an exception with the specified message. This method is
     * useful when validating according to an arbitrary boolean expression, such as validating a primitive number or using your own custom
     * validation expression.
     * </p>
     *
     * @param expression the boolean expression to check
     * @param message the exception message if invalid
     * @throws IllegalArgumentException if expression is <code>false</code>
     */
    private static void isTrue(final boolean expression, final String message) {
        if (expression == false) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void notNull(final Object object) {
        if (object == null) {
            throw new IllegalArgumentException();
        }
    }
}
