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
package org.eclipse.ditto.services.thingsearch.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.services.utils.test.mongo.MongoDbResource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * Test for {@link MongoHealthCheck}.
 */
public final class MongoHealthCheckIT {

    private static final Config CONFIG = ConfigFactory.load("test");

    private ActorSystem actorSystem;

    /** */
    @Before
    public void before() {
        actorSystem = ActorSystem.create("AkkaTestSystem", CONFIG);
    }

    /** */
    @After
    public void after() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    @Test
    public void healthCheckTest() {
        final MongoDbResource mongoResource = new MongoDbResource("localhost");
        mongoResource.start();

        final MongoHealthCheck persistenceUnderTest =
                new MongoHealthCheck(
                        MongoClientWrapper.newInstance(mongoResource.getBindIp(), mongoResource.getPort(),
                                UUID.randomUUID().toString(), 100, 5000,
                                10), actorSystem, actorSystem.log());

        assertThat(persistenceUnderTest.checkHealth()).isTrue();

        mongoResource.stop();

        assertThat(persistenceUnderTest.checkHealth()).isFalse();
    }
}
