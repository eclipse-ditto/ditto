/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import java.time.Instant;
import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.AttributesModified;
import org.junit.Test;

/**
 * Tests that target topic filters of {@link SignalFilter} only see the fields the target may read.
 */
public final class SignalFilterPartialAccessTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto:myThing");
    private static final AuthorizationSubject ALICE = AuthorizationSubject.newInstance("integration:alice");
    private static final AuthorizationSubject GROUP_X = AuthorizationSubject.newInstance("integration:group-x");
    private static final AuthorizationSubject CAROL = AuthorizationSubject.newInstance("integration:carol");

    @Test
    public void filterOnFieldRevokedForAnotherSubjectOfTheTargetDoesNotMatch() {
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey(),
                        "{\"subjects\":[\"integration:group-x\"],\"paths\":{\"attributes/secret\":[0]}}")
                .readGrantedSubjects(List.of(ALICE))
                .build();
        final Target multiSubject = target("multi", "eq(attributes/secret,\"s\")", ALICE, GROUP_X);
        final Target aliceOnly = target("alice", "eq(attributes/secret,\"s\")", ALICE);
        final Target multiSubjectOnReadableField = target("multi-pub", "eq(attributes/pub,\"p\")", ALICE, GROUP_X);

        assertThat(filter(headers, multiSubject, aliceOnly, multiSubjectOnReadableField))
                .containsExactly(aliceOnly, multiSubjectOnReadableField);
    }

    @Test
    public void filterOnFieldNotReadableForPartialSubjectDoesNotMatch() {
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(),
                        "{\"subjects\":[\"integration:carol\"],\"paths\":{\"attributes/pub\":[0]}}")
                .readGrantedSubjects(List.of(CAROL))
                .build();
        final Target secretFilter = target("secret", "eq(attributes/secret,\"s\")", CAROL);
        final Target pubFilter = target("pub", "eq(attributes/pub,\"p\")", CAROL);
        final Target noFilter = target("none", null, CAROL);

        assertThat(filter(headers, secretFilter, pubFilter, noFilter)).containsExactly(pubFilter, noFilter);
    }

    @Test
    public void filtersAreUnchangedWithoutAccessRestrictions() {
        final DittoHeaders headers = DittoHeaders.newBuilder().readGrantedSubjects(List.of(ALICE, GROUP_X)).build();
        final Target secretFilter = target("secret", "eq(attributes/secret,\"s\")", ALICE, GROUP_X);

        assertThat(filter(headers, secretFilter)).containsExactly(secretFilter);
    }

    private static List<Target> filter(final DittoHeaders headers, final Target... targets) {
        final AttributesModified event = AttributesModified.of(THING_ID,
                Attributes.newBuilder().set("pub", "p").set("secret", "s").build(), 1L, Instant.EPOCH, headers,
                null);
        final SignalFilter signalFilter = new SignalFilter(
                ConnectivityModelFactory.newConnectionBuilder(TestConstants.createRandomConnectionId(),
                        ConnectionType.AMQP_10, ConnectivityStatus.OPEN, "amqp://user:pass@host:1111/path")
                        .targets(List.of(targets))
                        .build(),
                TestConstants.Monitoring.MONITOR_REGISTRY_MOCK);
        return signalFilter.filter(event);
    }

    private static Target target(final String address, @Nullable final String filter,
            final AuthorizationSubject first, final AuthorizationSubject... further) {
        final var topicBuilder = ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS);
        if (filter != null) {
            topicBuilder.withFilter(filter);
        }
        return ConnectivityModelFactory.newTargetBuilder()
                .address(address)
                .authorizationContext(AuthorizationContext.newInstance(
                        DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION, first, further))
                .topics(topicBuilder.build())
                .build();
    }

}
