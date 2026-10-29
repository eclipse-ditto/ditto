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
package org.eclipse.ditto.base.model.headers;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Measures the paths along which headers with JSON values travel through a Ditto service: received from the cluster
 * as JSON, enriched with JSON-valued headers, and serialized to JSON again. Run with {@code -prof gc} to see the
 * allocation per operation.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(1)
public class DittoHeadersRoundTripBenchmark {

    private static final String PRE_DEFINED_EXTRA_FIELDS = DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS.getKey();
    private static final String PRE_DEFINED_EXTRA_FIELDS_READ_GRANT =
            DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS_READ_GRANT_OBJECT.getKey();
    private static final String PRE_DEFINED_EXTRA_FIELDS_OBJECT =
            DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS_OBJECT.getKey();

    private DittoHeaders dittoHeaders;
    private JsonObject headersJson;

    @Setup
    public void setup() {
        final AuthorizationContext authorizationContext = AuthorizationModelFactory.newAuthContext(
                DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
                AuthorizationModelFactory.newAuthSubject("integration:connection-subject-1"),
                AuthorizationModelFactory.newAuthSubject("pre:connection-subject-2"),
                AuthorizationModelFactory.newAuthSubject("nginx:ditto"));
        dittoHeaders = DittoHeaders.newBuilder()
                .correlationId("benchmark-correlation-id-0123456789")
                .contentType("application/json")
                .authorizationContext(authorizationContext)
                .readGrantedSubjects(Arrays.asList(
                        AuthorizationModelFactory.newAuthSubject("integration:connection-subject-1"),
                        AuthorizationModelFactory.newAuthSubject("pre:connection-subject-2"),
                        AuthorizationModelFactory.newAuthSubject("nginx:ditto")))
                .acknowledgementRequest(AcknowledgementRequest.of(AcknowledgementLabel.of("twin-persisted")))
                .responseRequired(true)
                .build();
        headersJson = enrichWithExtraFields(dittoHeaders.toBuilder()).build().toJson();
    }

    /**
     * Headers received from the cluster and serialized again, e.g. when a signal is forwarded to another node.
     */
    @Benchmark
    public JsonObject fromTrustedJsonThenToJson() {
        return DittoHeaders.newFromTrustedJson(headersJson).toJson();
    }

    /**
     * Headers created from JSON with validation of their values, then serialized again.
     */
    @Benchmark
    public JsonObject fromJsonThenToJson() {
        return DittoHeaders.newBuilder(headersJson).build().toJson();
    }

    /**
     * JSON-valued headers put as strings - as done when enriching events with pre-defined extra fields - then
     * serialized, e.g. to publish the event to the cluster.
     */
    @Benchmark
    public JsonObject putJsonHeaderStringsThenToJson() {
        return enrichWithExtraFields(dittoHeaders.toBuilder()).build().toJson();
    }

    /**
     * Like {@link #putJsonHeaderStringsThenToJson()}, but puts the JSON values themselves.
     */
    @Benchmark
    public JsonObject putJsonHeaderValuesThenToJson() {
        return dittoHeaders.toBuilder()
                .putJsonHeader(PRE_DEFINED_EXTRA_FIELDS, extraFields())
                .putJsonHeader(PRE_DEFINED_EXTRA_FIELDS_READ_GRANT, extraFieldsReadGrant())
                .putJsonHeader(PRE_DEFINED_EXTRA_FIELDS_OBJECT, extraFieldsObject())
                .build()
                .toJson();
    }

    private static DittoHeadersBuilder<?, ?> enrichWithExtraFields(final DittoHeadersBuilder<?, ?> builder) {
        return builder
                .putHeader(PRE_DEFINED_EXTRA_FIELDS, extraFields().toString())
                .putHeader(PRE_DEFINED_EXTRA_FIELDS_READ_GRANT, extraFieldsReadGrant().toString())
                .putHeader(PRE_DEFINED_EXTRA_FIELDS_OBJECT, extraFieldsObject().toString());
    }

    static JsonArray extraFields() {
        return JsonArray.of(JsonValue.of("/attributes/type"), JsonValue.of("/attributes/location"),
                JsonValue.of("/definition"));
    }

    static JsonObject extraFieldsReadGrant() {
        final JsonArray subjects = JsonArray.of(JsonValue.of("integration:connection-subject-1"),
                JsonValue.of("pre:connection-subject-2"));
        return JsonObject.newBuilder()
                .set("attributes-type", subjects)
                .set("attributes-location", subjects)
                .set("definition", subjects)
                .build();
    }

    static JsonObject extraFieldsObject() {
        return JsonObject.newBuilder()
                .set("definition", "https://models.example.com/sensor-1.0.0.tm.jsonld")
                .set("attributes", JsonObject.newBuilder()
                        .set("type", "temperature-sensor")
                        .set("location", JsonObject.newBuilder()
                                .set("building", "building-1")
                                .set("floor", 3)
                                .set("room", "room-12")
                                .build())
                        .build())
                .build();
    }

    public static void main(final String[] args) throws RunnerException {
        final Options opt = new OptionsBuilder()
                .include(DittoHeadersRoundTripBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }

}
