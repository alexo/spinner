package io.github.alexo.spinner;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public static <I, O> Spinner<I, O> create(final SpinnerConfig<I, O> config) {
        return new Spinner<I, O>(config);
    }

    /**
     * A wrapper around the system clock to allow custom implementations to be used in unit tests where we want to fake or control the clock
     * behavior.
     */
    public interface Clock {
        Clock WALL = () -> System.currentTimeMillis();

        long now();
    }

    /**
     * @param config {@link SpinnerConfig} used to setup the spinner.
     */
    private Spinner(final SpinnerConfig<I, O> config) {
        if (config == null) {
            throw new IllegalArgumentException("Invalid config");
        }
        config.validate();
        this.config = config;
        startTime = config.getClock().now();
        currentSlot = config.getSlotSupplier().get();
        queue = createQueue(config.getSlotsNumber());
        // compute initial value
        data = config.getSlotsAggregator().aggregate(queue.iterator(), currentSlot);
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
        final long numberOfExpiredSlots = diff / config.getTimeSlotSpan();
        startTime += numberOfExpiredSlots * config.getTimeSlotSpan();

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
        data = config.getSlotsAggregator().aggregate(queue.iterator(), expiredSlot);
        if (LOG.isDebugEnabled()) {
            LOG.debug("aggregated: {}", data);
        }
    }

    /**
     * @return true if the time associated with the current slot has passed
     */
    private boolean isSlotExpired() {
        return elapsedTimeMillis() >= config.getTimeSlotSpan();
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
