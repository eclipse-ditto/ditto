/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.ErrorResponse;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.api.OutboundSignalFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.service.util.ConnectionPubSub;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingResponse;
import org.eclipse.ditto.things.model.signals.events.ThingCreated;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit tests for {@link ResponseDiversionInterceptor}.
 */
@RunWith(MockitoJUnitRunner.class)
public class ResponseDiversionInterceptorTest {

    private static final ConnectionId SOURCE_CONNECTION_ID = ConnectionId.of("source-connection");
    private static final ConnectionId TARGET_CONNECTION_ID = ConnectionId.of("target-connection");
    private static final ThingId THING_ID = ThingId.of("test:thing");
    private static final String CORRELATION_ID = "test-correlation-id";

    @Mock
    private ConnectionPubSub pubSub;

    @Mock
    private Connection connection;

    private ResponseDiversionInterceptor underTest;

    @Before
    public void setUp() {
        when(connection.getId()).thenReturn(SOURCE_CONNECTION_ID);
//        when(pubSub.publishSignalForDiversion(any(), any()))
        underTest = ResponseDiversionInterceptor.of(connection, pubSub);
    }

    @Test
    public void nullConstructorArguments() {
        assertThatNullPointerException()
                .isThrownBy(() -> ResponseDiversionInterceptor.of(null, pubSub))
                .withMessage("The connection must not be null!");

        assertThatNullPointerException()
                .isThrownBy(() -> ResponseDiversionInterceptor.of(connection, null))
                .withMessage("The pubSub must not be null!");
    }

    @Test
    public void divertSuccessfulResponse() {
        final DittoHeaders headers = commonDittoHeaders().build();

        final CommandResponse<?> response = ModifyThingResponse.modified(THING_ID, headers);
        final OutboundSignal outboundSignal = createOutboundSignal(response);

        final boolean diverted = underTest.interceptAndDivert(outboundSignal);

        assertThat(diverted).isTrue();

        final ArgumentCaptor<CommandResponse<?>> responseCaptor = ArgumentCaptor.forClass(CommandResponse.class);
        verify(pubSub).publishResponseForDiversion(responseCaptor.capture(), eq(TARGET_CONNECTION_ID), eq(THING_ID.toString()), eq(null));

        final CommandResponse<?> capturedSignal = responseCaptor.getValue();
        assertThat(capturedSignal.getDittoHeaders())
                .containsEntry(DittoHeaderDefinition.DIVERTED_RESPONSE_FROM_CONNECTION.getKey(), SOURCE_CONNECTION_ID.toString());
    }

    @Test
    public void divertWithResponseTypeFiltering() {
        final DittoHeaders headers = commonDittoHeaders()
                .putHeader(DittoHeaderDefinition.DIVERT_EXPECTED_RESPONSE_TYPES.getKey(), "response")
                .build();

        final CommandResponse<?> response = ModifyThingResponse.modified(THING_ID, headers);
        final OutboundSignal outboundSignal = createOutboundSignal(response);

        final boolean diverted = underTest.interceptAndDivert(outboundSignal);
        final CommandResponse<?> expectedResponse = response.setDittoHeaders(response.getDittoHeaders().toBuilder()
                .putHeader(DittoHeaderDefinition.DIVERTED_RESPONSE_FROM_CONNECTION.getKey(), SOURCE_CONNECTION_ID.toString())
                .build());
        assertThat(diverted).isTrue();
        verify(pubSub).publishResponseForDiversion(eq(expectedResponse), eq(TARGET_CONNECTION_ID), eq(THING_ID.toString()), eq(null));
    }

    @Test
    public void doNotDivertErrorWhenOnlyResponseTypeConfigured() {
        final DittoHeaders headers = commonDittoHeaders()
                .putHeader(DittoHeaderDefinition.DIVERT_EXPECTED_RESPONSE_TYPES.getKey(), "response")
                .build();

        // Create an error response
        final CommandResponse<?> errorResponse = ThingErrorResponse.of(
                THING_ID,
                DittoRuntimeException.fromMessage("Test error", headers, ThingNotAccessibleException.newBuilder(THING_ID))
        );

        final OutboundSignal outboundSignal = createOutboundSignal(errorResponse);

        final boolean diverted = underTest.interceptAndDivert(outboundSignal);

        assertThat(diverted).isFalse();
        verify(pubSub, never()).publishResponseForDiversion(eq(errorResponse), eq(TARGET_CONNECTION_ID), eq(THING_ID), any());
    }

    @Test
    public void divertMultipleResponseTypes() {
        final DittoHeaders headers = commonDittoHeaders()
                .putHeader(DittoHeaderDefinition.DIVERT_EXPECTED_RESPONSE_TYPES.getKey(), "response,error,nack")
                .build();

        final CommandResponse<?> response = ModifyThingResponse.modified(THING_ID, headers);
        final OutboundSignal outboundSignal = createOutboundSignal(response);

        final boolean diverted = underTest.interceptAndDivert(outboundSignal);

        assertThat(diverted).isTrue();
        final CommandResponse<?> expectedResponse = getExpectedResponse(response);
        verify(pubSub).publishResponseForDiversion(eq(expectedResponse), eq(TARGET_CONNECTION_ID), eq(THING_ID.toString()), eq(null));
    }

