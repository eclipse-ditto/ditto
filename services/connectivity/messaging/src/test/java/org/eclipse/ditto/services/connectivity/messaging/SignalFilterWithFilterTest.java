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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.eclipse.ditto.model.base.auth.AuthorizationModelFactory.newAuthContext;
import static org.eclipse.ditto.model.base.auth.AuthorizationModelFactory.newAuthSubject;
import static org.eclipse.ditto.model.connectivity.ConnectivityModelFactory.newFilteredTopic;
import static org.eclipse.ditto.model.connectivity.ConnectivityModelFactory.newTarget;
import static org.eclipse.ditto.model.connectivity.Topic.LIVE_EVENTS;
import static org.eclipse.ditto.model.connectivity.Topic.TWIN_EVENTS;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.signals.events.things.ThingModified;
import org.junit.Test;

/**
 * Tests {@link SignalFilter} for filtering with namespace + RQL filter.
 */
public class SignalFilterWithFilterTest {

    private static final String URI = "amqp://user:pass@host:1111/path";
    private static final String CONNECTION = "id";
    private static final AuthorizationSubject AUTHORIZED = newAuthSubject("authorized");
    private static final AuthorizationSubject UNAUTHORIZED = newAuthSubject("unauthorized");

    @Test
    public void applySignalFilterWithNamespaces() {

        // targetA does filter for namespaces "org.eclipse.ditto" and "foo"
        final List<String> namespacesA = Arrays.asList("org.eclipse.ditto", "foo");
        final Target targetA = newTarget("twin/a", newAuthContext(AUTHORIZED),
                newFilteredTopic(TWIN_EVENTS, namespacesA));

        // targetB does filter for namespaces "org.example"
        final List<String> namespacesB = Arrays.asList("org.example");
        final Target targetB = newTarget("twin/b", newAuthContext(AUTHORIZED),
                newFilteredTopic(TWIN_EVENTS, namespacesB));

        // targetC does filter for namespaces "foo", but uses the "UNAUTHORIZED" subjects
        final List<String> namespacesC = Arrays.asList("foo");
        final Target targetC = newTarget("twin/c", newAuthContext(UNAUTHORIZED),
                newFilteredTopic(TWIN_EVENTS, namespacesC));

        final Connection connection = ConnectivityModelFactory
                .newConnectionBuilder(CONNECTION, ConnectionType.AMQP_10, ConnectionStatus.OPEN, URI)
                .targets(new HashSet<>(Arrays.asList(targetA, targetB, targetC)))
                .build();

        final Thing thing = Thing.newBuilder()
                .setId("foo:bar13") // WHEN: the namespace of the modifed thing is "foo"
                .setAttribute(JsonPointer.of("test"), JsonValue.of(42))
                .build();
        final DittoHeaders headers = DittoHeaders.newBuilder().readSubjects(Arrays.asList(AUTHORIZED.getId()))
                .build();
        final ThingModified thingModified = ThingModified.of(thing, 3L, headers);

        final SignalFilter signalFilter = new SignalFilter(connection);
        final Set<Target> filteredTargets = signalFilter.filter(thingModified);
        Assertions
                .assertThat(filteredTargets)
                .isEqualTo(Collections.singleton(targetA)); // THEN: only targetA should be in the filtered targets
    }

