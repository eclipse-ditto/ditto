/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;

/**
 * Immutable implementation of {@link Connection}.
 */
@Immutable
final class ImmutableConnection extends AbstractConnection {

    private ImmutableConnection(final ImmutableConnection.Builder builder) {
        super(builder);
    }

    /**
     * Returns a new {@code ConnectionBuilder} object.
     *
     * @param id the connection ID.
     * @param connectionType the connection type.
     * @param connectionStatus the connection status.
     * @param uri the URI.
     * @return new instance of {@code ConnectionBuilder}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ConnectionBuilder getBuilder(final ConnectionId id,
            final ConnectionType connectionType,
            final ConnectivityStatus connectionStatus,
            final String uri) {

        return new Builder(connectionType)
                .id(id)
                .connectionStatus(connectionStatus)
                .uri(uri);
    }

    /**
     * Returns a new {@code ConnectionBuilder} object.
     *
     * @param connection the connection to use for initializing the builder.
     * @return new instance of {@code ImmutableConnectionBuilder}.
     * @throws NullPointerException if {@code connection} is {@code null}.
     */
    public static ConnectionBuilder getBuilder(final Connection connection) {
        checkNotNull(connection, "connection");
        return fromConnection(connection, new Builder(connection.getConnectionType()));
    }

    @Override
    ConnectionUri getConnectionUri(@Nullable String builderConnectionUri) {
        return ConnectionUri.of(checkNotNull(builderConnectionUri, "uri"));
    }


    /**
     * Creates a new {@code Connection} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the Connection to be created.
     * @return a new Connection which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static Connection fromJson(final JsonObject jsonObject) {
        final ConnectionType type = getConnectionTypeOrThrow(jsonObject);

        final ImmutableConnection.Builder builder = new Builder(type);
        buildFromJson(jsonObject, builder);
        return builder.build();
    }

    static ConnectionType getConnectionTypeOrThrow(final JsonObject jsonObject) {
        final String readConnectionType = jsonObject.getValueOrThrow(JsonFields.CONNECTION_TYPE);
        return ConnectionType.forName(readConnectionType)
                .orElseThrow(() -> JsonParseException.newBuilder()
                        .message(MessageFormat.format("Connection type <{0}> is invalid!", readConnectionType))
                        .build());
    }

    /**
     * Builder for {@code ImmutableConnection}.
     */
    @NotThreadSafe
    private static final class Builder extends AbstractConnectionBuilder {
        Builder(final ConnectionType connectionType) {
            super(connectionType);
            this.connectionType = checkNotNull(connectionType, "connectionType");
        }

        @Override
        public Connection build() {
            checkSourceAndTargetAreValid();
            checkAuthorizationContextsAreValid();
            checkConnectionAnnouncementsOnlySetIfClientCount1();
            migrateLegacyConfigurationOnTheFly();
            return new ImmutableConnection(this);
        }

    }

}
