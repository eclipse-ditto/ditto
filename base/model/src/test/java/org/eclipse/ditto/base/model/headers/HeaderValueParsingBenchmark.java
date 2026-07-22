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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
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
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Benchmark quantifying the effect of memoizing JSON-typed {@link Header} value parsing versus the previous
 * behaviour of re-parsing header value strings on every serialization / accessor call.
 *
 * <p>Models a realistic signal {@code DittoHeaders} instance carrying the JSON-typed headers that dominate
 * production things-service CPU (authorization-context, read-subjects, expected-response-types) plus a couple of
 * plain string headers. Because {@code DittoHeaders} is immutable and its header map is shared across unmutated
 * copies, a single instance is serialized / accessed many times over its lifetime; this benchmark measures a
 * reused instance.</p>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@Threads(1)
public class HeaderValueParsingBenchmark {

    private DittoHeaders dittoHeaders;
    private List<Header> jsonHeaders;
    private String authContextHeaderValue;
    private String readSubjectsHeaderValue;
    private JsonObject headersJson;

    @Setup
    public void setup() {
        final AuthorizationContext authorizationContext = AuthorizationModelFactory.newAuthContext(
                DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
                AuthorizationModelFactory.newAuthSubject("ditto:connection-subject-1"),
                AuthorizationModelFactory.newAuthSubject("ditto:connection-subject-2"),
                AuthorizationModelFactory.newAuthSubject("nginx:ditto"));

        dittoHeaders = DittoHeaders.newBuilder()
                .correlationId("benchmark-correlation-id-0123456789")
                .contentType("application/json")
                .authorizationContext(authorizationContext)
                .readGrantedSubjects(Arrays.asList(
                        AuthorizationModelFactory.newAuthSubject("ditto:connection-subject-1"),
                        AuthorizationModelFactory.newAuthSubject("ditto:connection-subject-2"),
                        AuthorizationModelFactory.newAuthSubject("nginx:ditto")))
                .expectedResponseTypes(ResponseType.RESPONSE, ResponseType.ERROR)
                .build();

        // Collect the JSON-typed headers (object/array values) to contrast re-parse vs. cache-hit directly.
        final AbstractDittoHeaders abstractDittoHeaders = (AbstractDittoHeaders) dittoHeaders;
        jsonHeaders = new ArrayList<>();
        for (final Header header : abstractDittoHeaders.headers.values()) {
            final String value = header.getValue().trim();
            if (!value.isEmpty() && ('{' == value.charAt(0) || '[' == value.charAt(0))) {
                jsonHeaders.add(header);
            }
        }
        authContextHeaderValue = dittoHeaders.get(DittoHeaderDefinition.AUTHORIZATION_CONTEXT.getKey());
        readSubjectsHeaderValue = dittoHeaders.get(DittoHeaderDefinition.READ_SUBJECTS.getKey());
        headersJson = dittoHeaders.toJson();
    }

    /** Baseline: re-parse each JSON-typed header value on every call (the pre-memoization behaviour). */
    @Benchmark
    public void reparseEachTime(final Blackhole blackhole) {
        for (int i = 0; i < jsonHeaders.size(); i++) {
            blackhole.consume(JsonFactory.readFrom(jsonHeaders.get(i).getValue()));
        }
    }

    /** Memoized: read the cached parse for each JSON-typed header. */
    @Benchmark
    public void memoizedAccess(final Blackhole blackhole) {
        for (int i = 0; i < jsonHeaders.size(); i++) {
            blackhole.consume(jsonHeaders.get(i).getParsedValue());
        }
    }

    /** Realistic end-to-end serialization of a reused headers instance (now cache-backed). */
    @Benchmark
    public JsonObject toJson() {
        return dittoHeaders.toJson();
    }

    /** Realistic accessor that parses the read-subjects array (now cache-backed). */
    @Benchmark
    public Object getReadGrantedSubjects() {
        return dittoHeaders.getReadGrantedSubjects();
    }

    /** Realistic accessor that parses the authorization-context object (now cache-backed). */
    @Benchmark
    public Object getAuthorizationContext() {
        return dittoHeaders.getAuthorizationContext();
    }

    /** Pre-memoization equivalent of {@link #getReadGrantedSubjects()}: re-parses the array string each call. */
    @Benchmark
    public Object getReadGrantedSubjects_reparse() {
        return JsonArray.of(readSubjectsHeaderValue).stream()
                .map(JsonValue::asString)
                .map(AuthorizationModelFactory::newAuthSubject)
                .collect(Collectors.toSet());
    }

    /** Pre-memoization equivalent of {@link #getAuthorizationContext()}: re-parses the object string each call. */
    @Benchmark
    public Object getAuthorizationContext_reparse() {
        return AuthorizationModelFactory.newAuthContext(JsonObject.of(authContextHeaderValue));
    }

    // --- cluster-deserialization build from JSON, with vs. without value-type (re-)validation ---

    /** Current behaviour: build headers from the wire JSON, validating (re-parsing) every JSON-typed value. */
    @Benchmark
    public DittoHeaders buildFromJson_validated() {
        return DittoHeaders.newBuilder(headersJson).build();
    }

    /** Build headers from trusted wire JSON, skipping redundant value-type validation. */
    @Benchmark
    public DittoHeaders buildFromJson_trusted() {
        return DittoHeaders.newFromTrustedJson(headersJson);
    }

    public static void main(final String[] args) throws RunnerException {
        final Options options = new OptionsBuilder()
                .include(HeaderValueParsingBenchmark.class.getSimpleName())
                .build();
        new Runner(options).run();
    }

}
