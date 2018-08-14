/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */

package org.eclipse.ditto.services.connectivity.messaging;

import static java.util.Collections.emptySet;
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
import java.util.Set;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageDirection;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
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
    private static final String CONNECTION = "id";
    private static final AuthorizationSubject AUTHORIZED = newAuthSubject("authorized");
    private static final AuthorizationSubject UNAUTHORIZED = newAuthSubject("unauthorized");
    private static final AuthorizationSubject DUMMY = newAuthSubject("dummy");

    @Parameterized.Parameters(name = "topic={0}, readSubjects={1}, configuredTargets={2}, expectedTargets={3}")
    public static Collection<Object[]> data() {

        final Set<String> readSubjects = asSet("authorized", "ditto");

        final Target twin_authd =
                newTarget("twin/authorized", newAuthContext(AUTHORIZED, DUMMY), TWIN_EVENTS, LIVE_MESSAGES);
        final Target twin_unauthd =
                newTarget("twin/unauthorized", newAuthContext(DUMMY, UNAUTHORIZED), TWIN_EVENTS, LIVE_MESSAGES);
        final Target live_authd =
                newTarget("live/authorized", newAuthContext(DUMMY, AUTHORIZED), LIVE_EVENTS, LIVE_MESSAGES);
        final Target live_unauthd =
                newTarget("live/unauthorized", newAuthContext(UNAUTHORIZED, DUMMY), LIVE_EVENTS, LIVE_MESSAGES);
        final Target emptyContext =
                newTarget("live/unauthorized", newAuthContext(UNAUTHORIZED), LIVE_EVENTS, LIVE_MESSAGES, TWIN_EVENTS,
                        LIVE_COMMANDS);

        final Collection<Object[]> params = new ArrayList<>();

        params.add(new Object[]{TWIN_EVENTS, readSubjects, asSet(twin_authd), asSet(twin_authd)});
        params.add(new Object[]{TWIN_EVENTS, readSubjects, asSet(twin_authd, twin_unauthd), asSet(twin_authd)});
        params.add(new Object[]{TWIN_EVENTS, readSubjects, asSet(twin_authd, twin_unauthd, live_authd),
                asSet(twin_authd)});
        params.add(new Object[]{TWIN_EVENTS, readSubjects, asSet(twin_authd, twin_unauthd, live_authd, live_unauthd),
                asSet(twin_authd)});

        params.add(new Object[]{LIVE_EVENTS, readSubjects, asSet(twin_authd), emptySet()});
        params.add(new Object[]{LIVE_EVENTS, readSubjects, asSet(twin_authd, twin_unauthd), emptySet()});
        params.add(new Object[]{LIVE_EVENTS, readSubjects, asSet(twin_authd, twin_unauthd, live_authd),
                asSet(live_authd)});
        params.add(new Object[]{LIVE_EVENTS, readSubjects, asSet(twin_authd, twin_unauthd, live_authd, live_unauthd),
                asSet(live_authd)});

        params.add(new Object[]{LIVE_MESSAGES, readSubjects,
                asSet(twin_authd, twin_unauthd, live_authd, live_unauthd),
                asSet(twin_authd, live_authd)});

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
    public Set<Target> targets;
    @Parameterized.Parameter(3)
    public Set<Target> expectedTargets;

    @Test
    public void test() {
        final Connection connection =
                ConnectivityModelFactory.newConnectionBuilder(CONNECTION,
                        ConnectionType.AMQP_10,
                        ConnectionStatus.OPEN,
                        URI).targets(targets).build();

        final Set<Target> filteredTargets = SignalFilter.filter(connection, signal(signalTopic, readSubjects));
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
                    params.add(new Object[]{topic, subject, target, emptySet()});
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
    private static <T> Set<T> asSet(final T... elements) {
        return new HashSet<>(Arrays.asList(elements));
    }


    private static Signal<?> signal(final Topic topic, final Set<String> readSubjects) {

        final Thing thing = ThingsModelFactory.newThingBuilder().setGeneratedId().build();
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
                return ModifyThing.of(thing.getId().orElse("id"), thing, null, liveHeaders);
            case LIVE_MESSAGES:
                return SendThingMessage.of("ns:id",
                        Message.newBuilder(MessageHeaders.newBuilder(MessageDirection.TO, "ns:id", "ditto").build())
                                .build(), liveHeaders);
            default:
                throw new UnsupportedOperationException(topic + " not supported");
        }
    }
}
