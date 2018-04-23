/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.commands.connectivity.examplejson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.signals.commands.connectivity.TestConstants;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionUnavailableException;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.ModifyConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.ModifyConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatus;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatusResponse;

public class JsonExamplesProducer {

    public static void main(final String... args) throws IOException {
        run(args, new JsonExamplesProducer());
    }

    private static void run(final String[] args, final JsonExamplesProducer producer) throws
            IOException {
        if (args.length != 1) {
            System.err.println("Exactly 1 argument required: the target folder in which to generate the JSON files");
            System.exit(-1);
        }
        producer.produce(Paths.get(args[0]));
    }

    private void produce(final Path rootPath) throws IOException {
        produceConnectivityCommands(rootPath.resolve("connectivity"));
    }

    private static void produceConnectivityCommands(final Path rootPath) throws IOException {
        produceModifyCommands(rootPath);
        produceModifyResponse(rootPath);
        produceViewCommands(rootPath);
        produceViewCommandResponse(rootPath);
        produceExceptions(rootPath);
    }

    private static void produceModifyCommands(final Path rootPath) throws IOException {
        final Path commandsDir = rootPath.resolve(Paths.get("commands", "modify"));
        Files.createDirectories(commandsDir);

        final TestConnection testConnection =
                TestConnection.of(TestConstants.CONNECTION, DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("testConnection.json")), testConnection);

        final CreateConnection createConnection =
                CreateConnection.of(TestConstants.CONNECTION, DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("createConnection.json")), createConnection);

        final ModifyConnection modifyConnection =
                ModifyConnection.of(TestConstants.CONNECTION, DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("modifyConnection.json")), modifyConnection);

        final DeleteConnection deleteConnection =
                DeleteConnection.of(TestConstants.ID, DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("deleteConnection.json")), deleteConnection);

        final OpenConnection openConnection =
                OpenConnection.of(TestConstants.ID, DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("openConnection.json")), openConnection);

        final CloseConnection closeConnection =
                CloseConnection.of(TestConstants.ID, DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("closeConnection.json")), closeConnection);
    }

    private static void produceModifyResponse(final Path rootPath) throws IOException {
        final Path commandsDir = rootPath.resolve(Paths.get("responses", "modify"));
        Files.createDirectories(commandsDir);

        final TestConnectionResponse testConnectionResponse =
                TestConnectionResponse.success(TestConstants.CONNECTION.getId(), "connected",  DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("testConnectionResponse.json")), testConnectionResponse);

        final CreateConnectionResponse createConnectionResponse =
                CreateConnectionResponse.of(TestConstants.CONNECTION, DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("createConnection.json")), createConnectionResponse);

        final ModifyConnectionResponse modifyConnectionResponseCreated =
                ModifyConnectionResponse.created(TestConstants.CONNECTION, DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("modifyConnectionCreated.json")), modifyConnectionResponseCreated);

        final ModifyConnectionResponse modifyConnectionResponseModified =
                ModifyConnectionResponse.created(TestConstants.CONNECTION, DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("modifyConnectionModified.json")), modifyConnectionResponseModified);

        final DeleteConnectionResponse deleteConnectionResponse =
                DeleteConnectionResponse.of(TestConstants.ID, DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("deleteConnection.json")), deleteConnectionResponse);

        final OpenConnectionResponse openConnectionResponse =
                OpenConnectionResponse.of(TestConstants.ID, DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("openConnection.json")), openConnectionResponse);

        final CloseConnectionResponse closeConnectionResponse =
                CloseConnectionResponse.of(TestConstants.ID, DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("closeConnection.json")), closeConnectionResponse);

    }

    private static void produceViewCommands(final Path rootPath) throws IOException {
        final Path commandsDir = rootPath.resolve(Paths.get("commands", "view"));
        Files.createDirectories(commandsDir);

        final RetrieveConnection retrieveConnection =
                RetrieveConnection.of(TestConstants.ID, DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("retrieveConnection.json")), retrieveConnection);

        final RetrieveConnectionStatus retrieveConnectionStatus =
                RetrieveConnectionStatus.of(TestConstants.ID, DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("retrieveConnectionStatus.json")), retrieveConnectionStatus);

        final RetrieveConnectionMetrics retrieveConnectionMetrics =
                RetrieveConnectionMetrics.of(TestConstants.ID, DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("retrieveConnectionMetrics.json")), retrieveConnectionMetrics);
    }

    private static void produceViewCommandResponse(final Path rootPath) throws IOException {
        final Path commandsDir = rootPath.resolve(Paths.get("responses", "view"));
        Files.createDirectories(commandsDir);

        final RetrieveConnectionResponse retrieveConnectionResponse =
                RetrieveConnectionResponse.of(TestConstants.CONNECTION, DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("retrieveConnection.json")), retrieveConnectionResponse);

        final RetrieveConnectionStatusResponse retrieveConnectionStatusResponse =
                RetrieveConnectionStatusResponse.of(TestConstants.ID, ConnectionStatus.OPEN, DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("retrieveConnectionStatus.json")), retrieveConnectionStatusResponse);

        final RetrieveConnectionMetricsResponse retrieveConnectionMetricsResponse =
                RetrieveConnectionMetricsResponse.of(TestConstants.ID,
                        ConnectivityModelFactory.newConnectionMetrics(ConnectionStatus.OPEN, "some status",
                                Instant.now(), "CONNECTED",  Collections.emptyList(), Collections.emptyList()),
                        DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("retrieveConnectionMetrics.json")), retrieveConnectionMetricsResponse);
    }

    private static void produceExceptions(final Path rootPath) throws IOException {
        final Path exceptionsDir = rootPath.resolve(Paths.get("errors"));
        Files.createDirectories(exceptionsDir);

        final ConnectionFailedException
                connectionFailedException = ConnectionFailedException.newBuilder(TestConstants.ID).build();
        writeJson(exceptionsDir.resolve(Paths.get("connectionFailedException.json")), connectionFailedException);

        final ConnectionNotAccessibleException
                connectionNotAccessibleException = ConnectionNotAccessibleException.newBuilder(TestConstants.ID).build();
        writeJson(exceptionsDir.resolve(Paths.get("connectionNotAccessibleException.json")), connectionNotAccessibleException);

        final ConnectionUnavailableException
                connectionUnavailableException = ConnectionUnavailableException.newBuilder(TestConstants.ID).build();
        writeJson(exceptionsDir.resolve(Paths.get("connectionUnavailableException.json")), connectionUnavailableException);
    }

    private static void writeJson(final Path path, final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable)
            throws IOException {
        writeJson(path, jsonifiable, JsonSchemaVersion.LATEST);
    }

    private static void writeJson(final Path path, final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable,
            final JsonSchemaVersion schemaVersion) throws IOException {
        final String jsonString = jsonifiable.toJsonString(schemaVersion);
        System.out.println("Writing file: " + path.toAbsolutePath());
        Files.write(path, jsonString.getBytes());
    }

}
