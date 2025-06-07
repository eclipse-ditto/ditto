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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;

/**
 * Command to delete a single dynamic config section in the WoT validation config.
 * <p>
 * This command is used to delete a specific dynamic configuration section identified by its scope ID.
 * The dynamic config section contains validation settings that can be overridden for a specific scope.
 * This command is immutable and thread-safe.
 * </p>
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableCommand(typePrefix = WotValidationConfigCommand.TYPE_PREFIX, name = DeleteDynamicConfigSection.NAME)
public final class DeleteDynamicConfigSection extends AbstractWotValidationConfigCommand<DeleteDynamicConfigSection>
        implements WotValidationConfigCommand<DeleteDynamicConfigSection> {

    /**
     * Name of this command.
     * This is used to identify the command type in the command journal and for deserialization.
     */
    public static final String NAME = "deleteDynamicConfigSection";

    /**
     * Type of this command.
     * This is the full type identifier including the prefix.
     */
    private static final String TYPE = WotValidationConfigCommand.TYPE_PREFIX + NAME;

    /**
     * JSON field definition for the scope ID.
     * This field identifies the specific dynamic config section to delete.
     */
    private static final JsonFieldDefinition<String> SCOPE_ID =
            JsonFactory.newStringFieldDefinition("scopeId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final String scopeId;

    /**
     * Constructs a new {@code DeleteDynamicConfigSection} command.
     *
     * @param configId the ID of the WoT validation config.
     * @param scopeId the ID of the dynamic config section to delete.
     * @param dittoHeaders the headers of the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    private DeleteDynamicConfigSection(final WotValidationConfigId configId, final String scopeId, final DittoHeaders dittoHeaders) {
        super(TYPE, configId, dittoHeaders);
        this.scopeId = Objects.requireNonNull(scopeId, "scopeId");
    }

    /**
     * Creates a new instance of {@code DeleteDynamicConfigSection}.
     *
     * @param configId the ID of the WoT validation config.
     * @param scopeId the ID of the dynamic config section to delete.
     * @param dittoHeaders the headers of the command.
     * @return the new instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DeleteDynamicConfigSection of(final WotValidationConfigId configId, final String scopeId, final DittoHeaders dittoHeaders) {
        return new DeleteDynamicConfigSection(configId, scopeId, dittoHeaders);
    }

    /**
     * Creates a new instance of {@code DeleteDynamicConfigSection} from a JSON object.
     * The JSON object should contain the following fields:
     * <ul>
     *     <li>{@code configId} (required): The ID of the WoT validation config</li>
     *     <li>{@code scopeId} (required): The ID of the dynamic config section to delete</li>
     * </ul>
     *
     * @param jsonObject the JSON object.
     * @param dittoHeaders the headers of the command.
     * @return the new instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static DeleteDynamicConfigSection fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final String configIdString = jsonObject.getValueOrThrow(WotValidationConfigCommand.JsonFields.CONFIG_ID);
        final String scopeId = jsonObject.getValueOrThrow(SCOPE_ID);
        return of(WotValidationConfigId.of(configIdString), scopeId, dittoHeaders);
    }

    /**
     * Returns the scope ID of the dynamic config section to delete.
     *
     * @return the scope ID.
     */
    public String getScopeId() {
        return scopeId;
    }

    @Override
    public String getTypePrefix() {
        return WotValidationConfigCommand.TYPE_PREFIX;
    }

    @Override
    public DeleteDynamicConfigSection setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), scopeId, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
                                 final Predicate<JsonField> predicate) {
        super.appendPayload(jsonObjectBuilder, schemaVersion, predicate);
        jsonObjectBuilder.set(SCOPE_ID, scopeId, predicate);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/dynamicConfig/" + scopeId);
    }

    @Override
    public Command.Category getCategory() {
        return Command.Category.DELETE;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final DeleteDynamicConfigSection that = (DeleteDynamicConfigSection) o;
        return Objects.equals(scopeId, that.scopeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), scopeId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", scopeId=" + scopeId +
                "]";
    }
} 