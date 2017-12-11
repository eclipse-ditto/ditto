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
package org.eclipse.ditto.signals.commands.amqpbridge;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;

import org.eclipse.ditto.model.amqpbridge.AmqpBridgeModelFactory;
import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.model.amqpbridge.ConnectionStatus;

/**
 * Constants for testing.
 */
public final class TestConstants {

    public static String ID = "myConnection";

    public static String URI = "amqps://username:password@my.endpoint:443";

    public static AuthorizationSubject AUTHORIZATION_SUBJECT =
            AuthorizationSubject.newInstance("mySolutionId:mySubject");

    public static Set<String> SOURCES = new HashSet<>(Arrays.asList("amqp/source1", "amqp/source2"));

    public static AmqpConnection CONNECTION =
            AmqpBridgeModelFactory.newConnection(ID, URI, AUTHORIZATION_SUBJECT, SOURCES, true);

    public static Map<String, ConnectionStatus> CONNECTION_STATUSES;

    static {
        CONNECTION_STATUSES = new HashMap<>();
        CONNECTION_STATUSES.put(ID, ConnectionStatus.OPEN);
    }

}
