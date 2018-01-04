Spinner
=======

[![Build Status](https://api.travis-ci.org/alexo/spinner.svg)](http://travis-ci.org/alexo/spinner)
[<img src="https://badges.gitter.im/alexo/spinner.svg" class="copy-button view" data-copy-text="[![Gitter chat](https://badges.gitter.im/alexo/spinnerj.svg)]">](https://gitter.im/alexo/spinner)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.alexo/spinner/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.alexo/spinner)

A small library implementing an algorithm which can be used for efficient metrics calculations.

# Examples

The data is responsible to compute the average number of values for all slots.
~~~
Spinner<AtomicLong, Number> spinner = new Spinner.Builder<AtomicLong, Number>()        
        .withSupplier(AtomicLong::new)
        .withAggregator(asAverage())
        .withSlotsNumber(10)
        .build();

spinner.getCurrentSlot().incrementAndGet();
System.out.println(spinner.getData());        
~~~
The average aggregator looks like this:
~~~
private static Function<Stream<AtomicLong>, Double> asAverage() {
    return it -> it.mapToInt(AtomicLong::intValue)
            .average()
            .orElse(0);
}
~~~