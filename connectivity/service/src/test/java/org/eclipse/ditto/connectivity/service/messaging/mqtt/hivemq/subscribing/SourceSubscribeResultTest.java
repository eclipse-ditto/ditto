/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.connectivity.model.Source;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SourceSubscribeResult}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class SourceSubscribeResultTest {

    @Mock private Source connectionSource;
    @Mock private SubscribeResult subscribeResult;

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SourceSubscribeResult.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void newInstanceWithNullConnectionSourceThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> SourceSubscribeResult.newInstance(null, subscribeResult))
                .withMessage("The connectionSource must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceWithNullSubscribeResultThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> SourceSubscribeResult.newInstance(connectionSource, null))
                .withMessage("The subscribeResult must not be null!")
                .withNoCause();
    }

    @Test
    public void getConnectionSourceReturnsExpected() {
        final var underTest = SourceSubscribeResult.newInstance(connectionSource, subscribeResult);

        assertThat(underTest.getConnectionSource()).isEqualTo(connectionSource);
    }

    @Test
    public void isSuccessIsDelegatedToWrappedSubscribeResult() {
        final var underTest = SourceSubscribeResult.newInstance(connectionSource, subscribeResult);

        underTest.isSuccess();

        Mockito.verify(subscribeResult).isSuccess();
    }

    @Test
    public void isFailureIsNotDelegatedToWrappedSubscribeResult() {
        final var underTest = SourceSubscribeResult.newInstance(connectionSource, subscribeResult);

        underTest.isFailure();

        Mockito.verify(subscribeResult, Mockito.never()).isFailure();
    }

    @Test
    public void getMqttPublishSourceOrThrowIsDelegatedToWrappedSubscribeResult() {
        final var underTest = SourceSubscribeResult.newInstance(connectionSource, subscribeResult);

        underTest.getMqttPublishSourceOrThrow();

        Mockito.verify(subscribeResult).getMqttPublishSourceOrThrow();
    }

    @Test
    public void getMqttErrorOrThrowIsDelegatedToWrappedSubscribeResult() {
        final var underTest = SourceSubscribeResult.newInstance(connectionSource, subscribeResult);

        underTest.getErrorOrThrow();

        Mockito.verify(subscribeResult).getErrorOrThrow();
    }

}