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
package org.eclipse.ditto.services.models.acks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.entity.id.DefaultEntityId;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.acks.Acknowledgement;
import org.eclipse.ditto.signals.acks.Acknowledgements;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 * Unit test for {@link AcknowledgementAggregator}.
 */
public final class AcknowledgementAggregatorTest {

    private static final CharSequence ENTITY_ID = "foo:bar";
    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder()
            .correlationId(UUID.randomUUID().toString())
            .build();

    @Rule
    public final TestName testName = new TestName();

    @Test
    public void tryToAddNullRequestedAcknowledgementLabel() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance();

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.addAcknowledgementRequest(null, ENTITY_ID, DITTO_HEADERS))
                .withMessage("The acknowledgementRequest must not be null!")
                .withNoCause();
    }

    @Test
    public void emptyInstanceHasNoMissingAcknowledgementLabels() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance();

        final Acknowledgements acknowledgements = underTest.buildAggregatedAcknowledgements(ENTITY_ID, DITTO_HEADERS);
        assertThat(acknowledgements.getMissingAcknowledgementLabels()).isEmpty();
    }

    @Test
    public void getMissingAcknowledgementLabelsReturnsExpected() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance();
        final List<Acknowledgement> successfulAcks = createAcknowledgements(2, HttpStatusCode.NO_CONTENT);
        final List<Acknowledgement> failedAcks = createAcknowledgements(2, HttpStatusCode.FORBIDDEN);
        successfulAcks.forEach(
                ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel()), ENTITY_ID,
                        DITTO_HEADERS));
        failedAcks.forEach(
                ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel()), ENTITY_ID,
                        DITTO_HEADERS));
        failedAcks.forEach(underTest::addReceivedAcknowledgment);
        final List<AcknowledgementLabel> expected = successfulAcks.stream()
                .map(Acknowledgement::getLabel)
                .collect(Collectors.toList());

        final Acknowledgements acknowledgements = underTest.buildAggregatedAcknowledgements(ENTITY_ID, DITTO_HEADERS);
        assertThat(acknowledgements.getMissingAcknowledgementLabels()).containsExactlyElementsOf(expected);
    }

    @Test
    public void tryToAddNullRequestedAcknowledgementLabels() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance();

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.addAcknowledgementRequests(null, ENTITY_ID, DITTO_HEADERS))
                .withMessage("The acknowledgementRequests must not be null!")
                .withNoCause();
    }

    @Test
    public void emptyInstanceReceivedAllRequestedAcknowledgements() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance();

        assertThat(underTest.receivedAllRequestedAcknowledgements()).isTrue();
    }

    @Test
    public void didNotReceiveAllRequestedAcknowledgements() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance();
        final List<Acknowledgement> acknowledgements = createAcknowledgements(3, HttpStatusCode.NO_CONTENT);
        acknowledgements.addAll(createAcknowledgements(2, HttpStatusCode.BAD_REQUEST));
        acknowledgements.forEach(
                ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel()), ENTITY_ID,
                        DITTO_HEADERS));
        underTest.addReceivedAcknowledgment(acknowledgements.get(1));

        assertThat(underTest.receivedAllRequestedAcknowledgements()).isFalse();
    }

    @Test
    public void receivedAllRequestedAcknowledgements() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance();
        final List<Acknowledgement> acknowledgements = createAcknowledgements(3, HttpStatusCode.NO_CONTENT);
        acknowledgements.addAll(createAcknowledgements(2, HttpStatusCode.BAD_REQUEST));
        acknowledgements.forEach(
                ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel()), ENTITY_ID,
                        DITTO_HEADERS));
        acknowledgements.forEach(underTest::addReceivedAcknowledgment);

        assertThat(underTest.receivedAllRequestedAcknowledgements()).isTrue();
    }

    @Test
    public void onlyRegardFirstReceivedAcknowledgementForSameLabel() {
        final AcknowledgementLabel ackLabel = DittoAcknowledgementLabel.PERSISTED;
        final EntityId thingId = DefaultEntityId.generateRandom();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();
        final Acknowledgement failedAcknowledgement =
                Acknowledgement.of(ackLabel, thingId, HttpStatusCode.UNAUTHORIZED, dittoHeaders);
        final Acknowledgement successfulAcknowledgement =
                Acknowledgement.of(ackLabel, thingId, HttpStatusCode.NO_CONTENT, dittoHeaders);
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance();
        underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ackLabel), ENTITY_ID, DITTO_HEADERS);

        underTest.addReceivedAcknowledgment(failedAcknowledgement);
        underTest.addReceivedAcknowledgment(successfulAcknowledgement);

        final Acknowledgements acknowledgements = underTest.buildAggregatedAcknowledgements(ENTITY_ID, DITTO_HEADERS);
        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(acknowledgements.getFailedAcknowledgements())
                    .as("one failed ACK")
                    .containsOnly(failedAcknowledgement);
            softly.assertThat(acknowledgements.getSuccessfulAcknowledgements())
                    .as("no successful ACK")
                    .isEmpty();
        }
    }

    @Test
    public void tryToAddNullReceivedAcknowledgement() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance();

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.addReceivedAcknowledgment(null))
                .withMessage("The acknowledgement must not be null!")
                .withNoCause();
    }

    @Test
    public void addFailedReceivedAcknowledgement() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance();
        final Acknowledgement failedAcknowledgement = createAcknowledgement(HttpStatusCode.NOT_FOUND);
        underTest.addAcknowledgementRequest(AcknowledgementRequest.of(failedAcknowledgement.getLabel()), ENTITY_ID,
                DITTO_HEADERS);

        underTest.addReceivedAcknowledgment(failedAcknowledgement);

        final Acknowledgements acknowledgements = underTest.buildAggregatedAcknowledgements(ENTITY_ID, DITTO_HEADERS);
        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(acknowledgements.getMissingAcknowledgementLabels())
                    .as("no missing requested ACK labels")
                    .isEmpty();
            softly.assertThat(acknowledgements.getFailedAcknowledgements())
                    .as("expected failed ACKs")
                    .containsOnly(failedAcknowledgement);
            softly.assertThat(underTest.isSuccessful())
                    .as("is not successful")
                    .isFalse();
        }
    }

    @Test
    public void addSuccessfulReceivedAcknowledgement() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance();
        final Acknowledgement successfulAcknowledgement = createAcknowledgement(HttpStatusCode.OK);
        underTest.addAcknowledgementRequest(AcknowledgementRequest.of(successfulAcknowledgement.getLabel()), ENTITY_ID,
                DITTO_HEADERS);

        underTest.addReceivedAcknowledgment(successfulAcknowledgement);

        final Acknowledgements acknowledgements = underTest.buildAggregatedAcknowledgements(ENTITY_ID, DITTO_HEADERS);
        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(acknowledgements.getMissingAcknowledgementLabels())
                    .as("no missing requested ACK labels")
                    .isEmpty();
            softly.assertThat(acknowledgements.getFailedAcknowledgements())
                    .as("no failed ACKs")
                    .isEmpty();
            softly.assertThat(underTest.isSuccessful())
                    .as("is successful")
                    .isTrue();
        }
    }

    @Test
    public void unknownReceivedAcknowledgementsAreIgnored() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance();
        final AcknowledgementLabel requestedAckLabel = DittoAcknowledgementLabel.PERSISTED;
        underTest.addAcknowledgementRequest(AcknowledgementRequest.of(requestedAckLabel), ENTITY_ID, DITTO_HEADERS);
        final List<Acknowledgement> acknowledgementsList = createAcknowledgements(1, HttpStatusCode.NO_CONTENT);
        acknowledgementsList.addAll(createAcknowledgements(1, HttpStatusCode.BAD_REQUEST));
        acknowledgementsList.forEach(underTest::addReceivedAcknowledgment);

        final Acknowledgements acknowledgements = underTest.buildAggregatedAcknowledgements(ENTITY_ID, DITTO_HEADERS);
        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(acknowledgements.getSuccessfulAcknowledgements())
                    .as("no successful ACKs")
                    .isEmpty();
            softly.assertThat(acknowledgements.getFailedAcknowledgements())
                    .as("no failed ACKs")
                    .isEmpty();
        }
    }

    @Test
    public void successfulAcknowledgementsAreInExpectedOrder() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance();
        final List<Acknowledgement> successfulAcks = createAcknowledgements(3, HttpStatusCode.OK);
        successfulAcks.forEach(
                ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel()), ENTITY_ID,
                        DITTO_HEADERS));
        successfulAcks.forEach(underTest::addReceivedAcknowledgment);

        final Acknowledgements acknowledgements = underTest.buildAggregatedAcknowledgements(ENTITY_ID, DITTO_HEADERS);
        assertThat(acknowledgements.getSuccessfulAcknowledgements()).containsExactlyElementsOf(successfulAcks);
    }

    @Test
    public void failedAcknowledgementsAreInExpectedOrder() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance();
        final List<Acknowledgement> failedAcks = createAcknowledgements(3, HttpStatusCode.NOT_FOUND);
        failedAcks.forEach(
                ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel()), ENTITY_ID,
                        DITTO_HEADERS));
        failedAcks.forEach(underTest::addReceivedAcknowledgment);

        final Acknowledgements acknowledgements = underTest.buildAggregatedAcknowledgements(ENTITY_ID, DITTO_HEADERS);
        assertThat(acknowledgements.getFailedAcknowledgements()).containsExactlyElementsOf(failedAcks);
    }

    @Test
    public void successfulAcknowledgementsLeadToHttpStatusCodeOK() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance();
        final List<Acknowledgement> successfulAcks = new ArrayList<>();
        successfulAcks.add(createAcknowledgement(HttpStatusCode.OK));
        successfulAcks.add(createAcknowledgement(HttpStatusCode.NO_CONTENT));
        successfulAcks.add(createAcknowledgement(HttpStatusCode.CREATED));
        successfulAcks.forEach(
                ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel()), ENTITY_ID,
                        DITTO_HEADERS));
        successfulAcks.forEach(underTest::addReceivedAcknowledgment);

        final Acknowledgements acknowledgements = underTest.buildAggregatedAcknowledgements(ENTITY_ID, DITTO_HEADERS);
        assertThat(acknowledgements.getStatusCode()).isEqualByComparingTo(HttpStatusCode.OK);
    }

    @Test
    public void acknowledgementsWithOneFailedLeadToHttpStatusCodeFailedDependency() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance();
        final List<Acknowledgement> acks = new ArrayList<>();
        acks.add(createAcknowledgement(HttpStatusCode.OK));
        acks.add(createAcknowledgement(HttpStatusCode.NOT_FOUND));
        acks.forEach(
                ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel()), ENTITY_ID,
                        DITTO_HEADERS));
        acks.forEach(underTest::addReceivedAcknowledgment);

        final Acknowledgements acknowledgements = underTest.buildAggregatedAcknowledgements(ENTITY_ID, DITTO_HEADERS);
        assertThat(acknowledgements.getStatusCode()).isEqualByComparingTo(HttpStatusCode.FAILED_DEPENDENCY);
    }

    @Test
    public void acknowledgementsWithOneTimeoutLeadToHttpStatusCodeFailedDependency() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance();
        final List<Acknowledgement> acks = new ArrayList<>();
        acks.add(createAcknowledgement(HttpStatusCode.OK));
        acks.add(createAcknowledgement(HttpStatusCode.NOT_FOUND));
        acks.add(createAcknowledgement(HttpStatusCode.REQUEST_TIMEOUT));
        acks.forEach(
                ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel()), ENTITY_ID,
                        DITTO_HEADERS));
        acks.forEach(underTest::addReceivedAcknowledgment);

        final Acknowledgements acknowledgements = underTest.buildAggregatedAcknowledgements(ENTITY_ID, DITTO_HEADERS);
        assertThat(acknowledgements.getStatusCode()).isEqualByComparingTo(HttpStatusCode.FAILED_DEPENDENCY);
    }

    private Acknowledgement createAcknowledgement(final HttpStatusCode statusCode) {
        final List<Acknowledgement> acknowledgements = createAcknowledgements(1, statusCode);
        return acknowledgements.get(0);
    }

    private List<Acknowledgement> createAcknowledgements(final int amount, final HttpStatusCode statusCode) {
        final List<Acknowledgement> result = new ArrayList<>(amount);
        final EntityId thingId = DefaultEntityId.generateRandom();
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