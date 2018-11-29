/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.namespaces;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.services.utils.ddata.DistributedDataConfigReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.cluster.ddata.ORSet;
import akka.cluster.ddata.Replicator;
import akka.event.Logging;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link BlockedNamespaces}.
 */
public final class BlockedNamespacesTest {

    private ActorSystem actorSystem;
    private DistributedDataConfigReader configReader;

    @Before
    public void setup() {
        actorSystem = ActorSystem.create(getClass().getSimpleName(), ConfigFactory.load("test.conf"));
        configReader = DistributedDataConfigReader.of(actorSystem, "replicator", "");
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
        testCRUD(BlockedNamespaces.of(configReader, actorSystem), actorSystem);
    }

    @Test
    public void startWithMatchingRole() throws Exception {
        testCRUD(BlockedNamespaces.of(DistributedDataConfigReader.of(actorSystem, "replicator", "ddata-aware"),
                actorSystem), actorSystem);
    }

    @Test
    public void startWithWrongRole() {
        // logging disabled to not print expected stacktrace; re-enable logging to debug.
        actorSystem.eventStream().setLogLevel(Logging.levelFor("off").get().asInt());
        new TestKit(actorSystem) {{
            final BlockedNamespaces underTest = BlockedNamespaces.of(DistributedDataConfigReader.of(actorSystem,
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
