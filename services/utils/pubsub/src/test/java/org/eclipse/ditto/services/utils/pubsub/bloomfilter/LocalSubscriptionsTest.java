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
package org.eclipse.ditto.services.utils.pubsub.bloomfilter;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.Test;

import akka.actor.ActorPath;
import akka.actor.ActorRef;
import akka.util.ByteString;

/**
 * Tests {@link org.eclipse.ditto.services.utils.pubsub.bloomfilter.LocalSubscriptions}.
 */
public final class LocalSubscriptionsTest {

    private static final ActorRef ACTOR1 = new MockActorRef("actor1");
    private static final ActorRef ACTOR2 = new MockActorRef("actor2");
    private static final ActorRef ACTOR3 = new MockActorRef("actor3");

    @Test
    public void createEmptySubscriptions() {
        final int hashFamilySize = 8;
        final LocalSubscriptions underTest = LocalSubscriptions.of("hello world", hashFamilySize);
        assertThat(underTest.getSeeds().size()).isEqualTo(hashFamilySize);
        assertThat(underTest.subscriberToTopic.isEmpty()).isTrue();
        assertThat(underTest.topicToData.isEmpty()).isTrue();
    }

    @Test
    public void hashesHaveExpectedSize() {
        final int hashFamilySize = 8;
        final LocalSubscriptions underTest = LocalSubscriptions.of("hello world", hashFamilySize);
        final List<Integer> hashes = underTest.getHashes("goodbye cruel world");
        assertThat(hashes.size()).isEqualTo(hashFamilySize);
    }

    @Test
    public void testVennDiagramMembership() {
        final LocalSubscriptions underTest = getVennDiagram();
        assertThat(underTest.getSubscribers(singleton("1"))).containsExactlyInAnyOrder(ACTOR1);
        assertThat(underTest.getSubscribers(singleton("2"))).containsExactlyInAnyOrder(ACTOR1, ACTOR2);
        assertThat(underTest.getSubscribers(singleton("3"))).containsExactlyInAnyOrder(ACTOR2);
        assertThat(underTest.getSubscribers(singleton("4"))).containsExactlyInAnyOrder(ACTOR1, ACTOR3);
        assertThat(underTest.getSubscribers(singleton("5"))).containsExactlyInAnyOrder(ACTOR1, ACTOR2, ACTOR3);
        assertThat(underTest.getSubscribers(singleton("6"))).containsExactlyInAnyOrder(ACTOR2, ACTOR3);
        assertThat(underTest.getSubscribers(singleton("7"))).containsExactlyInAnyOrder(ACTOR3);
        assertThat(underTest.subscriberToTopic.size()).isEqualTo(3);
        assertThat(underTest.topicToData.size()).isEqualTo(7);
    }

    @Test
    public void testVennDiagramMembershipAfterRotation() {
        final LocalSubscriptions underTest = getVennDiagram()
                .subscribe(ACTOR1, singleton("3"))
                .subscribe(ACTOR1, singleton("6"))
                .subscribe(ACTOR2, singleton("4"))
                .subscribe(ACTOR2, singleton("7"))
                .subscribe(ACTOR3, singleton("1"))
                .subscribe(ACTOR3, singleton("2"))
                .unsubscribe(ACTOR1, singleton("1"))
                .unsubscribe(ACTOR1, singleton("4"))
                .unsubscribe(ACTOR2, singleton("2"))
                .unsubscribe(ACTOR2, singleton("3"))
                .unsubscribe(ACTOR3, singleton("6"))
                .unsubscribe(ACTOR3, singleton("7"));
        assertThat(underTest.getSubscribers(singleton("1"))).containsExactlyInAnyOrder(ACTOR3);
        assertThat(underTest.getSubscribers(singleton("2"))).containsExactlyInAnyOrder(ACTOR3, ACTOR1);
        assertThat(underTest.getSubscribers(singleton("3"))).containsExactlyInAnyOrder(ACTOR1);
        assertThat(underTest.getSubscribers(singleton("4"))).containsExactlyInAnyOrder(ACTOR3, ACTOR2);
        assertThat(underTest.getSubscribers(singleton("5"))).containsExactlyInAnyOrder(ACTOR3, ACTOR1, ACTOR2);
        assertThat(underTest.getSubscribers(singleton("6"))).containsExactlyInAnyOrder(ACTOR1, ACTOR2);
        assertThat(underTest.getSubscribers(singleton("7"))).containsExactlyInAnyOrder(ACTOR2);
    }