    @Test
    public void doNotDivertNonCommandResponse() {
        final DittoHeaders headers = commonDittoHeaders()
                .build();

        // Use an event instead of a command response
        final ThingCreated event = ThingCreated.of(
                ThingsModelFactory.newThingBuilder().setId(THING_ID).build(),
                1L,
                null,
                headers,
                null
        );

        final OutboundSignal outboundSignal = createOutboundSignal(event);

        final boolean diverted = underTest.interceptAndDivert(outboundSignal);

        assertThat(diverted).isFalse();
        verify(pubSub, never()).publishResponseForDiversion(any(), eq(TARGET_CONNECTION_ID), eq(THING_ID), any());
    }

    @Test
    public void doNotDivertWithoutDiversionHeader() {
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .correlationId(CORRELATION_ID)
                .build();

        final CommandResponse<?> response = ModifyThingResponse.modified(THING_ID, headers);
        final OutboundSignal outboundSignal = createOutboundSignal(response);

        final boolean diverted = underTest.interceptAndDivert(outboundSignal);

        assertThat(diverted).isFalse();
        verify(pubSub, never()).publishResponseForDiversion(any(CommandResponse.class), any(ConnectionId.class), any(CharSequence.class), any());
    }

    @Test
    public void doNotDivertToSelf() {
        final DittoHeaders headers = commonDittoHeaders()
                .putHeader(DittoHeaderDefinition.DIVERT_RESPONSE_TO_CONNECTION.getKey(), SOURCE_CONNECTION_ID.toString())
                .build();

        final CommandResponse<?> response = ModifyThingResponse.modified(THING_ID, headers);
        final OutboundSignal outboundSignal = createOutboundSignal(response);

        final boolean diverted = underTest.interceptAndDivert(outboundSignal);

        assertThat(diverted).isFalse();
        verify(pubSub, never()).publishResponseForDiversion(any(CommandResponse.class), any(ConnectionId.class), any(CharSequence.class), any());
    }

    @Test
    public void doNotDivertWithInvalidTargetConnectionId() {
        final DittoHeaders headers = commonDittoHeaders()
                .putHeader(DittoHeaderDefinition.DIVERT_RESPONSE_TO_CONNECTION.getKey(), "invalid connection id!")
                .build();

        final CommandResponse<?> response = ModifyThingResponse.modified(THING_ID, headers);
        final OutboundSignal outboundSignal = createOutboundSignal(response);

        final boolean diverted = underTest.interceptAndDivert(outboundSignal);

        assertThat(diverted).isFalse();
        verify(pubSub, never()).publishResponseForDiversion(any(CommandResponse.class), any(ConnectionId.class), any(CharSequence.class), any());
    }

    @Test
    public void nullOutboundSignal() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.interceptAndDivert(null))
                .withMessage("The outboundSignal must not be null!");
    }
    @Test
    public void emptyResponseTypesDefaultsToResponseAndError() {
        final DittoHeaders headers = commonDittoHeaders().build();

        final CommandResponse<?> response = ModifyThingResponse.modified(THING_ID, headers);
        final ErrorResponse<ThingErrorResponse> errorResponse = ThingErrorResponse.of(THING_ID,
                DittoRuntimeException.fromMessage("Test error", headers, ThingNotAccessibleException.newBuilder(THING_ID)));
        final Acknowledgement weak =
                Acknowledgement.of(AcknowledgementLabel.of("test-label"), THING_ID, HttpStatus.BAD_REQUEST, headers, null);


        final boolean diverted = underTest.interceptAndDivert(createOutboundSignal(response));
        final CommandResponse<?> expectedResponse = getExpectedResponse(response);
        assertThat(diverted).isTrue();
        verify(pubSub, times(1)).publishResponseForDiversion(eq(expectedResponse), eq(TARGET_CONNECTION_ID), eq(THING_ID.toString()), eq(null));

        final boolean divertedError = underTest.interceptAndDivert(createOutboundSignal(errorResponse));
        final CommandResponse<?> expectedErrorResponse = getExpectedResponse(errorResponse);
        assertThat(divertedError).isTrue();
        verify(pubSub, times(1)).publishResponseForDiversion(eq(expectedErrorResponse), eq(TARGET_CONNECTION_ID), eq(THING_ID.toString()), eq(null));

        final boolean divertedWeak = underTest.interceptAndDivert(createOutboundSignal(weak));
        assertThat(divertedWeak).isFalse();
        verifyNoMoreInteractions(pubSub);

    }

    private static @NotNull CommandResponse<?> getExpectedResponse(final CommandResponse<?> response) {
        return response.setDittoHeaders(response.getDittoHeaders().toBuilder()
                .putHeader(DittoHeaderDefinition.DIVERTED_RESPONSE_FROM_CONNECTION.getKey(), SOURCE_CONNECTION_ID.toString())
                .build());
    }

    private static @NotNull DittoHeadersBuilder<?,?> commonDittoHeaders() {
        return DittoHeaders.newBuilder()
                .correlationId(CORRELATION_ID)
                .putHeader(DittoHeaderDefinition.DIVERT_RESPONSE_TO_CONNECTION.getKey(), TARGET_CONNECTION_ID.toString())
                .putHeader(DittoHeaderDefinition.ORIGIN.getKey(), SOURCE_CONNECTION_ID.toString());
    }

    private OutboundSignal createOutboundSignal(final Signal<?> signal) {
        final Target target = ConnectivityModelFactory.newTargetBuilder()
                .address("test/address")
                .authorizationContext(AuthorizationContext.empty())
                .topics(Topic.TWIN_EVENTS)
                .build();

        return OutboundSignalFactory.newOutboundSignal(signal, Collections.singletonList(target));
    }
}