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

import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
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
 * Command for modifying an existing WoT validation configuration.
 * <p>
 * This command encapsulates all information required to modify a WoT validation config, including the config ID,
 * the new configuration, and the Ditto headers.
 * </p>
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableCommand(typePrefix = WotValidationConfigCommand.TYPE_PREFIX, name = ModifyWotValidationConfig.NAME)
public final class ModifyWotValidationConfig extends AbstractWotValidationConfigCommand<ModifyWotValidationConfig>
        implements WotValidationConfigCommand<ModifyWotValidationConfig> {

    /**
     * Name of this command.
     */
    public static final String NAME = "modifyWotValidationConfig";

    /**
     * Type of this command.
     */
    private static final String TYPE = WotValidationConfigCommand.TYPE_PREFIX + NAME;

    private final WotValidationConfig validationConfig;

    /**
     * Constructs a new {@code ModifyWotValidationConfig} command.
     *
     * @param configId the ID of the config to modify.
     * @param validationConfig the WoT validation config to modify.
     * @param dittoHeaders the headers of the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    private ModifyWotValidationConfig(final WotValidationConfigId configId,
            final WotValidationConfig validationConfig,
            final DittoHeaders dittoHeaders) {
        super(TYPE, configId, dittoHeaders);
        this.validationConfig = Objects.requireNonNull(validationConfig, "validationConfig");
    }

    /**
     * Creates a new {@code ModifyWotValidationConfig} command.
     *
     * @param configId the ID of the config to modify.
     * @param validationConfig the WoT validation config to modify.
     * @param dittoHeaders the headers of the command.
     * @return a new command for modifying the WoT validation config.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyWotValidationConfig of(final WotValidationConfigId configId,
            final WotValidationConfig validationConfig,
            final DittoHeaders dittoHeaders) {
        return new ModifyWotValidationConfig(configId, validationConfig, dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyWotValidationConfig} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     */
    public static ModifyWotValidationConfig fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final String configIdString = jsonObject.getValueOrThrow(WotValidationConfigCommand.JsonFields.CONFIG_ID);
        final JsonObject validationConfigJson = jsonObject.getValueOrThrow(JsonFields.VALIDATION_CONFIG).asObject();
        return of(WotValidationConfigId.of(configIdString),
                WotValidationConfig.fromJson(validationConfigJson),
                dittoHeaders);
    }

    /**
     * Returns the validation config to modify.
     *
     * @return the validation config.
     */
    public WotValidationConfig getValidationConfig() {
        return validationConfig;
    }

    /**
     * Returns the type prefix for this command.
     *
     * @return the type prefix
     */
    @Override
    public String getTypePrefix() {
        return WotValidationConfigCommand.TYPE_PREFIX;
    }

    /**
     * Returns the resource path for this command.
     *
     * @return the resource path
     */
    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    /**
     * Returns the command category.
     *
     * @return the command category
     */
    @Override
    public Command.Category getCategory() {
        return Command.Category.MODIFY;
    }

    /**
     * Returns the entity type for this command.
     *
     * @return the entity type
     */
    @Override
    public EntityType getEntityType() {
        return EntityType.of(WotValidationConfigCommand.RESOURCE_TYPE);
    }

    /**
     * Returns a new instance of this command with the given Ditto headers.
     *
     * @param dittoHeaders the new Ditto headers
     * @return a new instance with the updated headers
     */
    @Override
    public ModifyWotValidationConfig setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), validationConfig, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JsonFields.VALIDATION_CONFIG, validationConfig.toJson(), predicate);
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
        final ModifyWotValidationConfig that = (ModifyWotValidationConfig) o;
        return Objects.equals(validationConfig, that.validationConfig);
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
     * This class contains definitions for all specific fields of this command's JSON representation.
     */
    @Immutable
    static final class JsonFields {

        /**
         * JSON field containing the validation config.
         */
        public static final JsonFieldDefinition<JsonValue> VALIDATION_CONFIG =
                JsonFactory.newJsonValueFieldDefinition("validationConfig", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
