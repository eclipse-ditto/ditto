/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.pubsub.ddata;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import akka.actor.ActorPath;
import akka.actor.ActorRef;

/**
 * Tests subclasses of {@link org.eclipse.ditto.services.utils.pubsub.ddata.AbstractSubscriptions}.
 *
 * @param <H> type of hashes.
 * @param <T> type of distributed updates.
 * @param <S> type of subscriptions objects.
 */
public abstract class AbstractSubscriptionsTest<H, T, S extends AbstractSubscriptions<H, T>> {

    protected static final ActorRef ACTOR1 = new MockActorRef("actor1");
    protected static final ActorRef ACTOR2 = new MockActorRef("actor2");
    protected static final ActorRef ACTOR3 = new MockActorRef("actor3");
    protected static final ActorRef ACTOR4 = new MockActorRef("actor4");

    /**
     * @return a fresh empty subscriptions object to test.
     */
    protected abstract S newSubscriptions();

    /**
     * @return venn-diagram subscriptions.
     */
    protected S getVennDiagram() {
        final S subscriptions = newSubscriptions();
        subscriptions.subscribe(ACTOR1, asSet("1", "2", "4", "5"));
        subscriptions.subscribe(ACTOR2, asSet("2", "3", "5", "6"));
        subscriptions.subscribe(ACTOR3, asSet("4", "5", "6", "7"));
        return subscriptions;
    }

    @Test
    public void createEmptySubscriptions() {
        final int hashFamilySize = 8;
        final AbstractSubscriptions<H, T> underTest = newSubscriptions();
        assertThat(underTest.subscriberToTopic.isEmpty()).isTrue();
        assertThat(underTest.topicToData.isEmpty()).isTrue();
    }

    @Test
    public void testVennDiagramMembership() {
        final AbstractSubscriptions<H, T> underTest = getVennDiagram();
        final SubscriptionsReader reader = underTest.snapshot();
        assertThat(reader.getSubscribers(singleton("1"))).containsExactlyInAnyOrder(ACTOR1);
        assertThat(reader.getSubscribers(singleton("2"))).containsExactlyInAnyOrder(ACTOR1, ACTOR2);
        assertThat(reader.getSubscribers(singleton("3"))).containsExactlyInAnyOrder(ACTOR2);
        assertThat(reader.getSubscribers(singleton("4"))).containsExactlyInAnyOrder(ACTOR1, ACTOR3);
        assertThat(reader.getSubscribers(singleton("5"))).containsExactlyInAnyOrder(ACTOR1, ACTOR2, ACTOR3);
        assertThat(reader.getSubscribers(singleton("6"))).containsExactlyInAnyOrder(ACTOR2, ACTOR3);
        assertThat(reader.getSubscribers(singleton("7"))).containsExactlyInAnyOrder(ACTOR3);
        assertThat(underTest.subscriberToTopic.size()).isEqualTo(3);
        assertThat(underTest.topicToData.size()).isEqualTo(7);
    }

    @Test
    public void testVennDiagramWithFilter() {
        final AbstractSubscriptions<H, T> underTest = getVennDiagram();
        underTest.subscribe(ACTOR1, Collections.emptySet(), topics -> topics.contains("1"));
        underTest.subscribe(ACTOR2, Collections.emptySet(), topics -> !topics.contains("6"));
        final SubscriptionsReader reader = underTest.snapshot();
        assertThat(reader.getSubscribers(singleton("1"))).containsExactlyInAnyOrder(ACTOR1);
        assertThat(reader.getSubscribers(singleton("2"))).containsExactlyInAnyOrder(ACTOR2);
        assertThat(reader.getSubscribers(singleton("3"))).containsExactlyInAnyOrder(ACTOR2);
        assertThat(reader.getSubscribers(singleton("4"))).containsExactlyInAnyOrder(ACTOR3);
        assertThat(reader.getSubscribers(singleton("5"))).containsExactlyInAnyOrder(ACTOR2, ACTOR3);
        assertThat(reader.getSubscribers(singleton("6"))).containsExactlyInAnyOrder(ACTOR3);
        assertThat(reader.getSubscribers(singleton("7"))).containsExactlyInAnyOrder(ACTOR3);
    }

    @Test
    public void testVennDiagramMembershipAfterRotation() {
        final AbstractSubscriptions<H, T> underTest = getVennDiagram();
        underTest.subscribe(ACTOR1, singleton("3"));
        underTest.subscribe(ACTOR1, singleton("6"));
        underTest.subscribe(ACTOR2, singleton("4"));
        underTest.subscribe(ACTOR2, singleton("7"));
        underTest.subscribe(ACTOR3, singleton("1"));
        underTest.subscribe(ACTOR3, singleton("2"));
        underTest.unsubscribe(ACTOR1, singleton("1"));
        underTest.unsubscribe(ACTOR1, singleton("4"));
        underTest.unsubscribe(ACTOR2, singleton("2"));
        underTest.unsubscribe(ACTOR2, singleton("3"));
        underTest.unsubscribe(ACTOR3, singleton("6"));
        underTest.unsubscribe(ACTOR3, singleton("7"));
        final SubscriptionsReader reader = underTest.snapshot();
        assertThat(reader.getSubscribers(singleton("1"))).containsExactlyInAnyOrder(ACTOR3);
        assertThat(reader.getSubscribers(singleton("2"))).containsExactlyInAnyOrder(ACTOR3, ACTOR1);
        assertThat(reader.getSubscribers(singleton("3"))).containsExactlyInAnyOrder(ACTOR1);
        assertThat(reader.getSubscribers(singleton("4"))).containsExactlyInAnyOrder(ACTOR3, ACTOR2);
        assertThat(reader.getSubscribers(singleton("5"))).containsExactlyInAnyOrder(ACTOR3, ACTOR1, ACTOR2);
        assertThat(reader.getSubscribers(singleton("6"))).containsExactlyInAnyOrder(ACTOR1, ACTOR2);
        assertThat(reader.getSubscribers(singleton("7"))).containsExactlyInAnyOrder(ACTOR2);
    }

