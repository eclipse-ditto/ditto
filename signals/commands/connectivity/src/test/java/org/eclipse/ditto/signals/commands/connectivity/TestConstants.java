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
package org.eclipse.ditto.signals.commands.connectivity;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;

/**
 * Constants for testing.
 */
public final class TestConstants {

    public static String ID = "myConnection";

    public static ConnectionType TYPE = ConnectionType.AMQP_10;
    public static ConnectionStatus STATUS = ConnectionStatus.OPEN;

    public static String URI = "amqps://username:password@my.endpoint:443";

    public static AuthorizationContext AUTHORIZATION_CONTEXT = AuthorizationContext.newInstance(
            AuthorizationSubject.newInstance("mySolutionId:mySubject"));

    public static final Set<Source> SOURCES = new HashSet<>(
            Arrays.asList(ConnectivityModelFactory.newSource(2, "amqp/source1"),
                    ConnectivityModelFactory.newSource(2, "amqp/source2")));

    public static final Set<Target> TARGETS = new HashSet<>(
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

    public static Connection CONNECTION =
            ConnectivityModelFactory.newConnectionBuilder(ID, TYPE, STATUS, URI, AUTHORIZATION_CONTEXT)
                    .sources(SOURCES)
                    .targets(TARGETS)
                    .mappingContext(MAPPING_CONTEXT)
                    .build();

    public static Map<String, ConnectionStatus> CONNECTION_STATUSES;

    static {
        CONNECTION_STATUSES = new HashMap<>();
        CONNECTION_STATUSES.put(ID, ConnectionStatus.OPEN);
    }

}
