package io.github.alexo.spinner;

import io.github.alexo.spinner.Spinner.Clock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

import static java.lang.Runtime.getRuntime;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
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
        victim = Spinner.create(createConfig());
        executorService = newFixedThreadPool(getRuntime().availableProcessors());
        setClockToStep(0);
    }

    @After
    public void tearDown() {
        executorService.shutdown();
    }

    private SpinnerConfig<AtomicLong, Number> createConfig() {
        return new SpinnerConfig<AtomicLong, Number>()
                .setClock(clock)
                .setSlotSupplier(AtomicLong::new)
                .setSlotsAggregator(createAverageAggregator())
                .setSlotsNumber(NUMBER_OF_STEPS)
                .setTimeSlotSpan(1);
    }

    private BiFunction<Iterator<AtomicLong>, AtomicLong, Number> createAverageAggregator() {
        return (input, latestElapsed) -> {
            int index = 0;
            long sum = 0;
            for (; input.hasNext();) {
                index++;
                sum += input.next().longValue();
            }
            return index > 0 ? sum / index : sum;
        };
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
        final long time = step * victim.getConfig().getTimeSlotSpan();
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
        Spinner.create(createConfig().setTimeSlotSpan(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void cannotAcceptInvalidSize() {
        Spinner.create(createConfig().setSlotsNumber(0));
    }

    @Test
    public void shouldAggregateOnlyOncePerStep() throws Exception {
        final BiFunction<Iterator<AtomicLong>, AtomicLong, Number> stepAggregatorSpy = spy(createAverageAggregator());
        victim = Spinner.create(createConfig().setSlotsAggregator(stepAggregatorSpy));

        final int times = 100;

        final Callable<Void> adder = createAdderCallable();
        executeConcurrently(times, adder);
        assertEquals(0, victim.getData().intValue());

        setClockToStep(1);
        executeConcurrently(times, adder);

        setClockToStep(2);
        executeConcurrently(times, adder);

        verify(stepAggregatorSpy, times(3)).apply(any(Iterator.class), any(AtomicLong.class));
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
