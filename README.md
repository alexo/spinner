Spinner
=======

[![Build Status](https://api.travis-ci.org/alexo/spinner.svg)](http://travis-ci.org/alexo/spinner)
[<img src="https://badges.gitter.im/alexo/spinner.svg" class="copy-button view" data-copy-text="[![Gitter chat](https://badges.gitter.im/alexo/spinnerj.svg)]">](https://gitter.im/alexo/spinner)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.alexo/spinner/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.alexo/spinner)

A small library implementing an algorithm which can be used for efficient metrics calculations.

# Examples

~~~
Spinner<AtomicLong, Number> victim = Spinner.create(new SpinnerConfig<AtomicLong, Number>()        
        .setSlotSupplier(AtomicLong::new)
        .setSlotsAggregator((input, latestElapsed) -> {
            int index = 0;
            long sum = 0;
            for (; input.hasNext();) {
                index++;
                sum += input.next().longValue();
            }
            return index > 0 ? sum / index : sum;
        });
~~~