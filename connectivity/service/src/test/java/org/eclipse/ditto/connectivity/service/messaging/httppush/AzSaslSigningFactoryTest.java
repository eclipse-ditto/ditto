/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.httppush;

import java.util.Base64;

import org.eclipse.ditto.connectivity.model.HmacCredentials;
import org.eclipse.ditto.connectivity.service.messaging.signing.AzSaslSigningFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link AzSaslSigningFactory}.
 */
public final class AzSaslSigningFactoryTest {

    private ActorSystem actorSystem;

    @Before
    public void start() {
        actorSystem = ActorSystem.create();
    }

    @After
    public void stop() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void create() {
        final AzSaslSigningFactory underTest = new AzSaslSigningFactory();
        final HmacCredentials credentials = createCredentials();

        underTest.create(actorSystem, credentials);
    }

    @Test
    public void createAmqpConnectionSigning() {
        final AzSaslSigningFactory underTest = new AzSaslSigningFactory();
        final HmacCredentials credentials = createCredentials();

        underTest.createAmqpConnectionSigning(credentials);
    }

    private HmacCredentials createCredentials() {
        return HmacCredentials.of("az-sasl", JsonObject.newBuilder()
                .set("sharedKeyName", "name")
                .set("sharedKey", Base64.getEncoder().encodeToString("shared key".getBytes()))
                .set("endpoint", "example.com")
                .set("ttl", "15m")
                .build());
    }

}
