package io.github.alexo.spinner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Data structure responsible for managing data based on time slots. The underlying implementation uses a queue to store the expired data
 * slots (a limited number). The spinner is similar to a ring buffer, it rotates/moves one data slot every time the current slot slips by.
 * Besides keeping track of time slots data and rotating the Spinner also aggregates all the slots and this aggregation happens every time
 * it rotates/moves.
 *
 * @param <I> the type of elements (input) used to represent a single slot
 * @param <O> the type of data (output) computed by aggregation function
 *
 * @author Alex Objelean
 */
public class Spinner<I, O> {
    private static final Logger LOG = LoggerFactory.getLogger(Spinner.class);
    private final SpinnerConfig<I, O> config;
    /**
     * The start time of the current time slot.
     */
    private long startTime;
    /**
     * The current slot instance, which is used to collect various data for the current time slot.
     */
    private volatile I currentSlot;
    /**
     * Aggregated data based on previously expired slots (which are still stored in this spinner).
     */
    private volatile O data;
    /**
     * Flag used to prevent concurrent slot change.
     */
    private final AtomicBoolean slotIsChanging = new AtomicBoolean();
    /**
     * Holds the queue of slots.
     */
    private final Queue<I> queue;

    public static class Builder<I, O> {
        private SpinnerConfig<I, O> config;
        public Builder() {
            config = new SpinnerConfig<>();
        }

        Builder(final Clock clock) {
            this();
            this.config.setClock(clock);
        }

        public Builder withSupplier(final Supplier<I> supplier) {
            config.setSlotSupplier(supplier);
            return this;
        }

        public Builder withAggregator(final BiFunction<Stream<I>, I, O> aggregator) {
            config.setAggregator(aggregator);
            return this;
        }

        /**
         * @param aggregator a function which computes an aggregate for all the inputs. Similar to the {@link #withAggregator(BiFunction)} but doesn't provide the last expired input.
         */
        public Builder withAggregator(final Function<Stream<I>, O> aggregator) {
            return withAggregator((it, last) -> aggregator.apply(it));
        }

        public Builder withDuration(final long durationMs) {
            config.setDuration(durationMs);
            return this;
        }

        public Builder withSlotsNumber(final int slotsNumber) {
            config.setSlotsNumber(slotsNumber);
            return this;
        }

        SpinnerConfig<I, O> getConfig() {
            return config;
        }

        public Spinner<I, O> build() {
            notNull(config.getSlotSupplier());
            notNull(config.getAggregator());
            notNull(config.getClock());
            isTrue(config.getDuration() > 0, "timeSlotSpan must be a positive value");
            isTrue(config.getSlotsNumber() > 0, "slotsNumber must be a positive value");
            return new Spinner<>(this);
        }

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

    /**
     * A wrapper around the system clock to allow custom implementations to be used in unit tests where we want to fake or control the clock
     * behavior.
     */
    public interface Clock {
        Clock WALL = () -> System.currentTimeMillis();

        long now();
    }

    private Spinner(final Builder<I, O> builder) {
        this.config = builder.getConfig();
        startTime = config.getClock().now();
        currentSlot = config.getSlotSupplier().get();
        queue = createQueue(config.getSlotsNumber());
        // compute initial value
        data = config.getAggregator().apply(queue.stream(), currentSlot);
    }

    /**
     * The side effect of invoking this method is rotation of the spinner if the time span for the current slot has expired.
     *
     * @return the slot where the metric should be stored for current moment.
     */
    public I getCurrentSlot() {
        changeSlotIfRequired();
        return currentSlot;
    }

    /**
     * @return the most recent computed aggregated data.
     */
    public O getData() {
        changeSlotIfRequired();
        return data;
    }

    /**
     * If the current slot has expired, triggers the slot change operations in a thread safe manner.
     */
    private void changeSlotIfRequired() {
        if (isSlotExpired()) {
            if (slotIsChanging.compareAndSet(false, true)) {
                try {
                    if (isSlotExpired()) {
                        doSlotChange();
                    }
                } finally {
                    slotIsChanging.set(false);
                }
            }
        }
    }

    private void doSlotChange() {
        // first thing first - move the slot
        final I expiredSlot = currentSlot;
        try {
            currentSlot = config.getSlotSupplier().get();
        } catch (final Exception e) {
            LOG.error("Slot creation failed: {}", e.getMessage());
        }

        // compute the start time of the new slot
        final long diff = elapsedTimeMillis();
        final long numberOfExpiredSlots = diff / config.getDuration();
        startTime += numberOfExpiredSlots * config.getDuration();

        // Manage the queue of expired slots:
        // the expiredSlot must be added and older than configured slotsNumber (expired slots) must be dropped
        if (numberOfExpiredSlots > config.getSlotsNumber()) {
            // no need to keep expired slots
            queue.clear();
        } else {
            if (numberOfExpiredSlots > 1) {
                // add (numberOfExpiredSlots - 1) empty slots
                // -1 because the latest expired slot will be added as well
                try {
                    final I emptySlot = config.getSlotSupplier().get();
                    for (int i = 0; i < numberOfExpiredSlots - 1; i++) {
                        queue.add(emptySlot);
                    }
                } catch (final Exception e) {
                    LOG.error("Slot creation failed: {}", e.getMessage());
                }
            }
            queue.add(expiredSlot);
        }

        // compute the aggregated data
        data = config.getAggregator().apply(queue.stream(), expiredSlot);
        if (LOG.isDebugEnabled()) {
            LOG.debug("aggregated: {}", data);
        }
    }

    /**
     * @return true if the time associated with the current slot has passed
     */
    private boolean isSlotExpired() {
        return elapsedTimeMillis() >= config.getDuration();
    }

    /**
     * @return the time lapsed since the beginning of the current time slot; used to decide if the current slot must be changed
     */
    private long elapsedTimeMillis() {
        return config.getClock().now() - startTime;
    }

    /**
     * @return the Queue holding the slots. The queue will take care of evicting a slot every time a new slot is added and the queue is
     *         full.
     */
    @SuppressWarnings("serial")
    private Queue<I> createQueue(final int queueSize) {
        // TODO this does not need to be thread safe
        return new ArrayBlockingQueue<I>(queueSize) {
            @Override
            public boolean add(final I slot) {
                if (remainingCapacity() == 0) {
                    // evict one when the queue is full.
                    poll();
                }
                return super.add(slot);
            }
        };
    }

    /**
     * @VisibleForTesting
     */
    SpinnerConfig<I, O> getConfig() {
        return config;
    }
}
