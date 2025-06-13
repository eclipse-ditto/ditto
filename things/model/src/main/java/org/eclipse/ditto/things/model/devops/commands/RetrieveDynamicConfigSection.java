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
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.exceptions.WotValidationConfigRunTimeException;

/**
 * Command to retrieve a single dynamic config section in the WoT validation config.
 * <p>
 * This command is used to retrieve a specific dynamic configuration section identified by its scope ID.
 * The dynamic config section contains validation settings that can be overridden for a specific scope.
 * </p>
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableCommand(typePrefix = WotValidationConfigCommand.TYPE_PREFIX, name = RetrieveDynamicConfigSection.NAME)
public final class RetrieveDynamicConfigSection extends AbstractWotValidationConfigCommand<RetrieveDynamicConfigSection>
        implements WotValidationConfigCommand<RetrieveDynamicConfigSection> {

    /**
     * Name of this command.
     * This is used to identify the command type in the command journal and for deserialization.
     */
    public static final String NAME = "retrieveDynamicConfigSection";

    private static final String TYPE = TYPE_PREFIX + NAME;

    /**
     * JSON field definition for the scope ID.
     * This field identifies the specific dynamic config section to retrieve.
     */
    private static final JsonFieldDefinition<String> SCOPE_ID =
            JsonFactory.newStringFieldDefinition("scopeId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final String scopeId;

    /**
     * Constructs a new {@code RetrieveDynamicConfigSection} command.
     *
     * @param configId the ID of the WoT validation config.
     * @param scopeId the ID of the dynamic config section to retrieve.
     * @param dittoHeaders the headers of the command.
     */
    private RetrieveDynamicConfigSection(final WotValidationConfigId configId,
            final String scopeId,
            final DittoHeaders dittoHeaders) {
        super(TYPE, configId, dittoHeaders);
        if (scopeId.isEmpty()) {
            throw WotValidationConfigRunTimeException.newBuilder("Scope ID must not be empty").build();
        }
        this.scopeId = scopeId;
    }

    /**
     * Creates a new instance of {@code RetrieveDynamicConfigSection}.
     *
     * @param configId the ID of the WoT validation config.
     * @param scopeId the ID of the dynamic config section to retrieve.
     * @param dittoHeaders the headers of the command.
     * @return the new instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveDynamicConfigSection of(final WotValidationConfigId configId,
            final String scopeId,
            final DittoHeaders dittoHeaders) {
        return new RetrieveDynamicConfigSection(configId, scopeId, dittoHeaders);
    }

    /**
     * Creates a new instance of {@code RetrieveDynamicConfigSection} from a JSON object.
     * The JSON object should contain the following fields:
     * <ul>
     *     <li>{@code configId} (required): The ID of the WoT validation config</li>
     *     <li>{@code scopeId} (required): The ID of the dynamic config section to retrieve</li>
     * </ul>
     *
     * @param jsonObject the JSON object.
     * @param dittoHeaders the headers of the command.
     * @return the new instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static RetrieveDynamicConfigSection fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final String configIdString = jsonObject.getValueOrThrow(WotValidationConfigCommand.JsonFields.CONFIG_ID);
        final String scopeId = jsonObject.getValueOrThrow(SCOPE_ID);
        return of(WotValidationConfigId.of(configIdString), scopeId, dittoHeaders);
    }

    /**
     * @return the dynamic config scope identifier
     */
    public String getScopeId() {
        return scopeId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/dynamicConfigs/" + scopeId);
    }

    @Override
    public Command.Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public RetrieveDynamicConfigSection setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), scopeId, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(SCOPE_ID, scopeId, predicate);
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
        final RetrieveDynamicConfigSection that = (RetrieveDynamicConfigSection) o;
        return Objects.equals(scopeId, that.scopeId);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveDynamicConfigSection;
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