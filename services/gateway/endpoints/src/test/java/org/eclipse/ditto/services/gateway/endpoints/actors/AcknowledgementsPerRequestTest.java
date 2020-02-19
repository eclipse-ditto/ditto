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
package org.eclipse.ditto.services.gateway.endpoints.actors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.acks.Acknowledgement;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 * Unit test for {@link AcknowledgementsPerRequest}.
 */
public final class AcknowledgementsPerRequestTest {

    @Rule
    public final TestName testName = new TestName();

    @Test
    public void tryToAddNullRequestedAcknowledgementLabel() {
        final AcknowledgementsPerRequest underTest = AcknowledgementsPerRequest.getInstance();

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.addRequestedAcknowledgementLabel(null))
                .withMessage("The requestedAckLabel must not be null!")
                .withNoCause();
    }

    @Test
    public void emptyInstanceHasNoMissingAcknowledgementLabels() {
        final AcknowledgementsPerRequest underTest = AcknowledgementsPerRequest.getInstance();

        assertThat(underTest.getMissingAcknowledgementLabels()).isEmpty();
    }

    @Test
    public void getMissingAcknowledgementLabelsReturnsExpected() {
        final AcknowledgementsPerRequest underTest = AcknowledgementsPerRequest.getInstance();
        final List<Acknowledgement> successfulAcknowledgements = createAcknowledgements(2, HttpStatusCode.NO_CONTENT);
        final List<Acknowledgement> failedAcknowledgements = createAcknowledgements(2, HttpStatusCode.FORBIDDEN);
        successfulAcknowledgements.forEach(ack -> underTest.addRequestedAcknowledgementLabel(ack.getLabel()));
        failedAcknowledgements.forEach(ack -> underTest.addRequestedAcknowledgementLabel(ack.getLabel()));
        failedAcknowledgements.forEach(underTest::addReceivedAcknowledgment);
        final List<AcknowledgementLabel> expected = successfulAcknowledgements.stream()
                .map(Acknowledgement::getLabel)
                .collect(Collectors.toList());

        assertThat(underTest.getMissingAcknowledgementLabels()).containsExactlyElementsOf(expected);
    }

    @Test
    public void tryToAddNullRequestedAcknowledgementLabels() {
        final AcknowledgementsPerRequest underTest = AcknowledgementsPerRequest.getInstance();

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.addRequestedAcknowledgementLabels(null))
                .withMessage("The requestedAckLabels must not be null!")
                .withNoCause();
    }

    @Test
    public void emptyInstanceReceivedAllRequestedAcknowledgements() {
        final AcknowledgementsPerRequest underTest = AcknowledgementsPerRequest.getInstance();

        assertThat(underTest.receivedAllRequestedAcknowledgements()).isTrue();
    }

    @Test
    public void didNotReceiveAllRequestedAcknowledgements() {
        final AcknowledgementsPerRequest underTest = AcknowledgementsPerRequest.getInstance();
        final List<Acknowledgement> acknowledgements = createAcknowledgements(3, HttpStatusCode.NO_CONTENT);
        acknowledgements.addAll(createAcknowledgements(2, HttpStatusCode.BAD_REQUEST));
        acknowledgements.forEach(ack -> underTest.addRequestedAcknowledgementLabel(ack.getLabel()));
        underTest.addReceivedAcknowledgment(acknowledgements.get(1));

        assertThat(underTest.receivedAllRequestedAcknowledgements()).isFalse();
    }

    @Test
    public void receivedAllRequestedAcknowledgements() {
        final AcknowledgementsPerRequest underTest = AcknowledgementsPerRequest.getInstance();
        final List<Acknowledgement> acknowledgements = createAcknowledgements(3, HttpStatusCode.NO_CONTENT);
        acknowledgements.addAll(createAcknowledgements(2, HttpStatusCode.BAD_REQUEST));
        acknowledgements.forEach(ack -> underTest.addRequestedAcknowledgementLabel(ack.getLabel()));
        acknowledgements.forEach(underTest::addReceivedAcknowledgment);

        assertThat(underTest.receivedAllRequestedAcknowledgements()).isTrue();
    }

    @Test
    public void getMissingAcknowledgementLabelsFromEmptyInstance() {
        final AcknowledgementsPerRequest underTest = AcknowledgementsPerRequest.getInstance();

        assertThat(underTest.getMissingAcknowledgementLabels()).isEmpty();
    }

    @Test
    public void onlyRegardFirstReceivedAcknowledgementForSameLabel() {
        final DittoAcknowledgementLabel ackLabel = DittoAcknowledgementLabel.PERSISTED;
        final ThingId thingId = ThingId.generateRandom();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();
        final Acknowledgement failedAcknowledgement =
                Acknowledgement.of(ackLabel, thingId, HttpStatusCode.UNAUTHORIZED, dittoHeaders);
        final Acknowledgement successfulAcknowledgement =
                Acknowledgement.of(ackLabel, thingId, HttpStatusCode.NO_CONTENT, dittoHeaders);
        final AcknowledgementsPerRequest underTest = AcknowledgementsPerRequest.getInstance();
        underTest.addRequestedAcknowledgementLabel(ackLabel);

        underTest.addReceivedAcknowledgment(failedAcknowledgement);
        underTest.addReceivedAcknowledgment(successfulAcknowledgement);

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(underTest.getFailedAcknowledgements())
                    .as("one failed ACK")
                    .containsOnly(failedAcknowledgement);
            softly.assertThat(underTest.getSuccessfulAcknowledgements())
                    .as("no successful ACK")
                    .isEmpty();
        }
    }

    @Test
    public void tryToAddNullReceivedAcknowledgement() {
        final AcknowledgementsPerRequest underTest = AcknowledgementsPerRequest.getInstance();

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.addReceivedAcknowledgment(null))
                .withMessage("The acknowledgement must not be null!")
                .withNoCause();
    }

    @Test
    public void addFailedReceivedAcknowledgement() {
        final AcknowledgementsPerRequest underTest = AcknowledgementsPerRequest.getInstance();
        final Acknowledgement failedAcknowledgement = createAcknowledgement(HttpStatusCode.NOT_FOUND);
        underTest.addRequestedAcknowledgementLabel(failedAcknowledgement.getLabel());

        underTest.addReceivedAcknowledgment(failedAcknowledgement);

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(underTest.getMissingAcknowledgementLabels())
                    .as("no missing requested ACK labels")
                    .isEmpty();
            softly.assertThat(underTest.getFailedAcknowledgements())
                    .as("expected failed ACKs")
                    .containsOnly(failedAcknowledgement);
            softly.assertThat(underTest.isSuccessful())
                    .as("is not successful")
                    .isFalse();
        }
    }

    @Test
    public void addSuccessfulReceivedAcknowledgement() {
        final AcknowledgementsPerRequest underTest = AcknowledgementsPerRequest.getInstance();
        final Acknowledgement successfulAcknowledgement = createAcknowledgement(HttpStatusCode.OK);
        underTest.addRequestedAcknowledgementLabel(successfulAcknowledgement.getLabel());

        underTest.addReceivedAcknowledgment(successfulAcknowledgement);

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(underTest.getMissingAcknowledgementLabels())
                    .as("no missing requested ACK labels")
                    .isEmpty();
            softly.assertThat(underTest.getFailedAcknowledgements())
                    .as("no failed ACKs")
                    .isEmpty();
            softly.assertThat(underTest.isSuccessful())
                    .as("is successful")
                    .isTrue();
        }
    }

    @Test
    public void unknownReceivedAcknowledgementsAreIgnored() {
        final AcknowledgementsPerRequest underTest = AcknowledgementsPerRequest.getInstance();
        final DittoAcknowledgementLabel requestedAckLabel = DittoAcknowledgementLabel.PERSISTED;
        underTest.addRequestedAcknowledgementLabel(requestedAckLabel);
        final List<Acknowledgement> acknowledgements = createAcknowledgements(1, HttpStatusCode.NO_CONTENT);
        acknowledgements.addAll(createAcknowledgements(1, HttpStatusCode.BAD_REQUEST));
        acknowledgements.forEach(underTest::addReceivedAcknowledgment);

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(underTest.getSuccessfulAcknowledgements())
                    .as("no successful ACKs")
                    .isEmpty();
            softly.assertThat(underTest.getFailedAcknowledgements())
                    .as("no failed ACKs")
                    .isEmpty();
        }
    }

    @Test
    public void successfulAcknowledgementsAreInExpectedOrder() {
        final AcknowledgementsPerRequest underTest = AcknowledgementsPerRequest.getInstance();
        final List<Acknowledgement> successfulAcknowledgements = createAcknowledgements(3, HttpStatusCode.OK);
        successfulAcknowledgements.forEach(ack -> underTest.addRequestedAcknowledgementLabel(ack.getLabel()));
        successfulAcknowledgements.forEach(underTest::addReceivedAcknowledgment);

        assertThat(underTest.getSuccessfulAcknowledgements()).containsExactlyElementsOf(successfulAcknowledgements);
    }

    @Test
    public void failedAcknowledgementsAreInExpectedOrder() {
        final AcknowledgementsPerRequest underTest = AcknowledgementsPerRequest.getInstance();
        final List<Acknowledgement> failedAcknowledgements = createAcknowledgements(3, HttpStatusCode.NOT_FOUND);
        failedAcknowledgements.forEach(ack -> underTest.addRequestedAcknowledgementLabel(ack.getLabel()));
        failedAcknowledgements.forEach(underTest::addReceivedAcknowledgment);

        assertThat(underTest.getFailedAcknowledgements()).containsExactlyElementsOf(failedAcknowledgements);
    }

    private Acknowledgement createAcknowledgement(final HttpStatusCode statusCode) {
        final List<Acknowledgement> acknowledgements = createAcknowledgements(1, statusCode);
        return acknowledgements.get(0);
    }

    private List<Acknowledgement> createAcknowledgements(final int amount, final HttpStatusCode statusCode) {
        final List<Acknowledgement> result = new ArrayList<>(amount);
        final ThingId thingId = ThingId.generateRandom();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(testName.getMethodName())
                .build();
        for (int i = 0; i < amount; i++) {
            final AcknowledgementLabel ackLabel = AcknowledgementLabel.of("status-" + statusCode.toInt() + "-" + i);
            result.add(Acknowledgement.of(ackLabel, thingId, statusCode, dittoHeaders));
        }
        return result;
    }

}