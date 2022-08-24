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
package org.eclipse.ditto.base.service;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.eclipse.ditto.base.service.MainMethodExceptionHandler.LOG_MESSAGE_PATTERN;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.text.MessageFormat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;

import akka.actor.ActorSystem;

/**
 * Unit test for {@link MainMethodExceptionHandler}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class MainMethodExceptionHandlerTest {

    private static final String CLASS_NAME = "com.example.FooService";

    @Mock
    private Logger logger;

    private MainMethodExceptionHandler underTest;

    @Before
    public void setUp() {
        Mockito.when(logger.getName()).thenReturn(CLASS_NAME);
        underTest = MainMethodExceptionHandler.getInstance(logger);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(MainMethodExceptionHandler.class, areImmutable(), provided(Logger.class).isAlsoImmutable());
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToCreateInstanceWithNullLogger() {
        assertThatNullPointerException()
                .isThrownBy(() -> MainMethodExceptionHandler.getInstance(null))
                .withMessage("The %s must not be null!", "logger")
                .withNoCause();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToRunNullRunnable() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.call(null))
                .withMessage("The %s must not be null!", "Runnable to be executed")
                .withNoCause();
    }

    @Test
    public void nothingIsLoggedIfRunnableThrowsNoException() {
        underTest.call(() -> {
            final int illuminati = 23;
            final int answer = 42;
            int i = illuminati;
            i += answer;
            return ActorSystem.create();
        });

        Mockito.verifyNoInteractions(logger);
    }

    @Test
    public void exceptionIsReThrownAndLoggedAppropriately() {
        final String exceptionMessage = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.";
        final RuntimeException cause = new NullPointerException("Foo must not be null!");
        final RuntimeException exception = new IllegalStateException(exceptionMessage, cause);
        final String expectedLogMessage = MessageFormat.format(LOG_MESSAGE_PATTERN, CLASS_NAME);

        assertThatExceptionOfType(exception.getClass())
                .isThrownBy(() -> underTest.call(() -> {
                    throw exception;
                }))
                .withMessage(exceptionMessage)
                .withCause(cause);

        Mockito.verify(logger).error(expectedLogMessage, exception);
    }

    @Test
    public void errorIsReThrownAndLoggedAppropriately() {
        final String exceptionMessage = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.";
        final Error error = new OutOfMemoryError(exceptionMessage);
        final String expectedLogMessage = MessageFormat.format(LOG_MESSAGE_PATTERN, CLASS_NAME);

        assertThatExceptionOfType(error.getClass())
                .isThrownBy(() -> underTest.call(() -> {
                    throw error;
                }))
                .withMessage(exceptionMessage)
                .withNoCause();

        Mockito.verify(logger).error(expectedLogMessage, error);
    }

}
