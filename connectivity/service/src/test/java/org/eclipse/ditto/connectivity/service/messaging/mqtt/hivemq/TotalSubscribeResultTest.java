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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.SourceSubscribeResult;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.SubscribeResult;
import org.junit.Test;
import org.mockito.Mockito;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link TotalSubscribeResult}.
 */
public final class TotalSubscribeResultTest {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(TotalSubscribeResult.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void ofWithNullSourceSubscribeResultsThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> TotalSubscribeResult.of(null))
                .withMessage("The sourceSubscribeResults must not be null!")
                .withNoCause();
    }

    @Test
    public void totalSubscribeResultsWithSuccessResultsOnlyHasNoFailures() {
        final var underTest = TotalSubscribeResult.of(List.of(
                SourceSubscribeResult.newInstance(Mockito.mock(Source.class), getSuccessfulSubscribeResult()),
                SourceSubscribeResult.newInstance(Mockito.mock(Source.class), getSuccessfulSubscribeResult()),
                SourceSubscribeResult.newInstance(Mockito.mock(Source.class), getSuccessfulSubscribeResult())
        ));

        assertThat(underTest.hasFailures()).isFalse();
    }

    private static SubscribeResult getSuccessfulSubscribeResult() {
        final var result = Mockito.mock(SubscribeResult.class);
        Mockito.when(result.isSuccess()).thenReturn(true);
        return result;
    }

    @Test
    public void gettersOfMixedTotalSubscribeResultReturnExpected() {
        final var sourceSubscribeResults = List.of(
                SourceSubscribeResult.newInstance(Mockito.mock(Source.class), getSuccessfulSubscribeResult()),
                SourceSubscribeResult.newInstance(Mockito.mock(Source.class), getFailedSubscribeResult()),
                SourceSubscribeResult.newInstance(Mockito.mock(Source.class), getSuccessfulSubscribeResult()),
                SourceSubscribeResult.newInstance(Mockito.mock(Source.class), getFailedSubscribeResult()),
                SourceSubscribeResult.newInstance(Mockito.mock(Source.class), getSuccessfulSubscribeResult())
        );
        final var underTest = TotalSubscribeResult.of(sourceSubscribeResults);

        try (final var softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(underTest.hasFailures()).as("has failures").isTrue();
            softly.assertThat(underTest.failedSourceSubscribeResults())
                    .as("failed results")
                    .containsExactly(sourceSubscribeResults.get(1), sourceSubscribeResults.get(3));
            softly.assertThat(underTest.successfulSourceSubscribeResults())
                    .as("successful results")
                    .containsExactly(sourceSubscribeResults.get(0),
                            sourceSubscribeResults.get(2),
                            sourceSubscribeResults.get(4));
        }
    }

    private static SubscribeResult getFailedSubscribeResult() {
        final var result = Mockito.mock(SubscribeResult.class);
        Mockito.when(result.isSuccess()).thenReturn(false);
        return result;
    }

}