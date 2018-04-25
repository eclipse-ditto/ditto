/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.thingsearch.starter.actors.health;

import static java.util.Objects.requireNonNull;

import java.time.Duration;

import org.eclipse.ditto.services.thingsearch.common.util.ConfigKeys;
import org.eclipse.ditto.services.utils.akka.streaming.StreamMetadataPersistence;

import com.typesafe.config.Config;

/**
 * Holds the configuration properties for a new instance of
 * {@link org.eclipse.ditto.services.thingsearch.starter.actors.health.LastSuccessfulStreamCheckingActor}.
 */
final class LastSuccessfulStreamCheckingActorConfigurationProperties {

    private final boolean syncEnabled;
    private final Duration warningOffset;
    private final Duration errorOffset;
    private final StreamMetadataPersistence streamMetadataPersistence;

    LastSuccessfulStreamCheckingActorConfigurationProperties(final boolean syncEnabled,
            final Duration warningOffset,
            final Duration errorOffset, final StreamMetadataPersistence streamMetadataPersistence) {

        if (errorOffset.compareTo(warningOffset) <= 0) {
            throw new IllegalArgumentException("Warning offset must be shorter than error offset.");
        }
        requireNonNull(streamMetadataPersistence);

        this.syncEnabled = syncEnabled;
        this.warningOffset = warningOffset;
        this.errorOffset = errorOffset;
        this.streamMetadataPersistence = streamMetadataPersistence;
    }

    /**
     * Creates properties for the policies sync health check.
     *
     * @param config The application config.
     * @param policiesSyncPersistence The persistence that should be used to retrieve time of last successful
     * policies stream.
     * @return The properties.
     */
    public static LastSuccessfulStreamCheckingActorConfigurationProperties policiesSync(final Config config,
            final StreamMetadataPersistence policiesSyncPersistence) {
        final boolean policiesSynchronizationActive = config.getBoolean(ConfigKeys.POLICIES_SYNCER_ACTIVE);
        final Duration policiesSyncerOutdatedWarningOffset =
                config.getDuration(ConfigKeys.POLICIES_SYNCER_OUTDATED_WARNING_OFFSET);
        final Duration policiesSyncerOutdatedErrorOffset =
                config.getDuration(ConfigKeys.POLICIES_SYNCER_OUTDATED_ERROR_OFFSET);

        return new LastSuccessfulStreamCheckingActorConfigurationProperties(policiesSynchronizationActive,
                policiesSyncerOutdatedWarningOffset,
                policiesSyncerOutdatedErrorOffset, policiesSyncPersistence);
    }

    /**
     * Creates properties for the things sync health check.
     *
     * @param config The application config.
     * @param thingsSyncPersistence The persistence that should be used to retrieve time of last successful
     * things stream.
     * @return The properties.
     */
    public static LastSuccessfulStreamCheckingActorConfigurationProperties thingsSync(final Config config,
            final StreamMetadataPersistence thingsSyncPersistence) {
        final boolean policiesSynchronizationActive = config.getBoolean(ConfigKeys.THINGS_SYNCER_ACTIVE);
        final Duration policiesSyncerOutdatedWarningOffset =
                config.getDuration(ConfigKeys.THINGS_SYNCER_OUTDATED_WARNING_OFFSET);
        final Duration policiesSyncerOutdatedErrorOffset =
                config.getDuration(ConfigKeys.THINGS_SYNCER_OUTDATED_ERROR_OFFSET);

        return new LastSuccessfulStreamCheckingActorConfigurationProperties(policiesSynchronizationActive,
                policiesSyncerOutdatedWarningOffset,
                policiesSyncerOutdatedErrorOffset, thingsSyncPersistence);
    }

    /**
     * Provides information whether the health check is enabled or not.
     *
     * @return True if enabled false if not
     */
    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    /**
     * Returns the configured warning offset.
     *
     * @return The duration if {@link #isSyncEnabled()} is true. Null if not.
     */
    public Duration getWarningOffset() {
        return warningOffset;
    }

    /**
     * Returns the configured error offset.
     *
     * @return The duration if {@link #isSyncEnabled()} is true. Null if not.
     */
    public Duration getErrorOffset() {
        return errorOffset;
    }

    /**
     * Returns the configured persistence.
     *
     * @return The persistence if {@link #isSyncEnabled()} is true. Null if not.
     */
    public StreamMetadataPersistence getStreamMetadataPersistence() {
        return streamMetadataPersistence;
    }
}
