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
package org.eclipse.ditto.internal.utils.health;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * Configuration options for the {@link DefaultHealthCheckingActorFactory}.
 */
@Immutable
public final class HealthCheckingActorOptions {

    private final boolean healthCheckEnabled;
    private final Duration interval;
    private final boolean persistenceCheckEnabled;

    /**
     * Constructs a new {@code HealthCheckingActorOptions} object from a {@code Builder}.
     */
    private HealthCheckingActorOptions(final Builder builder) {
        this.healthCheckEnabled = builder.healthCheckEnabled;
        this.interval = builder.interval;
        this.persistenceCheckEnabled = builder.persistenceCheckEnabled;
    }

    /**
     * Returns a {@code Builder} to build a {@code HealthCheckActorOptions} object.
     *
     * @param healthCheckEnabled whether the health check is enabled globally.
     * @param interval the interval for the check.
     * @return the builder.
     */
    public static Builder getBuilder(final boolean healthCheckEnabled, final Duration interval) {
        return new Builder(healthCheckEnabled, interval);
    }

    /**
     * Returns if the health check is enabled globally.
     *
     * @return true if enabled, else false.
     */
    public boolean isHealthCheckEnabled() {
        return healthCheckEnabled;
    }

    /**
     * Returns the persistence check interval.
     *
     * @return the interval.
     */
    public Duration getInterval() {
        return interval;
    }

    /**
     * Returns if the persistence health check is enabled.
     *
     * @return true if enabled, else false.
     */
    public boolean isPersistenceCheckEnabled() {
        return persistenceCheckEnabled;
    }


    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "OverlyComplexMethod"})
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HealthCheckingActorOptions that = (HealthCheckingActorOptions) o;
        return healthCheckEnabled == that.healthCheckEnabled &&
                persistenceCheckEnabled == that.persistenceCheckEnabled &&
                Objects.equals(interval, that.interval);
    }

    @Override
    public int hashCode() {
        return Objects
                .hash(healthCheckEnabled, interval, persistenceCheckEnabled);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "healthCheckEnabled=" + healthCheckEnabled +
                ", interval=" + interval +
                ", persistenceCheckEnabled=" + persistenceCheckEnabled +
                "]";
    }

    /**
     * A builder for building {@code HealthCheckingActorOptions} objects.
     */
    public static final class Builder {

        private final boolean healthCheckEnabled;
        private final Duration interval;

        private boolean persistenceCheckEnabled;

        /**
         * Constructs a new {@code Builder} object.
         *
         * @param healthCheckEnabled whether the health check should be enabled globally or not.
         * @param interval the interval for the check.
         */
        public Builder(final boolean healthCheckEnabled, final Duration interval) {
            this.healthCheckEnabled = healthCheckEnabled;
            this.interval = interval;
        }

        /**
         * Enables the health check for the persistence.
         *
         * @return this builder for method chaining.
         */
        public Builder enablePersistenceCheck() {
            this.persistenceCheckEnabled = true;
            return this;
        }

        /**
         * Builds the {@code HealthCheckingActorOptions} object.
         *
         * @return the HealthCheckingActorOptions.
         */
        public HealthCheckingActorOptions build() {
            return new HealthCheckingActorOptions(this);
        }
    }
}
