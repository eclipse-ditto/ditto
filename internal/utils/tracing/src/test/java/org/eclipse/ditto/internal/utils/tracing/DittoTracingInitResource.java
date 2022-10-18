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
package org.eclipse.ditto.internal.utils.tracing;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.internal.utils.tracing.config.TracingConfig.TracingConfigValue.TRACING_ENABLED;
import static org.eclipse.ditto.internal.utils.tracing.config.TracingConfig.TracingConfigValue.TRACING_PROPAGATION_CHANNEL;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.internal.utils.tracing.config.DefaultTracingConfig;
import org.eclipse.ditto.internal.utils.tracing.config.TracingConfig;
import org.junit.rules.ExternalResource;

import com.typesafe.config.ConfigFactory;

/**
 * This {@code TestRule} can be used to call {@link DittoTracing#init(TracingConfig)} before the test and
 * reset DittoTracing after the test.
 * Doing so prevents {@code IllegalStateException}s in tests because of calls to uninitialized DittoTracing.
 */
@NotThreadSafe
public final class DittoTracingInitResource extends ExternalResource {

    private final TracingConfig tracingConfig;

    private DittoTracingInitResource(final TracingConfig tracingConfig) {
        this.tracingConfig = tracingConfig;
    }

    /**
     * Returns a new instance of {@code DittoTracingInitResource} for the specified TracingConfig argument.
     *
     * @param tracingConfig the configuration properties for {@code DittoTracing}.
     * @return the new instance.
     * @throws NullPointerException if {@code tracingConfig} is {@code null}.
     * @see TracingConfigBuilder#defaultValues()
     */
    public static DittoTracingInitResource newInstance(final TracingConfig tracingConfig) {
        return new DittoTracingInitResource(checkNotNull(tracingConfig, "tracingConfig"));
    }

    /**
     * Returns a new instance of {@code DittoTracingInitResource} which disables {@code DittoTracing} for the test.
     *
     * @return the new instance.
     */
    public static DittoTracingInitResource disableDittoTracing() {
        return newInstance(TracingConfigBuilder.defaultValues().withTracingDisabled().build());
    }

    @Override
    protected void before() throws Throwable {
        super.before();
        DittoTracing.init(tracingConfig);
    }

    @Override
    protected void after() {
        DittoTracing.reset();
        super.after();
    }

    @NotThreadSafe
    public static final class TracingConfigBuilder {

        private final Map<String, Object> rawConfigMap;

        private TracingConfigBuilder() {
            rawConfigMap = new HashMap<>();
        }

        public static TracingConfigBuilder defaultValues() {
            final var result = new TracingConfigBuilder();
            result.setTracingEnabledValue(TRACING_ENABLED.getDefaultValue());
            result.setTracingPropagationChannelValue(TRACING_PROPAGATION_CHANNEL.getDefaultValue());
            return result;
        }

        public TracingConfigBuilder withTracingEnabled() {
            setTracingEnabledValue(true);
            return this;
        }

        private void setTracingEnabledValue(final Object tracingEnabled) {
            rawConfigMap.put(TRACING_ENABLED.getConfigPath(), tracingEnabled);
        }

        public TracingConfigBuilder withTracingDisabled() {
            setTracingEnabledValue(false);
            return this;
        }

        public TracingConfigBuilder withTracingPropagationChannel(final CharSequence propagationChannelName) {
            setTracingPropagationChannelValue(checkNotNull(propagationChannelName, "propagationChannelName"));
            return this;
        }

        private void setTracingPropagationChannelValue(final Object propagationChannelName) {
            rawConfigMap.put(TRACING_PROPAGATION_CHANNEL.getConfigPath(), propagationChannelName);
        }

        public TracingConfig build() {
            return DefaultTracingConfig.of(ConfigFactory.parseMap(Map.of("tracing", rawConfigMap)));
        }

    }

}
