/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.api.commands.sudo;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.WithConnectionId;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Command which retrieves all {@code tags} of a specified {@code ConnectionId}.
 *
 * @since 3.0.0
 */
@Immutable
@JsonParsableCommand(typePrefix = ConnectivitySudoCommand.TYPE_PREFIX, name = SudoRetrieveConnectionTags.NAME)
public final class SudoRetrieveConnectionTags extends AbstractCommand<SudoRetrieveConnectionTags>
        implements ConnectivitySudoCommand<SudoRetrieveConnectionTags>, WithConnectionId {

    public static final String NAME = "sudoRetrieveConnectionTags";

    public static final String TYPE = TYPE_PREFIX + NAME;

    private final ConnectionId connectionId;

    private SudoRetrieveConnectionTags(final ConnectionId connectionId, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.connectionId = connectionId;
    }

    /**
     * Returns a new instance of {@code SudoRetrieveConnectionTags}.
     *
     * @param connectionId the Connection ID for which the tags should be retrieved.
     * @param dittoHeaders the headers of the request.
     * @return a new SudoRetrieveConnectionTags command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SudoRetrieveConnectionTags of(final ConnectionId connectionId, final DittoHeaders dittoHeaders) {
        return new SudoRetrieveConnectionTags(connectionId, dittoHeaders);
    }

    /**
     * Creates a new {@code SudoRetrieveConnectionTags} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be retrieved.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static SudoRetrieveConnectionTags fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code SudoRetrieveConnectionTags} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static SudoRetrieveConnectionTags fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<SudoRetrieveConnectionTags>(TYPE, jsonObject).deserialize(
                () -> {
                    final String readConnectionId = jsonObject.getValueOrThrow(
                            ConnectivitySudoCommand.JsonFields.JSON_CONNECTION_ID);
                    final ConnectionId connectionId = ConnectionId.of(readConnectionId);
                    return of(connectionId, dittoHeaders);
                });
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ConnectivitySudoCommand.JsonFields.JSON_CONNECTION_ID, connectionId.toString(),
                predicate);
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public SudoRetrieveConnectionTags setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(connectionId, dittoHeaders);
    }

    @Override
    public ConnectionId getEntityId() {
        return connectionId;
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SudoRetrieveConnectionTags;
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
        final SudoRetrieveConnectionTags that = (SudoRetrieveConnectionTags) o;
        return Objects.equals(connectionId, that.connectionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connectionId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", connectionId=" + connectionId +
                "]";
    }

}
