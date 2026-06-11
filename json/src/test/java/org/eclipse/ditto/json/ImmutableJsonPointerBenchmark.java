/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.json;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH micro-benchmark for {@link ImmutableJsonPointer}, used to validate the
 * regex-removal optimization in {@code escapeTilde}, {@code decodeTilde} and the
 * consecutive-slash check.
 *
 * <h2>Scenarios</h2>
 * <ul>
 *   <li><b>toStringShallowNoTilde</b> &mdash; render a 1-segment pointer ({@code /thingId})
 *       to its string form. Dominant pointer length in Ditto's hot serialize path.</li>
 *   <li><b>toStringDeepNoTilde</b> &mdash; render a 5-segment pointer
 *       ({@code /features/coolant/properties/temperature/value}) typical for feature
 *       property updates.</li>
 *   <li><b>toStringDeepWithTilde</b> &mdash; render a 5-segment pointer whose middle
 *       segment contains a {@code ~} that must be escaped to {@code ~0}.</li>
 *   <li><b>ofParsedShallowNoTilde</b> &mdash; parse {@code "/thingId"}.</li>
 *   <li><b>ofParsedDeepNoTilde</b> &mdash; parse a typical 5-segment slash-delimited string.</li>
 *   <li><b>ofParsedDeepWithEscape</b> &mdash; parse a 5-segment string containing
 *       {@code ~0} (escaped tilde) to exercise {@code decodeTilde}.</li>
 * </ul>
 *
 * <h2>How to run</h2>
 * <pre>
 * mvn test-compile -pl json -am -Djapicmp.skip=true
 * java -cp "$(mvn -pl json dependency:build-classpath -Dmdep.outputFile=/dev/stdout -Dmdep.includeScope=test -q):json/target/classes:json/target/test-classes" \
 *      org.eclipse.ditto.json.ImmutableJsonPointerBenchmark
 * </pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@Threads(1)
public class ImmutableJsonPointerBenchmark {

    private JsonPointer shallowNoTilde;
    private JsonPointer deepNoTilde;
    private JsonPointer deepWithTilde;

    private String shallowNoTildeString;
    private String deepNoTildeString;
    private String deepWithEscapeString;

    @Setup
    public void setup() {
        shallowNoTilde = JsonFactory.newPointer("thingId");
        deepNoTilde = JsonFactory.newPointer("features/coolant/properties/temperature/value");
        deepWithTilde =
                JsonFactory.newPointer(JsonFactory.newKey("features"),
                        JsonFactory.newKey("coolant"),
                        JsonFactory.newKey("an~odd~name"),
                        JsonFactory.newKey("temperature"),
                        JsonFactory.newKey("value"));

        shallowNoTildeString = "/thingId";
        deepNoTildeString = "/features/coolant/properties/temperature/value";
        deepWithEscapeString = "/features/coolant/an~0odd~0name/temperature/value";
    }

    @Benchmark
    public void toStringShallowNoTilde(final Blackhole bh) {
        bh.consume(shallowNoTilde.toString());
    }

    @Benchmark
    public void toStringDeepNoTilde(final Blackhole bh) {
        bh.consume(deepNoTilde.toString());
    }

    @Benchmark
    public void toStringDeepWithTilde(final Blackhole bh) {
        bh.consume(deepWithTilde.toString());
    }

    @Benchmark
    public void ofParsedShallowNoTilde(final Blackhole bh) {
        bh.consume(JsonFactory.newPointer(shallowNoTildeString));
    }

    @Benchmark
    public void ofParsedDeepNoTilde(final Blackhole bh) {
        bh.consume(JsonFactory.newPointer(deepNoTildeString));
    }

    @Benchmark
    public void ofParsedDeepWithEscape(final Blackhole bh) {
        bh.consume(JsonFactory.newPointer(deepWithEscapeString));
    }

    public static void main(final String[] args) throws RunnerException {
        final Options opt = new OptionsBuilder()
                .include(ImmutableJsonPointerBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
