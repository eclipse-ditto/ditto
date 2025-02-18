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
package org.eclipse.ditto.things.model.signals.commands.modify;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.UnsupportedSchemaVersionException;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandSizeValidator;

/**
 * Command to update a Thing's definition and apply a migration payload.
 *
 * @since 3.7.0
 */
@Immutable
@JsonParsableCommand(typePrefix = ThingCommand.TYPE_PREFIX, name = MigrateThingDefinition.NAME)
public final class MigrateThingDefinition extends AbstractCommand<MigrateThingDefinition>
        implements ThingModifyCommand<MigrateThingDefinition> {

    /**
     * Name of the "Migrate Thing Definition" command.
     */
    public static final String NAME = "migrateThingDefinition";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final ThingId thingId;
    private final String thingDefinitionUrl;
    private final JsonObject migrationPayload;
    private final Map<ResourceKey, String> patchConditions;
    private final boolean initializeMissingPropertiesFromDefaults;


    private MigrateThingDefinition(final ThingId thingId,
            final String thingDefinitionUrl,
            final JsonObject migrationPayload,
            final Map<ResourceKey, String> patchConditions,
            final boolean initializeMissingPropertiesFromDefaults,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.thingDefinitionUrl = checkNotNull(thingDefinitionUrl, "thingDefinitionUrl");
        this.migrationPayload = checkJsonSize(checkNotNull(migrationPayload, "migrationPayload"), dittoHeaders);
        this.patchConditions = Collections.unmodifiableMap(checkNotNull(patchConditions, "patchConditions"));
        this.initializeMissingPropertiesFromDefaults = initializeMissingPropertiesFromDefaults;
        checkSchemaVersion();
    }

    /**
     * Factory method to create a new {@code MigrateThingDefinition} command.
     *
     * @param thingId the Thing ID.
     * @param thingDefinitionUrl the URL of the new Thing definition.
     * @param migrationPayload the migration payload.
     * @param patchConditions the patch conditions.
     * @param initializeMissingPropertiesFromDefaults whether to initialize properties.
     * @param dittoHeaders the Ditto headers.
     * @return the created {@link MigrateThingDefinition} command.
     */
    public static MigrateThingDefinition of(final ThingId thingId,
            final String thingDefinitionUrl,
            final JsonObject migrationPayload,
            final Map<ResourceKey, String> patchConditions,
            final boolean initializeMissingPropertiesFromDefaults,
            final DittoHeaders dittoHeaders) {
        return new MigrateThingDefinition(thingId, thingDefinitionUrl, migrationPayload,
                patchConditions, initializeMissingPropertiesFromDefaults, dittoHeaders);
    }

    /**
     * Creates a new {@code MigrateThingDefinition} from a JSON object.
     *
     * @param jsonObject the JSON object from which to create the command.
     * @param dittoHeaders the Ditto headers.
     * @return the created {@code MigrateThingDefinition} command.
     */
    public static MigrateThingDefinition fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final String thingIdStr = jsonObject.getValueOrThrow(ThingCommand.JsonFields.JSON_THING_ID);
        final String thingDefinitionUrl = jsonObject.getValueOrThrow(JsonFields.JSON_THING_DEFINITION_URL);
        final JsonObject migrationPayload = jsonObject.getValue(JsonFields.JSON_MIGRATION_PAYLOAD)
                .map(JsonValue::asObject).orElse(JsonFactory.newObject());
        final JsonObject patchConditionsJson = jsonObject.getValue(JsonFields.JSON_PATCH_CONDITIONS)
                .map(JsonValue::asObject).orElse(JsonFactory.newObject());
        final Map<ResourceKey, String> patchConditions = patchConditionsJson.stream()
                .collect(Collectors.toMap(
                        field -> ResourceKey.newInstance(field.getKey()),
                        field -> field.getValue().asString()
                ));

        final boolean initializeMissingPropertiesFromDefaults =
                jsonObject.getValue(JsonFields.JSON_INITIALIZE_MISSING_PROPERTIES_FROM_DEFAULTS)
                        .orElse(false);

        return new MigrateThingDefinition(
                ThingId.of(thingIdStr),
                thingDefinitionUrl,
                migrationPayload,
                patchConditions,
                initializeMissingPropertiesFromDefaults,
                dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    public String getThingDefinitionUrl() {
        return thingDefinitionUrl;
    }

    public JsonObject getMigrationPayload() {
        return migrationPayload;
    }

    public Map<ResourceKey, String> getPatchConditions() {
        return patchConditions;
    }

    public boolean isInitializeMissingPropertiesFromDefaults() {
        return initializeMissingPropertiesFromDefaults;
    }

    @Override
    public Optional<JsonValue> getEntity() {
        // This command doesn't represent an entity directly.
        return Optional.empty();
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return getEntity();
    }

    @Override
    public MigrateThingDefinition setEntity(final JsonValue entity) {
        return this;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Override
    public Category getCategory() {
        return Category.MIGRATE;
    }

    @Override
    public MigrateThingDefinition setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new MigrateThingDefinition(
                thingId, thingDefinitionUrl, migrationPayload, patchConditions, initializeMissingPropertiesFromDefaults,
                dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicateParameter) {
        final Predicate<JsonField> predicate = schemaVersion.and(predicateParameter);
        jsonObjectBuilder.set(ThingCommand.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.JSON_THING_DEFINITION_URL, thingDefinitionUrl, predicate);
        jsonObjectBuilder.set(JsonFields.JSON_MIGRATION_PAYLOAD, migrationPayload, predicate);
        jsonObjectBuilder.set(JsonFields.JSON_INITIALIZE_MISSING_PROPERTIES_FROM_DEFAULTS,
                initializeMissingPropertiesFromDefaults, predicate);

        if (!patchConditions.isEmpty()) {
            final JsonObjectBuilder conditionsBuilder = JsonFactory.newObjectBuilder();
            patchConditions.forEach(conditionsBuilder::set);
            jsonObjectBuilder.set(JsonFields.JSON_PATCH_CONDITIONS, conditionsBuilder.build(), predicate);
        }
    }

    private void checkSchemaVersion() {
        final JsonSchemaVersion implementedSchemaVersion = getImplementedSchemaVersion();
        if (!implementsSchemaVersion(implementedSchemaVersion)) {
            throw UnsupportedSchemaVersionException.newBuilder(implementedSchemaVersion).build();
        }
    }

    private JsonObject checkJsonSize(final JsonObject value, final DittoHeaders dittoHeaders) {
        ThingCommandSizeValidator.getInstance().ensureValidSize(
                value::getUpperBoundForStringSize,
                () -> value.toString().length(),
                () -> dittoHeaders);
        return value;
    }

    /**
     * An enumeration of the JSON fields of an {@code MigrateThingDefinition} command.
     */
    @Immutable
    public static final class JsonFields {

        public static final JsonFieldDefinition<String> JSON_THING_DEFINITION_URL =
                JsonFactory.newStringFieldDefinition("thingDefinitionUrl", FieldType.REGULAR, JsonSchemaVersion.V_2);

        public static final JsonFieldDefinition<JsonObject> JSON_MIGRATION_PAYLOAD =
                JsonFactory.newJsonObjectFieldDefinition("migrationPayload", FieldType.REGULAR, JsonSchemaVersion.V_2);

        public static final JsonFieldDefinition<JsonObject> JSON_PATCH_CONDITIONS =
                JsonFactory.newJsonObjectFieldDefinition("patchConditions", FieldType.REGULAR, JsonSchemaVersion.V_2);

        public static final JsonFieldDefinition<Boolean> JSON_INITIALIZE_MISSING_PROPERTIES_FROM_DEFAULTS =
                JsonFactory.newBooleanFieldDefinition("initializeMissingPropertiesFromDefaults", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof MigrateThingDefinition)) return false;
        if (!super.equals(o)) return false;
        final MigrateThingDefinition that = (MigrateThingDefinition) o;
        return initializeMissingPropertiesFromDefaults == that.initializeMissingPropertiesFromDefaults &&
                thingId.equals(that.thingId) &&
                thingDefinitionUrl.equals(that.thingDefinitionUrl) &&
                migrationPayload.equals(that.migrationPayload) &&
                patchConditions.equals(that.patchConditions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, thingDefinitionUrl, migrationPayload, patchConditions,
                initializeMissingPropertiesFromDefaults);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", thingId=" + thingId +
                ", thingDefinitionUrl='" + thingDefinitionUrl + '\'' +
                ", migrationPayload=" + migrationPayload +
                ", patchConditions=" + patchConditions +
                ", initializeMissingPropertiesFromDefaults=" + initializeMissingPropertiesFromDefaults +
                ", dittoHeaders=" + getDittoHeaders() +
                ']';
    }
}
