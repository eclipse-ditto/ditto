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
package org.eclipse.services.thingsearch.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.eclipse.ditto.services.utils.test.mongo.MongoDbResource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;

/**
 * Test for {@link MongoHealthCheck}.
 */
public final class MongoHealthCheckTest {

    private static final Config CONFIG = ConfigFactory.load("test");

    private ActorSystem actorSystem;

    /** */
    @Before
    public void before() throws InterruptedException {
        final Config config = ConfigFactory.load("test");
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
    }

    /** */
    @After
    public void after() throws InterruptedException {
        if (actorSystem != null) {
            JavaTestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    @Test
    public void healthCheckTest() {
        final MongoDbResource mongoResource = new MongoDbResource("localhost");
        mongoResource.start();

        final MongoHealthCheck persistenceUnderTest =
                new MongoHealthCheck(new MongoClientWrapper(mongoResource.getBindIp(), mongoResource.getPort(), UUID.randomUUID()
                        .toString(), CONFIG), actorSystem, actorSystem.log());

        assertThat(persistenceUnderTest.checkHealth()).isTrue();

        mongoResource.stop();

        assertThat(persistenceUnderTest.checkHealth()).isFalse();
    }
}
