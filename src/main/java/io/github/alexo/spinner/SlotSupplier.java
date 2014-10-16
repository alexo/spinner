package io.github.alexo.spinner;

/**
 * Factory for slot objects of type <I>. The implementation is responsible for providing a fresh instance each time the expired slot
 * must be replaced with a new one.
 *
 * @param <I> the type of slot object.
 */
public interface SlotSupplier<I> {
    /**
     * @return the instance type <I> to replace the expired slot.
     */
    I get();
}