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
package org.eclipse.ditto.signals.events.connectivity;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.Topic;

/**
 * Constants for testing.
 */
public final class TestConstants {

    public static String ID = "myConnectionId";

    private static final ConnectionType TYPE = ConnectionType.AMQP_10;
    public static ConnectionStatus STATUS = ConnectionStatus.OPEN;

    public static String URI = "amqps://username:password@my.endpoint:443";

    public static AuthorizationContext AUTHORIZATION_CONTEXT = AuthorizationContext.newInstance(
            AuthorizationSubject.newInstance("mySolutionId:mySubject"));

    public static final List<Source> SOURCES = Arrays.asList(ConnectivityModelFactory.newSource(2,0, AUTHORIZATION_CONTEXT, "amqp/source1"),
            ConnectivityModelFactory.newSource(2, 1, AUTHORIZATION_CONTEXT, "amqp/source2"));

    public static final Set<Target> TARGETS = new HashSet<>(
            Collections.singletonList(ConnectivityModelFactory.newTarget("eventQueue", AUTHORIZATION_CONTEXT, Topic.TWIN_EVENTS)));
    public static Connection CONNECTION =
            ConnectivityModelFactory.newConnectionBuilder(ID, TYPE, STATUS, URI)
                    .sources(SOURCES)
                    .targets(TARGETS)
                    .build();

    public static Map<String, ConnectionStatus> CONNECTION_STATUSES;

    static {
        CONNECTION_STATUSES = new HashMap<>();
        CONNECTION_STATUSES.put(ID, ConnectionStatus.OPEN);
    }

}
