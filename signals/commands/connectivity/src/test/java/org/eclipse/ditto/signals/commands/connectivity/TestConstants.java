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

/**
 * Constants for testing.
 */
public final class TestConstants {

    public static String ID = "amqp-10:myConnection";

    public static ConnectionType TYPE = ConnectionType.AMQP_10;

    public static String URI = "amqps://username:password@my.endpoint:443";

    public static AuthorizationContext AUTHORIZATION_CONTEXT = AuthorizationContext.newInstance(
            AuthorizationSubject.newInstance("mySolutionId:mySubject"));

    public static Set<String> SOURCES = new HashSet<>(Arrays.asList("amqp/source1", "amqp/source2"));

    public static String TARGET = "eventQueue";

    public static Connection CONNECTION =
            ConnectivityModelFactory.newConnectionBuilder(ID, TYPE, URI, AUTHORIZATION_CONTEXT)
                    .sources(SOURCES)
                    .eventTarget(TARGET)
                    .build();

    public static MappingContext MAPPING_CONTEXT = ConnectivityModelFactory.newMappingContext("text/plain",
            "JavaScript",
            Collections.singletonMap("incomingMappingScript",
                    "ditto_protocolJson.topic = 'org.eclipse.ditto/foo-bar/things/twin/commands/create';" +
                    "ditto_protocolJson.path = '/';" +
                    "ditto_protocolJson.headers = {};" +
                    "ditto_protocolJson.headers['correlation-id'] = ditto_mappingHeaders['correlation-id'];" +
                    "ditto_protocolJson.value = ditto_mappingString;"));

    public static Map<String, ConnectionStatus> CONNECTION_STATUSES;

    static {
        CONNECTION_STATUSES = new HashMap<>();
        CONNECTION_STATUSES.put(ID, ConnectionStatus.OPEN);
    }

}
