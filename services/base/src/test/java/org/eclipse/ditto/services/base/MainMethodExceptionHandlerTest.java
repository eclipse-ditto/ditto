/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.base;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.eclipse.ditto.services.base.MainMethodExceptionHandler.LOG_MESSAGE_PATTERN;
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
                .isThrownBy(() -> underTest.run(null))
                .withMessage("The %s must not be null!", "Runnable to be executed")
                .withNoCause();
    }

    @Test
    public void nothingIsLoggedIfRunnableThrowsNoException() {
        underTest.run(() -> {
            final int illuminati = 23;
            final int answer = 42;
            int i = illuminati;
            i += answer;
        });

        Mockito.verifyZeroInteractions(logger);
    }

    @Test
    public void exceptionIsReThrownAndLoggedAppropriately() {
        final String exceptionMessage = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.";
        final RuntimeException cause = new NullPointerException("Foo must not be null!");
        final RuntimeException exception = new IllegalStateException(exceptionMessage, cause);
        final String expectedLogMessage = MessageFormat.format(LOG_MESSAGE_PATTERN, CLASS_NAME);

        assertThatExceptionOfType(exception.getClass())
                .isThrownBy(() -> underTest.run(() -> {
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
                .isThrownBy(() -> underTest.run(() -> {
                    throw error;
                }))
                .withMessage(exceptionMessage)
                .withNoCause();

        Mockito.verify(logger).error(expectedLogMessage, error);
    }

}