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

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.ThingDefinition;

/**
 * Interface for configuration overrides in WoT validation.
 * This represents overrides for configuration settings.
 *
 * @since 3.8.0
 */
public interface ConfigOverrides extends Jsonifiable<JsonObject> {

    /**
     * Returns the override for the enabled flag.
     *
     * @return an optional containing the override value
     */
    Optional<Boolean> isEnabled();

    /**
     * Returns the override for the log warning flag.
     *
     * @return an optional containing the override value
     */
    Optional<Boolean> isLogWarningInsteadOfFailingApiCalls();

    /**
     * Returns the Thing-level config overrides.
     *
     * @return an optional containing the Thing-level config overrides
     */
    Optional<ThingValidationConfig> getThingConfig();

    /**
     * Returns the Feature-level config overrides.
     *
     * @return an optional containing the Feature-level config overrides
     */
    Optional<FeatureValidationConfig> getFeatureConfig();

    /**
     * Returns the alternative Thing definitions a write is validated against when it fails validation against the
     * Thing's own definition.
     *
     * @return the alternative Thing definitions, empty if none are configured
     * @since 3.10.0
     */
    List<ThingDefinition> getAlternativeThingDefinitions();

    /**
     * Creates a new instance of {@link ConfigOverrides} with the specified overrides.
     *
     * @param enabled the override for the enabled flag, may be {@code null}
     * @param logWarningInsteadOfFailingApiCalls the override for the log warning flag, may be {@code null}
     * @param thingConfig the Thing-level config overrides, may be {@code null}
     * @param featureConfig the Feature-level config overrides, may be {@code null}
     * @return a new instance of {@link ConfigOverrides}
     */
    static ConfigOverrides of(
            @Nullable final Boolean enabled,
            @Nullable final Boolean logWarningInsteadOfFailingApiCalls,
            @Nullable final ThingValidationConfig thingConfig,
            @Nullable final FeatureValidationConfig featureConfig) {
        return ImmutableConfigOverrides.of(enabled, logWarningInsteadOfFailingApiCalls, thingConfig, featureConfig);
    }

    /**
     * Creates a new instance of {@link ConfigOverrides} with the specified overrides.
     *
     * @param enabled the override for the enabled flag, may be {@code null}
     * @param logWarningInsteadOfFailingApiCalls the override for the log warning flag, may be {@code null}
     * @param thingConfig the Thing-level config overrides, may be {@code null}
     * @param featureConfig the Feature-level config overrides, may be {@code null}
     * @param alternativeThingDefinitions the alternative Thing definitions, each must be a URL
     * @return a new instance of {@link ConfigOverrides}
     * @throws org.eclipse.ditto.things.model.devops.exceptions.WotValidationConfigInvalidException if one of the
     * {@code alternativeThingDefinitions} is not a URL
     * @since 3.10.0
     */
    static ConfigOverrides of(
            @Nullable final Boolean enabled,
            @Nullable final Boolean logWarningInsteadOfFailingApiCalls,
            @Nullable final ThingValidationConfig thingConfig,
            @Nullable final FeatureValidationConfig featureConfig,
            final List<ThingDefinition> alternativeThingDefinitions) {
        return ImmutableConfigOverrides.of(enabled, logWarningInsteadOfFailingApiCalls, thingConfig, featureConfig,
                alternativeThingDefinitions);
    }

} 