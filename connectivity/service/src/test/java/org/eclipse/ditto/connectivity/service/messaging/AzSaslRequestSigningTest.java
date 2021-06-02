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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import javax.annotation.Nullable;

import org.junit.After;
import org.junit.Test;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.headers.HttpCredentials;
import akka.stream.javadsl.Sink;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link org.eclipse.ditto.connectivity.service.messaging.AzSaslRequestSigning}.
 */
public final class AzSaslRequestSigningTest {

    @Nullable private ActorSystem actorSystem;

    @After
    public void shutdown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void testToken() {
        final String sharedKey = Base64.getEncoder().encodeToString("my-awesome-shared-key".getBytes());
        final String resource = "resource-name.hostname.domain";
        final var underTest = AzSaslRequestSigning.of("keyName", sharedKey, Duration.ZERO, resource);
        final Instant timestamp = Instant.ofEpochSecond(1622480838);
        final String expectedToken = "sr=resource-name.hostname.domain&" +
                "sig=J5XH%2BFoY%2Bo0wGKh3I0m%2BsPVB6CYCFZcQRg95pImtQPA%3D&se=1622480838&skn=keyName";
        assertThat(underTest.getSasToken(timestamp)).isEqualTo(expectedToken);
        assertThat(underTest.getAmqpUsername()).isEqualTo("keyName@sas.root.resource-name.hostname.domain");
        assertThat(underTest.getAmqpPassword(timestamp)).isEqualTo("SharedAccessSignature " + expectedToken);
    }

    @Test
    public void testRequestSigning() {
        final HttpRequest request = HttpRequest.POST("https://resource-name.hostname.domain")
                .withEntity(HttpEntities.create("irrelevant payload"));
        final String resource = "resource-name.hostname.domain";
        final String sharedKey = Base64.getEncoder().encodeToString("my-awesome-shared-key".getBytes());
        final var underTest = AzSaslRequestSigning.of("keyName", sharedKey, Duration.ZERO, resource);
        final Instant timestamp = Instant.ofEpochSecond(1622480838);

        final String expectedToken = "sr=resource-name.hostname.domain&" +
                "sig=J5XH%2BFoY%2Bo0wGKh3I0m%2BsPVB6CYCFZcQRg95pImtQPA%3D&se=1622480838&skn=keyName";

        final HttpRequest expectedRequest =
                request.addCredentials(HttpCredentials.create("SharedAccessSignature", expectedToken));

        actorSystem = ActorSystem.create();
        assertThat(underTest.sign(request, timestamp).runWith(Sink.head(), actorSystem).toCompletableFuture().join())
                .isEqualTo(expectedRequest);
    }
}
