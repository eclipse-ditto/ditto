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
 * Immutable value object representing feature-level configuration for WoT validation.
 *
 * @since 3.8.0
 */
@Immutable
final class ImmutableFeatureValidationConfig implements FeatureValidationConfig {


    private static final JsonFieldDefinition<JsonObject> ENFORCE_FIELD =
            JsonFactory.newJsonObjectFieldDefinition("enforce", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<JsonObject> FORBID_FIELD =
            JsonFactory.newJsonObjectFieldDefinition("forbid", FieldType.REGULAR, JsonSchemaVersion.V_2);

    @Nullable private final FeatureValidationEnforceConfig enforce;
    @Nullable private final FeatureValidationForbidConfig forbid;

    private ImmutableFeatureValidationConfig(
            @Nullable final FeatureValidationEnforceConfig enforce,
            @Nullable final FeatureValidationForbidConfig forbid) {
        this.enforce = enforce;
        this.forbid = forbid;
    }

    /**
     * Creates a new instance of {@code ImmutableFeatureConfig}.
     *
     * @param enforce optional enforce configuration
     * @param forbid optional forbid configuration
     * @return a new instance with the specified values
     */
    public static ImmutableFeatureValidationConfig of(
            @Nullable final FeatureValidationEnforceConfig enforce,
            @Nullable final FeatureValidationForbidConfig forbid) {
        return new ImmutableFeatureValidationConfig(enforce, forbid);
    }

    @Override
    public Optional<FeatureValidationEnforceConfig> getEnforce() {
        return Optional.ofNullable(enforce);
    }

    @Override
    public Optional<FeatureValidationForbidConfig> getForbid() {
        return Optional.ofNullable(forbid);
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        getEnforce().ifPresent(config -> builder.set(ENFORCE_FIELD, config.toJson()));
        getForbid().ifPresent(config -> builder.set(FORBID_FIELD, config.toJson()));
        return builder.build();
    }

    /**
     * Creates a new instance of {@code ImmutableFeatureConfig} from a JSON object.
     * The JSON object should contain the following fields:
     * <ul>
     *     <li>{@code enforce} (optional): The enforce configuration</li>
     *     <li>{@code forbid} (optional): The forbid configuration</li>
     * </ul>
     *
     * @param jsonObject the JSON object to create the configuration from
     * @return a new instance created from the JSON object
     * @throws NullPointerException if {@code jsonObject} is {@code null}
     * @throws IllegalArgumentException if the JSON object is invalid
     */
    public static ImmutableFeatureValidationConfig fromJson(final JsonObject jsonObject) {
        final ImmutableFeatureValidationEnforceConfig enforce = jsonObject.getValue(ENFORCE_FIELD)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(ImmutableFeatureValidationEnforceConfig::fromJson)
                .orElse(null);

        final ImmutableFeatureValidationForbidConfig forbid = jsonObject.getValue(FORBID_FIELD)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(obj -> {
                    try {
                        return ImmutableFeatureValidationForbidConfig.fromJson(obj);
                    } catch (final Exception e) {
                        throw new IllegalArgumentException("Failed to parse forbid config: " + e.getMessage(), e);
                    }
                })
                .orElse(null);

        return of(enforce, forbid);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableFeatureValidationConfig that = (ImmutableFeatureValidationConfig) o;
        return Objects.equals(enforce, that.enforce) &&
                Objects.equals(forbid, that.forbid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enforce, forbid);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enforce=" + enforce +
                ", forbid=" + forbid +
                "]";
    }
}