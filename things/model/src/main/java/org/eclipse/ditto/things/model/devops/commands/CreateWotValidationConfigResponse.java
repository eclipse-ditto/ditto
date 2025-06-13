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
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;

/**
 * Response to a {@link CreateWotValidationConfig} command.
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableCommandResponse(type = CreateWotValidationConfigResponse.TYPE)
public final class CreateWotValidationConfigResponse
        extends AbstractWotValidationConfigCommandResponse<CreateWotValidationConfigResponse>
        implements WithOptionalEntity<CreateWotValidationConfigResponse> {

    static final String TYPE = TYPE_PREFIX + CreateWotValidationConfig.NAME;

    private final JsonValue validationConfig;

    private CreateWotValidationConfigResponse(final WotValidationConfigId configId, final JsonValue validationConfig,
            final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatus.CREATED, configId, dittoHeaders);
        this.validationConfig = Objects.requireNonNull(validationConfig, "validationConfig");
    }

    /**
     * Returns a new instance of {@code CreateWotValidationConfigResponse}.
     *
     * @param configId the ID of the WoT validation config.
     * @param validationConfig the validation config.
     * @param dittoHeaders the headers of the response.
     * @return a new CreateWotValidationConfigResponse.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static CreateWotValidationConfigResponse of(final WotValidationConfigId configId,
            final JsonValue validationConfig,
            final DittoHeaders dittoHeaders) {
        return new CreateWotValidationConfigResponse(configId, validationConfig, dittoHeaders);
    }

    /**
     * Creates a new {@code CreateWotValidationConfigResponse} from the given JSON object and Ditto headers.
     *
     * @param jsonObject the JSON object containing the response data.
     * @param dittoHeaders the Ditto headers associated with this response.
     * @return a new CreateWotValidationConfigResponse.
     */
    public static CreateWotValidationConfigResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        final WotValidationConfigId configId =
                WotValidationConfigId.of(jsonObject.getValueOrThrow(WotValidationConfigCommand.JsonFields.CONFIG_ID));
        final JsonValue validationConfig =
                jsonObject.getValueOrThrow(WotValidationConfigCommand.JsonFields.VALIDATION_CONFIG);
        return of(configId, validationConfig, dittoHeaders);
    }

    /**
     * Returns the validation config.
     *
     * @return the validation config.
     */
    public JsonValue getValidationConfig() {
        return validationConfig;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(validationConfig);
    }

    @Override
    public CreateWotValidationConfigResponse setEntity(final JsonValue entity) {
        return of(configId, entity, getDittoHeaders());
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(WotValidationConfigCommand.JsonFields.VALIDATION_CONFIG, validationConfig, predicate);
    }

    @Override
    public CreateWotValidationConfigResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new CreateWotValidationConfigResponse(configId, validationConfig, dittoHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), validationConfig, configId);
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
        final CreateWotValidationConfigResponse that = (CreateWotValidationConfigResponse) o;
        return Objects.equals(validationConfig, that.validationConfig);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof CreateWotValidationConfigResponse;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", validationConfig=" + validationConfig +
                "]";
    }
}