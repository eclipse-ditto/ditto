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

import static java.util.Collections.emptyList;
import static org.eclipse.ditto.model.base.auth.AuthorizationModelFactory.newAuthContext;
import static org.eclipse.ditto.model.base.auth.AuthorizationModelFactory.newAuthSubject;
import static org.eclipse.ditto.model.connectivity.ConnectivityModelFactory.newTarget;
import static org.eclipse.ditto.model.connectivity.Topic.LIVE_COMMANDS;
import static org.eclipse.ditto.model.connectivity.Topic.LIVE_EVENTS;
import static org.eclipse.ditto.model.connectivity.Topic.LIVE_MESSAGES;
import static org.eclipse.ditto.model.connectivity.Topic.TWIN_EVENTS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.HeaderMapping;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageDirection;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitorRegistry;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.messages.SendThingMessage;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.events.things.ThingModified;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SignalFilterTest {

    private static final String URI = "amqp://user:pass@host:1111/path";
    private static final ConnectionId CONNECTION_ID = TestConstants.createRandomConnectionId();
    private static final AuthorizationSubject AUTHORIZED = newAuthSubject("authorized");
    private static final AuthorizationSubject UNAUTHORIZED = newAuthSubject("unauthorized");
    private static final AuthorizationSubject DUMMY = newAuthSubject("dummy");
    private static final HeaderMapping HEADER_MAPPING = null;

    @Parameterized.Parameters(name = "topic={0}, readSubjects={1}, configuredTargets={2}, expectedTargets={3}")
    public static Collection<Object[]> data() {

        final Set<String> readSubjects = asSet("authorized", "ditto");

        final Target twin_authd =
                newTarget("twin/authorized", newAuthContext(AUTHORIZED, DUMMY), HEADER_MAPPING, null, TWIN_EVENTS, LIVE_MESSAGES);
        final Target twin_unauthd =
                newTarget("twin/unauthorized", newAuthContext(DUMMY, UNAUTHORIZED), HEADER_MAPPING, null, TWIN_EVENTS, LIVE_MESSAGES);
        final Target live_authd =
                newTarget("live/authorized", newAuthContext(DUMMY, AUTHORIZED), HEADER_MAPPING, null, LIVE_EVENTS, LIVE_MESSAGES);
        final Target live_unauthd =
                newTarget("live/unauthorized", newAuthContext(UNAUTHORIZED, DUMMY), HEADER_MAPPING, null, LIVE_EVENTS, LIVE_MESSAGES);
        final Target emptyContext =
                newTarget("live/unauthorized", newAuthContext(UNAUTHORIZED), HEADER_MAPPING, null, LIVE_EVENTS, LIVE_MESSAGES, TWIN_EVENTS,
                        LIVE_COMMANDS);

        final Collection<Object[]> params = new ArrayList<>();

        params.add(new Object[]{TWIN_EVENTS, readSubjects, asList(twin_authd), asList(twin_authd)});
        params.add(new Object[]{TWIN_EVENTS, readSubjects, asList(twin_authd, twin_unauthd), asList(twin_authd)});
        params.add(new Object[]{TWIN_EVENTS, readSubjects, asList(twin_authd, twin_unauthd, live_authd),
                asList(twin_authd)});
        params.add(new Object[]{TWIN_EVENTS, readSubjects, asList(twin_authd, twin_unauthd, live_authd, live_unauthd),
                asList(twin_authd)});

        params.add(new Object[]{LIVE_EVENTS, readSubjects, asList(twin_authd), emptyList()});
        params.add(new Object[]{LIVE_EVENTS, readSubjects, asList(twin_authd, twin_unauthd), emptyList()});
        params.add(new Object[]{LIVE_EVENTS, readSubjects, asList(twin_authd, twin_unauthd, live_authd),
                asList(live_authd)});
        params.add(new Object[]{LIVE_EVENTS, readSubjects, asList(twin_authd, twin_unauthd, live_authd, live_unauthd),
                asList(live_authd)});

        params.add(new Object[]{LIVE_MESSAGES, readSubjects,
                asList(twin_authd, twin_unauthd, live_authd, live_unauthd),
                asList(twin_authd, live_authd)});

        // subject "ditto" is not authorized to read any signal
        addAllCombinationsExpectingEmptyResult(params,
                Topic.values(),
                asSet("ditto"),
                asSet(twin_authd, twin_unauthd, live_authd, live_unauthd));

        // LIVE_COMMANDS are not subscribed
        addAllCombinationsExpectingEmptyResult(params,
                new Topic[]{LIVE_COMMANDS},
                asSet("authorized"),
                asSet(twin_authd, twin_unauthd, live_authd, live_unauthd));

        // empty auth context
        addAllCombinationsExpectingEmptyResult(params,
                Topic.values(),
                asSet("authorized"),
                asSet(emptyContext));

        return params;

    }

    @Parameterized.Parameter
    public Topic signalTopic;
    @Parameterized.Parameter(1)
    public Set<String> readSubjects;
    @Parameterized.Parameter(2)
    public List<Target> targets;
    @Parameterized.Parameter(3)
    public List<Target> expectedTargets;

    private final ConnectionMonitorRegistry connectionMonitorRegistry = TestConstants.Monitoring.MONITOR_REGISTRY_MOCK;

    @Test
    public void test() {
        final Connection connection =
                ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID,
                        ConnectionType.AMQP_10,
                        ConnectivityStatus.OPEN,
                        URI).targets(targets).build();

        final SignalFilter signalFilter = new SignalFilter(connection, connectionMonitorRegistry);
        final List<Target> filteredTargets = signalFilter.filter(signal(signalTopic, readSubjects));
        Assertions
                .assertThat(filteredTargets)
                .isEqualTo(expectedTargets);
    }

    private static void addAllCombinationsExpectingEmptyResult(final Collection<Object[]> params, final Topic[] topics,
            final Set<String> readSubjects, final Set<Target> targets) {
        for (final Topic topic : topics) {
            final Set<Set> subjects = getCombinations(readSubjects, new HashSet<>());
            for (final Set subject : subjects) {
                final Set<Set> targetCombinations = getCombinations(targets, new HashSet<>());
                for (final Set target : targetCombinations) {
                    params.add(new Object[]{topic, subject, new ArrayList<>(target), emptyList()});
                }
            }
        }
    }

    private static Set<Set> getCombinations(final Set elements, final Set<Set> result) {

        result.add(elements);

        if (elements.size() == 1) {
            return result;
        }

        for (final Object element : elements) {
            final Set sub = new HashSet(elements);
            sub.remove(element);
            result.add(sub);
            getCombinations(sub, result);
        }
        return result;
    }

    @SafeVarargs
    private static <T> List<T> asList(final T... elements) {
        return Arrays.asList(elements);
    }

    @SafeVarargs
    private static <T> Set<T> asSet(final T... elements) {
        return new HashSet<>(asList(elements));
    }


    private static Signal<?> signal(final Topic topic, final Set<String> readSubjects) {

        final ThingId thingId = ThingId.of("org.eclipse.ditto:myThing");
        final Thing thing = ThingsModelFactory.newThingBuilder().setId(thingId).build();
        final ThingModified thingModified =
                ThingModified.of(thing, 1L, DittoHeaders.newBuilder().readSubjects(readSubjects).build());

        final DittoHeaders liveHeaders =
                DittoHeaders.newBuilder().readSubjects(readSubjects).channel(TopicPath.Channel.LIVE.getName()).build();
        switch (topic) {
            case TWIN_EVENTS:
                return thingModified;
            case LIVE_EVENTS:
                return thingModified.setDittoHeaders(liveHeaders);
            case LIVE_COMMANDS:
                return ModifyThing.of(thingId, thing, null, liveHeaders);
            case LIVE_MESSAGES:
                return SendThingMessage.of(thingId,
                        Message.newBuilder(MessageHeaders.newBuilder(MessageDirection.TO, thingId, "ditto").build())
                                .build(), liveHeaders);
            default:
                throw new UnsupportedOperationException(topic + " not supported");
        }
    }

}
