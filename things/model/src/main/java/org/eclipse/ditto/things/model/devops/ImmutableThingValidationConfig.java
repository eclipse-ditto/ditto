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
 * Immutable value object representing thing-level configuration for WoT validation.
 *
 * @since 3.8.0
 */
@Immutable
final class ImmutableThingValidationConfig implements ThingValidationConfig {


    private static final JsonFieldDefinition<JsonObject> ENFORCE_FIELD =
            JsonFactory.newJsonObjectFieldDefinition("enforce", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<JsonObject> FORBID_FIELD =
            JsonFactory.newJsonObjectFieldDefinition("forbid", FieldType.REGULAR, JsonSchemaVersion.V_2);

    @Nullable private final ThingValidationEnforceConfig enforce;
    @Nullable private final ThingValidationForbidConfig forbid;

    private ImmutableThingValidationConfig(
            @Nullable final ThingValidationEnforceConfig enforce,
            @Nullable final ThingValidationForbidConfig forbid) {
        this.enforce = enforce;
        this.forbid = forbid;
    }

    /**
     * Creates a new instance of {@code ImmutableThingConfig}.
     *
     * @param enforce optional enforce configuration
     * @param forbid optional forbid configuration
     * @return a new instance with the specified values
     */
    public static ImmutableThingValidationConfig of(
            @Nullable final ThingValidationEnforceConfig enforce,
            @Nullable final ThingValidationForbidConfig forbid) {
        return new ImmutableThingValidationConfig(enforce, forbid);
    }

    @Override
    public Optional<ThingValidationEnforceConfig> getEnforce() {
        return Optional.ofNullable(enforce);
    }

    @Override
    public Optional<ThingValidationForbidConfig> getForbid() {
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
     * Creates a new instance of {@code ImmutableThingConfig} from a JSON object.
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
    public static ImmutableThingValidationConfig fromJson(final JsonObject jsonObject) {
        final ImmutableThingValidationEnforceConfig enforce = jsonObject.getValue(ENFORCE_FIELD)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(ImmutableThingValidationEnforceConfig::fromJson)
                .orElse(null);

        final ImmutableThingValidationForbidConfig forbid = jsonObject.getValue(FORBID_FIELD)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(ImmutableThingValidationForbidConfig::fromJson)
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
        final ImmutableThingValidationConfig that = (ImmutableThingValidationConfig) o;
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