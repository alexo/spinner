package io.github.alexo.spinner;

import io.github.alexo.spinner.Spinner;
import io.github.alexo.spinner.SpinnerConfig;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Ignore;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

@Ignore
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
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
        final SlotSupplier<AtomicLong> stepSupplier = new SlotSupplier<AtomicLong>() {
            public AtomicLong get() {
                return new AtomicLong();
            }
        };
        final SpinnerConfig<AtomicLong, Number> config = new SpinnerConfig<AtomicLong, Number>()
                .setSlotSupplier(stepSupplier).setSlotsAggregator(createAverageAggregator()).setSlotsNumber(10);
        return config;
    }

    private SlotsAggregator<AtomicLong, Number> createAverageAggregator() {
        return new SlotsAggregator<AtomicLong, Number>() {
            public Number aggregate(final Iterator<AtomicLong> input, final AtomicLong latestElapsed) {
                int index = 0;
                long sum = 0;
                for (; input.hasNext();) {
                    index++;
                    final AtomicLong value = input.next();
                    sum += value.longValue();
                }
                return index > 0 ? sum / index : sum;
            }
        };
    }

    public static void main(final String[] args) throws RunnerException {
        final int threadsNumber = Runtime.getRuntime().availableProcessors();
        final Options opt = new OptionsBuilder().include(".*" + SpinnerBenchmark.class.getSimpleName() + ".*")
                .resultFormat(ResultFormatType.JSON).measurementTime(TimeValue.seconds(10)).threads(threadsNumber)
                .build();

        new Runner(opt).run();
    }
}
