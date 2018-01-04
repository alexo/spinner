package io.github.alexo.spinner;

import org.junit.Ignore;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openjdk.jmh.annotations.Mode.Throughput;
import static org.openjdk.jmh.results.format.ResultFormatType.JSON;
import static org.openjdk.jmh.runner.options.TimeValue.seconds;

@Ignore
@State(Scope.Benchmark)
@BenchmarkMode(Throughput)
@OutputTimeUnit(SECONDS)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 3, time = 5)
@Fork(1)
public class SpinnerBenchmark {
    private Spinner<AtomicLong, Number> victim;
    long index = 0;

    @Setup
    public void setUp() {
        victim = Spinner.create(createDefaultConfig());
    }

    @Benchmark
    public void run() {
        victim.getCurrentSlot().incrementAndGet();
    }

    private SpinnerConfig<AtomicLong, Number> createDefaultConfig() {
        final SlotSupplier<AtomicLong> stepSupplier = () -> new AtomicLong();
        final SpinnerConfig<AtomicLong, Number> config = new SpinnerConfig<AtomicLong, Number>()
                .setSlotSupplier(stepSupplier).setSlotsAggregator(createAverageAggregator()).setSlotsNumber(10);
        return config;
    }

    private SlotsAggregator<AtomicLong, Number> createAverageAggregator() {
        return (input, latestElapsed) -> {
            int index = 0;
            long sum = 0;
            for (; input.hasNext();) {
                index++;
                final AtomicLong value = input.next();
                sum += value.longValue();
            }
            return index > 0 ? sum / index : sum;
        };
    }

    public static void main(final String[] args) throws RunnerException {
        final int threadsNumber = Runtime.getRuntime().availableProcessors();
        final Options opt = new OptionsBuilder()
                .include(SpinnerBenchmark.class.getSimpleName())
                .resultFormat(JSON)
                .measurementTime(seconds(10))
                .threads(threadsNumber)
                .build();

        new Runner(opt).run();
    }
}
