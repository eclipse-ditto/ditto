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
package org.eclipse.ditto.internal.utils.tracing.filter;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.internal.utils.tracing.span.SpanOperationName;

import com.typesafe.config.Config;

import kamon.util.Filter;
import scala.util.Try;

/**
 * This class wraps a Kamon {@link Filter} to implement {@link TracingFilter}.
 * See also <a href="https://kamon.io/docs/latest/core/utilities/">Kamon Filters.</a>
 */
@Immutable
public final class KamonTracingFilter implements TracingFilter {

    private final Filter kamonFilter;
    private final Config filterConfig;

    private KamonTracingFilter(final Filter kamonFilter, final Config filterConfig) {
        this.kamonFilter = kamonFilter;
        this.filterConfig = filterConfig;
    }

    /**
     * Tries to return a new instance of {@code KamonTracingFilter} for the specified {@code Config} argument.
     * If instantiation failed the returned {@code Try} is a failure.
     *
     * @param config the configuration which provides the includes and excludes of the returned filter.
     * The configuration is supposed to have the following structure:
     * <pre>
     * config { includes = [ "some/pattern", "regex:some[0-9]" ] excludes = [ ] }
     * </pre>
     * @return a {@code Try} with the new KamonTracingFilter which is based on {@code config}.
     * @throws NullPointerException if {@code config} is {@code null}.
     */
    public static Try<KamonTracingFilter> tryFromConfig(final Config config) {

        // NPE should not lead to a Try failure because passing a null config
        // is a programming error.
        ConditionChecker.checkNotNull(config, "config");
        return Try.apply(() -> new KamonTracingFilter(Filter.from(validateConfig(config)), config));
    }

    private static Config validateConfig(final Config config) {
        if (!config.hasPath("includes") && !config.hasPath("excludes")) {
            throw new IllegalArgumentException("Configuration is missing <includes> and <excludes> paths.");
        }
        return config;
    }

    @Override
    public boolean accept(final SpanOperationName operationName) {
        ConditionChecker.checkNotNull(operationName, "operationName");
        return kamonFilter.accept(operationName.toString());
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (KamonTracingFilter) o;
        return Objects.equals(kamonFilter, that.kamonFilter) &&
                Objects.equals(filterConfig, that.filterConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kamonFilter, filterConfig);
    }

    @Override
    public String toString() {

        // It is sufficient to include the filter config.
        return getClass().getSimpleName() + " [" +
                "filterConfig=" + filterConfig +
                "]";
    }

}
