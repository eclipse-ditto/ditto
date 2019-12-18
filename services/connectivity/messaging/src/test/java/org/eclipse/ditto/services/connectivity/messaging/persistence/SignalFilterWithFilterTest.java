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
package org.eclipse.ditto.services.connectivity.messaging.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.base.auth.AuthorizationModelFactory.newAuthContext;
import static org.eclipse.ditto.model.base.auth.AuthorizationModelFactory.newAuthSubject;
import static org.eclipse.ditto.model.connectivity.Topic.LIVE_EVENTS;
import static org.eclipse.ditto.model.connectivity.Topic.TWIN_EVENTS;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.HeaderMapping;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitorRegistry;
import org.eclipse.ditto.signals.events.things.ThingModified;
import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.services.connectivity.messaging.persistence.SignalFilter} for filtering with namespace + RQL filter.
 */
public class SignalFilterWithFilterTest {

    private static final String URI = "amqp://user:pass@host:1111/path";
    private static final ConnectionId CONNECTION_ID = ConnectionId.of("id");
    private static final ThingId THING_ID = ThingId.of("foo:bar13");
    private static final AuthorizationSubject AUTHORIZED = newAuthSubject("authorized");
    private static final AuthorizationSubject UNAUTHORIZED = newAuthSubject("unauthorized");
    private static final HeaderMapping HEADER_MAPPING =
            ConnectivityModelFactory.newHeaderMapping(Collections.singletonMap("reply-to", "{{fn:delete()}}"));

    private final ConnectionMonitorRegistry connectionMonitorRegistry = TestConstants.Monitoring.MONITOR_REGISTRY_MOCK;

    @Test
    public void applySignalFilterWithNamespaces() {

        // targetA does filter for namespaces "org.eclipse.ditto" and "foo"
        final List<String> namespacesA = Arrays.asList("org.eclipse.ditto", "foo");
        final Target targetA = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/a")
                .authorizationContext(newAuthContext(AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS)
                        .withNamespaces(namespacesA)
                        .build())
                .build();

        // targetB does filter for namespaces "org.example"
        final List<String> namespacesB = Collections.singletonList("org.example");
        final Target targetB = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/b")
                .authorizationContext(newAuthContext(AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS)
                        .withNamespaces(namespacesB)
                        .build())
                .build();

        // targetC does filter for namespaces "foo", but uses the "UNAUTHORIZED" subjects
        final List<String> namespacesC = Collections.singletonList("foo");
        final Target targetC = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/c")
                .authorizationContext(newAuthContext(UNAUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS)
                        .withNamespaces(namespacesC)
                        .build())
                .build();

        final Connection connection = ConnectivityModelFactory
                .newConnectionBuilder(CONNECTION_ID, ConnectionType.AMQP_10, ConnectivityStatus.OPEN, URI)
                .targets(Arrays.asList(targetA, targetB, targetC))
                .build();

        final Thing thing = Thing.newBuilder()
                .setId(THING_ID) // WHEN: the namespace of the modified thing is "foo"
                .setAttribute(JsonPointer.of("test"), JsonValue.of(42))
                .build();
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .readSubjects(Collections.singletonList(AUTHORIZED.getId()))
                .build();
        final ThingModified thingModified = ThingModified.of(thing, 3L, headers);

        final SignalFilter signalFilter = new SignalFilter(connection, connectionMonitorRegistry);
        final List<Target> filteredTargets = signalFilter.filter(thingModified);

        assertThat(filteredTargets).containsOnly(targetA); // THEN: only targetA should be in the filtered targets
    }

