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
package org.eclipse.ditto.gateway.service.endpoints.actors;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributeResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import akka.http.javadsl.model.StatusCodes;

/**
 * Test the interaction between timeout, response-required and requested-acks for {@link HttpRequestActor}.
 */
@RunWith(Parameterized.class)
public final class HttpRequestActorHeaderInteractionTest extends AbstractHttpRequestActorTest {

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
        ).collect(Collectors.toList());
    }

    private final Duration timeout;
    private final boolean responseRequired;
    private final List<AcknowledgementRequest> requestedAcks;
    private final boolean isSuccess;

    public HttpRequestActorHeaderInteractionTest(final Duration timeout, final Boolean responseRequired,
            final List<AcknowledgementRequest> requestedAcks, final Boolean isSuccess) {
        this.timeout = timeout;
        this.responseRequired = responseRequired;
        this.requestedAcks = requestedAcks;
        this.isSuccess = isSuccess;
    }

    @Test
    public void run() throws Exception {
        final var thingId = ThingId.generateRandom();
        final var attributeName = "foo";
        final var attributePointer = JsonPointer.of(attributeName);

        final var dittoHeaders = setHeadersByParameter(createAuthorizedHeaders());
        final var probeResponse = getProbeResponse(thingId, attributePointer, dittoHeaders);

        final var expectedHttpStatusCode = StatusCodes.get(getExpectedHttpStatus().getCode());

        testThingModifyCommand(thingId,
                attributePointer,
                dittoHeaders,
                dittoHeaders,
                probeResponse,
                expectedHttpStatusCode,
                null);
    }

    private DittoHeaders setHeadersByParameter(final DittoHeaders dittoHeaders) {
        return dittoHeaders.toBuilder()
                .timeout(timeout)
                .responseRequired(responseRequired)
                .acknowledgementRequests(requestedAcks)
                .build();
    }

    private Object getProbeResponse(final ThingId thingId, final JsonPointer attributePointer,
            final DittoHeaders dittoHeaders) {
        return isSuccess
                ? ModifyAttributeResponse.modified(thingId, attributePointer, dittoHeaders)
                : ThingNotAccessibleException.newBuilder(thingId).dittoHeaders(dittoHeaders).build();
    }

    private HttpStatus getExpectedHttpStatus() {
        final HttpStatus status;
        final boolean isAwaiting = responseRequired || !requestedAcks.isEmpty();
        final HttpStatus successCode = HttpStatus.NO_CONTENT;
        final HttpStatus errorCode = HttpStatus.NOT_FOUND;
        if (timeout.isZero()) {
            status = isAwaiting ? HttpStatus.BAD_REQUEST : HttpStatus.ACCEPTED;
        } else {
            if (isSuccess) {
                status = responseRequired ? successCode : HttpStatus.ACCEPTED;
            } else {
                status = isAwaiting ? errorCode : HttpStatus.ACCEPTED;
            }
        }
        return status;
    }
}
