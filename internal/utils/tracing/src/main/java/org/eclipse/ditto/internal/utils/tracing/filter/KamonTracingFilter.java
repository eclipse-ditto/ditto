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
import org.eclipse.ditto.utils.result.Result;

import com.typesafe.config.Config;

import kamon.util.Filter;

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
     * Creates an instance of {@code KamonTracingFilter} for the specified {@code Config} argument.
     * If instantiation failed the returned {@code Result} is an Err.
     *
     * @param config the configuration which provides the includes and excludes of the returned filter.
     * The configuration is supposed to have the following structure:
     * <pre>
     * config { includes = [ "some/pattern", "regex:some[0-9]" ] excludes = [ ] }
     * </pre>
     * @return a {@code Result} with the new KamonTracingFilter which is based on {@code config}, if successful.
     * Otherwise, an error result.
     * @throws NullPointerException if {@code config} is {@code null}.
     */
    public static Result<KamonTracingFilter, Throwable> fromConfig(final Config config) {

        // NPE should not lead to an Err result because passing a null config
        // is a programming error.
        ConditionChecker.checkNotNull(config, "config");
        return validateConfig(config).map(Filter::from).map(filter -> new KamonTracingFilter(filter, config));
    }

    private static Result<Config, Throwable> validateConfig(final Config config) {
        final Result<Config, Throwable> result;
        if (!config.hasPath("includes") && !config.hasPath("excludes")) {
            result = Result.err(
                    new IllegalArgumentException("Configuration is missing <includes> and <excludes> paths.")
            );
        } else {
            result = Result.ok(config);
        }
        return result;
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
