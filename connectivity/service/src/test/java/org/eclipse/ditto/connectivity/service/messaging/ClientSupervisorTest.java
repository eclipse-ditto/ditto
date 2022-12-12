/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
import java.util.Map;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.service.UriEncoding;
import org.eclipse.ditto.connectivity.api.commands.sudo.SudoRetrieveClientActorProps;
import org.eclipse.ditto.connectivity.api.commands.sudo.SudoRetrieveConnectionStatus;
import org.eclipse.ditto.connectivity.api.commands.sudo.SudoRetrieveConnectionStatusResponse;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.testkit.TestKit;
import scala.concurrent.duration.FiniteDuration;

/**
 * Tests {@link ClientSupervisor}.
 */
public final class ClientSupervisorTest {

    @Rule
    public final ActorSystemResource ACTOR_SYSTEM_RESOURCE = ActorSystemResource.newInstance(
            ConfigFactory.parseMap(Map.of(
                    "ditto.extensions.client-actor-props-factory.extension-class",
                    MockClientActorPropsFactory.class.getName()
            )).withFallback(TestConstants.CONFIG));

    @Test
    public void checkConnectionStatusOnStartUp() {
        new TestKit(ACTOR_SYSTEM_RESOURCE.getActorSystem()) {{
            final var props = ClientSupervisor.propsForTest(Duration.ofDays(1), testActor());
            final var clientActorId = new ClientActorId(TestConstants.createRandomConnectionId(), 0);

            // WHEN: Client supervisor starts
            // THEN: It retrieves the connection status
            final var underTest = childActorOf(props, UriEncoding.encodePath(clientActorId.toString()));
            watch(underTest);
            expectMsgClass(SudoRetrieveConnectionStatus.class);
            assertThat(lastSender()).isEqualTo(underTest);

            // WHEN: Connection status is open and the client actor is not started
            // THEN: Client supervisor retrieves the client actor props args
            underTest.tell(SudoRetrieveConnectionStatusResponse.of(ConnectivityStatus.OPEN, 1, DittoHeaders.empty()),
                    testActor());
            expectMsgClass(SudoRetrieveClientActorProps.class);

            // WHEN: Connection status is closed
            // THEN: Client supervisor terminates
            underTest.tell(SudoRetrieveConnectionStatusResponse.of(ConnectivityStatus.CLOSED, 1, DittoHeaders.empty()),
                    testActor());
            expectTerminated(underTest, FiniteDuration.apply(10, "s"));
        }};
    }
}
