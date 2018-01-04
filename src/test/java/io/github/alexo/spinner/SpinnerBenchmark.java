package io.github.alexo.spinner;

import org.junit.Ignore;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Stream;

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

    @Setup
    public void setUp() {
        victim = new Spinner.Builder<AtomicLong, Number>()
                .withSupplier(AtomicLong::new)
                .withAggregator(asAverage())
                .withSlotsNumber(10)
                .build();
    }

    @Benchmark
    public void run() {
        victim.getCurrentSlot().incrementAndGet();
    }

    private static Function<Stream<AtomicLong>, Double> asAverage() {
        return it -> it.mapToInt(AtomicLong::intValue)
                .average()
                .orElse(0);
    }

    public static void main(final String[] args) throws RunnerException {
        final int threadsNumber = Runtime.getRuntime().availableProcessors();
        new Runner(new OptionsBuilder()
                .include(SpinnerBenchmark.class.getSimpleName())
                .resultFormat(JSON)
                .measurementTime(seconds(10))
                .threads(threadsNumber)
                .build()).run();
    }
}
