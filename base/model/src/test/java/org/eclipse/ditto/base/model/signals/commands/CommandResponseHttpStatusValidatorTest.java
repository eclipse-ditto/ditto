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
package org.eclipse.ditto.base.model.signals.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.junit.Test;

/**
 * Unit test for {@link CommandResponseHttpStatusValidator}.
 */
public final class CommandResponseHttpStatusValidatorTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(CommandResponseHttpStatusValidator.class, areImmutable());
    }

    @Test
    public void validateHttpStatusWithNullHttpStatusThrowsNullPointerException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> CommandResponseHttpStatusValidator.validateHttpStatus(null,
                        Collections.singleton(HttpStatus.OK),
                        AbstractCommandResponse.class))
                .withMessage("The httpStatus must not be null!")
                .withNoCause();
    }

    @Test
    public void validateHttpStatusWithNullAllowedHttpStatusesThrowsNullPointerException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> CommandResponseHttpStatusValidator.validateHttpStatus(HttpStatus.OK,
                        null,
                        AbstractCommandResponse.class))
                .withMessage("The allowedHttpStatuses must not be null!")
                .withNoCause();
    }

    @Test
    public void validateHttpStatusWithEmptyAllowedHttpStatusesThrowsIllegalArgumentException() {
        Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> CommandResponseHttpStatusValidator.validateHttpStatus(HttpStatus.OK,
                        Collections.emptySet(),
                        AbstractCommandResponse.class))
                .withMessage("The argument 'allowedHttpStatuses' must not be empty!")
                .withNoCause();
    }

    @Test
    public void validateHttpStatusWithNullCommandResponseTypeThrowsNullPointerException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> CommandResponseHttpStatusValidator.validateHttpStatus(HttpStatus.OK,
                        Collections.singleton(HttpStatus.OK),
                        null))
                .withMessage("The commandResponseType must not be null!")
                .withNoCause();
    }

    @Test
    public void validateHttpStatusWithAllowedHttpStatusReturnsExpected() {
        final HttpStatus httpStatus = HttpStatus.NO_CONTENT;

        final HttpStatus validHttpStatus = CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                Arrays.asList(HttpStatus.OK, HttpStatus.CREATED, HttpStatus.NO_CONTENT),
                AbstractCommandResponse.class);

        assertThat(validHttpStatus).isEqualTo(httpStatus);
    }

    @Test
    public void validateHttpStatusWithDisallowedHttpStatusThrowsIllegalArgumentException() {
        final HttpStatus httpStatus = HttpStatus.OK;
        final List<HttpStatus> allowedHttpStatuses = Arrays.asList(HttpStatus.CREATED, HttpStatus.NO_CONTENT);
        final Class<AbstractCommandResponse> commandResponseType = AbstractCommandResponse.class;

        Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        allowedHttpStatuses,
                        commandResponseType))
                .withMessage("%s is invalid. %s only allows %s.",
                        httpStatus,
                        commandResponseType.getSimpleName(),
                        allowedHttpStatuses)
                .withNoCause();
    }

}