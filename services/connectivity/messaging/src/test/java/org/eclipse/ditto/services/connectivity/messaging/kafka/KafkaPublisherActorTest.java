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
package org.eclipse.ditto.services.connectivity.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.connectivity.messaging.BasePublisherActor.PublisherStarted.PUBLISHER_STARTED;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.awaitility.Awaitility;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.AbstractPublisherActorTest;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.mockito.Mockito;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.kafka.ProducerMessage;
import akka.stream.javadsl.Flow;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link org.eclipse.ditto.services.connectivity.messaging.kafka.KafkaPublisherActor}.
 */
public class KafkaPublisherActorTest extends AbstractPublisherActorTest {

    private static final String OUTBOUND_ADDRESS = "anyTopic/keyA";

    private final List<ProducerMessage.Message<String, String, Object>> received = new LinkedList<>();
    private KafkaConnectionFactory connectionFactory;

    @Override
    protected void setupMocks(final TestProbe probe) {
        connectionFactory = mock(KafkaConnectionFactory.class);
        when(connectionFactory.newFlow())
                .thenReturn(
                        Flow.fromFunction(envelope -> {
                            final ProducerMessage.Message<String, String, Object> message =
                                    (ProducerMessage.Message<String, String, Object>) envelope;
                            received.add(message);
                            return createResult(message);
                        }));
    }

    @SuppressWarnings("unchecked")
    private static ProducerMessage.Results<String, String, Object> createResult(final ProducerMessage.Message<String, String, Object> message) {
        final ProducerMessage.Results<String, String, Object> resultMock = Mockito.mock(ProducerMessage.Results.class);
        when(resultMock.passThrough()).thenReturn(message.passThrough());
        return resultMock;
    }

    @Override
    protected Props getPublisherActorProps() {
        return KafkaPublisherActor.props("theConnection", Collections.emptyList(), connectionFactory, false);
    }

    @Override
    protected void verifyPublishedMessage() throws Exception {
        Awaitility.await().until(() -> !received.isEmpty());
        assertThat(received).hasSize(1);
        final ProducerMessage.Message<String, String, Object> message = received.get(0);
        assertThat(message.record().topic()).isEqualTo("anyTopic");
        assertThat(message.record().key()).isEqualTo("keyA");
        assertThat(message.record().value()).isEqualTo("payload");
        final List<Header> headers = Arrays.asList(message.record().headers().toArray());
        shouldContainHeader(headers, "thing_id", TestConstants.Things.THING_ID);
        shouldContainHeader(headers, "suffixed_thing_id", TestConstants.Things.THING_ID + ".some.suffix");
        shouldContainHeader(headers, "prefixed_thing_id", "some.prefix." + TestConstants.Things.THING_ID);
        shouldContainHeader(headers, "eclipse", "ditto");
        shouldContainHeader(headers, "device_id", TestConstants.Things.THING_ID);
    }

    @Override
    protected void publisherCreated(final TestKit kit, final ActorRef publisherActor) {
        kit.expectMsg(PUBLISHER_STARTED);
        kit.expectMsgClass(Status.Success.class);
    }

    @Override
    protected Target decorateTarget(final Target target) {
        return target;
    }

    @Override
    protected String getOutboundAddress() {
        return OUTBOUND_ADDRESS;
    }

    private void shouldContainHeader(final List<Header> headers, final String key, final String value) {
        final RecordHeader expectedHeader = new RecordHeader(key, value.getBytes(StandardCharsets.US_ASCII));
        assertThat(headers).contains(expectedHeader);
    }

}
