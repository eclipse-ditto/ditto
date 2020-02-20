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
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
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
                .isThrownBy(() -> underTest.addAcknowledgementRequest(null))
                .withMessage("The acknowledgementRequest must not be null!")
                .withNoCause();
    }

    @Test
    public void emptyInstanceHasNoMissingAcknowledgementLabels() {
        final AcknowledgementsPerRequest underTest = AcknowledgementsPerRequest.getInstance();

        assertThat(underTest.getMissingAcknowledgementRequests()).isEmpty();
    }

    @Test
    public void getMissingAcknowledgementLabelsReturnsExpected() {
        final AcknowledgementsPerRequest underTest = AcknowledgementsPerRequest.getInstance();
        final List<Acknowledgement> successfulAcks = createAcknowledgements(2, HttpStatusCode.NO_CONTENT);
        final List<Acknowledgement> failedAcks = createAcknowledgements(2, HttpStatusCode.FORBIDDEN);
        successfulAcks.forEach(ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel())));
        failedAcks.forEach(ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel())));
        failedAcks.forEach(underTest::addReceivedAcknowledgment);
        final List<AcknowledgementRequest> expected = successfulAcks.stream()
                .map(Acknowledgement::getLabel)
                .map(AcknowledgementRequest::of)
                .collect(Collectors.toList());

        assertThat(underTest.getMissingAcknowledgementRequests()).containsExactlyElementsOf(expected);
    }

    @Test
    public void tryToAddNullRequestedAcknowledgementLabels() {
        final AcknowledgementsPerRequest underTest = AcknowledgementsPerRequest.getInstance();

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.addAcknowledgementRequests(null))
                .withMessage("The acknowledgementRequests must not be null!")
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
        acknowledgements.forEach(ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel())));
        underTest.addReceivedAcknowledgment(acknowledgements.get(1));

        assertThat(underTest.receivedAllRequestedAcknowledgements()).isFalse();
    }

    @Test
    public void receivedAllRequestedAcknowledgements() {
        final AcknowledgementsPerRequest underTest = AcknowledgementsPerRequest.getInstance();
        final List<Acknowledgement> acknowledgements = createAcknowledgements(3, HttpStatusCode.NO_CONTENT);
        acknowledgements.addAll(createAcknowledgements(2, HttpStatusCode.BAD_REQUEST));
        acknowledgements.forEach(ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel())));
        acknowledgements.forEach(underTest::addReceivedAcknowledgment);

        assertThat(underTest.receivedAllRequestedAcknowledgements()).isTrue();
    }

    @Test
    public void getMissingAcknowledgementLabelsFromEmptyInstance() {
        final AcknowledgementsPerRequest underTest = AcknowledgementsPerRequest.getInstance();

        assertThat(underTest.getMissingAcknowledgementRequests()).isEmpty();
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
        underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ackLabel));

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
        underTest.addAcknowledgementRequest(AcknowledgementRequest.of(failedAcknowledgement.getLabel()));

        underTest.addReceivedAcknowledgment(failedAcknowledgement);

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(underTest.getMissingAcknowledgementRequests())
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
        underTest.addAcknowledgementRequest(AcknowledgementRequest.of(successfulAcknowledgement.getLabel()));

        underTest.addReceivedAcknowledgment(successfulAcknowledgement);

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(underTest.getMissingAcknowledgementRequests())
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
        underTest.addAcknowledgementRequest(AcknowledgementRequest.of(requestedAckLabel));
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
        final List<Acknowledgement> successfulAcks = createAcknowledgements(3, HttpStatusCode.OK);
        successfulAcks.forEach(ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel())));
        successfulAcks.forEach(underTest::addReceivedAcknowledgment);

        assertThat(underTest.getSuccessfulAcknowledgements()).containsExactlyElementsOf(successfulAcks);
    }

    @Test
    public void failedAcknowledgementsAreInExpectedOrder() {
        final AcknowledgementsPerRequest underTest = AcknowledgementsPerRequest.getInstance();
        final List<Acknowledgement> failedAcks = createAcknowledgements(3, HttpStatusCode.NOT_FOUND);
        failedAcks.forEach(ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel())));
        failedAcks.forEach(underTest::addReceivedAcknowledgment);

        assertThat(underTest.getFailedAcknowledgements()).containsExactlyElementsOf(failedAcks);
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