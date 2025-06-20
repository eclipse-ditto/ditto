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
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;

/**
 * Command to retrieve a WoT validation configuration.
 * <p>
 * This command retrieves a WoT validation configuration identified by its config ID. The configuration
 * contains validation settings that determine how WoT Thing Models are validated, including settings
 * for thing and feature validation, as well as dynamic configuration sections for specific scopes.
 * </p>
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableCommand(typePrefix = WotValidationConfigCommand.TYPE_PREFIX, name = RetrieveWotValidationConfig.NAME)
public final class RetrieveWotValidationConfig extends AbstractWotValidationConfigCommand<RetrieveWotValidationConfig>
        implements WotValidationConfigCommand<RetrieveWotValidationConfig> {

    /**
     * Name of this command.
     * This is used to identify the command type in the command journal and for deserialization.
     */
    public static final String NAME = "retrieveWotValidationConfig";

    private static final String TYPE = TYPE_PREFIX + NAME;

    /**
     * Constructs a new {@code RetrieveWotValidationConfig} command.
     *
     * @param configId the ID of the WoT validation config to retrieve.
     * @param dittoHeaders the headers of the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code configId} is empty.
     */
    private RetrieveWotValidationConfig(final WotValidationConfigId configId, final DittoHeaders dittoHeaders) {
        super(TYPE, configId, dittoHeaders);

        // Validate config ID
        if (configId.toString().isEmpty()) {
            throw new IllegalArgumentException("Config ID must not be empty");
        }
    }

    /**
     * Creates a new instance of {@code RetrieveWotValidationConfig}.
     *
     * @param configId the ID of the WoT validation config to retrieve.
     * @param dittoHeaders the headers of the command.
     * @return the new instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveWotValidationConfig of(final WotValidationConfigId configId,
            final DittoHeaders dittoHeaders) {
        Objects.requireNonNull(configId, "configId");
        Objects.requireNonNull(dittoHeaders, "dittoHeaders");
        return new RetrieveWotValidationConfig(configId, dittoHeaders);
    }

    /**
     * Creates a new instance of {@code RetrieveWotValidationConfig} from a JSON object.
     * The JSON object should contain the following field:
     * <ul>
     *     <li>{@code configId} (required): The ID of the WoT validation config to retrieve</li>
     * </ul>
     *
     * @param jsonObject the JSON object.
     * @param dittoHeaders the headers of the command.
     * @return the new instance.
     */
    public static RetrieveWotValidationConfig fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final String configIdString = jsonObject.getValueOrThrow(WotValidationConfigCommand.JsonFields.CONFIG_ID);
        final WotValidationConfigId configId = WotValidationConfigId.of(configIdString);
        return of(configId, dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public Command.Category getCategory() {
        return Command.Category.QUERY;
    }

    @Override
    public RetrieveWotValidationConfig setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(WotValidationConfigCommand.JsonFields.CONFIG_ID, getEntityId().toString(), predicate);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveWotValidationConfig that = (RetrieveWotValidationConfig) o;
        return super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveWotValidationConfig;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                "]";
    }
}
