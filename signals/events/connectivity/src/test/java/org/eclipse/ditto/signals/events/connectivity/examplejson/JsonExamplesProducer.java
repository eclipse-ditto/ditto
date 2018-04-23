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
package org.eclipse.ditto.signals.events.connectivity.examplejson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.signals.events.connectivity.ConnectionClosed;
import org.eclipse.ditto.signals.events.connectivity.ConnectionCreated;
import org.eclipse.ditto.signals.events.connectivity.ConnectionDeleted;
import org.eclipse.ditto.signals.events.connectivity.ConnectionModified;
import org.eclipse.ditto.signals.events.connectivity.ConnectionOpened;

public class JsonExamplesProducer {

    private static final String ID = "myConnection";

    private static final ConnectionType TYPE = ConnectionType.AMQP_10;
    public static ConnectionStatus STATUS = ConnectionStatus.OPEN;

    private static final String URI = "amqps://foo:bar@example.com:443";

    private static final AuthorizationContext AUTHORIZATION_CONTEXT = AuthorizationContext.newInstance(
            AuthorizationSubject.newInstance("mySolutionId:mySubject"));

    private static final Set<Source> SOURCES = new HashSet<>(
            Arrays.asList(ConnectivityModelFactory.newSource(2, "amqp/source1"),
                    ConnectivityModelFactory.newSource(2, "amqp/source2")));

    private static final Set<Target> TARGETS = new HashSet<>(
            Collections.singletonList(ConnectivityModelFactory.newTarget("eventQueue", "_/_/things/twin/events")));

    public static MappingContext MAPPING_CONTEXT = ConnectivityModelFactory.newMappingContext(
            "JavaScript",
            Collections.singletonMap("incomingScript",
                    "function mapToDittoProtocolMsg(\n" +
                            "    headers,\n" +
                            "    textPayload,\n" +
                            "    bytePayload,\n" +
                            "    contentType\n" +
                            ") {\n" +
                            "\n" +
                            "    // ###\n" +
                            "    // Insert your mapping logic here\n" +
                            "    let namespace = \"org.eclipse.ditto\";\n" +
                            "    let id = \"foo-bar\";\n" +
                            "    let group = \"things\";\n" +
                            "    let channel = \"twin\";\n" +
                            "    let criterion = \"commands\";\n" +
                            "    let action = \"modify\";\n" +
                            "    let path = \"/attributes/foo\";\n" +
                            "    let dittoHeaders = headers;\n" +
                            "    let value = textPayload;\n" +
                            "    // ###\n" +
                            "\n" +
                            "    return Ditto.buildDittoProtocolMsg(\n" +
                            "        namespace,\n" +
                            "        id,\n" +
                            "        group,\n" +
                            "        channel,\n" +
                            "        criterion,\n" +
                            "        action,\n" +
                            "        path,\n" +
                            "        dittoHeaders,\n" +
                            "        value\n" +
                            "    );\n" +
                            "}"));

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
        produceConnectivityEvents(rootPath.resolve("connectivity"));
    }

    private static void produceConnectivityEvents(final Path rootPath) throws IOException {
        produceEvents(rootPath);
    }

    private static void produceEvents(final Path rootPath) throws IOException {
        final Path eventsDir = rootPath.resolve(Paths.get("events"));
        Files.createDirectories(eventsDir);

        final Connection connection =
                ConnectivityModelFactory.newConnectionBuilder(ID, TYPE, STATUS, URI, AUTHORIZATION_CONTEXT)
                        .sources(SOURCES)
                        .targets(TARGETS)
                        .mappingContext(MAPPING_CONTEXT)
                        .build();
        final DittoHeaders headers = DittoHeaders.empty();

        final ConnectionCreated connectionCreated = ConnectionCreated.of(connection, headers);
        writeJson(eventsDir.resolve(Paths.get("connectionCreated.json")), connectionCreated);

        final ConnectionModified connectionModified = ConnectionModified.of(connection, headers);
        writeJson(eventsDir.resolve(Paths.get("connectionModified.json")), connectionModified);

        final ConnectionOpened connectionOpened = ConnectionOpened.of(ID, headers);
        writeJson(eventsDir.resolve(Paths.get("connectionOpened.json")), connectionOpened);

        final ConnectionClosed connectionClosed = ConnectionClosed.of(ID, headers);
        writeJson(eventsDir.resolve(Paths.get("connectionClosed.json")), connectionClosed);

        final ConnectionDeleted connectionDeleted = ConnectionDeleted.of(ID, headers);
        writeJson(eventsDir.resolve(Paths.get("connectionDeleted.json")), connectionDeleted);
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
