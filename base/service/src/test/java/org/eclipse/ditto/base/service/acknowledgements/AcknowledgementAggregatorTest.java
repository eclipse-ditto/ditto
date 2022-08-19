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
package org.eclipse.ditto.base.service.acknowledgements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.AcknowledgementRequestTimeoutException;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 * Unit test for {@link org.eclipse.ditto.base.service.acknowledgements.AcknowledgementAggregator}.
 */
public final class AcknowledgementAggregatorTest {

    private static final EntityId ENTITY_ID = randomEntityId();
    private static final Duration TIMEOUT = Duration.ofMillis(1337);
    private static final HeaderTranslator HEADER_TRANSLATOR = HeaderTranslator.empty();

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
        final AcknowledgementAggregator underTest =
                AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT, HEADER_TRANSLATOR);

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.addAcknowledgementRequest(null))
                .withMessage("The acknowledgementRequest must not be null!")
                .withNoCause();
    }

    @Test
    public void emptyInstanceHasNoMissingAcknowledgementLabels() {
        final AcknowledgementAggregator underTest =
                AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT, HEADER_TRANSLATOR);

        final Acknowledgements acknowledgements = underTest.getAggregatedAcknowledgements(dittoHeaders);

        assertThat(acknowledgements.getMissingAcknowledgementLabels()).isEmpty();
    }

    @Test
    public void getMissingAcknowledgementLabelsReturnsExpected() {
        final AcknowledgementAggregator underTest =
                AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT, HEADER_TRANSLATOR);
        final List<Acknowledgement> successfulAcks = createAcknowledgements(2, HttpStatus.NO_CONTENT);
        final List<Acknowledgement> failedAcks = createAcknowledgements(2, HttpStatus.FORBIDDEN);
        successfulAcks.forEach(ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel())));
        failedAcks.forEach(ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel())));
        failedAcks.forEach(underTest::addReceivedAcknowledgment);
        final List<AcknowledgementLabel> expected = successfulAcks.stream()
                .map(Acknowledgement::getLabel)
                .toList();

        final Acknowledgements acknowledgements = underTest.getAggregatedAcknowledgements(dittoHeaders);

        assertThat(acknowledgements.getMissingAcknowledgementLabels()).containsExactlyElementsOf(expected);
    }

    @Test
    public void tryToAddNullRequestedAcknowledgementLabels() {
        final AcknowledgementAggregator underTest =
                AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT, HEADER_TRANSLATOR);

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.addAcknowledgementRequests(null))
                .withMessage("The acknowledgementRequests must not be null!")
                .withNoCause();
    }

    @Test
    public void emptyInstanceReceivedAllRequestedAcknowledgements() {
        final AcknowledgementAggregator underTest =
                AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT, HEADER_TRANSLATOR);

        assertThat(underTest.receivedAllRequestedAcknowledgements()).isTrue();
    }

    @Test
    public void didNotReceiveAllRequestedAcknowledgements() {
        final AcknowledgementAggregator underTest =
                AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT, HEADER_TRANSLATOR);
        final List<Acknowledgement> acknowledgements = createAcknowledgements(3, HttpStatus.NO_CONTENT);
        acknowledgements.addAll(createAcknowledgements(2, HttpStatus.BAD_REQUEST));
        acknowledgements.forEach(ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel())));
        underTest.addReceivedAcknowledgment(acknowledgements.get(1));

        assertThat(underTest.receivedAllRequestedAcknowledgements()).isFalse();
    }

    @Test
    public void receivedAllRequestedAcknowledgements() {
        final AcknowledgementAggregator underTest =
                AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT, HEADER_TRANSLATOR);
        final List<Acknowledgement> acknowledgements = createAcknowledgements(3, HttpStatus.NO_CONTENT);
        acknowledgements.addAll(createAcknowledgements(2, HttpStatus.BAD_REQUEST));
        acknowledgements.forEach(
                ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel())));
        acknowledgements.forEach(underTest::addReceivedAcknowledgment);

        assertThat(underTest.receivedAllRequestedAcknowledgements()).isTrue();
    }

    @Test
    public void onlyRegardFirstReceivedAcknowledgementForSameLabel() {
        final AcknowledgementLabel ackLabel = DittoAcknowledgementLabel.TWIN_PERSISTED;
        final EntityId entityId = randomEntityId();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();
        final Acknowledgement failedAcknowledgement =
                Acknowledgement.of(ackLabel, entityId, HttpStatus.UNAUTHORIZED, dittoHeaders);
        final Acknowledgement successfulAcknowledgement =
                Acknowledgement.of(ackLabel, entityId, HttpStatus.NO_CONTENT, dittoHeaders);
        final AcknowledgementAggregator underTest =
                AcknowledgementAggregator.getInstance(entityId, correlationId, TIMEOUT, HEADER_TRANSLATOR);
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
        final AcknowledgementAggregator underTest =
                AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT, HEADER_TRANSLATOR);

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.addReceivedAcknowledgment(null))
                .withMessage("The acknowledgement must not be null!")
                .withNoCause();
    }

    @Test
    public void addFailedReceivedAcknowledgement() {
        final AcknowledgementAggregator underTest =
                AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT, HEADER_TRANSLATOR);
        final Acknowledgement failedAcknowledgement = createAcknowledgement(HttpStatus.NOT_FOUND);
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
        final AcknowledgementAggregator underTest =
                AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT, HEADER_TRANSLATOR);
        final Acknowledgement successfulAcknowledgement = createAcknowledgement(HttpStatus.OK);
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
        final AcknowledgementAggregator underTest =
                AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT, HEADER_TRANSLATOR);
        final DittoRuntimeException timeoutException = new AcknowledgementRequestTimeoutException(TIMEOUT)
                .setDittoHeaders(DittoHeaders.newBuilder().correlationId(correlationId).build());
        final Acknowledgement expected = Acknowledgement.of(DittoAcknowledgementLabel.TWIN_PERSISTED,
                ENTITY_ID,
                timeoutException.getHttpStatus(),
                timeoutException.getDittoHeaders(),
                timeoutException.toJson()
        );
        underTest.addAcknowledgementRequest(AcknowledgementRequest.of(expected.getLabel()));
        final List<Acknowledgement> acknowledgementsList = createAcknowledgements(1, HttpStatus.NO_CONTENT);
        acknowledgementsList.addAll(createAcknowledgements(1, HttpStatus.BAD_REQUEST));
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
        final AcknowledgementAggregator underTest =
                AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT, HEADER_TRANSLATOR);
        final List<Acknowledgement> successfulAcks = createAcknowledgements(3, HttpStatus.OK);
        successfulAcks.forEach(ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel())));
        successfulAcks.forEach(underTest::addReceivedAcknowledgment);

        final Acknowledgements acknowledgements = underTest.getAggregatedAcknowledgements(dittoHeaders);

        assertThat(acknowledgements.getSuccessfulAcknowledgements()).containsExactlyElementsOf(successfulAcks);
    }

    @Test
    public void failedAcknowledgementsAreInExpectedOrder() {
        final AcknowledgementAggregator underTest =
                AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT, HEADER_TRANSLATOR);
        final List<Acknowledgement> failedAcks = createAcknowledgements(3, HttpStatus.NOT_FOUND);
        failedAcks.forEach(ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel())));
        failedAcks.forEach(underTest::addReceivedAcknowledgment);

        final Acknowledgements acknowledgements = underTest.getAggregatedAcknowledgements(dittoHeaders);

        assertThat(acknowledgements.getFailedAcknowledgements()).containsExactlyElementsOf(failedAcks);
    }

    @Test
    public void successfulAcknowledgementsLeadToHttpStatusOK() {
        final AcknowledgementAggregator underTest =
                AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT, HEADER_TRANSLATOR);
        final Collection<Acknowledgement> successfulAcks = new ArrayList<>();
        successfulAcks.add(createAcknowledgement(HttpStatus.OK));
        successfulAcks.add(createAcknowledgement(HttpStatus.NO_CONTENT));
        successfulAcks.add(createAcknowledgement(HttpStatus.CREATED));
        successfulAcks.forEach(ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel())));
        successfulAcks.forEach(underTest::addReceivedAcknowledgment);

        final Acknowledgements acknowledgements = underTest.getAggregatedAcknowledgements(dittoHeaders);

        assertThat(acknowledgements.getHttpStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    public void acknowledgementsWithOneFailedLeadToHttpStatusFailedDependency() {
        final AcknowledgementAggregator underTest =
                AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT, HEADER_TRANSLATOR);
        final Collection<Acknowledgement> acks = new ArrayList<>();
        acks.add(createAcknowledgement(HttpStatus.OK));
        acks.add(createAcknowledgement(HttpStatus.NOT_FOUND));
        acks.forEach(ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel())));
        acks.forEach(underTest::addReceivedAcknowledgment);

        final Acknowledgements acknowledgements = underTest.getAggregatedAcknowledgements(dittoHeaders);

        assertThat(acknowledgements.getHttpStatus()).isEqualTo(HttpStatus.FAILED_DEPENDENCY);
    }

    @Test
    public void acknowledgementsWithOneTimeoutLeadToHttpStatusFailedDependency() {
        final AcknowledgementAggregator underTest =
                AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT, HEADER_TRANSLATOR);
        final Collection<Acknowledgement> acks = new ArrayList<>();
        acks.add(createAcknowledgement(HttpStatus.OK));
        acks.add(createAcknowledgement(HttpStatus.NOT_FOUND));
        acks.add(createAcknowledgement(HttpStatus.REQUEST_TIMEOUT));
        acks.forEach(ack -> underTest.addAcknowledgementRequest(AcknowledgementRequest.of(ack.getLabel())));
        acks.forEach(underTest::addReceivedAcknowledgment);

        final Acknowledgements acknowledgements = underTest.getAggregatedAcknowledgements(dittoHeaders);

        assertThat(acknowledgements.getHttpStatus()).isEqualTo(HttpStatus.FAILED_DEPENDENCY);
    }

    @Test
    public void tryToGetAggregatedAcknowledgementsForNullDittoHeaders() {
        final AcknowledgementAggregator underTest =
                AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT, HEADER_TRANSLATOR);

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
        final AcknowledgementAggregator underTest =
                AcknowledgementAggregator.getInstance(ENTITY_ID, correlationId, TIMEOUT, HEADER_TRANSLATOR);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> underTest.getAggregatedAcknowledgements(
                        dittoHeadersWithDifferentCorrelationId))
                .withMessage("The provided correlation ID <%s> differs from the expected <%s>!",
                        differentCorrelationId, correlationId, TIMEOUT)
                .withNoCause();
    }

    private Acknowledgement createAcknowledgement(final HttpStatus httpStatus) {
        final List<Acknowledgement> acknowledgements = createAcknowledgements(1, httpStatus);
        return acknowledgements.get(0);
    }

    private List<Acknowledgement> createAcknowledgements(final int amount, final HttpStatus httpStatus) {
        final List<Acknowledgement> result = new ArrayList<>(amount);
        for (int i = 0; i < amount; i++) {
            final AcknowledgementLabel ackLabel = AcknowledgementLabel.of("status-" + httpStatus.getCode() + "-" + i);
            result.add(Acknowledgement.of(ackLabel, ENTITY_ID, httpStatus, dittoHeaders));
        }
        return result;
    }

    private static EntityId randomEntityId() {
        return EntityId.of(EntityType.of("foo"), UUID.randomUUID().toString());
    }

}