    @Test
    public void applySignalFilterWithRqlFilter() {

        final List<String> allNamespaces = Collections.emptyList();

        // targetA does filter for all namespaces and filters that attribute "test" > 23
        final String filterA = "gt(attributes/test,23)";
        final Target targetA = newTarget("twin/a", newAuthContext(AUTHORIZED),
                newFilteredTopic(LIVE_EVENTS, allNamespaces, filterA));

        // targetB does filter for all namespaces and filters that attribute "test" > 50
        final String filterB = "gt(attributes/test,50)";
        final Target targetB = newTarget("twin/b", newAuthContext(AUTHORIZED),
                newFilteredTopic(LIVE_EVENTS, allNamespaces, filterB));

        // targetC does filter for all namespaces and filters that attribute "test" > 23, but uses the "UNAUTHORIZED" subjects
        final String filterC = "gt(attributes/test,50)";
        final Target targetC = newTarget("twin/c", newAuthContext(UNAUTHORIZED),
                newFilteredTopic(LIVE_EVENTS, allNamespaces, filterC));

        final Connection connection = ConnectivityModelFactory
                .newConnectionBuilder(CONNECTION, ConnectionType.AMQP_10, ConnectionStatus.OPEN, URI)
                .targets(new HashSet<>(Arrays.asList(targetA, targetB, targetC)))
                .build();

        final Thing thing = Thing.newBuilder()
                .setId("foo:bar13")
                .setAttribute(JsonPointer.of("test"), JsonValue.of(42)) // WHEN: the "test" value is 42
                .build();
        final DittoHeaders headers = DittoHeaders.newBuilder().readSubjects(Arrays.asList(AUTHORIZED.getId()))
                .channel(TopicPath.Channel.LIVE.getName())
                .build();
        final ThingModified thingModified = ThingModified.of(thing, 3L, headers);

        final SignalFilter signalFilter = new SignalFilter(connection);
        final Set<Target> filteredTargets = signalFilter.filter(thingModified);
        Assertions
                .assertThat(filteredTargets)
                .isEqualTo(Collections.singleton(targetA)); // THEN: only targetA should be in the filtered targets
    }

    @Test
    public void applySignalFilterWithNamespacesAndRqlFilter() {

        // targetA does filter for namespaces "org.eclipse.ditto" and "foo" and filters that attribute "test" > 23
        final List<String> namespacesA = Arrays.asList("org.eclipse.ditto", "foo");
        final String filterA = "gt(attributes/test,23)";
        final Target targetA = newTarget("twin/a", newAuthContext(AUTHORIZED),
                newFilteredTopic(TWIN_EVENTS, namespacesA, filterA));

        // targetB does filter for namespaces "org.example" and filters that attribute "test" < 50
        final List<String> namespacesB = Arrays.asList("org.example");
        final String filterB = "lt(attributes/test,50)";
        final Target targetB = newTarget("twin/b", newAuthContext(AUTHORIZED),
                newFilteredTopic(TWIN_EVENTS, namespacesB, filterB));

        // targetC does filter for namespaces "foo" and filters that attribute "test" ==  42, but uses the "UNAUTHORIZED" subjects
        final List<String> namespacesC = Arrays.asList("foo");
        final String filterC = "eq(attributes/test,42)";
        final Target targetC = newTarget("twin/c", newAuthContext(UNAUTHORIZED),
                newFilteredTopic(TWIN_EVENTS, namespacesC, filterC));

        // targetD does filter for namespaces "foo" and filters that attribute "test" ==  42
        final List<String> namespacesD = Arrays.asList("foo");
        final String filterD = "eq(attributes/test,42)";
        final Target targetD = newTarget("twin/d", newAuthContext(AUTHORIZED),
                newFilteredTopic(TWIN_EVENTS, namespacesD, filterD));

        final Connection connection = ConnectivityModelFactory
                .newConnectionBuilder(CONNECTION, ConnectionType.AMQP_10, ConnectionStatus.OPEN, URI)
                .targets(new HashSet<>(Arrays.asList(targetA, targetB, targetC, targetD)))
                .build();

        final Thing thing = Thing.newBuilder()
                .setId("foo:bar13") // WHEN: the namespace of the modifed thing is "foo"
                .setAttribute(JsonPointer.of("test"), JsonValue.of(42)) // WHEN: the "test" value is 42
                .build();
        final DittoHeaders headers = DittoHeaders.newBuilder().readSubjects(Arrays.asList(AUTHORIZED.getId()))
                .build();
        final ThingModified thingModified = ThingModified.of(thing, 3L, headers);

        final SignalFilter signalFilter = new SignalFilter(connection);
        final Set<Target> filteredTargets = signalFilter.filter(thingModified);
        Assertions
                .assertThat(filteredTargets)
                .contains(targetA, targetD); // THEN: only targetA and targetD should be in the filtered targets
    }
}