    @Test
    public void testVennDiagramMembershipAfterAnotherRotation() {
        final LocalSubscriptions underTest = getVennDiagram()
                .subscribe(ACTOR1, singleton("3"))
                .unsubscribe(ACTOR1, singleton("1"))
                .unsubscribe(ACTOR1, singleton("4"))
                .unsubscribe(ACTOR2, singleton("2"))
                .unsubscribe(ACTOR2, singleton("3"))
                .subscribe(ACTOR1, singleton("6"))
                .subscribe(ACTOR2, singleton("4"))
                .subscribe(ACTOR2, singleton("7"))
                .subscribe(ACTOR3, singleton("1"))
                .unsubscribe(ACTOR3, singleton("6"))
                .subscribe(ACTOR3, singleton("2"))
                .unsubscribe(ACTOR3, singleton("7"));
        assertThat(underTest.getSubscribers(singleton("1"))).containsExactlyInAnyOrder(ACTOR3);
        assertThat(underTest.getSubscribers(singleton("2"))).containsExactlyInAnyOrder(ACTOR3, ACTOR1);
        assertThat(underTest.getSubscribers(singleton("3"))).containsExactlyInAnyOrder(ACTOR1);
        assertThat(underTest.getSubscribers(singleton("4"))).containsExactlyInAnyOrder(ACTOR3, ACTOR2);
        assertThat(underTest.getSubscribers(singleton("5"))).containsExactlyInAnyOrder(ACTOR3, ACTOR1, ACTOR2);
        assertThat(underTest.getSubscribers(singleton("6"))).containsExactlyInAnyOrder(ACTOR1, ACTOR2);
        assertThat(underTest.getSubscribers(singleton("7"))).containsExactlyInAnyOrder(ACTOR2);
    }

    @Test
    public void testSubscriberRemoval() {
        final LocalSubscriptions underTest = getVennDiagram()
                .removeSubscriber(ACTOR1)
                .removeSubscriber(ACTOR2);
        assertThat(underTest.getSubscribers(singleton("1"))).isEmpty();
        assertThat(underTest.getSubscribers(singleton("2"))).isEmpty();
        assertThat(underTest.getSubscribers(singleton("3"))).isEmpty();
        assertThat(underTest.getSubscribers(singleton("4"))).containsExactlyInAnyOrder(ACTOR3);
        assertThat(underTest.getSubscribers(singleton("5"))).containsExactlyInAnyOrder(ACTOR3);
        assertThat(underTest.getSubscribers(singleton("6"))).containsExactlyInAnyOrder(ACTOR3);
        assertThat(underTest.getSubscribers(singleton("7"))).containsExactlyInAnyOrder(ACTOR3);
        assertThat(underTest.subscriberToTopic.size()).isEqualTo(1);
        assertThat(underTest.topicToData.size()).isEqualTo(4);
    }

    @Test
    public void testBloomFilter() {
        final LocalSubscriptions underTest = getVennDiagram();
        final ByteString bloomFilter = underTest.toOptimalBloomFilter(1.0);
        IntStream.rangeClosed(1, 7)
                .mapToObj(String::valueOf)
                .forEach(topic ->
                        assertThat(ByteStringAsBitSet.contains(bloomFilter, underTest.getHashes(topic).stream()))
                                .describedAs("Bloom filter should not reject topic \"%s\"", topic)
                                .isTrue());

        // There is no false-positive among topics "8", "9", ..., "100" unless Scala changes its murmur-hash
        // implementation. The first false-positive is "402".
        IntStream.rangeClosed(8, 100)
                .mapToObj(String::valueOf)
                .forEach(topic ->
                        assertThat(ByteStringAsBitSet.contains(bloomFilter, underTest.getHashes(topic).stream()))
                                .describedAs("Bloom filter should not accept topic \"%s\"", topic)
                                .isFalse());
    }

    @Test
    public void testSnapshot() {
        // GIVEN: A snapshot is taken
        final LocalSubscriptions underTest = getVennDiagram();
        final LocalSubscriptions snapshot = underTest.snapshot();

        // THEN: snapshot cannot be modified but can be queried
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> snapshot.subscribe(ACTOR1, singleton("8")));
        assertThat(snapshot.getSubscribers(Arrays.asList("1", "2", "3"))).containsExactlyInAnyOrder(ACTOR1, ACTOR2);

        // WHEN: the original mutates
        underTest.subscribe(ACTOR1, singleton("8"))
                .unsubscribe(ACTOR1, singleton("1"))
                .removeSubscriber(ACTOR2);
        assertThat(underTest.subscriberToTopic.size()).isEqualTo(2);
        assertThat(underTest.topicToData.size()).isEqualTo(6);

        // THEN: snapshot should not change
        assertThat(snapshot).isEqualTo(getVennDiagram());
        assertThat(snapshot).isNotEqualTo(underTest);
    }

    private static LocalSubscriptions getVennDiagram() {
        return LocalSubscriptions.of("venn diagram", 8)
                .subscribe(ACTOR1, asSet("1", "2", "4", "5"))
                .subscribe(ACTOR2, asSet("2", "3", "5", "6"))
                .subscribe(ACTOR3, asSet("4", "5", "6", "7"));
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