    @Test
    public void testVennDiagramMembershipAfterAnotherRotation() {
        final AbstractSubscriptions<H, T> underTest = getVennDiagram();
        underTest.subscribe(ACTOR1, singleton("3"));
        underTest.unsubscribe(ACTOR1, singleton("1"));
        underTest.unsubscribe(ACTOR1, singleton("4"));
        underTest.unsubscribe(ACTOR2, singleton("2"));
        underTest.unsubscribe(ACTOR2, singleton("3"));
        underTest.subscribe(ACTOR1, singleton("6"));
        underTest.subscribe(ACTOR2, singleton("4"));
        underTest.subscribe(ACTOR2, singleton("7"));
        underTest.subscribe(ACTOR3, singleton("1"));
        underTest.unsubscribe(ACTOR3, singleton("6"));
        underTest.subscribe(ACTOR3, singleton("2"));
        underTest.unsubscribe(ACTOR3, singleton("7"));
        final SubscriptionsReader reader = underTest.snapshot();
        assertThat(reader.getSubscribers(singleton("1"))).containsExactlyInAnyOrder(ACTOR3);
        assertThat(reader.getSubscribers(singleton("2"))).containsExactlyInAnyOrder(ACTOR3, ACTOR1);
        assertThat(reader.getSubscribers(singleton("3"))).containsExactlyInAnyOrder(ACTOR1);
        assertThat(reader.getSubscribers(singleton("4"))).containsExactlyInAnyOrder(ACTOR3, ACTOR2);
        assertThat(reader.getSubscribers(singleton("5"))).containsExactlyInAnyOrder(ACTOR3, ACTOR1, ACTOR2);
        assertThat(reader.getSubscribers(singleton("6"))).containsExactlyInAnyOrder(ACTOR1, ACTOR2);
        assertThat(reader.getSubscribers(singleton("7"))).containsExactlyInAnyOrder(ACTOR2);
    }

    @Test
    public void testSubscriberRemoval() {
        final AbstractSubscriptions<H, T> underTest = getVennDiagram();
        underTest.removeSubscriber(ACTOR1);
        underTest.removeSubscriber(ACTOR2);
        final SubscriptionsReader reader = underTest.snapshot();
        assertThat(reader.getSubscribers(singleton("1"))).isEmpty();
        assertThat(reader.getSubscribers(singleton("2"))).isEmpty();
        assertThat(reader.getSubscribers(singleton("3"))).isEmpty();
        assertThat(reader.getSubscribers(singleton("4"))).containsExactlyInAnyOrder(ACTOR3);
        assertThat(reader.getSubscribers(singleton("5"))).containsExactlyInAnyOrder(ACTOR3);
        assertThat(reader.getSubscribers(singleton("6"))).containsExactlyInAnyOrder(ACTOR3);
        assertThat(reader.getSubscribers(singleton("7"))).containsExactlyInAnyOrder(ACTOR3);
        assertThat(underTest.subscriberToTopic.size()).isEqualTo(1);
        assertThat(underTest.topicToData.size()).isEqualTo(4);
    }

    @Test
    public void testSnapshot() {
        // GIVEN: A snapshot is taken
        final AbstractSubscriptions<H, T> underTest = getVennDiagram();
        final SubscriptionsReader snapshot = underTest.snapshot();

        // THEN: snapshot cannot be modified but can be queried
        assertThat(snapshot.getSubscribers(Arrays.asList("1", "2", "3"))).containsExactlyInAnyOrder(ACTOR1, ACTOR2);

        // WHEN: the original mutates
        underTest.subscribe(ACTOR1, singleton("8"));
        underTest.unsubscribe(ACTOR1, singleton("1"));
        underTest.removeSubscriber(ACTOR2);
        assertThat(underTest.subscriberToTopic.size()).isEqualTo(2);
        assertThat(underTest.topicToData.size()).isEqualTo(6);

        // THEN: snapshot should not change
        assertThat(snapshot).isEqualTo(getVennDiagram().snapshot());
        assertThat(snapshot).isNotEqualTo(underTest.snapshot());
    }

    @Test
    public void changeDetectionIsAccurate() {
        final AbstractSubscriptions<H, T> underTest = getVennDiagram();

        assertThat(underTest.subscribe(ACTOR1, asSet("1", "2"))).isFalse();
        assertThat(underTest.subscribe(ACTOR1, asSet("2", "3"))).isTrue();
        assertThat(underTest.unsubscribe(ACTOR2, asSet("1", "4"))).isFalse();
        assertThat(underTest.unsubscribe(ACTOR2, asSet("2", "3", "5", "7"))).isTrue();
        assertThat(underTest.removeSubscriber(ACTOR4)).isFalse();
        assertThat(underTest.removeSubscriber(ACTOR3)).isTrue();
    }

    private static Set<String> asSet(final String... elements) {
        return new HashSet<>(Arrays.asList(elements));
    }

    private static final class MockActorRef extends ActorRef {

        private static final String GUARDIAN = "akka://user@hostname:1234/user/";

        private final String path;

        private MockActorRef(final String name) {
            path = GUARDIAN + name;
        }

        @Override
        public ActorPath path() {
            return ActorPath.fromString(path);
        }

        @Override
        public boolean isTerminated() {
            return false;
        }
    }
}
