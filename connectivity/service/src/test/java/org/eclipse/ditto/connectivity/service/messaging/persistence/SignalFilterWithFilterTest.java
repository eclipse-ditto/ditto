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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.base.model.auth.AuthorizationModelFactory.newAuthContext;
import static org.eclipse.ditto.base.model.auth.AuthorizationModelFactory.newAuthSubject;
import static org.eclipse.ditto.connectivity.model.Topic.LIVE_EVENTS;
import static org.eclipse.ditto.connectivity.model.Topic.LIVE_MESSAGES;
import static org.eclipse.ditto.connectivity.model.Topic.TWIN_EVENTS;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.HeaderMapping;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitorRegistry;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.MessageDirection;
import org.eclipse.ditto.messages.model.MessageHeaders;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessage;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingFieldSelector;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.ThingModified;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Tests {@link SignalFilter} for filtering with namespace + RQL filter.
 */
public final class SignalFilterWithFilterTest {

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
        final Target targetA = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/a")
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS)
                        .withNamespaces(List.of("org.eclipse.ditto", "foo"))
                        .build())
                .build();

        // targetB does filter for namespaces "org.example"
        final Target targetB = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/b")
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS)
                        .withNamespaces(List.of("org.example"))
                        .build())
                .build();

        // targetC does filter for namespaces "foo", but uses the "UNAUTHORIZED" subjects
        final Target targetC = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/c")
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, UNAUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS)
                        .withNamespaces(List.of("foo"))
                        .build())
                .build();

        final Connection connection = ConnectivityModelFactory
                .newConnectionBuilder(CONNECTION_ID, ConnectionType.AMQP_10, ConnectivityStatus.OPEN, URI)
                .targets(List.of(targetA, targetB, targetC))
                .build();

        final Thing thing = Thing.newBuilder()
                .setId(THING_ID) // WHEN: the namespace of the modified thing is "foo"
                .setAttribute(JsonPointer.of("test"), JsonValue.of(42))
                .build();
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .readGrantedSubjects(List.of(AUTHORIZED))
                .build();
        final ThingModified thingModified = ThingModified.of(thing, 3L, Instant.now(),
                headers, null);

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
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
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
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
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
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, UNAUTHORIZED))
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
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .channel(TopicPath.Channel.LIVE.getName())
                .build();
        final ThingModified thingModified = ThingModified.of(thing, 3L, Instant.now(),
                headers, null);

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
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
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
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
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
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, UNAUTHORIZED))
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
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
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
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .build();
        final ThingModified thingModified = ThingModified.of(thing, 3L, Instant.now(),
                headers, null);

        final SignalFilter signalFilter = new SignalFilter(connection, connectionMonitorRegistry);
        final List<Target> filteredTargets = signalFilter.filter(thingModified);

        assertThat(filteredTargets).containsOnly(targetA,
                targetD); // THEN: only targetA and targetD should be in the filtered targets
    }

    @Test
    public void applySignalFilterForMessagesWithExtraFieldsAndRqlFilter() {

        // targetA does filter for resource path "/inbox/messages/fubar"
        final String filterA = "eq(resource:path,'/inbox/messages/fubar')";
        final Target targetA = ConnectivityModelFactory.newTargetBuilder()
                .address("message/a")
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(LIVE_MESSAGES)
                        .withExtraFields(ThingFieldSelector.fromString("attributes"))
                        .withFilter(filterA)
                        .build())
                .build();

        // targetB does filter for resource path "/inbox/messages/booo"
        final String filterB = "eq(resource:path,'/inbox/messages/booo')";
        final Target targetB = ConnectivityModelFactory.newTargetBuilder()
                .address("message/a")
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(LIVE_MESSAGES)
                        .withExtraFields(ThingFieldSelector.fromString("attributes"))
                        .withFilter(filterB)
                        .build())
                .build();

        final Connection connection =
                ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID, ConnectionType.AMQP_10,
                        ConnectivityStatus.OPEN, URI)
                        .targets(Arrays.asList(targetA, targetB))
                        .build();

        final DittoHeaders headers = DittoHeaders.newBuilder()
                .channel(TopicPath.Channel.LIVE.getName())
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .build();
        final SendThingMessage<Object> sendThingMessage = SendThingMessage.of(THING_ID,
                Message.newBuilder(MessageHeaders.newBuilder(MessageDirection.TO, THING_ID, "fubar").build())
                        .build(), headers);

        final SignalFilter signalFilter = new SignalFilter(connection, connectionMonitorRegistry);
        final List<Target> filteredTargets = signalFilter.filter(sendThingMessage);

        assertThat(filteredTargets).containsOnly(targetA); // THEN: only targetA should be in the filtered targets
    }

    /**
     * Test that target filtering works also for desired properties events. Issue #1599
     */
    @Test
    public void applySignalFilterOnFeatureDesiredPropertiesModified() {
        final Target target = ConnectivityModelFactory.newTargetBuilder().address("address")
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS)
                        .withFilter("like(resource:path,'/features/" + TestConstants.Feature.FEATURE_ID + "*')")
                        .build()).build();
        final Connection connection = TestConstants.createConnection(CONNECTION_ID, target);
        final SignalFilter signalFilter = new SignalFilter(connection, connectionMonitorRegistry);
        final Signal<?> signal = TestConstants.featureDesiredPropertiesModified(Collections.singletonList(AUTHORIZED));

        final List<Target> filteredTargets = signalFilter.filter(signal);
        Assertions.assertThat(filteredTargets).hasSize(1).contains(target);
    }

    /**
     * Test that target filtering works using feature:id placeholder
     */
    @Test
    public void applySignalFilterWithFeatureIdPlaceholder() {
        Target target = ConnectivityModelFactory.newTargetBuilder().address("address")
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS)
                        .withFilter("eq(feature:id,'Feature')")
                        .build()
                )
                .build();
        final Connection connection = TestConstants.createConnection(CONNECTION_ID, target);
        final SignalFilter signalFilter = new SignalFilter(connection, connectionMonitorRegistry);
        final Signal<?> signal = TestConstants.featurePropertiesModified(Collections.singletonList(AUTHORIZED));

        final List<Target> filteredTargets = signalFilter.filter(signal);
        Assertions.assertThat(filteredTargets).hasSize(1).contains(target);
    }

    // ===== pure pipeline (fn:) target topic filter =====

    @Test
    public void applySignalFilterWithPurePipelineFilterMatchesAndNonMatchesOnDittoOriginator() {
        final String filter = "fn:filter(header:ditto-originator,'eq','some:subject')";
        final Target target = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/a")
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS)
                        .withFnFilter(filter)
                        .build())
                .build();

        final Connection connection = ConnectivityModelFactory
                .newConnectionBuilder(CONNECTION_ID, ConnectionType.AMQP_10, ConnectivityStatus.OPEN, URI)
                .targets(List.of(target))
                .build();

        final Thing thing = Thing.newBuilder()
                .setId(THING_ID)
                .setAttribute(JsonPointer.of("test"), JsonValue.of(42))
                .build();

        final DittoHeaders matchingHeaders = DittoHeaders.newBuilder()
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .putHeader("ditto-originator", "some:subject")
                .build();
        final ThingModified matching = ThingModified.of(thing, 3L, Instant.now(), matchingHeaders, null);

        final DittoHeaders nonMatchingHeaders = DittoHeaders.newBuilder()
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .putHeader("ditto-originator", "other:subject")
                .build();
        final ThingModified nonMatching = ThingModified.of(thing, 3L, Instant.now(), nonMatchingHeaders, null);

        final SignalFilter signalFilter = new SignalFilter(connection, connectionMonitorRegistry);

        assertThat(signalFilter.filter(matching)).containsOnly(target); // THEN: matching originator is returned
        assertThat(signalFilter.filter(nonMatching)).isEmpty(); // THEN: non-matching originator is not returned
    }

    @Test
    public void applySignalFilterWithPurePipelineFilterAbsentHeaderNegationPublishes() {
        // absent header + "ne" => publish (verified fact 5)
        final String filter = "fn:filter(header:ditto-originator,'ne','some:subject')";
        final Target target = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/a")
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS)
                        .withFnFilter(filter)
                        .build())
                .build();

        final Connection connection = ConnectivityModelFactory
                .newConnectionBuilder(CONNECTION_ID, ConnectionType.AMQP_10, ConnectivityStatus.OPEN, URI)
                .targets(List.of(target))
                .build();

        final Thing thing = Thing.newBuilder()
                .setId(THING_ID)
                .setAttribute(JsonPointer.of("test"), JsonValue.of(42))
                .build();
        final DittoHeaders headers = DittoHeaders.newBuilder() // WHEN: no "ditto-originator" header is set
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .build();
        final ThingModified thingModified = ThingModified.of(thing, 3L, Instant.now(), headers, null);

        final SignalFilter signalFilter = new SignalFilter(connection, connectionMonitorRegistry);

        assertThat(signalFilter.filter(thingModified)).containsOnly(target);
    }

    // ===== RQL and pipeline filter params on one topic (AND semantics) =====

    @Test
    public void applySignalFilterWithRqlAndPipelineFilterParams() {
        final Target target = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/a")
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS)
                        .withFilter("eq(attributes/test,42)")
                        .withFnFilter("fn:filter(header:ditto-originator,'eq','some:subject')")
                        .build())
                .build();

        final Connection connection = ConnectivityModelFactory
                .newConnectionBuilder(CONNECTION_ID, ConnectionType.AMQP_10, ConnectivityStatus.OPEN, URI)
                .targets(List.of(target))
                .build();

        final Thing matchingThing = Thing.newBuilder()
                .setId(THING_ID)
                .setAttribute(JsonPointer.of("test"), JsonValue.of(42)) // RQL matches
                .build();
        final Thing nonMatchingThing = Thing.newBuilder()
                .setId(THING_ID)
                .setAttribute(JsonPointer.of("test"), JsonValue.of(99)) // RQL does not match
                .build();

        final DittoHeaders matchingOriginatorHeaders = DittoHeaders.newBuilder()
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .putHeader("ditto-originator", "some:subject") // pipeline matches
                .build();
        final DittoHeaders nonMatchingOriginatorHeaders = DittoHeaders.newBuilder()
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .putHeader("ditto-originator", "other:subject") // pipeline does not match
                .build();

        final SignalFilter signalFilter = new SignalFilter(connection, connectionMonitorRegistry);

        // RQL matches + pipeline matches => returned
        final ThingModified rqlMatchPipelineMatch =
                ThingModified.of(matchingThing, 3L, Instant.now(), matchingOriginatorHeaders, null);
        assertThat(signalFilter.filter(rqlMatchPipelineMatch)).containsOnly(target);

        // RQL matches + pipeline does not match => not returned
        final ThingModified rqlMatchPipelineNonMatch =
                ThingModified.of(matchingThing, 3L, Instant.now(), nonMatchingOriginatorHeaders, null);
        assertThat(signalFilter.filter(rqlMatchPipelineNonMatch)).isEmpty();

        // RQL does not match + pipeline matches => not returned
        final ThingModified rqlNonMatchPipelineMatch =
                ThingModified.of(nonMatchingThing, 3L, Instant.now(), matchingOriginatorHeaders, null);
        assertThat(signalFilter.filter(rqlNonMatchPipelineMatch)).isEmpty();
    }

    // ===== T-I2: pure pipeline filter for LIVE_MESSAGES (primary echo-suppression use case) =====

    @Test
    public void applySignalFilterForLiveMessagesWithPurePipelineFilter() {
        final String filter = "fn:filter(header:ditto-originator,'eq','some:subject')";
        final Target target = ConnectivityModelFactory.newTargetBuilder()
                .address("message/a")
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(LIVE_MESSAGES)
                        .withFnFilter(filter)
                        .build())
                .build();

        final Connection connection =
                ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID, ConnectionType.AMQP_10,
                        ConnectivityStatus.OPEN, URI)
                        .targets(List.of(target))
                        .build();

        final DittoHeaders matchingHeaders = DittoHeaders.newBuilder()
                .channel(TopicPath.Channel.LIVE.getName())
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .putHeader("ditto-originator", "some:subject")
                .build();
        final SendThingMessage<Object> matchingMessage = SendThingMessage.of(THING_ID,
                Message.newBuilder(MessageHeaders.newBuilder(MessageDirection.TO, THING_ID, "fubar").build())
                        .build(), matchingHeaders);

        final DittoHeaders nonMatchingHeaders = DittoHeaders.newBuilder()
                .channel(TopicPath.Channel.LIVE.getName())
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .putHeader("ditto-originator", "other:subject")
                .build();
        final SendThingMessage<Object> nonMatchingMessage = SendThingMessage.of(THING_ID,
                Message.newBuilder(MessageHeaders.newBuilder(MessageDirection.TO, THING_ID, "fubar").build())
                        .build(), nonMatchingHeaders);

        final SignalFilter signalFilter = new SignalFilter(connection, connectionMonitorRegistry);

        assertThat(signalFilter.filter(matchingMessage)).containsOnly(target);
        assertThat(signalFilter.filter(nonMatchingMessage)).isEmpty();
    }

    // ===== T-I3: OR across two FilteredTopics (same base Topic, different filter) on ONE target =====

    @Test
    public void applySignalFilterOrAcrossTwoFilteredTopicsOnOneTarget() {
        // topic A: non-matching RQL filter
        final String rqlFilter = "eq(attributes/test,999)";
        // topic B: matching pipeline filter
        final String pipelineFilter = "fn:filter(header:ditto-originator,'eq','some:subject')";

        final Target target = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/a")
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS).withFilter(rqlFilter).build(),
                        ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS).withFnFilter(pipelineFilter)
                                .build())
                .build();

        final Connection connection = ConnectivityModelFactory
                .newConnectionBuilder(CONNECTION_ID, ConnectionType.AMQP_10, ConnectivityStatus.OPEN, URI)
                .targets(List.of(target))
                .build();

        final Thing thing = Thing.newBuilder()
                .setId(THING_ID)
                .setAttribute(JsonPointer.of("test"), JsonValue.of(42)) // never matches topic A's RQL filter
                .build();

        final SignalFilter signalFilter = new SignalFilter(connection, connectionMonitorRegistry);

        // topic A (RQL) does not match, topic B (pipeline) matches => returned
        final DittoHeaders matchingHeaders = DittoHeaders.newBuilder()
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .putHeader("ditto-originator", "some:subject")
                .build();
        final ThingModified matching = ThingModified.of(thing, 3L, Instant.now(), matchingHeaders, null);
        assertThat(signalFilter.filter(matching)).containsOnly(target);

        // neither topic A (RQL) nor topic B (pipeline) matches => not returned
        final DittoHeaders nonMatchingHeaders = DittoHeaders.newBuilder()
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .putHeader("ditto-originator", "other:subject")
                .build();
        final ThingModified nonMatching = ThingModified.of(thing, 3L, Instant.now(), nonMatchingHeaders, null);
        assertThat(signalFilter.filter(nonMatching)).isEmpty();
    }

    // ===== T-I4: mix of a pure-pipeline target and a pure-RQL target on one connection =====

    @Test
    public void applySignalFilterWithMixOfPipelineTargetAndRqlTarget() {
        final String pipelineFilter = "fn:filter(header:ditto-originator,'eq','some:subject')";
        final Target targetA = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/a")
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS)
                        .withFnFilter(pipelineFilter)
                        .build())
                .build();

        final String rqlFilter = "eq(attributes/test,42)";
        final Target targetB = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/b")
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS)
                        .withFilter(rqlFilter)
                        .build())
                .build();

        final Connection connection = ConnectivityModelFactory
                .newConnectionBuilder(CONNECTION_ID, ConnectionType.AMQP_10, ConnectivityStatus.OPEN, URI)
                .targets(List.of(targetA, targetB))
                .build();

        final Thing matchingThing = Thing.newBuilder()
                .setId(THING_ID)
                .setAttribute(JsonPointer.of("test"), JsonValue.of(42)) // matches targetB's RQL filter
                .build();
        final Thing nonMatchingThing = Thing.newBuilder()
                .setId(THING_ID)
                .setAttribute(JsonPointer.of("test"), JsonValue.of(99)) // does not match targetB's RQL filter
                .build();

        final DittoHeaders matchingOriginatorHeaders = DittoHeaders.newBuilder()
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .putHeader("ditto-originator", "some:subject") // matches targetA's pipeline filter
                .build();
        final DittoHeaders nonMatchingOriginatorHeaders = DittoHeaders.newBuilder()
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .putHeader("ditto-originator", "other:subject") // does not match targetA's pipeline filter
                .build();

        final SignalFilter signalFilter = new SignalFilter(connection, connectionMonitorRegistry);

        // both match
        assertThat(signalFilter.filter(
                ThingModified.of(matchingThing, 3L, Instant.now(), matchingOriginatorHeaders, null)))
                .containsOnly(targetA, targetB);

        // only targetB (RQL) matches
        assertThat(signalFilter.filter(
                ThingModified.of(matchingThing, 3L, Instant.now(), nonMatchingOriginatorHeaders, null)))
                .containsOnly(targetB);

        // only targetA (pipeline) matches
        assertThat(signalFilter.filter(
                ThingModified.of(nonMatchingThing, 3L, Instant.now(), matchingOriginatorHeaders, null)))
                .containsOnly(targetA);

        // neither matches
        assertThat(signalFilter.filter(
                ThingModified.of(nonMatchingThing, 3L, Instant.now(), nonMatchingOriginatorHeaders, null)))
                .isEmpty();
    }

    // ===== T-M5: regression - pure RQL filter with a literal "|" in a quoted attribute value =====

    @Test
    public void applySignalFilterWithRqlFilterContainingLiteralPipeInAttributeValue() {
        final String filter = "like(attributes/test,'*|*')";
        final Target target = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/a")
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS)
                        .withFilter(filter)
                        .build())
                .build();

        final Connection connection = ConnectivityModelFactory
                .newConnectionBuilder(CONNECTION_ID, ConnectionType.AMQP_10, ConnectivityStatus.OPEN, URI)
                .targets(List.of(target))
                .build();

        final Thing thing = Thing.newBuilder()
                .setId(THING_ID)
                .setAttribute(JsonPointer.of("test"), JsonValue.of("a|b")) // WHEN: literal "|" in the value
                .build();
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .build();
        final ThingModified thingModified = ThingModified.of(thing, 3L, Instant.now(), headers, null);

        final SignalFilter signalFilter = new SignalFilter(connection, connectionMonitorRegistry);

        assertThat(signalFilter.filter(thingModified)).containsOnly(target);
    }

    // ===== runtime failure policy: pipeline evaluation failure drops the target AND records a connection-log entry =====

    @Test
    public void applySignalFilterWithFailingPipelineFilterDropsTargetAndRecordsConnectionLogFailure() {
        // parses fine as a pure pipeline filter but throws a DittoRuntimeException at evaluation time
        // (unknown pipeline function)
        final String filter = "fn:unknownfn('x')";
        final Target target = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/a")
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS)
                        .withFnFilter(filter)
                        .build())
                .build();

        final Connection connection = ConnectivityModelFactory
                .newConnectionBuilder(CONNECTION_ID, ConnectionType.AMQP_10, ConnectivityStatus.OPEN, URI)
                .targets(List.of(target))
                .build();

        final Thing thing = Thing.newBuilder()
                .setId(THING_ID)
                .setAttribute(JsonPointer.of("test"), JsonValue.of(42))
                .build();
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .build();
        final ThingModified thingModified = ThingModified.of(thing, 3L, Instant.now(), headers, null);

        // a local (non-stub-only) registry so that the failure interaction can be verified
        final ConnectionMonitor filteredMonitor = Mockito.mock(ConnectionMonitor.class);
        @SuppressWarnings("unchecked")
        final ConnectionMonitorRegistry<ConnectionMonitor> registry = Mockito.mock(ConnectionMonitorRegistry.class);
        Mockito.when(registry.forOutboundDispatched(Mockito.any(Connection.class), Mockito.anyString()))
                .thenReturn(Mockito.mock(ConnectionMonitor.class));
        Mockito.when(registry.forOutboundFiltered(Mockito.any(Connection.class), Mockito.anyString()))
                .thenReturn(filteredMonitor);

        final SignalFilter signalFilter = new SignalFilter(connection, registry);

        // THEN: the evaluation failure is treated as a non-match ...
        assertThat(signalFilter.filter(thingModified)).isEmpty();
        // ... AND recorded as a FAILURE entry in the target's user-visible connection log
        Mockito.verify(filteredMonitor).failure(Mockito.eq(thingModified), Mockito.anyString(),
                Mockito.eq(filter), Mockito.anyString());
    }

    // ===== chained pipeline stages in one filter param (AND semantics) =====

    @Test
    public void applySignalFilterWithChainedPipelineStagesAndSemantics() {
        final Target target = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/a")
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS)
                        .withFnFilter("fn:filter(header:ditto-originator,'ne','excluded:subject')" +
                                "|fn:filter(header:ditto-origin,'ne','excluded-connection')")
                        .build())
                .build();

        final Connection connection = ConnectivityModelFactory
                .newConnectionBuilder(CONNECTION_ID, ConnectionType.AMQP_10, ConnectivityStatus.OPEN, URI)
                .targets(List.of(target))
                .build();

        final Thing thing = Thing.newBuilder()
                .setId(THING_ID)
                .setAttribute(JsonPointer.of("test"), JsonValue.of(42))
                .build();

        final SignalFilter signalFilter = new SignalFilter(connection, connectionMonitorRegistry);

        // both chained stages match => returned
        final DittoHeaders bothMatch = DittoHeaders.newBuilder()
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .putHeader("ditto-originator", "other:subject")
                .putHeader("ditto-origin", "other-connection")
                .build();
        assertThat(signalFilter.filter(ThingModified.of(thing, 3L, Instant.now(), bothMatch, null)))
                .containsOnly(target);

        // first chained stage does not match => not returned
        final DittoHeaders firstNonMatch = DittoHeaders.newBuilder()
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .putHeader("ditto-originator", "excluded:subject")
                .putHeader("ditto-origin", "other-connection")
                .build();
        assertThat(signalFilter.filter(ThingModified.of(thing, 3L, Instant.now(), firstNonMatch, null)))
                .isEmpty();

        // second chained stage does not match => not returned
        final DittoHeaders secondNonMatch = DittoHeaders.newBuilder()
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .putHeader("ditto-originator", "other:subject")
                .putHeader("ditto-origin", "excluded-connection")
                .build();
        assertThat(signalFilter.filter(ThingModified.of(thing, 3L, Instant.now(), secondNonMatch, null)))
                .isEmpty();
    }

    @Test
    public void applySignalFilterWithRqlAndChainedPipelineFilterParam() {
        final Target target = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/a")
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS)
                        .withFilter("eq(attributes/test,42)")
                        .withFnFilter("fn:filter(header:ditto-originator,'ne','excluded:subject')" +
                                "|fn:filter(header:ditto-origin,'ne','excluded-connection')")
                        .build())
                .build();

        final Connection connection = ConnectivityModelFactory
                .newConnectionBuilder(CONNECTION_ID, ConnectionType.AMQP_10, ConnectivityStatus.OPEN, URI)
                .targets(List.of(target))
                .build();

        final Thing matchingThing = Thing.newBuilder()
                .setId(THING_ID)
                .setAttribute(JsonPointer.of("test"), JsonValue.of(42)) // RQL matches
                .build();
        final Thing nonMatchingThing = Thing.newBuilder()
                .setId(THING_ID)
                .setAttribute(JsonPointer.of("test"), JsonValue.of(99)) // RQL does not match
                .build();
        final DittoHeaders allPipelinesMatch = DittoHeaders.newBuilder()
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .putHeader("ditto-originator", "other:subject")
                .putHeader("ditto-origin", "other-connection")
                .build();
        final DittoHeaders firstPipelineNonMatch = DittoHeaders.newBuilder()
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .putHeader("ditto-originator", "excluded:subject")
                .putHeader("ditto-origin", "other-connection")
                .build();
        final DittoHeaders secondPipelineNonMatch = DittoHeaders.newBuilder()
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .putHeader("ditto-originator", "other:subject")
                .putHeader("ditto-origin", "excluded-connection")
                .build();

        final SignalFilter signalFilter = new SignalFilter(connection, connectionMonitorRegistry);

        // RQL + both chained stages match => returned
        assertThat(signalFilter.filter(
                ThingModified.of(matchingThing, 3L, Instant.now(), allPipelinesMatch, null)))
                .containsOnly(target);

        // RQL does not match => not returned
        assertThat(signalFilter.filter(
                ThingModified.of(nonMatchingThing, 3L, Instant.now(), allPipelinesMatch, null)))
                .isEmpty();

        // first chained stage does not match => not returned
        assertThat(signalFilter.filter(
                ThingModified.of(matchingThing, 3L, Instant.now(), firstPipelineNonMatch, null)))
                .isEmpty();

        // second chained stage does not match => not returned
        assertThat(signalFilter.filter(
                ThingModified.of(matchingThing, 3L, Instant.now(), secondPipelineNonMatch, null)))
                .isEmpty();
    }

    @Test
    public void applySignalFilterWithFailingChainedStageRecordsFailureForWholeFnFilter() {
        // a topic carries exactly one fn-filter, so a failure is always reported with the WHOLE expression: the
        // first stage matches on ditto-originator=other:subject, the second one (fn:unknownfn) throws a
        // PlaceholderFunctionUnknownException regardless of the carrier value.
        final String matchingFilter = "fn:filter(header:ditto-originator,'ne','excluded:subject')";
        final String failingFilter = "fn:unknownfn('x')";
        final String fnFilter = matchingFilter + "|" + failingFilter;
        final Target target = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/a")
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS)
                        .withFnFilter(fnFilter)
                        .build())
                .build();

        final Connection connection = ConnectivityModelFactory
                .newConnectionBuilder(CONNECTION_ID, ConnectionType.AMQP_10, ConnectivityStatus.OPEN, URI)
                .targets(List.of(target))
                .build();

        final Thing thing = Thing.newBuilder()
                .setId(THING_ID)
                .setAttribute(JsonPointer.of("test"), JsonValue.of(42))
                .build();
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .putHeader("ditto-originator", "other:subject")
                .build();
        final ThingModified thingModified = ThingModified.of(thing, 3L, Instant.now(), headers, null);

        final ConnectionMonitor filteredMonitor = Mockito.mock(ConnectionMonitor.class);
        @SuppressWarnings("unchecked")
        final ConnectionMonitorRegistry<ConnectionMonitor> registry = Mockito.mock(ConnectionMonitorRegistry.class);
        Mockito.when(registry.forOutboundDispatched(Mockito.any(Connection.class), Mockito.anyString()))
                .thenReturn(Mockito.mock(ConnectionMonitor.class));
        Mockito.when(registry.forOutboundFiltered(Mockito.any(Connection.class), Mockito.anyString()))
                .thenReturn(filteredMonitor);

        final SignalFilter signalFilter = new SignalFilter(connection, registry);

        assertThat(signalFilter.filter(thingModified)).isEmpty();
        Mockito.verify(filteredMonitor).failure(Mockito.eq(thingModified), Mockito.anyString(),
                Mockito.eq(fnFilter), Mockito.anyString());
    }

    // ===== placeholder-first fn-filter and the widened RuntimeException guard =====

    @Test
    public void applySignalFilterWithPlaceholderFirstFnFilter() {
        final Target target = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/a")
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS)
                        .withFnFilter("header:ditto-originator|fn:filter('ne','excluded:subject')")
                        .build())
                .build();
        final Connection connection = ConnectivityModelFactory
                .newConnectionBuilder(CONNECTION_ID, ConnectionType.AMQP_10, ConnectivityStatus.OPEN, URI)
                .targets(List.of(target))
                .build();
        final Thing thing = Thing.newBuilder().setId(THING_ID).build();
        final SignalFilter signalFilter = new SignalFilter(connection, connectionMonitorRegistry);

        final DittoHeaders otherOriginator = DittoHeaders.newBuilder()
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .putHeader("ditto-originator", "other:subject")
                .build();
        assertThat(signalFilter.filter(ThingModified.of(thing, 3L, Instant.now(), otherOriginator, null)))
                .containsOnly(target);

        final DittoHeaders excludedOriginator = otherOriginator.toBuilder()
                .putHeader("ditto-originator", "excluded:subject")
                .build();
        assertThat(signalFilter.filter(ThingModified.of(thing, 3L, Instant.now(), excludedOriginator, null)))
                .isEmpty();

        // placeholder-first: an ABSENT header never resolves the leading placeholder -> suppressed even for 'ne'
        final DittoHeaders noOriginator = DittoHeaders.newBuilder()
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .build();
        assertThat(signalFilter.filter(ThingModified.of(thing, 3L, Instant.now(), noOriginator, null)))
                .isEmpty();
    }

    @Test
    public void applySignalFilterWithFnFilterThrowingNonDittoExceptionDropsTargetAndRecordsFailure() {
        // a leading placeholder without a name passes the pipeline GRAMMAR (the validation resolver never resolves
        // placeholder values); built via the model it bypasses ConnectionValidator's dedicated name check and makes
        // the headers placeholder throw an IllegalArgumentException per signal. The runtime guard must catch ANY
        // RuntimeException - not only DittoRuntimeExceptions - or the exception would escape SignalFilter#filter
        // and fail the OutboundDispatchingActor's message handling.
        final String fnFilter = "header:|fn:filter('eq','x')";
        final Target target = ConnectivityModelFactory.newTargetBuilder()
                .address("twin/a")
                .authorizationContext(newAuthContext(DittoAuthorizationContextType.UNSPECIFIED, AUTHORIZED))
                .headerMapping(HEADER_MAPPING)
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(TWIN_EVENTS)
                        .withFnFilter(fnFilter)
                        .build())
                .build();
        final Connection connection = ConnectivityModelFactory
                .newConnectionBuilder(CONNECTION_ID, ConnectionType.AMQP_10, ConnectivityStatus.OPEN, URI)
                .targets(List.of(target))
                .build();
        final Thing thing = Thing.newBuilder().setId(THING_ID).build();
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .readGrantedSubjects(Collections.singletonList(AUTHORIZED))
                .putHeader("ditto-originator", "some:subject")
                .build();
        final ThingModified thingModified = ThingModified.of(thing, 3L, Instant.now(), headers, null);

        final ConnectionMonitor filteredMonitor = Mockito.mock(ConnectionMonitor.class);
        @SuppressWarnings("unchecked")
        final ConnectionMonitorRegistry<ConnectionMonitor> registry = Mockito.mock(ConnectionMonitorRegistry.class);
        Mockito.when(registry.forOutboundDispatched(Mockito.any(Connection.class), Mockito.anyString()))
                .thenReturn(Mockito.mock(ConnectionMonitor.class));
        Mockito.when(registry.forOutboundFiltered(Mockito.any(Connection.class), Mockito.anyString()))
                .thenReturn(filteredMonitor);
        final SignalFilter signalFilter = new SignalFilter(connection, registry);

        assertThat(signalFilter.filter(thingModified)).isEmpty();
        Mockito.verify(filteredMonitor).failure(Mockito.eq(thingModified), Mockito.anyString(),
                Mockito.eq(fnFilter), Mockito.anyString());
    }
}
