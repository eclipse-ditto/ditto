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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;

/**
 * Command for creating a new WoT validation configuration.
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableCommand(typePrefix = WotValidationConfigCommand.TYPE_PREFIX, name = CreateWotValidationConfig.NAME)
public final class CreateWotValidationConfig extends AbstractWotValidationConfigCommand<CreateWotValidationConfig>
        implements WotValidationConfigCommand<CreateWotValidationConfig>,
        WithOptionalEntity<CreateWotValidationConfig> {

    /**
     * Name of this command.
     */
    public static final String NAME = "createWotValidationConfig";

    private static final String TYPE = TYPE_PREFIX + NAME;

    private final WotValidationConfig validationConfig;

    /**
     * Constructs a new {@code CreateWotValidationConfig} command.
     *
     * @param configId the ID of the config to create.
     * @param validationConfig the WoT validation config to create.
     * @param dittoHeaders the headers of the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    private CreateWotValidationConfig(final WotValidationConfigId configId,
            final WotValidationConfig validationConfig,
            final DittoHeaders dittoHeaders) {
        super(TYPE, configId, dittoHeaders);
        this.validationConfig = checkNotNull(validationConfig, "validationConfig");

        if (!validationConfig.getEntityId().isPresent()) {
            throw new IllegalArgumentException("Entity ID must be present");
        }
    }

    /**
     * Creates a new {@code CreateWotValidationConfig} command.
     *
     * @param configId the ID of the config to create.
     * @param validationConfig the WoT validation config to create.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static CreateWotValidationConfig of(final WotValidationConfigId configId,
            final WotValidationConfig validationConfig,
            final DittoHeaders dittoHeaders) {
        return new CreateWotValidationConfig(configId, validationConfig, dittoHeaders);
    }

    /**
     * Creates a new {@code CreateWotValidationConfig} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     */
    public static CreateWotValidationConfig fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final JsonObject validationConfigJson = jsonObject.getValueOrThrow(JsonFields.VALIDATION_CONFIG);
        final WotValidationConfig validationConfig = WotValidationConfig.fromJson(validationConfigJson);
        final String configIdString = jsonObject.getValueOrThrow(WotValidationConfigCommand.JsonFields.CONFIG_ID);

        return of(WotValidationConfigId.of(configIdString), validationConfig, dittoHeaders);
    }

    @Override
    public Optional<JsonValue> getEntity() {
        return Optional.of(validationConfig.toJson());
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return getEntity();
    }

    @Override
    public CreateWotValidationConfig setEntity(final JsonValue entity) {
        return of(getEntityId(), WotValidationConfig.fromJson(entity.asObject()), getDittoHeaders());
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public Command.Category getCategory() {
        return Category.CREATE;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JsonFields.VALIDATION_CONFIG, validationConfig.toJson(), predicate);
    }

    @Override
    public CreateWotValidationConfig setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), validationConfig, dittoHeaders);
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
        final CreateWotValidationConfig that = (CreateWotValidationConfig) o;
        return Objects.equals(validationConfig, that.validationConfig);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof CreateWotValidationConfig;
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

    /**
     * Returns the WoT validation config to be created.
     *
     * @return the WoT validation config.
     */
    public WotValidationConfig getValidationConfig() {
        return validationConfig;
    }

    private static final class JsonFields {

        static final JsonFieldDefinition<JsonObject> VALIDATION_CONFIG =
                JsonFactory.newJsonObjectFieldDefinition("validationConfig", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);
    }
}