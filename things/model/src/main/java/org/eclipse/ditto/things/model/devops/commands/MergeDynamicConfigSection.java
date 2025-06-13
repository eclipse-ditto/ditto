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
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

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
import org.eclipse.ditto.things.model.devops.DynamicValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.exceptions.WotValidationConfigInvalidException;

/**
 * Command to merge a single dynamic config section in the WoT validation config.
 * <p>
 * This command is used to merge (create or update) a specific dynamic validation config section, identified by its scope ID,
 * into a WoT validation configuration. The command ensures consistency between the scope ID in the path and the payload.
 * </p>
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableCommand(typePrefix = WotValidationConfigCommand.TYPE_PREFIX, name = MergeDynamicConfigSection.NAME)
public final class MergeDynamicConfigSection extends AbstractWotValidationConfigCommand<MergeDynamicConfigSection>
        implements WotValidationConfigCommand<MergeDynamicConfigSection> {

    /**
     * Name of this command.
     */
    public static final String NAME = "mergeDynamicConfigSection";

    private static final String TYPE = TYPE_PREFIX + NAME;

    private static final JsonFieldDefinition<String> SCOPE_ID =
            JsonFactory.newStringFieldDefinition("scopeId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final JsonFieldDefinition<JsonObject> VALIDATION_CONTEXT =
            JsonFactory.newJsonObjectFieldDefinition("validationContext", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final JsonFieldDefinition<JsonObject> CONFIG_OVERRIDES =
            JsonFactory.newJsonObjectFieldDefinition("configOverrides", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final String scopeId;
    private final DynamicValidationConfig dynamicConfigSection;

    private MergeDynamicConfigSection(final WotValidationConfigId configId,
            final String scopeId,
            final DynamicValidationConfig dynamicConfigSection,
            final DittoHeaders dittoHeaders) {
        super(TYPE, configId, dittoHeaders);
        this.scopeId = checkNotNull(scopeId, "scopeId");
        this.dynamicConfigSection = Objects.requireNonNull(dynamicConfigSection, "dynamicConfigSection");
    }

    /**
     * Creates a new {@code MergeDynamicConfigSection} command.
     *
     * @param configId the ID of the config to modify.
     * @param scopeId the dynamic config scope identifier
     * @param dynamicConfigSection the dynamic config validation config section
     * @param dittoHeaders the headers of the command.
     * @return a new command for modifying the WoT validation config.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static MergeDynamicConfigSection of(final WotValidationConfigId configId,
            final String scopeId,
            final DynamicValidationConfig dynamicConfigSection,
            final DittoHeaders dittoHeaders) {
        ensureScopeIdMatches(scopeId, dynamicConfigSection);

        return new MergeDynamicConfigSection(configId, scopeId, dynamicConfigSection, dittoHeaders);
    }

    private static void ensureScopeIdMatches(String scopeId, DynamicValidationConfig dynamicConfigSection) {
        if (!dynamicConfigSection.getScopeId().equals(scopeId)) {
            throw WotValidationConfigInvalidException.newBuilder(
                            "The scopeId in the thing JSON is not equal to the scopeId in the topic path.")
                    .build();
        }
    }

    /**
     * Creates a new {@code MergeDynamicConfigSection} command from a JSON object.
     *
     * @param jsonObject the JSON object to parse.
     * @param dittoHeaders the Ditto headers.
     * @return the parsed command.
     * @throws NullPointerException if any required argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the JSON is invalid or missing required fields.
     */
    public static MergeDynamicConfigSection fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        if (!jsonObject.getValue(SCOPE_ID).isPresent()) {
            throw WotValidationConfigInvalidException.newBuilder("Missing required field 'scopeId' in payload")
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
        final String configIdString = jsonObject.getValueOrThrow(WotValidationConfigCommand.JsonFields.CONFIG_ID);
        final String scopeId = jsonObject.getValueOrThrow(SCOPE_ID);
        final JsonObject dynamicConfigJson = JsonObject.newBuilder()
                .set(SCOPE_ID, scopeId)
                .set(VALIDATION_CONTEXT, jsonObject.getValueOrThrow(VALIDATION_CONTEXT))
                .set(CONFIG_OVERRIDES, jsonObject.getValueOrThrow(CONFIG_OVERRIDES))
                .build();
        final DynamicValidationConfig dynamicConfigSection = DynamicValidationConfig.fromJson(dynamicConfigJson);
        return of(WotValidationConfigId.of(configIdString), scopeId, dynamicConfigSection, dittoHeaders);
    }

    /**
     * @return the dynamic config scope identifier
     */
    public String getScopeId() {
        return scopeId;
    }

    /**
     * @return the dynamic config validation config section
     */
    public DynamicValidationConfig getDynamicConfigSection() {
        return dynamicConfigSection;
    }

    @Override
    public MergeDynamicConfigSection setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), getScopeId(), dynamicConfigSection, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(SCOPE_ID, getScopeId(), predicate);
        dynamicConfigSection.getValidationContext()
                .ifPresent(validationContext ->
                        jsonObjectBuilder.set(VALIDATION_CONTEXT, validationContext.toJson(), predicate)
                );
        dynamicConfigSection.getConfigOverrides()
                .ifPresent(configOverrides ->
                        jsonObjectBuilder.set(CONFIG_OVERRIDES, configOverrides.toJson(), predicate)
                );
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/dynamicConfigs/" + scopeId);
    }

    @Override
    public Command.Category getCategory() {
        return Command.Category.MERGE;
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
        final MergeDynamicConfigSection that = (MergeDynamicConfigSection) o;
        return Objects.equals(scopeId, that.scopeId) && Objects.equals(dynamicConfigSection, that.dynamicConfigSection);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof MergeDynamicConfigSection;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), scopeId, dynamicConfigSection);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", scopeId=" + scopeId +
                ", dynamicConfigSection=" + dynamicConfigSection +
                "]";
    }
} 