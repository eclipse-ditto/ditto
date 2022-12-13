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
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.WithConnectionId;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Command which requests the connection persistence actor to send arguments with which to construct client actor props.
 *
 * @since 3.1.0
 */
@Immutable
@JsonParsableCommand(typePrefix = ConnectivitySudoCommand.TYPE_PREFIX, name = SudoRetrieveClientActorProps.NAME)
public final class SudoRetrieveClientActorProps extends AbstractCommand<SudoRetrieveClientActorProps>
        implements ConnectivitySudoCommand<SudoRetrieveClientActorProps>, WithConnectionId {

    public static final String NAME = "sudoRetrieveClientActorProps";

    public static final String TYPE = TYPE_PREFIX + NAME;

    private static final JsonFieldDefinition<Integer> CLIENT_ACTOR_NUMBER =
            JsonFactory.newIntFieldDefinition("clientActorNumber", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final ConnectionId connectionId;
    private final int clientActorNumber;

    private SudoRetrieveClientActorProps(final ConnectionId connectionId, final int clientActorId,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.connectionId = connectionId;
        this.clientActorNumber = clientActorId;
    }

    /**
     * Returns a new instance of {@code SudoRetrieveClientActorProps}.
     *
     * @param connectionId the Connection ID.
     * @param clientActorNumber the client actor number.
     * @param dittoHeaders the headers of the request.
     * @return a new SudoRetrieveClientActorProps command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SudoRetrieveClientActorProps of(final ConnectionId connectionId,
            final int clientActorNumber, final DittoHeaders dittoHeaders) {
        return new SudoRetrieveClientActorProps(connectionId, clientActorNumber, dittoHeaders);
    }

    /**
     * Creates a new {@code SudoRetrieveClientActorProps} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static SudoRetrieveClientActorProps fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<SudoRetrieveClientActorProps>(TYPE, jsonObject).deserialize(
                () -> {
                    final String readConnectionId =
                            jsonObject.getValueOrThrow(ConnectivitySudoCommand.JsonFields.JSON_CONNECTION_ID);
                    final ConnectionId connectionId = ConnectionId.of(readConnectionId);
                    final int clientNumber = jsonObject.getValueOrThrow(CLIENT_ACTOR_NUMBER);
                    return of(connectionId, clientNumber, dittoHeaders);
                });
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ConnectivitySudoCommand.JsonFields.JSON_CONNECTION_ID, connectionId.toString(),
                predicate);
        jsonObjectBuilder.set(CLIENT_ACTOR_NUMBER, clientActorNumber);
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public SudoRetrieveClientActorProps setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(connectionId, clientActorNumber, dittoHeaders);
    }

    @Override
    public ConnectionId getEntityId() {
        return connectionId;
    }

    /**
     * Retrieve the client actor number.
     *
     * @return The client actor number.
     */
    public int getClientActorNumber() {
        return clientActorNumber;
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SudoRetrieveClientActorProps;
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
        final SudoRetrieveClientActorProps that = (SudoRetrieveClientActorProps) o;
        return Objects.equals(connectionId, that.connectionId) && clientActorNumber == that.clientActorNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connectionId, clientActorNumber);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", connectionId=" + connectionId +
                ", clientActorNumber=" + clientActorNumber +
                "]";
    }

}
