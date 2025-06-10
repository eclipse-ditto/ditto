/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.devops;

import org.eclipse.ditto.base.model.entity.Entity;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.json.JsonObject;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Public API for a WoT (Web of Things) validation configuration.
 * <p>
 * This interface exposes the configuration options for WoT validation, including global enablement,
 * warning logging, Thing-level and Feature-level validation, and dynamic context-specific overrides.
 * Implementations must be immutable and thread-safe.
 * </p>
 *
 * @since 3.8.0
 */
@Immutable
public interface WotValidationConfig extends Entity<WotValidationConfigRevision> {
    /**
     * Gets the unique identifier of this configuration.
     *
     * @return the config ID.
     */
    WotValidationConfigId getConfigId();

    /**
     * Whether WoT validation is enabled globally.
     *
     * @return {@code Optional} containing the enabled flag, or empty if not set.
     */
    Optional<Boolean> isEnabled();

    /**
     * Whether validation failures should only log warnings instead of failing API calls.
     *
     * @return {@code Optional} containing the flag, or empty if not set.
     */
    Optional<Boolean> logWarningInsteadOfFailingApiCalls();

    /**
     * Gets the Thing-level validation configuration, if present.
     *
     * @return {@code Optional} containing the Thing validation config, or empty if not set.
     */
    Optional<ThingValidationConfig> getThingConfig();

    /**
     * Gets the Feature-level validation configuration, if present.
     *
     * @return {@code Optional} containing the Feature validation config, or empty if not set.
     */
    Optional<FeatureValidationConfig> getFeatureConfig();

    /**
     * Returns the list of dynamic validation configurations for specific contexts.
     *
     * @return the list of dynamic validation configurations (never {@code null}).
     */
    List<DynamicValidationConfig> getDynamicConfigs();

    /**
     * Gets the entity ID for this config.
     *
     * @return the entity ID.
     */
    Optional<WotValidationConfigId> getEntityId();

    /**
     * Gets the revision of this configuration.
     *
     * @return the revision.
     */
    Optional<WotValidationConfigRevision> getRevision();

    /**
     * Gets the last modified timestamp, if present.
     *
     * @return {@code Optional} containing the last modified timestamp, or empty if not set.
     */
    Optional<Instant> getModified();

    /**
     * Gets the created timestamp, if present.
     *
     * @return {@code Optional} containing the created timestamp, or empty if not set.
     */
    Optional<Instant> getCreated();

    /**
     * Whether this configuration is marked as deleted.
     *
     * @return {@code true} if deleted, otherwise {@code false}.
     */
    boolean isDeleted();

    /**
     * Gets the metadata associated with this configuration, if present.
     *
     * @return {@code Optional} containing the metadata, or empty if not set.
     */
    Optional<Metadata> getMetadata();

    /**
     * Converts this configuration to a JSON object.
     *
     * @return the JSON representation of this configuration.
     */
    static WotValidationConfig fromJson(final JsonObject json) {
        return ImmutableWotValidationConfig.fromJson(json);
    }

    /**
     * Creates a new {@link WotValidationConfig} instance with the specified values.
     *
     * @param configId the unique identifier of this configuration
     * @param enabled whether WoT validation is enabled globally
     * @param logWarningInsteadOfFailingApiCalls whether validation failures should only log warnings instead of failing
     * @param thingConfig optional configuration for Thing-level validation
     * @param featureConfig optional configuration for Feature-level validation
     * @param dynamicConfig list of dynamic validation configurations for specific contexts
     * @param revision the revision number of this configuration
     * @param created the timestamp when this configuration was created
     * @param modified the timestamp when this configuration was last modified
     * @param deleted whether this configuration is marked as deleted
     * @param metadata optional metadata associated with this configuration
     * @return a new instance with the specified values
     * @since 3.8.0
     */
    static WotValidationConfig of(
            final WotValidationConfigId configId,
            @Nullable final Boolean enabled,
            @Nullable final Boolean logWarningInsteadOfFailingApiCalls,
            @Nullable final ThingValidationConfig thingConfig,
            @Nullable final FeatureValidationConfig featureConfig,
            final List<DynamicValidationConfig> dynamicConfig,
            @Nullable final WotValidationConfigRevision revision,
            @Nullable final Instant created,
            @Nullable final Instant modified,
            @Nullable final Boolean deleted,
            @Nullable final Metadata metadata) {
        return ImmutableWotValidationConfig.of(
                configId, enabled, logWarningInsteadOfFailingApiCalls, thingConfig, featureConfig,
                dynamicConfig, revision, created, modified, deleted, metadata
        );
    }

} 