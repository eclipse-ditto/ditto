/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.mapping.javascript.benchmark;

import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.connectivity.service.mapping.MessageMapper;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * JMH Benchmark for {@code JavaScriptMessageMapperRhino} mappings.
 */
@State(Scope.Benchmark)
public class JavaScriptMessageMapperRhinoBenchmark {

    private static final int WARMUP_ITERATIONS = 10;
    private static final int MEASUREMENT_ITERATIONS = 10;
    private static final int WARMUP_TIME = 1000;
    private static final int MEASUREMENT_TIME = 1000;

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public void simpleMapTextPayload(final SimpleMapTextPayloadToDitto scenario) {
        runScenario(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public void test1DecodeBinaryPayloadToDitto(final Test1DecodeBinaryPayloadToDitto scenario) {
        runScenario(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public void test2ParseJsonPayloadToDitto(final Test2ParseJsonPayloadToDitto scenario) {
        runScenario(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public void test3FormatJsonPayloadToDitto(final Test3FormatJsonPayloadToDitto scenario) {
        runScenario(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public void test4ConstructJsonPayloadToDitto(final Test4ConstructJsonPayloadToDitto scenario) {
        runScenario(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public void test5DecodeBinaryToDitto(final Test5DecodeBinaryToDitto scenario) {
        runScenario(scenario);
    }

    private void runScenario(final MapToDittoProtocolScenario scenario) {
        final MessageMapper messageMapper = scenario.getMessageMapper();
        final ExternalMessage externalMessage = scenario.getExternalMessage();
        messageMapper.map(externalMessage);
    }
}
