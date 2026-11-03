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
package org.eclipse.ditto.gateway.service.security.authorization;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.util.config.security.NamespaceAccessConfig;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.junit.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

/**
 * JMH benchmark for the per-signal cost of {@link NamespaceAccessValidator#isNamespaceAccessible(String)}.
 * <p>
 * A streaming session (SSE / WebSocket) holds one validator for its whole lifetime and checks every outgoing signal
 * against it, so {@link #streamingSignal()} measures the hot path which dominated gateway CPU profiles when
 * namespace-access rules with several placeholder conditions are configured. {@link #httpRequest()} additionally
 * includes the validator creation done once per HTTP request.
 * <p>
 * The rules mirror a real-world setup: several rules guarded by JWT claim conditions using {@code fn:filter},
 * {@code fn:default} and {@code fn:format}, with explicit allowed namespaces per resource type.
 * <p>
 * Not a CI test (no {@code *Test} class name). Run:
 * <pre>
 *   mvn test -pl gateway/service -am -Djapicmp.skip=true \
 *       -Dtest=NamespaceAccessValidatorJmh -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class NamespaceAccessValidatorJmh {

    private static final String DEX_ISSUER = "https://dex.example.com/dex";
    private static final String RESOURCE_TYPE = "thing";
    private static final String NAMESPACE = "com.acme.smartheating.srt";

    /**
     * Which rule grants access: {@code reseller} matches only the last, namespace-restricted rules (all conditions of
     * all rules are evaluated), {@code platform} the third and {@code foreignIssuer} the first rule.
     */
    @Param({"reseller", "platform", "foreignIssuer"})
    public String token;

    private NamespaceAccessValidatorFactory factory;
    private DittoHeaders dittoHeaders;
    private JsonWebToken jwt;
    private NamespaceAccessValidator sessionValidator;

    @Setup
    public void setup() {
        factory = new NamespaceAccessValidatorFactory(rules());
        dittoHeaders = NamespaceAccessTestSupport.newHeaders();
        jwt = NamespaceAccessTestSupport.jwt(switch (token) {
            case "reseller" -> "{\"iss\":\"" + DEX_ISSUER + "\",\"groups\":[\"team:dev\"]," +
                    "\"authz\":[{\"reseller\":\"com.acme\",\"roles\":[\"admin\"]}]}";
            case "platform" -> "{\"iss\":\"" + DEX_ISSUER + "\",\"groups\":[\"team:dev\"]," +
                    "\"authz\":[{\"roles\":[\"platform-admin\"]}]}";
            case "foreignIssuer" -> "{\"iss\":\"https://cognito.example.com/pool\"}";
            default -> throw new IllegalArgumentException(token);
        });
        sessionValidator = factory.createValidator(dittoHeaders, jwt, RESOURCE_TYPE);
        if (!sessionValidator.isNamespaceAccessible(NAMESPACE)) {
            throw new IllegalStateException("Benchmark setup broken: <" + token + "> token must grant access");
        }
    }

    @Benchmark
    public boolean streamingSignal() {
        return sessionValidator.isNamespaceAccessible(NAMESPACE);
    }

    @Benchmark
    public boolean httpRequest() {
        return factory.createValidator(dittoHeaders, jwt, RESOURCE_TYPE).isNamespaceAccessible(NAMESPACE);
    }

    @Test
    public void runBenchmarks() throws RunnerException {
        new Runner(new OptionsBuilder()
                .include(NamespaceAccessValidatorJmh.class.getSimpleName())
                .warmupIterations(3).warmupTime(TimeValue.seconds(2))
                .measurementIterations(5).measurementTime(TimeValue.seconds(2))
                .forks(1)
                .threads(1)
                .addProfiler("gc")
                .build()).run();
    }

    private static List<NamespaceAccessConfig> rules() {
        final String isDex = "{{ jwt:iss | fn:filter('eq','" + DEX_ISSUER + "') }}";
        return List.of(
                NamespaceAccessTestSupport.config(
                        List.of("{{ jwt:iss | fn:filter('ne','" + DEX_ISSUER + "') }}"),
                        List.of(), List.of()),
                NamespaceAccessTestSupport.config(
                        List.of(isDex,
                                "{{ jwt:authz/reseller | fn:default('no-authz') | fn:filter('eq','no-authz') }}",
                                "{{ jwt:groups | fn:filter('like','prod:*') }}"),
                        List.of(), List.of()),
                NamespaceAccessTestSupport.config(
                        List.of(isDex, "{{ jwt:authz | fn:format('{reseller}#{roles}') | fn:filter('like','#*') }}"),
                        List.of(), List.of()),
                NamespaceAccessTestSupport.config(
                        List.of(isDex, "{{ jwt:authz/reseller | fn:filter('eq','com.acme') }}"),
                        List.of("com.acme.building", "com.acme.gateway", "com.acme.property", "com.acme.room",
                                "com.acme.room-group", "com.acme.smartheating.heatsource",
                                "com.acme.smartheating.srt"),
                        List.of(), List.of("thing", "policy")),
                NamespaceAccessTestSupport.config(
                        List.of(isDex, "{{ jwt:authz/reseller | fn:filter('eq','com.acme') }}"),
                        List.of("com.acme.gateway.mbus.repeater", "com.acme.iot.sensor.temperature",
                                "com.acme.lorawan.gateway", "com.acme.submetering.heat-meter",
                                "com.acme.submetering.water-meter"),
                        List.of(), List.of("thing"))
        );
    }

}
