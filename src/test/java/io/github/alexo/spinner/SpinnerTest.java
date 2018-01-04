package io.github.alexo.spinner;

import io.github.alexo.spinner.Spinner.Clock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.Runtime.getRuntime;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author Alex Objelean
 */
public class SpinnerTest {
    private ExecutorService executorService;
    private static final int NUMBER_OF_STEPS = 2;
    @Mock
    private Clock clock;
    private Spinner<AtomicLong, Number> victim;

    @Before
    public void setUp() {
        initMocks(this);
        victim = createBuilder().build();
        executorService = newFixedThreadPool(getRuntime().availableProcessors());
        setClockToStep(0);
    }

    private Spinner.Builder<AtomicLong, Number> createBuilder() {
        return new Spinner.Builder<>(clock)
                .withSupplier(AtomicLong::new)
                .withAggregator(asAverage())
                .withSlotsNumber(NUMBER_OF_STEPS)
                .withDuration(1);
    }

    @After
    public void tearDown() {
        executorService.shutdown();
    }

    private static Function<Stream<AtomicLong>, Double> asAverage() {
        return it -> it.mapToInt(AtomicLong::intValue)
                .average()
                .orElse(0);
    }

    @Test
    public void shouldReturnZeroByDefault() {
        assertEquals(0, victim.getData().longValue());
    }

    @Test
    public void shouldReturnZeroWhenNoStepIsComputedYetAndCorrectValueForPreviousStep() {
        victim.getCurrentSlot().incrementAndGet();
        assertEquals(0, victim.getData().longValue());

        setClockToStep(1);
        assertEquals(1, victim.getData().longValue());
    }

    @Test
    public void shouldComputeAverageData() {
        victim.getCurrentSlot().addAndGet(10);

        setClockToStep(1);
        victim.getCurrentSlot().addAndGet(12);
        assertEquals(10, victim.getData().intValue());

        setClockToStep(2);
        assertEquals(11, victim.getData().intValue());
    }

    @Test
    public void shouldIgnoreExpiredStepsWhenComputingAverageData() {
        victim.getCurrentSlot().addAndGet(10);

        setClockToStep(1);
        victim.getCurrentSlot().addAndGet(12);
        assertEquals(10, victim.getData().intValue());

        setClockToStep(2);
        victim.getCurrentSlot().addAndGet(14);

        setClockToStep(3);
        assertEquals(13, victim.getData().intValue());
    }

    @Test
    public void shouldComputeAverageDataUnderConcurrentLoad() throws Exception {
        final int times = 1000;
        final Callable<Void> adder = createAdderCallable();
        executeConcurrently(times, adder);
        assertEquals(0, victim.getData().intValue());

        setClockToStep(1);
        assertEquals(times, victim.getData().intValue());
        executeConcurrently(times * 3, adder);

        setClockToStep(2);
        assertEquals(times * 2, victim.getData().intValue());
    }

    private Callable<Void> createAdderCallable() {
        final Callable<Void> adder = () -> {
            victim.getCurrentSlot().incrementAndGet();
            return null;
        };
        return adder;
    }

    /**
     * Responsible to set the clock to the time corresponding to the provided step.
     *
     * @param step zero based index.
     */
    private void setClockToStep(final int step) {
        final long time = step * victim.getConfig().getDuration();
        when(clock.now()).thenReturn(time);
    }

    private void executeConcurrently(final int times, final Callable<Void> callable) throws Exception {
        final CountDownLatch latch = new CountDownLatch(times);
        for (int i = 0; i < times; i++) {
            executorService.submit((Callable<Void>) () -> {
                try {
                    callable.call();
                } finally {
                    latch.countDown();
                }
                return null;
            });
        }
        latch.await();
    }

    @Test(expected = IllegalArgumentException.class)
    public void cannotAcceptInvalidRange() {
        createBuilder().withDuration(0).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void cannotAcceptInvalidSize() {
        createBuilder().withSlotsNumber(0).build();
    }

    @Test
    public void shouldAggregateOnlyOncePerStep() throws Exception {
        final AtomicInteger counter = new AtomicInteger();

        victim = createBuilder()
                .withAggregator(asAverage().andThen(d -> {
                    counter.incrementAndGet();
                    return d;
                }))
                .build();

        final int times = 100;

        final Callable<Void> adder = createAdderCallable();
        executeConcurrently(times, adder);
        assertEquals(0, victim.getData().intValue());

        setClockToStep(1);
        executeConcurrently(times, adder);

        setClockToStep(2);
        executeConcurrently(times, adder);

        assertEquals(counter.get(), 3);
    }

    @Test
    public void shouldIgnoreExpiredSteps() {
        victim.getCurrentSlot().addAndGet(10);
        // Go a step which would expire the previously stored values
        setClockToStep(NUMBER_OF_STEPS + 1);
        assertEquals(0, victim.getData().intValue());
    }

    @Test
    public void shouldAddEmptyStepsWhenStepExpires() {
        victim.getCurrentSlot().addAndGet(10);
        // Go a step which would expire the previously stored values
        setClockToStep(2);
        assertEquals(5, victim.getData().intValue());
    }
}
