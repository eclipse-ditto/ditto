/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import akka.actor.ActorRef;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

/**
 * Tests {@link InboundMappingProcessor}.
 */
@RunWith(Parameterized.class)
public final class MessageMappingProcessorActorHeaderInteractionTest extends AbstractMessageMappingProcessorActorTest {

    private static final List<Duration> TIMEOUT = List.of(Duration.ZERO, Duration.ofMinutes(1L));
    private static final List<Boolean> RESPONSE_REQUIRED = List.of(false, true);
    private static final List<List<AcknowledgementRequest>> REQUESTED_ACKS =
            List.of(List.of(), List.of(AcknowledgementRequest.of(DittoAcknowledgementLabel.TWIN_PERSISTED)));
    private static final List<Boolean> IS_SUCCESS = List.of(false, true);

    @Parameterized.Parameters(name = "timeout={0} response-required={1} requested-acks={2} is-success={3}")
    public static Collection<Object[]> getParameters() {
        return TIMEOUT.stream().flatMap(timeout ->
                RESPONSE_REQUIRED.stream().flatMap(responseRequired ->
                        REQUESTED_ACKS.stream().flatMap(requestedAcks ->
                                IS_SUCCESS.stream().map(isSuccess ->
                                        new Object[]{timeout, responseRequired, requestedAcks, isSuccess}
                                )
                        )
                )
        ).toList();
    }

    private final Duration timeout;
    private final boolean responseRequired;
    private final List<AcknowledgementRequest> requestedAcks;
    private final boolean isSuccess;

    public MessageMappingProcessorActorHeaderInteractionTest(final Duration timeout, final Boolean responseRequired,
            final List<AcknowledgementRequest> requestedAcks, final Boolean isSuccess) {
        this.timeout = timeout;
        this.responseRequired = responseRequired;
        this.requestedAcks = requestedAcks;
        this.isSuccess = isSuccess;
    }

    @Test
    public void run() {
        new TestKit(actorSystem) {{
            final TestProbe collectorProbe = TestProbe.apply("collector", actorSystem);
            final ActorRef outboundMappingProcessorActor = createOutboundMappingProcessorActor(this);
            final ActorRef inboundMappingProcessorActor =
                    createInboundMappingProcessorActor(this, outboundMappingProcessorActor);
            final ModifyThing modifyThing = getModifyThing();
            final Optional<HttpStatus> expectedStatusCode = getExpectedOutcome();
            final boolean isBadRequest = expectedStatusCode.filter(HttpStatus.BAD_REQUEST::equals).isPresent();
            final boolean settleImmediately = modifyThing.getDittoHeaders().getAcknowledgementRequests().isEmpty();

            inboundMappingProcessorActor.tell(
                    new ExternalMessageWithSender(toExternalMessage(modifyThing), collectorProbe.ref()),
                    ActorRef.noSender()
            );

            // transport-layer settlement based on requested-acks alone
            if (settleImmediately && !isBadRequest) {
                collectorProbe.expectMsg(FiniteDuration.apply(20L, TimeUnit.SECONDS),
                        ResponseCollectorActor.setCount(0));
            } else if (isBadRequest) {
                // bad requests should settle immediately because no command is forwarded
                collectorProbe.expectMsgClass(DittoHeaderInvalidException.class);
                collectorProbe.expectMsg(ResponseCollectorActor.setCount(0));
            } else {
                // no immediate settlement; response collector actor is asked to wait for the response.
                collectorProbe.expectMsg(ResponseCollectorActor.setCount(1));
            }

            if (!isBadRequest) {
                // command forwarded for non-bad requests.
                final ModifyThing forwardedModifyThing = fishForMsg(this, ModifyThing.class);

                // send a response always - MessageMappingProcessorActor should drop it if not wanted.
                final Object response = getModifyThingResponse(forwardedModifyThing);
                reply(response);

                // if an acknowledgement is requested, expect response collector to receive it and trigger settlement.
                if (!settleImmediately) {
                    collectorProbe.expectMsg(response);
                }
            }

            if (expectedStatusCode.isPresent()) {
                // check published response for expected status
                final BaseClientActor.PublishMappedMessage publish =
                        fishForMsg(this, BaseClientActor.PublishMappedMessage.class);
                final HttpStatus publishedStatusCode =
                        ((CommandResponse<?>) publish.getOutboundSignal().getSource()).getHttpStatus();
                assertThat(publishedStatusCode).isEqualTo(expectedStatusCode.get());
            }
        }};
    }

    private ModifyThing getModifyThing() {
        return ModifyThing.of(ThingId.of("thing:id"), Thing.newBuilder().build(), null,
                HEADERS_WITH_REPLY_INFORMATION.toBuilder()
                        .timeout(timeout)
                        .responseRequired(responseRequired)
                        .acknowledgementRequests(requestedAcks)
                        .build());
    }

    private Object getModifyThingResponse(final ModifyThing modifyThing) {
        return (isSuccess
                ? ModifyThingResponse.modified(modifyThing.getEntityId(), modifyThing.getDittoHeaders())
                : ThingNotAccessibleException.newBuilder(modifyThing.getEntityId()).build())
                .setDittoHeaders(modifyThing.getDittoHeaders()); // use setDittoHeaders to lose concrete exception type
    }

    private Optional<HttpStatus> getExpectedOutcome() {
        final Optional<HttpStatus> status;
        final HttpStatus successCode = HttpStatus.NO_CONTENT;
        final HttpStatus errorCode = HttpStatus.NOT_FOUND;
        final HttpStatus badRequest = HttpStatus.BAD_REQUEST;
        if (timeout.isZero()) {
            status = (responseRequired || !requestedAcks.isEmpty()) ? Optional.of(badRequest) : Optional.empty();
        } else {
            if (isSuccess) {
                status = responseRequired ? Optional.of(successCode) : Optional.empty();
            } else {
                status = responseRequired ? Optional.of(errorCode) : Optional.empty();
            }
        }
        return status;
    }

}
