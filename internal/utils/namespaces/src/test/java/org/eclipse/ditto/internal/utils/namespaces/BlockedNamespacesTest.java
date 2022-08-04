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
package org.eclipse.ditto.internal.utils.namespaces;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;

import org.eclipse.ditto.internal.utils.ddata.DistributedData;
import org.eclipse.ditto.internal.utils.ddata.DistributedDataConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.cluster.ddata.ORSet;
import akka.cluster.ddata.Replicator;
import akka.stream.Attributes;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link BlockedNamespaces}.
 */
public final class BlockedNamespacesTest {

    private ActorSystem actorSystem;
    private DistributedDataConfig config;

    @Before
    public void setup() {
        actorSystem = ActorSystem.create(getClass().getSimpleName(), ConfigFactory.load("test.conf"));
        config = DistributedData.createConfig(actorSystem, "replicator", "");
    }

    @After
    public void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    @Test
    public void startWithDefaultConfig() throws Exception {
        testCRUD(BlockedNamespaces.of(actorSystem), actorSystem);
    }

    @Test
    public void startWithoutRole() throws Exception {
        testCRUD(BlockedNamespaces.create(config, actorSystem), actorSystem);
    }

    @Test
    public void startWithMatchingRole() throws Exception {
        testCRUD(BlockedNamespaces.create(DistributedData.createConfig(actorSystem, "replicator", "blocked-namespaces-aware"),
                actorSystem), actorSystem);
    }

    @Test
    public void startSeveralTimes() throws Exception {
        // This test simulates the situation where the root actor of a Ditto service restarts several times.

        // GIVEN: Many blocked-namespaces objects were obtained in the same actor system.
        final Supplier<BlockedNamespaces> blockedNamespacesSupplier = () -> BlockedNamespaces.of(actorSystem);
        for (int i = 0; i < 10; ++i) {
            blockedNamespacesSupplier.get();
        }

        // WHEN: Another blocked-namespaces object is obtained.
        // THEN: It fulfills its function.
        testCRUD(blockedNamespacesSupplier.get(), actorSystem);
    }

    @Test
    public void startWithWrongRole() {
        // logging disabled to not print expected stacktrace; re-enable logging to debug.
        actorSystem.eventStream().setLogLevel(Attributes.logLevelOff());
        new TestKit(actorSystem) {{
            final BlockedNamespaces underTest = BlockedNamespaces.create(DistributedData.createConfig(actorSystem,
                    "replicator", "wrong-role"), actorSystem);
            watch(underTest.getReplicator());
            expectTerminated(underTest.getReplicator());
        }};
    }

    @Test
    @SuppressWarnings("unchecked")
    public void subscribeForChanges() throws Exception {
        new TestKit(actorSystem) {{
            final BlockedNamespaces underTest = BlockedNamespaces.of(actorSystem);
            underTest.subscribeForChanges(getRef());

            final String namespace = "ns1";
            assertThat(underTest.contains(namespace).toCompletableFuture().get()).isFalse();

            // wait for a round trip to the replicator to ensure subscription is active
            underTest.add(namespace).toCompletableFuture().get();
            assertThat(underTest.contains(namespace).toCompletableFuture().get()).isTrue();

            final ORSet change1 = (ORSet) expectMsgClass(Replicator.Changed.class).dataValue();

            underTest.remove(namespace).toCompletableFuture().get();
            assertThat(underTest.contains(namespace).toCompletableFuture().get()).isFalse();

            final ORSet change2 = (ORSet) expectMsgClass(Replicator.Changed.class).dataValue();

            underTest.add("ns2");
            final ORSet change3 = (ORSet) expectMsgClass(Replicator.Changed.class).dataValue();

            underTest.add("ns3");
            final ORSet change4 = (ORSet) expectMsgClass(Replicator.Changed.class).dataValue();

            assertThat(change1.getElements()).containsExactly("ns1");
            assertThat(change2.getElements()).isEmpty();
            assertThat(change3.getElements()).containsExactly("ns2");
            assertThat(change4.getElements()).containsExactly("ns2", "ns3");
        }};

    }

    private static void testCRUD(final BlockedNamespaces underTest, final ActorSystem actorSystem) throws Exception {
        new TestKit(actorSystem) {{
            final String namespace = "dummy.namespace";
            assertThat(underTest.contains(namespace).toCompletableFuture().get()).isFalse();

            underTest.add(namespace).toCompletableFuture().get();
            assertThat(underTest.contains(namespace).toCompletableFuture().get()).isTrue();

            underTest.remove(namespace).toCompletableFuture().get();
            assertThat(underTest.contains(namespace).toCompletableFuture().get()).isFalse();
        }};
    }

}
