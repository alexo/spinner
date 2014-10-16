package io.github.alexo.spinner;

import java.util.Iterator;

/**
 * Responsible for aggregating the slots into a single result.
 *
 * @param <I> type used by each slot.
 * @param <O> type of the aggregated value.
 */
public interface SlotsAggregator<I, O> {
    O aggregate(Iterator<I> slotsIterator, I latestElapsedSlot);
}