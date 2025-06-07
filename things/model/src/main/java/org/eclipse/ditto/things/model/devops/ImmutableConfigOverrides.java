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

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Immutable value object representing configuration overrides for WoT validation.
 * <p>
 * This class encapsulates override values for global, Thing-level, and Feature-level validation settings
 * used in dynamic WoT validation config sections.
 * </p>
 *
 * @since 3.8.0
 */
@Immutable
final class ImmutableConfigOverrides implements ConfigOverrides{


    private static final JsonFieldDefinition<Boolean> ENABLED_FIELD =
            JsonFactory.newBooleanFieldDefinition("enabled", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<Boolean> LOG_WARNING_INSTEAD_OF_FAILING_FIELD =
            JsonFactory.newBooleanFieldDefinition("logWarningInsteadOfFailingApiCalls", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<JsonObject> THING_FIELD =
            JsonFactory.newJsonObjectFieldDefinition("thing", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<JsonObject> FEATURE_FIELD =
            JsonFactory.newJsonObjectFieldDefinition("feature", FieldType.REGULAR, JsonSchemaVersion.V_2);

    @Nullable private final Boolean enabled;
    @Nullable private final Boolean logWarningInsteadOfFailingApiCalls;
    @Nullable private final ThingValidationConfig thingConfig;
    @Nullable private final FeatureValidationConfig featureConfig;

    private ImmutableConfigOverrides(
            @Nullable final Boolean enabled,
            @Nullable final Boolean logWarningInsteadOfFailingApiCalls,
            @Nullable final ThingValidationConfig thingConfig,
            @Nullable final FeatureValidationConfig featureConfig) {
        this.enabled = enabled;
        this.logWarningInsteadOfFailingApiCalls = logWarningInsteadOfFailingApiCalls;
        this.thingConfig = thingConfig;
        this.featureConfig = featureConfig;
    }

    /**
     * Creates a new instance of {@code ImmutableConfigOverrides}.
     *
     * @param enabled override for global enabled flag
     * @param logWarningInsteadOfFailingApiCalls override for global log warning flag
     * @param thingConfig Thing-level config overrides
     * @param featureConfig Feature-level config overrides
     * @return a new instance with the specified values
     */
    public static ImmutableConfigOverrides of(
            @Nullable final Boolean enabled,
            @Nullable final Boolean logWarningInsteadOfFailingApiCalls,
            @Nullable final ThingValidationConfig thingConfig,
            @Nullable final FeatureValidationConfig featureConfig) {
        return new ImmutableConfigOverrides(enabled, logWarningInsteadOfFailingApiCalls, thingConfig, featureConfig);
    }

    @Override
    public Optional<Boolean> isEnabled() {
        return Optional.ofNullable(enabled);
    }

    @Override
    public Optional<Boolean> isLogWarningInsteadOfFailingApiCalls() {
        return Optional.ofNullable(logWarningInsteadOfFailingApiCalls);
    }

    @Override
    public Optional<ThingValidationConfig> getThingConfig() {
        return Optional.ofNullable(thingConfig);
    }

    @Override
    public Optional<FeatureValidationConfig> getFeatureConfig() {
        return Optional.ofNullable(featureConfig);
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        isEnabled().ifPresent(value -> builder.set(ENABLED_FIELD, value));
        isLogWarningInsteadOfFailingApiCalls().ifPresent(value -> builder.set(LOG_WARNING_INSTEAD_OF_FAILING_FIELD, value));
        getThingConfig().ifPresent(config -> builder.set(THING_FIELD, config.toJson()));
        getFeatureConfig().ifPresent(config -> builder.set(FEATURE_FIELD, config.toJson()));
        return builder.build();
    }

    /**
     * Creates a new instance of {@code ImmutableConfigOverrides} from a JSON object.
     * The JSON object should contain the following fields:
     * <ul>
     *     <li>{@code enabled} (optional): The override for the global enabled flag</li>
     *     <li>{@code logWarningInsteadOfFailing} (optional): The override for the global log warning flag</li>
     *     <li>{@code thing} (optional): The Thing-level config overrides</li>
     *     <li>{@code feature} (optional): The Feature-level config overrides</li>
     * </ul>
     *
     * @param jsonObject the JSON object to create the configuration from
     * @return a new instance created from the JSON object
     * @throws NullPointerException if {@code jsonObject} is {@code null}
     * @throws IllegalArgumentException if the JSON object is invalid
     */
    public static ImmutableConfigOverrides fromJson(final JsonObject jsonObject) {
        final Boolean enabled = jsonObject.getValue(ENABLED_FIELD)
                .orElse(null);

        final Boolean logWarning = jsonObject.getValue(LOG_WARNING_INSTEAD_OF_FAILING_FIELD)
                .orElse(null);

        final ThingValidationConfig thing = jsonObject.getValue(THING_FIELD)
                .map(JsonValue::asObject)
                .map(ImmutableThingValidationConfig::fromJson)
                .orElse(null);

        final FeatureValidationConfig feature = jsonObject.getValue(FEATURE_FIELD)
                .map(JsonValue::asObject)
                .map(ImmutableFeatureValidationConfig::fromJson)
                .orElse(null);

        return of(enabled, logWarning, thing, feature);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableConfigOverrides that = (ImmutableConfigOverrides) o;
        return Objects.equals(enabled, that.enabled) &&
                Objects.equals(logWarningInsteadOfFailingApiCalls, that.logWarningInsteadOfFailingApiCalls) &&
                Objects.equals(thingConfig, that.thingConfig) &&
                Objects.equals(featureConfig, that.featureConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, logWarningInsteadOfFailingApiCalls, thingConfig, featureConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enabled=" + enabled +
                ", logWarningInsteadOfFailingApiCalls=" + logWarningInsteadOfFailingApiCalls +
                ", thingConfig=" + thingConfig +
                ", featureConfig=" + featureConfig +
                "]";
    }
}
