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
package org.eclipse.ditto.services.gateway.proxy.actors.handlers;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.services.utils.cluster.ShardedMessageEnvelope;
import org.eclipse.ditto.signals.commands.base.Command;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Base class of tests of handler actors of thing commands.
 */
public class AbstractThingHandlerActorTest {

    protected static final Subject defaultSubject =
            Subject.newInstance(SubjectIssuer.GOOGLE_URL, "testSubject", SubjectType.JWT);

    protected static final DittoHeaders defaultHeaders = DittoHeaders.newBuilder()
            .authorizationSubjects(defaultSubject.getId().toString())
            .readSubjects(
                    Collections.singleton(defaultSubject.getId().toString())
            )
            .build();
    protected static final String defaultEnforcerId = "default:enforcerShardId";

    protected static final Config CONFIG = ConfigFactory.load("test");

    protected static ActorSystem actorSystem;

    // actor refs for cleanup
    protected TestProbe enforcerShard;
    protected TestProbe aclEnforcerShard;
    protected TestProbe policyEnforcerShard;

    protected static String randomize(final String id) {
        return id + UUID.randomUUID().toString();
    }

    protected ShardedMessageEnvelope createShardedMessage(final String shardId, final Command<?> command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final JsonSchemaVersion schemaVersion =
                dittoHeaders.getSchemaVersion().orElse(dittoHeaders.getLatestSchemaVersion());
        return ShardedMessageEnvelope.of(shardId, command.getType(),
                command.toJson(schemaVersion, FieldType.regularOrSpecial()), dittoHeaders);
    }

    @BeforeClass
    public static void setUpActorSystem() throws Exception {
        actorSystem = ActorSystem.create("AkkaTestSystem", CONFIG);
    }

    @AfterClass
    public static void tearDownActorSystem() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Before
    public void startActors() {
        if (actorSystem != null) {
            enforcerShard = new TestProbe(actorSystem, randomize("testProbe.enforcerShard"));
            aclEnforcerShard = new TestProbe(actorSystem, randomize("testProbe.aclEnforcerShard"));
            policyEnforcerShard = new TestProbe(actorSystem, randomize("testProbe.policyEnforcerShard"));
        }
    }

    @After
    public void cleanupActors() {
        if (actorSystem != null) {
            Arrays.asList(enforcerShard, aclEnforcerShard, policyEnforcerShard).forEach(testProbe -> {
                if (testProbe != null) {
                    actorSystem.stop(testProbe.ref());
                }
            });
        }
    }

}
