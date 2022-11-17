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
package org.eclipse.ditto.connectivity.service.messaging;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.api.OutboundSignalFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.HeaderMapping;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThingResponse;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

public abstract class AbstractPublisherActorTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    protected static final Config CONFIG = ConfigFactory.load("test");
    protected static final ThingId THING_ID = ThingId.of("thing", "id");
    protected static final String DEVICE_ID = "ditto:thing";

    @Rule
    public final ActorSystemResource actorSystemResource = ActorSystemResource.newInstance(
            getClass().getSimpleName(),
            CONFIG
    );

    protected ActorSystem actorSystem;
    protected TestProbe proxyActorTestProbe;
    protected ActorRef proxyActor;

    @Before
    public void setUp() {
        actorSystem = actorSystemResource.getActorSystem();
        proxyActorTestProbe = TestProbe.apply("proxyActor", actorSystem);
        proxyActor = proxyActorTestProbe.ref();
    }

    @Test
    public void testPublishMessage() throws Exception {
        new TestKit(actorSystem) {{

            final TestProbe probe = new TestProbe(actorSystem);
            setupMocks(probe);
            final OutboundSignal.MultiMapped multiMapped =
                    OutboundSignalFactory.newMultiMappedOutboundSignal(List.of(getMockOutboundSignal()), getRef());

            final Props props = getPublisherActorProps();
            final ActorRef publisherActor = childActorOf(props);

            publisherCreated(this, publisherActor);

            publisherActor.tell(multiMapped, getRef());

            verifyPublishedMessage();
        }};

    }

    @Test
    public void testAutoAck() throws Exception {
        setupMocks(actorSystemResource.newTestProbe());
        final var sender = actorSystemResource.newTestKit();
        final var multiMapped = OutboundSignalFactory.newMultiMappedOutboundSignal(
                List.of(
                        getMockOutboundSignalWithAutoAck("please-verify",
                                DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(),
                                sender.getRef().path().toSerializationFormat())
                ),
                sender.getRef()
        );

        final var publisherActor = sender.childActorOf(getPublisherActorProps());

        publisherCreated(sender, publisherActor);

        verifyAcknowledgements(() -> {
            publisherActor.tell(multiMapped, sender.getRef());
            return sender.expectMsgClass(Acknowledgements.class);
        });
    }

    @Test
    public void testPublishResponseToReplyTarget() throws Exception {
        new TestKit(actorSystem) {{

            final TestProbe probe = new TestProbe(actorSystem);
            setupMocks(probe);
            final OutboundSignal.MultiMapped mappedOutboundSignal =
                    OutboundSignalFactory.newMultiMappedOutboundSignal(List.of(getResponseWithReplyTarget()), getRef());

            final Props props = getPublisherActorProps();
            final ActorRef publisherActor = childActorOf(props);

            publisherCreated(this, publisherActor);

            publisherActor.tell(mappedOutboundSignal, getRef());

            verifyPublishedMessageToReplyTarget();
        }};

    }

    protected Target createTestTarget(final CharSequence... acks) {
        return ConnectivityModelFactory.newTargetBuilder()
                .address(getOutboundAddress())
                .originalAddress(getOutboundAddress())
                .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                .headerMapping(getHeaderMapping())
                .issuedAcknowledgementLabel(acks.length != 1 ? null : AcknowledgementLabel.of(acks[0]))
                .topics(Topic.TWIN_EVENTS)
                .build();
    }

    protected HeaderMapping getHeaderMapping() {
        return TestConstants.HEADER_MAPPING;
    }

    protected abstract String getOutboundAddress();

    protected abstract void setupMocks(final TestProbe probe) throws Exception;

    protected abstract Props getPublisherActorProps();

    protected void publisherCreated(final TestKit kit, final ActorRef publisherActor) {
        // do nothing by default
    }

    protected abstract Target decorateTarget(Target target);

    protected abstract void verifyPublishedMessage() throws Exception;

    protected abstract void verifyPublishedMessageToReplyTarget() throws Exception;

    protected abstract void verifyAcknowledgements(final Supplier<Acknowledgements> ackSupplier) throws Exception;

    protected OutboundSignal.Mapped getMockOutboundSignal(final String... extraHeaders) {
        return getMockOutboundSignal(decorateTarget(createTestTarget()), extraHeaders);
    }

    protected OutboundSignal.Mapped getMockOutboundSignalWithAutoAck(final CharSequence ack,
            final String... extraHeaders) {
        final String[] extra = Stream.concat(Arrays.stream(extraHeaders), Stream.of("requested-acks",
                JsonArray.of(JsonValue.of(ack.toString())).toString()))
                .toArray(String[]::new);
        return getMockOutboundSignal(decorateTarget(createTestTarget(ack)), extra);
    }

    protected OutboundSignal.Mapped getMockOutboundSignal(final Target target,
            final String... extraHeaders) {

        final DittoHeadersBuilder<?, ?> headersBuilder = DittoHeaders.newBuilder()
                .correlationId(TestConstants.CORRELATION_ID)
                .putHeader("device_id", DEVICE_ID);
        for (int i = 0; 2 * i + 1 < extraHeaders.length; ++i) {
            headersBuilder.putHeader(extraHeaders[2 * i], extraHeaders[2 * i + 1]);
        }
        final DittoHeaders dittoHeaders = headersBuilder.build();

        final Signal<?> source = ThingDeleted.of(TestConstants.Things.THING_ID, 99L, Instant.now(), dittoHeaders,
                null);
        final OutboundSignal outboundSignal =
                OutboundSignalFactory.newOutboundSignal(source, Collections.singletonList(target));
        final ExternalMessage externalMessage =
                ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap()).withText("payload").build();
        final Adaptable adaptable =
                DittoProtocolAdapter.newInstance().toAdaptable(source);
        return OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable, externalMessage);
    }

    private OutboundSignal.Mapped getResponseWithReplyTarget() {
        final DittoHeaders externalHeaders = DittoHeaders.newBuilder()
                .correlationId(TestConstants.CORRELATION_ID)
                .putHeader("original-header", "original-header-value")
                .build();
        final DittoHeaders internalHeaders = externalHeaders.toBuilder()
                .replyTarget(0)
                .build();
        final ThingCommandResponse<?> source = DeleteThingResponse.of(THING_ID, internalHeaders);
        final ExternalMessage externalMessage =
                ExternalMessageFactory.newExternalMessageBuilder(externalHeaders)
                        .withAdditionalHeaders(DittoHeaderDefinition.CORRELATION_ID.getKey(),
                                TestConstants.CORRELATION_ID)
                        .withAdditionalHeaders(ExternalMessage.REPLY_TO_HEADER, "replies")
                        .withAdditionalHeaders(ExternalMessage.CONTENT_TYPE_HEADER, "text/plain")
                        .withText("payload")
                        .withInternalHeaders(internalHeaders)
                        .asResponse(true)
                        .build();
        final OutboundSignal outboundSignal = OutboundSignalFactory.newOutboundSignal(source, Collections.emptyList());
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(source);
        return OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable, externalMessage);
    }

}
