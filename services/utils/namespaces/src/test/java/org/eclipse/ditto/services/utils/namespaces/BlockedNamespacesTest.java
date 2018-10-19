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

import org.eclipse.ditto.services.utils.ddata.DDataConfigReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link BlockedNamespaces}.
 */
public final class BlockedNamespacesTest {

    private ActorSystem actorSystem;
    private DDataConfigReader configReader;

    @Before
    public void setup() {
        actorSystem = ActorSystem.create(getClass().getSimpleName(), ConfigFactory.load("test.conf"));
        configReader = DDataConfigReader.of(actorSystem);
    }

    @After
    public void teardown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    @Test
    public void startWithoutRole() throws Exception {
        testCRUD(configReader, actorSystem);
    }

    @Test
    public void startWithMatchingRole() throws Exception {
        testCRUD(configReader.withRole("ddata-aware"), actorSystem);
    }

    @Test
    public void startWithWrongRole() {
        // logging disabled to not print expected stacktrace; re-enable logging to debug.
        actorSystem.eventStream().setLogLevel(Logging.levelFor("off").get().asInt());
        new TestKit(actorSystem) {{
            final BlockedNamespaces underTest = BlockedNamespaces.of(configReader.withRole("wrong-role"), actorSystem);
            watch(underTest.getReplicator());
            expectTerminated(underTest.getReplicator());
        }};
    }

    private static void testCRUD(final DDataConfigReader configReader, final ActorSystem actorSystem) throws Exception {
        new TestKit(actorSystem) {{
            final BlockedNamespaces underTest = BlockedNamespaces.of(configReader, actorSystem);

            final String namespace = "dummy.namespace";
            assertThat(underTest.contains(namespace).toCompletableFuture().get()).isFalse();

            underTest.add(namespace).toCompletableFuture().get();
            assertThat(underTest.contains(namespace).toCompletableFuture().get()).isTrue();

            underTest.remove(namespace).toCompletableFuture().get();
            assertThat(underTest.contains(namespace).toCompletableFuture().get()).isFalse();
        }};
    }
}