    @Test
    public void applySignalFilterWithRqlFilter() {

        final List<String> allNamespaces = Collections.emptyList();

        // targetA does filter for all namespaces and filters that attribute "test" > 23
        final String filterA = "gt(attributes/test,23)";
        final Target targetA = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/a")
                .authorizationContext(newAuthContext(AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(LIVE_EVENTS)
                        .withNamespaces(allNamespaces)
                        .withFilter(filterA)
                        .build())
                .build();

        // targetB does filter for all namespaces and filters that attribute "test" > 50
        final String filterB = "gt(attributes/test,50)";
        final Target targetB = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/b")
                .authorizationContext(newAuthContext(AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(LIVE_EVENTS)
                        .withNamespaces(allNamespaces)
                        .withFilter(filterB)
                        .build())
                .build();

        // targetC does filter for all namespaces and filters that attribute "test" > 23, but uses the "UNAUTHORIZED" subjects
        final String filterC = "gt(attributes/test,50)";
        final Target targetC = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/c")
                .authorizationContext(newAuthContext(UNAUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(LIVE_EVENTS)
                        .withNamespaces(allNamespaces)
                        .withFilter(filterC)
                        .build())
                .build();

        final Connection connection = ConnectivityModelFactory
                .newConnectionBuilder(CONNECTION_ID, ConnectionType.AMQP_10, ConnectivityStatus.OPEN, URI)
                .targets(Arrays.asList(targetA, targetB, targetC))
                .build();

        final Thing thing = Thing.newBuilder()
                .setId(THING_ID)
                .setAttribute(JsonPointer.of("test"), JsonValue.of(42)) // WHEN: the "test" value is 42
                .build();
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .readSubjects(Collections.singletonList(AUTHORIZED.getId()))
                .channel(TopicPath.Channel.LIVE.getName())
                .build();
        final ThingModified thingModified = ThingModified.of(thing, 3L, headers);

        final SignalFilter signalFilter = new SignalFilter(connection, connectionMonitorRegistry);
        final List<Target> filteredTargets = signalFilter.filter(thingModified);

        assertThat(filteredTargets).containsOnly(targetA); // THEN: only targetA should be in the filtered targets
    }

    @Test
    public void applySignalFilterWithNamespacesAndRqlFilter() {

        // targetA does filter for namespaces "org.eclipse.ditto" and "foo" and filters that attribute "test" > 23
        final List<String> namespacesA = Arrays.asList("org.eclipse.ditto", "foo");
        final String filterA = "gt(attributes/test,23)";
        final Target targetA = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/a")
                .authorizationContext(newAuthContext(AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS)
                        .withNamespaces(namespacesA)
                        .withFilter(filterA)
                        .build())
                .build();

        // targetB does filter for namespaces "org.example" and filters that attribute "test" < 50
        final List<String> namespacesB = Collections.singletonList("org.example");
        final String filterB = "lt(attributes/test,50)";
        final Target targetB = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/b")
                .authorizationContext(newAuthContext(AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS)
                        .withNamespaces(namespacesB)
                        .withFilter(filterB)
                        .build())
                .build();

        // targetC does filter for namespaces "foo" and filters that attribute "test" ==  42, but uses the "UNAUTHORIZED" subjects
        final List<String> namespacesC = Collections.singletonList("foo");
        final String filterC = "eq(attributes/test,42)";
        final Target targetC = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/c")
                .authorizationContext(newAuthContext(UNAUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS)
                        .withNamespaces(namespacesC)
                        .withFilter(filterC)
                        .build())
                .build();

        // targetD does filter for namespaces "foo" and filters that attribute "test" ==  42
        final List<String> namespacesD = Collections.singletonList("foo");
        final String filterD = "eq(attributes/test,42)";
        final Target targetD = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/d")
                .authorizationContext(newAuthContext(AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS)
                        .withNamespaces(namespacesD)
                        .withFilter(filterD)
                        .build())
                .build();

        final Connection connection =
                ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID, ConnectionType.AMQP_10,
                        ConnectivityStatus.OPEN, URI)
                        .targets(Arrays.asList(targetA, targetB, targetC, targetD))
                        .build();

        final Thing thing = Thing.newBuilder()
                .setId(THING_ID) // WHEN: the namespace of the modified thing is "foo"
                .setAttribute(JsonPointer.of("test"), JsonValue.of(42)) // WHEN: the "test" value is 42
                .build();
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .readSubjects(Collections.singletonList(AUTHORIZED.getId()))
                .build();
        final ThingModified thingModified = ThingModified.of(thing, 3L, headers);

        final SignalFilter signalFilter = new SignalFilter(connection, connectionMonitorRegistry);
        final List<Target> filteredTargets = signalFilter.filter(thingModified);

        assertThat(filteredTargets).containsOnly(targetA, targetD); // THEN: only targetA and targetD should be in the filtered targets
    }

}
