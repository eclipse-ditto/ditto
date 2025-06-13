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
package org.eclipse.ditto.things.model.devops.commands;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.WithResource;
import org.eclipse.ditto.base.model.signals.commands.WithEntity;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;

/**
 * Response to a {@link RetrieveDynamicConfigSection} command.
 * <p>
 * This response contains the retrieved dynamic configuration sections that have been defined for WoT validation
 * configuration as a JSON value.
 * The configuration includes validation settings for WoT Thing Models, such as thing and feature validation
 * settings.
 * </p>
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveDynamicConfigSectionResponse.TYPE)
public final class RetrieveDynamicConfigSectionResponse
        extends AbstractWotValidationConfigCommandResponse<RetrieveDynamicConfigSectionResponse>
        implements WithEntity<RetrieveDynamicConfigSectionResponse>, WithResource {

    static final String TYPE = TYPE_PREFIX + RetrieveDynamicConfigSection.NAME;

    private final JsonValue validationConfig;

    /**
     * Constructs a new {@code RetrieveDynamicConfigSectionResponse} object.
     *
     * @param configId the ID of the WoT validation config that was retrieved.
     * @param validationConfig the validation config as a JSON value, containing all validation settings
     * including thing and feature validation settings, and any dynamic config sections.
     * @param dittoHeaders the headers of the response.
     */
    private RetrieveDynamicConfigSectionResponse(final WotValidationConfigId configId, final JsonValue validationConfig,
            final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatus.OK, configId, dittoHeaders);
        this.validationConfig = Objects.requireNonNull(validationConfig, "validationConfig");
    }

    /**
     * Creates a new instance of {@code RetrieveDynamicConfigSectionResponse}.
     *
     * @param configId the ID of the WoT validation config that was retrieved.
     * @param validationConfig the validation config as a JSON value, containing all validation settings
     * including thing and feature validation settings, and any dynamic config sections.
     * @param dittoHeaders the headers of the response.
     * @return a new RetrieveDynamicConfigSectionResponse.
     */
    public static RetrieveDynamicConfigSectionResponse of(final WotValidationConfigId configId,
            final JsonValue validationConfig,
            final DittoHeaders dittoHeaders) {
        return new RetrieveDynamicConfigSectionResponse(configId, validationConfig, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveDynamicConfigSectionResponse} from a JSON string.
     * The JSON string should contain the following fields:
     * <ul>
     *     <li>{@code configId} (required): The ID of the WoT validation config that was retrieved</li>
     *     <li>{@code validationConfig} (required): The validation config as a JSON value</li>
     * </ul>
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     */
    public static RetrieveDynamicConfigSectionResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveDynamicConfigSectionResponse} from a JSON object.
     * The JSON object should contain the following fields:
     * <ul>
     *     <li>{@code configId} (required): The ID of the WoT validation config that was retrieved</li>
     *     <li>{@code validationConfig} (required): The validation config as a JSON value</li>
     * </ul>
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     */
    public static RetrieveDynamicConfigSectionResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        final WotValidationConfigId configId =
                WotValidationConfigId.of(jsonObject.getValueOrThrow(WotValidationConfigCommand.JsonFields.CONFIG_ID));
        final JsonValue validationConfig =
                jsonObject.getValueOrThrow(WotValidationConfigCommand.JsonFields.VALIDATION_CONFIG);
        return of(configId, validationConfig, dittoHeaders);
    }

    /**
     * Returns the validation config as a JSON value.
     * The config contains all validation settings including thing and feature validation settings,
     * as well as any dynamic config sections that have been defined for specific scopes.
     *
     * @return the validation config as a JSON value.
     */
    public JsonValue getValidationConfig() {
        return validationConfig;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return validationConfig;
    }

    @Override
    public RetrieveDynamicConfigSectionResponse setEntity(final JsonValue entity) {
        return of(configId, entity, getDittoHeaders());
    }

    @Override
    public RetrieveDynamicConfigSectionResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(configId, validationConfig, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(WotValidationConfigCommand.JsonFields.VALIDATION_CONFIG, validationConfig, predicate);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final RetrieveDynamicConfigSectionResponse that = (RetrieveDynamicConfigSectionResponse) o;
        return Objects.equals(validationConfig, that.validationConfig);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveDynamicConfigSectionResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), validationConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", validationConfig=" + validationConfig +
                "]";
    }
} 