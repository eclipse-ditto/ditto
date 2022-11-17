/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.tracing.span;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.junit.rules.ExternalResource;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import kamon.Kamon;

/**
 * This ExternalResource initializes Kamon tracing before test start and stops Kamon after test stop.
 * Without initialization, tracing unit tests relying on Kamon most probably don't work in the expected way.
 */
@NotThreadSafe
public final class KamonTracingInitResource extends ExternalResource {

    private final Config config;

    private KamonTracingInitResource(final Config config) {
        this.config = config;
    }

    public static KamonTracingInitResource newInstance(final KamonTracingConfig kamonTracingConfig) {
        ConditionChecker.checkNotNull(kamonTracingConfig, "kamonTracingConfig");
        return new KamonTracingInitResource(kamonTracingConfig.toTypesafeConfig());
    }

    @Override
    protected void before() throws Throwable {
        super.before();
        Kamon.init(config);
    }

    @Override
    protected void after() {
        Kamon.stop();
        super.after();
    }

    @NotThreadSafe
    public static final class KamonTracingConfig {

        private static final String KEY_TRACE_IDENTIFIER_SCHEME = "kamon.trace.identifier-scheme";

        private final Map<String, Object> configValues;

        private KamonTracingConfig() {
            configValues = new HashMap<>();
        }

        public static KamonTracingConfig defaultValues() {
            return new KamonTracingConfig()
                    .withIdentifierSchemeDouble()
                    .withPropagateW3cTraceparentOnIncomingHttp()
                    .withPropagateW3cTraceparentOnOutgoingHttp();
        }

        public KamonTracingConfig withIdentifierSchemeSingle() {
            configValues.put(KEY_TRACE_IDENTIFIER_SCHEME, "single");
            return this;
        }

        public KamonTracingConfig withIdentifierSchemeDouble() {
            configValues.put(KEY_TRACE_IDENTIFIER_SCHEME, "double");
            return this;
        }

        public KamonTracingConfig withPropagateW3cTraceparentOnIncomingHttp() {
            configValues.put("kamon.propagation.http.default.entries.incoming.span", "w3c");
            return this;
        }

        public KamonTracingConfig withPropagateW3cTraceparentOnOutgoingHttp() {
            configValues.put("kamon.propagation.http.default.entries.outgoing.span", "w3c");
            return this;
        }

        public KamonTracingConfig withSamplerAlways() {
            configValues.put("kamon.trace.sampler", "always");
            return this;
        }

        public KamonTracingConfig withTickInterval(final Duration duration) {
            configValues.put("kamon.trace.tick-interval", String.format("%dms", duration.toMillis()));
            return this;
        }

        public KamonTracingConfig withKeyValue(final String key, final String value) {
            configValues.put(key, value);
            return this;
        }

        public Config toTypesafeConfig() {
            return ConfigFactory.parseMap(configValues).withFallback(ConfigFactory.load());
        }

    }

}
