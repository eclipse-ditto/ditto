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
package org.eclipse.ditto.internal.models.signal.correlation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Optional;
import java.util.UUID;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link MatchingValidationResult}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class MatchingValidationResultTest {

    private static final String DETAIL_MESSAGE = "My detail message.";
    private static final String CONNECTION_ID = UUID.randomUUID().toString();

    @Mock private Command<?> command;
    @Mock private CommandResponse<?> commandResponse;

    @Before
    public void before() {
        Mockito.when(commandResponse.getDittoHeaders()).thenReturn(DittoHeaders.empty());
    }

    @Test
    public void assertImmutabilityForSuccess() {
        final var success = MatchingValidationResult.success();

        assertInstancesOf(success.getClass(), areImmutable());
    }

    @Test
    public void getSuccessInstanceReturnsNotNull() {
        assertThat(MatchingValidationResult.success()).isNotNull();
    }

    @Test
    public void successIsSuccess() {
        final var underTest = MatchingValidationResult.success();

        assertThat(underTest.isSuccess()).isTrue();
    }

    @Test
    public void getSuccessAsFailureThrowsException() {
        final var success = MatchingValidationResult.success();

        assertThatIllegalStateException()
                .isThrownBy(success::asFailureOrThrow)
                .withMessage("This result is a success and thus cannot be returned as failure.")
                .withNoCause();
    }

    @Test
    public void assertImmutabilityForFailure() {
        assertInstancesOf(MatchingValidationResult.Failure.class,
                areImmutable(),
                provided(Command.class, CommandResponse.class, EntityId.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEqualsForFailure() {
        final var failureClass = MatchingValidationResult.Failure.class;
        final var red = MatchingValidationResult.failure(command, commandResponse, DETAIL_MESSAGE);
        final var black = MatchingValidationResult.failure(command, commandResponse, "Ut ea dolor placeat.");

        EqualsVerifier.forClass(failureClass)
                .usingGetClass()
                .withIgnoredFields("connectionId")
                .withPrefabValues(failureClass, red, black)
                .verify();
    }

    @Test
    public void getFailureInstanceReturnsNotNull() {
        assertThat(MatchingValidationResult.failure(command, commandResponse, DETAIL_MESSAGE)).isNotNull();
    }

    @Test
    public void getFailureInstanceWithNullCommandThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> MatchingValidationResult.failure(null, commandResponse, DETAIL_MESSAGE))
                .withMessage("The command must not be null!")
                .withNoCause();
    }

    @Test
    public void getFailureInstanceWithNullCommandResponseThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> MatchingValidationResult.failure(command, null, DETAIL_MESSAGE))
                .withMessage("The commandResponse must not be null!")
                .withNoCause();
    }

    @Test
    public void getFailureInstanceWithNullDetailMessageThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> MatchingValidationResult.failure(command, commandResponse, null))
                .withMessage("The detailMessage must not be null!")
                .withNoCause();
    }

    @Test
    public void getFailureInstanceWithEmptyDetailMessageThrowsException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MatchingValidationResult.failure(command, commandResponse, ""))
                .withMessage("The detailMessage must not be blank.")
                .withNoCause();
    }

    @Test
    public void getFailureInstanceWithBlankDetailMessageThrowsException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MatchingValidationResult.failure(command, commandResponse, "   "))
                .withMessage("The detailMessage must not be blank.")
                .withNoCause();
    }

    @Test
    public void failureIsNotSuccess() {
        final var underTest = MatchingValidationResult.failure(command, commandResponse, DETAIL_MESSAGE);

        assertThat(underTest.isSuccess()).isFalse();
    }

    @Test
    public void getFailureAsFailureReturnsSameInstance() {
        final var underTest = MatchingValidationResult.failure(command, commandResponse, DETAIL_MESSAGE);

        assertThat(underTest.asFailureOrThrow()).isSameAs(underTest);
    }

    @Test
    public void getDetailMessageReturnsDetailMessage() {
        final var underTest = MatchingValidationResult.failure(command, commandResponse, DETAIL_MESSAGE);

        assertThat(underTest.getDetailMessage()).isEqualTo(DETAIL_MESSAGE);
    }

    @Test
    public void getCommandFromFailureReturnsExpected() {
        final var underTest = MatchingValidationResult.failure(command, commandResponse, DETAIL_MESSAGE);

        assertThat(underTest.getCommand()).isEqualTo(command);
    }

    @Test
    public void getCommandResponseFromFailureReturnsExpected() {
        final var underTest = MatchingValidationResult.failure(command, commandResponse, DETAIL_MESSAGE);

        assertThat(underTest.getCommandResponse()).isEqualTo(commandResponse);
    }

    @Test
    public void getConnectionIdFromFailureWithCommandResponseWithEmptyHeadersReturnsEmptyOptional() {
        final var underTest = MatchingValidationResult.failure(command, commandResponse, DETAIL_MESSAGE);

        assertThat(underTest.getConnectionId()).isEmpty();
    }

    @Test
    public void getConnectionIdFromFailureWithCommandResponseWithValidConnectionIdInHeadersReturnsExpected() {
        Mockito.when(commandResponse.getDittoHeaders())
                .thenReturn(DittoHeaders.newBuilder()
                        .putHeader(DittoHeaderDefinition.CONNECTION_ID.getKey(), CONNECTION_ID)
                        .build());
        final var underTest = MatchingValidationResult.failure(command, commandResponse, DETAIL_MESSAGE);

        assertThat(underTest.getConnectionId()).isEqualTo(Optional.of(CONNECTION_ID));
    }

}