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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.DisconnectException;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.awaitility.Awaitility;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.api.OutboundSignalFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.service.config.DittoConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.KafkaConfig;
import org.eclipse.ditto.connectivity.service.messaging.AbstractPublisherActorTest;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link KafkaPublisherActor}.
 */
public class KafkaPublisherActorTest extends AbstractPublisherActorTest {

    private static final String TARGET_TOPIC = "anyTopic";
    private static final String OUTBOUND_ADDRESS = TARGET_TOPIC + "/keyA";

    private final Queue<ProducerRecord<String, String>> published = new ConcurrentLinkedQueue<>();

    private final KafkaConfig kafkaConfig =
            DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(CONFIG)).getConnectionConfig().getKafkaConfig();
    private MockSendProducerFactory mockProducerFlowFactory;

    @Override
    protected void setupMocks(final TestProbe notUsed) {
        mockProducerFlowFactory = MockSendProducerFactory.getInstance(TARGET_TOPIC, published);
    }

    private void setUpMocksToFailWith(final RuntimeException exception) {
        mockProducerFlowFactory = MockSendProducerFactory.getInstance(TARGET_TOPIC, published, exception);
    }

    @Override
    protected Props getPublisherActorProps() {
        final Connection connection = TestConstants.createConnection();
        final String clientId = UUID.randomUUID().toString();
        return KafkaPublisherActor.props(connection, kafkaConfig, mockProducerFlowFactory, false, clientId);
    }

    protected Props getPublisherActorPropsWithDebugEnabled() {
        final Connection connectionWithDebugEnabled = TestConstants.createConnectionWithDebugEnabled();
        final String clientId = UUID.randomUUID().toString();
        return KafkaPublisherActor.props(connectionWithDebugEnabled, kafkaConfig, mockProducerFlowFactory, false,
                clientId);
    }

    @Override
    protected void verifyPublishedMessage() {
        Awaitility.await().until(() -> !published.isEmpty());
        final ProducerRecord<String, String> record = checkNotNull(published.poll());
        assertThat(published).isEmpty();
        assertThat(record).isNotNull();
        assertThat(record.topic()).isEqualTo(TARGET_TOPIC);
        assertThat(record.key()).isEqualTo("keyA");
        assertThat(record.value()).isEqualTo("payload");
        final List<Header> headers = Arrays.asList(record.headers().toArray());
        shouldContainHeader(headers, "thing_id", TestConstants.Things.THING_ID.toString());
        shouldContainHeader(headers, "suffixed_thing_id", TestConstants.Things.THING_ID + ".some.suffix");
        shouldContainHeader(headers, "prefixed_thing_id", "some.prefix." + TestConstants.Things.THING_ID);
        shouldContainHeader(headers, "eclipse", "ditto");
        shouldContainHeader(headers, "device_id", TestConstants.Things.THING_ID.toString());
        shouldContainHeader(headers, "ditto-connection-id");
        final Optional<Header> expectedHeader = headers.stream()
                .filter(header -> header.key().equals("ditto-connection-id"))
                .findAny();
        assertThat(expectedHeader).isPresent();
        assertThat(new String(expectedHeader.get().value()))
                .isNotEqualTo("hallo");//verify that header mapping has no effect
    }

    @Override
    protected void verifyPublishedMessageToReplyTarget() {
        Awaitility.await().until(() -> !published.isEmpty());
        final ProducerRecord<String, String> record = checkNotNull(published.poll());
        assertThat(published).isEmpty();
        assertThat(record.topic()).isEqualTo("replyTarget");
        assertThat(record.key()).isEqualTo("thing:id");
        final List<Header> headers = Arrays.asList(record.headers().toArray());
        shouldContainHeader(headers, "correlation-id", TestConstants.CORRELATION_ID);
        shouldContainHeader(headers, "mappedHeader2", "thing:id");
    }

    @Override
    protected void verifyAcknowledgements(final Supplier<Acknowledgements> ackSupplier) {
        final Acknowledgements acks = ackSupplier.get();
        assertThat(acks.getSize()).isEqualTo(1);
        final Acknowledgement ack = acks.stream().findAny().orElseThrow();
        assertThat(ack.getHttpStatus()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(ack.getLabel().toString()).isEqualTo("please-verify");
        assertThat(ack.getEntity()).isEmpty();
    }

    @Test
    public void verifyAcknowledgementsWithDebugEnabled() {
        new TestKit(actorSystem) {
            {
                final TestProbe probe = new TestProbe(actorSystem);
                setupMocks(probe);
                final Props publisherProps = getPublisherActorPropsWithDebugEnabled();
                final ActorRef publisherActor = childActorOf(publisherProps);

                final AcknowledgementLabel acknowledgementLabel = AcknowledgementLabel.of("please-verify");
                final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                        .correlationId(TestConstants.CORRELATION_ID)
                        .putHeader("device_id", "ditto:thing")
                        .acknowledgementRequest(AcknowledgementRequest.of(acknowledgementLabel))
                        .build();
                final Target target = ConnectivityModelFactory.newTargetBuilder()
                        .address(getOutboundAddress())
                        .originalAddress(getOutboundAddress())
                        .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                        .headerMapping(TestConstants.HEADER_MAPPING)
                        .issuedAcknowledgementLabel(acknowledgementLabel)
                        .topics(Topic.TWIN_EVENTS)
                        .build();

                final ThingEvent<?> source =
                        ThingDeleted.of(TestConstants.Things.THING_ID, 99L, Instant.now(), dittoHeaders,
                                null);
                final OutboundSignal outboundSignal = OutboundSignalFactory.newOutboundSignal(source, List.of(target));
                final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(Map.of())
                        .withText("payload")
                        .build();
                final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(source);
                final OutboundSignal.Mapped mappedSignal =
                        OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable, externalMessage);
                final OutboundSignal.MultiMapped multiMappedSignal =
                        OutboundSignalFactory.newMultiMappedOutboundSignal(List.of(mappedSignal), getRef());

                publisherCreated(this, publisherActor);
                publisherActor.tell(multiMappedSignal, getRef());

                final Acknowledgements acknowledgements = expectMsgClass(Acknowledgements.class);

                assertThat(acknowledgements)
                        .hasSize(1)
                        .first()
                        .satisfies(ack -> {
                            assertThat(ack.getHttpStatus()).isEqualTo(HttpStatus.OK);
                            assertThat(ack.getLabel().toString()).isEqualTo("please-verify");
                            assertThat(ack.getEntity()).contains(JsonObject.newBuilder()
                                    .set("timestamp", 0)
                                    .set("serializedKeySize", 0)
                                    .set("serializedValueSize", 0)
                                    .set("topic", TARGET_TOPIC)
                                    .set("partition", 5)
                                    .set("offset", 0)
                                    .build());
                        });
            }
        };
    }

    @Test
    public void retriableExceptionBecomesInternalErrorAcknowledgement() {
        testSendFailure(new DisconnectException(), (sender, parent) ->
                assertThat(sender.expectMsgClass(Acknowledgements.class).getHttpStatus())
                        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        );
    }

    @Test
    public void nonRetriableExceptionBecomesClientErrorAcknowledgement() {
        testSendFailure(new InvalidTopicException(), (sender, parent) -> {
            assertThat(sender.expectMsgClass(Acknowledgements.class).getHttpStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);

            // expect failure escalation
            parent.expectMsgClass(ConnectionFailure.class);
        });
    }

    @Override
    protected void publisherCreated(final TestKit kit, final ActorRef publisherActor) {
        kit.expectMsgClass(Duration.ofSeconds(10), Status.Success.class);
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

    private void shouldContainHeader(final List<Header> headers, final String key) {
        final Optional<Header> expectedHeader = headers.stream().filter(header -> header.key().equals(key)).findAny();
        assertThat(expectedHeader).isPresent();
    }

    private void testSendFailure(final RuntimeException exception, final BiConsumer<TestProbe, TestKit> assertions) {
        new TestKit(actorSystem) {{
            // GIVEN
            setUpMocksToFailWith(exception);

            final TestProbe senderProbe = TestProbe.apply("sender", actorSystem);
            final OutboundSignal.MultiMapped multiMapped = OutboundSignalFactory.newMultiMappedOutboundSignal(
                    List.of(getMockOutboundSignalWithAutoAck(exception.getClass().getSimpleName())),
                    senderProbe.ref()
            );
            final ActorRef publisherActor = childActorOf(getPublisherActorProps());
            publisherCreated(this, publisherActor);

            // WHEN
            publisherActor.tell(multiMapped, senderProbe.ref());

            // THEN
            assertions.accept(senderProbe, this);
        }};
    }

}
