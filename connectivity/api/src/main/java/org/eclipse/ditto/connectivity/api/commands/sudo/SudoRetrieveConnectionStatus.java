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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

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
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Command which retrieves the expected status of a connection.
 *
 * @since 3.1.0
 */
@Immutable
@JsonParsableCommand(typePrefix = ConnectivitySudoCommand.TYPE_PREFIX, name = SudoRetrieveConnectionStatus.NAME)
public final class SudoRetrieveConnectionStatus extends AbstractCommand<SudoRetrieveConnectionStatus>
        implements ConnectivitySudoCommand<SudoRetrieveConnectionStatus>, WithConnectionId {

    public static final String NAME = "sudoRetrieveConnectionStatus";

    public static final String TYPE = TYPE_PREFIX + NAME;

    private final ConnectionId connectionId;

    private SudoRetrieveConnectionStatus(final ConnectionId connectionId, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.connectionId = checkNotNull(connectionId, "connectionId");
    }

    /**
     * Returns a new instance of {@code SudoRetrieveConnectionStatus}.
     *
     * @param connectionId the Connection ID.
     * @param dittoHeaders the headers of the request.
     * @return a new SudoRetrieveConnectionStatus command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SudoRetrieveConnectionStatus of(final ConnectionId connectionId, final DittoHeaders dittoHeaders) {
        return new SudoRetrieveConnectionStatus(connectionId, dittoHeaders);
    }

    /**
     * Creates a new {@code SudoRetrieveConnectionStatus} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static SudoRetrieveConnectionStatus fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<SudoRetrieveConnectionStatus>(TYPE, jsonObject).deserialize(
                () -> {
                    final String readConnectionId =
                            jsonObject.getValueOrThrow(ConnectivitySudoCommand.JsonFields.JSON_CONNECTION_ID);
                    final ConnectionId connectionId = ConnectionId.of(readConnectionId);
                    return of(connectionId, dittoHeaders);
                });
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        jsonObjectBuilder.set(ConnectivitySudoCommand.JsonFields.JSON_CONNECTION_ID, connectionId.toString(),
                schemaVersion.and(thePredicate));
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public SudoRetrieveConnectionStatus setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(connectionId, dittoHeaders);
    }

    @Override
    public ConnectionId getEntityId() {
        return connectionId;
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SudoRetrieveConnectionStatus;
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
        final SudoRetrieveConnectionStatus that = (SudoRetrieveConnectionStatus) o;
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
