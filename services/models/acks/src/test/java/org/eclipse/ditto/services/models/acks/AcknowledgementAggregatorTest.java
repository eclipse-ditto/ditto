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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.AcknowledgementRequestTimeoutException;
import org.eclipse.ditto.signals.acks.base.Acknowledgements;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.mockito.Mockito;

/**
 * Unit test for {@link AcknowledgementAggregator}.
 */
public final class AcknowledgementAggregatorTest {

    private static final ThingId ENTITY_ID = ThingId.generateRandom();
    private static final Duration TIMEOUT = Duration.ofMillis(1337);

    @Rule
    public final TestName testName = new TestName();

    private String correlationId;
    private DittoHeaders dittoHeaders;

    @Before
    public void setUp() {
        correlationId = testName.getMethodName();
        dittoHeaders = DittoHeaders.newBuilder().correlationId(correlationId).build();
    }

    @Test
    public void tryToAddNullRequestedAcknowledgementLabel() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT);

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.addAcknowledgementRequest(null))
                .withMessage("The acknowledgementRequest must not be null!")
                .withNoCause();
    }

    @Test
    public void emptyInstanceHasNoMissingAcknowledgementLabels() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT);

        final Acknowledgements acknowledgements = underTest.getAggregatedAcknowledgements(dittoHeaders);

        assertThat(acknowledgements.getMissingAcknowledgementLabels()).isEmpty();
    }

    @Test
    public void getMissingAcknowledgementLabelsReturnsExpected() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT);
        final List<Acknowledgement> successfulAcks = createAcknowledgements(2, HttpStatusCode.NO_CONTENT);
        final List<Acknowledgement> failedAcks = createAcknowledgements(2, HttpStatusCode.FORBIDDEN);
        successfulAcks.forEach(ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel())));
        failedAcks.forEach(ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel())));
        failedAcks.forEach(underTest::addReceivedAcknowledgment);
        final List<AcknowledgementLabel> expected = successfulAcks.stream()
                .map(Acknowledgement::getLabel)
                .collect(Collectors.toList());

        final Acknowledgements acknowledgements = underTest.getAggregatedAcknowledgements(dittoHeaders);

        assertThat(acknowledgements.getMissingAcknowledgementLabels()).containsExactlyElementsOf(expected);
    }

    @Test
    public void tryToAddNullRequestedAcknowledgementLabels() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT);

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.addAcknowledgementRequests(null))
                .withMessage("The acknowledgementRequests must not be null!")
                .withNoCause();
    }

    @Test
    public void emptyInstanceReceivedAllRequestedAcknowledgements() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT);

        assertThat(underTest.receivedAllRequestedAcknowledgements()).isTrue();
    }

    @Test
    public void didNotReceiveAllRequestedAcknowledgements() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT);
        final List<Acknowledgement> acknowledgements = createAcknowledgements(3, HttpStatusCode.NO_CONTENT);
        acknowledgements.addAll(createAcknowledgements(2, HttpStatusCode.BAD_REQUEST));
        acknowledgements.forEach(ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel())));
        underTest.addReceivedAcknowledgment(acknowledgements.get(1));

        assertThat(underTest.receivedAllRequestedAcknowledgements()).isFalse();
    }

    @Test
    public void receivedAllRequestedAcknowledgements() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT);
        final List<Acknowledgement> acknowledgements = createAcknowledgements(3, HttpStatusCode.NO_CONTENT);
        acknowledgements.addAll(createAcknowledgements(2, HttpStatusCode.BAD_REQUEST));
        acknowledgements.forEach(
                ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel())));
        acknowledgements.forEach(underTest::addReceivedAcknowledgment);

        assertThat(underTest.receivedAllRequestedAcknowledgements()).isTrue();
    }

    @Test
    public void onlyRegardFirstReceivedAcknowledgementForSameLabel() {
        final AcknowledgementLabel ackLabel = DittoAcknowledgementLabel.PERSISTED;
        final ThingId entityId = ThingId.generateRandom();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();
        final Acknowledgement failedAcknowledgement =
                Acknowledgement.of(ackLabel, entityId, HttpStatusCode.UNAUTHORIZED, dittoHeaders);
        final Acknowledgement successfulAcknowledgement =
                Acknowledgement.of(ackLabel, entityId, HttpStatusCode.NO_CONTENT, dittoHeaders);
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance(entityId, correlationId, TIMEOUT);
        underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ackLabel));
        underTest.addReceivedAcknowledgment(failedAcknowledgement);
        underTest.addReceivedAcknowledgment(successfulAcknowledgement);

        final Acknowledgements acknowledgements = underTest.getAggregatedAcknowledgements(this.dittoHeaders);

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
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT);

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.addReceivedAcknowledgment(null))
                .withMessage("The acknowledgement must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToAddReceivedAcknowledgementWithoutCorrelationId() {
        final Acknowledgement acknowledgement = Mockito.mock(Acknowledgement.class);
        Mockito.when(acknowledgement.getDittoHeaders()).thenReturn(DittoHeaders.empty());
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.addReceivedAcknowledgment(acknowledgement))
                .withMessage("The received Acknowledgement did not provide a correlation ID at all but expected was "
                        + "<%s>!", correlationId, TIMEOUT)
                .withNoCause();
    }

    @Test
    public void tryToAddReceivedAcknowledgementWithDifferentCorrelationId() {
        final String unexpectedCorrelationId = String.valueOf(UUID.randomUUID());
        final DittoHeaders acknowledgementHeaders = DittoHeaders.newBuilder()
                .correlationId(unexpectedCorrelationId)
                .build();
        final Acknowledgement acknowledgement = Mockito.mock(Acknowledgement.class);
        Mockito.when(acknowledgement.getDittoHeaders()).thenReturn(acknowledgementHeaders);
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.addReceivedAcknowledgment(acknowledgement))
                .withMessage("The received Acknowledgement's correlation ID <%s> differs from the expected <%s>!",
                        unexpectedCorrelationId, correlationId, TIMEOUT)
                .withNoCause();
    }

    @Test
    public void tryToAddReceivedAcknowledgementWithDifferentEntityId() {
        final ThingId unexpectedEntityId = ThingId.generateRandom();
        final Acknowledgement acknowledgement =
                Acknowledgement.of(DittoAcknowledgementLabel.PERSISTED, unexpectedEntityId, HttpStatusCode.NO_CONTENT,
                        dittoHeaders);

        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.addReceivedAcknowledgment(acknowledgement))
                .withMessage("The received Acknowledgement's entity ID <%s> differs from the expected <%s>!",
                        unexpectedEntityId, ENTITY_ID)
                .withNoCause();
    }

    @Test
    public void addFailedReceivedAcknowledgement() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT);
        final Acknowledgement failedAcknowledgement = createAcknowledgement(HttpStatusCode.NOT_FOUND);
        underTest.addAcknowledgementRequest(AcknowledgementRequest.of(failedAcknowledgement.getLabel()));
        underTest.addReceivedAcknowledgment(failedAcknowledgement);

        final Acknowledgements acknowledgements = underTest.getAggregatedAcknowledgements(dittoHeaders);

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
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT);
        final Acknowledgement successfulAcknowledgement = createAcknowledgement(HttpStatusCode.OK);
        underTest.addAcknowledgementRequest(AcknowledgementRequest.of(successfulAcknowledgement.getLabel()));
        underTest.addReceivedAcknowledgment(successfulAcknowledgement);

        final Acknowledgements acknowledgements = underTest.getAggregatedAcknowledgements(dittoHeaders);

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
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT);
        final Acknowledgement expected = Acknowledgement.of(DittoAcknowledgementLabel.PERSISTED,
                ENTITY_ID,
                HttpStatusCode.REQUEST_TIMEOUT,
                DittoHeaders.empty(), AcknowledgementRequestTimeoutException.newBuilder(TIMEOUT)
                        .build()
                        .toJson()
        );
        underTest.addAcknowledgementRequest(AcknowledgementRequest.of(expected.getLabel()));
        final List<Acknowledgement> acknowledgementsList = createAcknowledgements(1, HttpStatusCode.NO_CONTENT);
        acknowledgementsList.addAll(createAcknowledgements(1, HttpStatusCode.BAD_REQUEST));
        acknowledgementsList.forEach(underTest::addReceivedAcknowledgment);

        final Acknowledgements acknowledgements = underTest.getAggregatedAcknowledgements(dittoHeaders);

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(acknowledgements.getSuccessfulAcknowledgements())
                    .as("no successful ACKs")
                    .isEmpty();
            softly.assertThat(acknowledgements.getFailedAcknowledgements())
                    .as("requested timed out")
                    .containsOnly(expected);
        }
    }

    @Test
    public void successfulAcknowledgementsAreInExpectedOrder() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT);
        final List<Acknowledgement> successfulAcks = createAcknowledgements(3, HttpStatusCode.OK);
        successfulAcks.forEach(ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel())));
        successfulAcks.forEach(underTest::addReceivedAcknowledgment);

        final Acknowledgements acknowledgements = underTest.getAggregatedAcknowledgements(dittoHeaders);

        assertThat(acknowledgements.getSuccessfulAcknowledgements()).containsExactlyElementsOf(successfulAcks);
    }

    @Test
    public void failedAcknowledgementsAreInExpectedOrder() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT);
        final List<Acknowledgement> failedAcks = createAcknowledgements(3, HttpStatusCode.NOT_FOUND);
        failedAcks.forEach(ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel())));
        failedAcks.forEach(underTest::addReceivedAcknowledgment);

        final Acknowledgements acknowledgements = underTest.getAggregatedAcknowledgements(dittoHeaders);

        assertThat(acknowledgements.getFailedAcknowledgements()).containsExactlyElementsOf(failedAcks);
    }

    @Test
    public void successfulAcknowledgementsLeadToHttpStatusCodeOK() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT);
        final Collection<Acknowledgement> successfulAcks = new ArrayList<>();
        successfulAcks.add(createAcknowledgement(HttpStatusCode.OK));
        successfulAcks.add(createAcknowledgement(HttpStatusCode.NO_CONTENT));
        successfulAcks.add(createAcknowledgement(HttpStatusCode.CREATED));
        successfulAcks.forEach(ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel())));
        successfulAcks.forEach(underTest::addReceivedAcknowledgment);

        final Acknowledgements acknowledgements = underTest.getAggregatedAcknowledgements(dittoHeaders);

        assertThat(acknowledgements.getStatusCode()).isEqualByComparingTo(HttpStatusCode.OK);
    }

    @Test
    public void acknowledgementsWithOneFailedLeadToHttpStatusCodeFailedDependency() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT);
        final Collection<Acknowledgement> acks = new ArrayList<>();
        acks.add(createAcknowledgement(HttpStatusCode.OK));
        acks.add(createAcknowledgement(HttpStatusCode.NOT_FOUND));
        acks.forEach(ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel())));
        acks.forEach(underTest::addReceivedAcknowledgment);

        final Acknowledgements acknowledgements = underTest.getAggregatedAcknowledgements(dittoHeaders);

        assertThat(acknowledgements.getStatusCode()).isEqualByComparingTo(HttpStatusCode.FAILED_DEPENDENCY);
    }

    @Test
    public void acknowledgementsWithOneTimeoutLeadToHttpStatusCodeFailedDependency() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT);
        final Collection<Acknowledgement> acks = new ArrayList<>();
        acks.add(createAcknowledgement(HttpStatusCode.OK));
        acks.add(createAcknowledgement(HttpStatusCode.NOT_FOUND));
        acks.add(createAcknowledgement(HttpStatusCode.REQUEST_TIMEOUT));
        acks.forEach(ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel())));
        acks.forEach(underTest::addReceivedAcknowledgment);

        final Acknowledgements acknowledgements = underTest.getAggregatedAcknowledgements(dittoHeaders);

        assertThat(acknowledgements.getStatusCode()).isEqualByComparingTo(HttpStatusCode.FAILED_DEPENDENCY);
    }

    @Test
    public void tryToGetAggregatedAcknowledgementsForNullDittoHeaders() {
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT);

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.getAggregatedAcknowledgements(null))
                .withMessage("The dittoHeaders must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToGetAggregatedAcknowledgementsForDittoHeadersWithDifferentCorrelationId() {
        final String differentCorrelationId = String.valueOf(UUID.randomUUID());
        final DittoHeaders dittoHeadersWithDifferentCorrelationId = DittoHeaders.newBuilder()
                .correlationId(differentCorrelationId)
                .build();
        final AcknowledgementAggregator underTest = AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> underTest.getAggregatedAcknowledgements(
                        dittoHeadersWithDifferentCorrelationId))
                .withMessage("The provided correlation ID <%s> differs from the expected <%s>!",
                        differentCorrelationId, correlationId, TIMEOUT)
                .withNoCause();
    }

    private Acknowledgement createAcknowledgement(final HttpStatusCode statusCode) {
        final List<Acknowledgement> acknowledgements = createAcknowledgements(1, statusCode);
        return acknowledgements.get(0);
    }

    private List<Acknowledgement> createAcknowledgements(final int amount, final HttpStatusCode statusCode) {
        final List<Acknowledgement> result = new ArrayList<>(amount);
        for (int i = 0; i < amount; i++) {
            final AcknowledgementLabel ackLabel = AcknowledgementLabel.of("status-" + statusCode.toInt() + "-" + i);
            result.add(Acknowledgement.of(ackLabel, ENTITY_ID, statusCode, dittoHeaders));
        }
        return result;
    }

}