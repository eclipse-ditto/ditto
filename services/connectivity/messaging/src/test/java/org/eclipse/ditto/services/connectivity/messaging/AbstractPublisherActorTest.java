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
package org.eclipse.ditto.services.connectivity.messaging;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.OutboundSignalFactory;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThingResponse;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

public abstract class AbstractPublisherActorTest {

    protected static final Config CONFIG = ConfigFactory.load("test");
    protected ActorSystem actorSystem;

    @Rule
    public TestName name = new TestName();

    @Before
    public void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", CONFIG);
    }

    @After
    public void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                    false);
        }
    }

    @Test
    public void testPublishMessage() throws Exception {

        new TestKit(actorSystem) {{

            final TestProbe probe = new TestProbe(actorSystem);
            setupMocks(probe);
            final OutboundSignal.Mapped mappedOutboundSignal = getMockOutboundSignal();

            final Props props = getPublisherActorProps();
            final ActorRef publisherActor = childActorOf(props);

            publisherCreated(this, publisherActor);

            publisherActor.tell(mappedOutboundSignal, getRef());

            verifyPublishedMessage();
        }};

    }

    @Test
    public void testPublishResponseToReplyTarget() throws Exception {

        new TestKit(actorSystem) {{

            final TestProbe probe = new TestProbe(actorSystem);
            setupMocks(probe);
            final OutboundSignal.Mapped mappedOutboundSignal = getResponseWithReplyTarget();

            final Props props = getPublisherActorProps();
            final ActorRef publisherActor = childActorOf(props);

            publisherCreated(this, publisherActor);

            publisherActor.tell(mappedOutboundSignal, getRef());

            verifyPublishedMessageToReplyTarget();
        }};

    }

    protected Target createTestTarget() {
        return ConnectivityModelFactory.newTargetBuilder()
                .address(getOutboundAddress())
                .originalAddress(getOutboundAddress())
                .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                .headerMapping(TestConstants.HEADER_MAPPING)
                .topics(Topic.TWIN_EVENTS)
                .build();
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

    protected OutboundSignal.Mapped getMockOutboundSignal(final String... extraHeaders) {
        return getMockOutboundSignal(decorateTarget(createTestTarget()), extraHeaders);
    }

    protected OutboundSignal.Mapped getMockOutboundSignal(final Target target,
            final String... extraHeaders) {

        final DittoHeadersBuilder headersBuilder = DittoHeaders.newBuilder().putHeader("device_id", "ditto:thing");
        for (int i = 0; 2 * i + 1 < extraHeaders.length; ++i) {
            headersBuilder.putHeader(extraHeaders[2 * i], extraHeaders[2 * i + 1]);
        }
        final DittoHeaders dittoHeaders = headersBuilder.build();

        final ThingEvent source = ThingDeleted.of(TestConstants.Things.THING_ID, 99L, dittoHeaders);
        final OutboundSignal outboundSignal =
                OutboundSignalFactory.newOutboundSignal(source, Collections.singletonList(target));
        final ExternalMessage externalMessage =
                ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap()).withText("payload").build();
        final Adaptable adaptable =
                DittoProtocolAdapter.newInstance().toAdaptable(source, TopicPath.Channel.TWIN);
        return OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable, externalMessage);
    }

    private OutboundSignal.Mapped getResponseWithReplyTarget() {
        final DittoHeaders externalHeaders = DittoHeaders.newBuilder()
                .putHeader("original-header", "original-header-value")
                .build();
        final DittoHeaders internalHeaders = externalHeaders.toBuilder()
                .replyTarget(0)
                .build();
        final ThingCommandResponse source = DeleteThingResponse.of(ThingId.of("thing", "id"), internalHeaders);
        final ExternalMessage externalMessage =
                ExternalMessageFactory.newExternalMessageBuilder(externalHeaders)
                        .withText("payload")
                        .withInternalHeaders(internalHeaders)
                        .asResponse(true)
                        .build();
        final OutboundSignal outboundSignal = OutboundSignalFactory.newOutboundSignal(source, Collections.emptyList());
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(source, TopicPath.Channel.TWIN);
        return OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable, externalMessage);
    }

}
