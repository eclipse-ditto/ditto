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
package org.eclipse.ditto.signals.events.connectivity;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.Topic;

/**
 * Constants for testing.
 */
public final class TestConstants {

    public static final ConnectionId ID = ConnectionId.of("myConnectionId");

    private static final ConnectionType TYPE = ConnectionType.AMQP_10;
    public static final ConnectivityStatus STATUS = ConnectivityStatus.OPEN;

    public static final String URI = "amqps://username:password@my.endpoint:443";

    public static final AuthorizationContext AUTHORIZATION_CONTEXT = AuthorizationContext.newInstance(
            DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION, AuthorizationSubject.newInstance("myIssuer:mySubject"));

    public static final List<Source> SOURCES = Arrays.asList(
            ConnectivityModelFactory.newSourceBuilder()
                    .authorizationContext(AUTHORIZATION_CONTEXT)
                    .address("amqp/source1")
                    .consumerCount(2)
                    .index(0).build(),
            ConnectivityModelFactory.newSourceBuilder()
                    .authorizationContext(AUTHORIZATION_CONTEXT)
                    .address("amqp/source2")
                    .consumerCount(2)
                    .index(1).build()
    );

    public static final List<Target> TARGETS = Collections.singletonList(ConnectivityModelFactory.newTargetBuilder()
            .address("eventQueue")
            .authorizationContext(AUTHORIZATION_CONTEXT)
            .topics(Topic.TWIN_EVENTS)
            .build());

    public static final Connection CONNECTION = ConnectivityModelFactory.newConnectionBuilder(ID, TYPE, STATUS, URI)
            .sources(SOURCES)
            .targets(TARGETS)
            .build();

}
