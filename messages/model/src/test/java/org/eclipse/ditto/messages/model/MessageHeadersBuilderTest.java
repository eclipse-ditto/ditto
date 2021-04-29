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
package org.eclipse.ditto.messages.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import org.assertj.core.api.SoftAssertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link MessageHeadersBuilder}.
 */
public final class MessageHeadersBuilderTest {

    private static final MessageDirection DIRECTION = MessageDirection.TO;
    private final static ThingId THING_ID = ThingId.of("bla","foo-bar");
    private static final String SUBJECT = KnownMessageSubjects.CLAIM_SUBJECT;
    private static final String FEATURE_ID = "flux-condensator-0815";
    private static final String CONTENT_TYPE = "application/json";
    private static final String CORRELATION_ID = "myCorrelationId";

    private MessageHeadersBuilder underTest;

    @Before
    public void setUp() {
        underTest = MessageHeadersBuilder.newInstance(DIRECTION, THING_ID, SUBJECT);
    }

    @Test
    public void tryToCreateInstanceWithNullDirection() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> MessageHeadersBuilder.newInstance(null, THING_ID, SUBJECT))
                .withMessage("The %s must not be null!", "direction")
                .withNoCause();
    }

    @Test
    public void tryToCreateInstanceWithNullThingId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> MessageHeadersBuilder.newInstance(DIRECTION, null, SUBJECT))
                .withMessage("The %s must not be null!", "thing-id")
                .withNoCause();
    }

    @Test
    public void tryToCreateInstanceWithNullSubject() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> MessageHeadersBuilder.newInstance(DIRECTION, THING_ID, null))
                .withMessage("The %s must not be null!", "subject")
                .withNoCause();
    }

    @Test
    public void tryToCreateInstanceWithEmptySubject() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> MessageHeadersBuilder.newInstance(DIRECTION, THING_ID, ""))
                .withMessage("The argument '%s' must not be empty!", "subject")
                .withNoCause();
    }

    @Test
    public void tryToCreateInstanceWithInvalidSubject() {
        final String invalidSubject = "§foo";

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> MessageHeadersBuilder.newInstance(DIRECTION, THING_ID, invalidSubject))
                .withMessageContaining(invalidSubject)
                .withMessageEndingWith("is not a valid message subject.")
                .withNoCause();
    }

    @Test
    public void createInstanceFromMinimalMap() {
        final Map<String, String> validHeaders = new HashMap<>();
        validHeaders.put(MessageHeaderDefinition.DIRECTION.getKey(), MessageDirection.TO.toString());
        validHeaders.put(MessageHeaderDefinition.SUBJECT.getKey(), SUBJECT);
        validHeaders.put(MessageHeaderDefinition.THING_ID.getKey(), THING_ID.toString());

        final MessageHeaders messageHeaders = MessageHeadersBuilder.of(validHeaders).build();

        assertThat(messageHeaders).containsAllEntriesOf(validHeaders);
    }

    @Test
    public void tryToCreateInstanceFromMapWithoutThingId() {
        final Map<String, String> insufficientHeaders = new HashMap<>();
        insufficientHeaders.put(MessageHeaderDefinition.DIRECTION.getKey(), MessageDirection.TO.toString());
        insufficientHeaders.put(MessageHeaderDefinition.SUBJECT.getKey(), SUBJECT);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> MessageHeadersBuilder.of(insufficientHeaders))
                .withMessage("The headers did not contain a value for mandatory header with key <%s>!",
                        MessageHeaderDefinition.THING_ID.getKey())
                .withNoCause();
    }

    @Test
    public void tryToCreateInstanceFromEmptyJsonObject() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> MessageHeadersBuilder.of(JsonFactory.newObject()))
                .withMessage("The headers did not contain a value for mandatory header with key <%s>!",
                        MessageHeaderDefinition.DIRECTION.getKey())
                .withNoCause();
    }

    @Test
    public void setFeatureIdWorksAsExpected() {
        final MessageHeaders messageHeaders = underTest.featureId(FEATURE_ID).build();

        assertThat(messageHeaders.getFeatureId()).contains(FEATURE_ID);
    }

    @Test
    public void tryToSetEmptyFeatureId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.featureId(""))
                .withMessage("The %s must not be empty!", MessageHeaderDefinition.FEATURE_ID.getKey())
                .withNoCause();
    }

    @Test
    public void setHttpStatusWorksAsExpected() {
        final HttpStatus knownStatus = HttpStatus.NOT_FOUND;

        final MessageHeaders messageHeaders = underTest.httpStatus(knownStatus).build();

        assertThat(messageHeaders.getHttpStatus()).contains(knownStatus);
    }

    @Test
    public void putValidHeadersWorksAsExpected() {
        final Map<String, String> validHeaders = new HashMap<>();
        validHeaders.put(DittoHeaderDefinition.CONTENT_TYPE.getKey(), CONTENT_TYPE);
        validHeaders.put(MessageHeaderDefinition.STATUS_CODE.getKey(), String.valueOf(HttpStatus.CREATED.getCode()));
        validHeaders.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), CORRELATION_ID);

        final MessageHeaders messageHeaders = underTest.putHeaders(validHeaders).build();

        assertThat(messageHeaders).containsAllEntriesOf(validHeaders);
    }

    @Test
    public void tryToPutMapWithInvalidDittoHeader() {
        final String key = DittoHeaderDefinition.RESPONSE_REQUIRED.getKey();
        final String invalidValue = "bar";

        final Map<String, String> invalidHeaders = new HashMap<>();
        invalidHeaders.put(DittoHeaderDefinition.CONTENT_TYPE.getKey(), CONTENT_TYPE);
        invalidHeaders.put(MessageHeaderDefinition.STATUS_CODE.getKey(), String.valueOf(HttpStatus.CREATED.getCode()));
        invalidHeaders.put(key, invalidValue);

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.putHeaders(invalidHeaders))
                .withMessage("The value '%s' of the header '%s' is not a valid boolean.", invalidValue, key)
                .withNoCause();
    }

    @Test
    public void tryToRemoveMandatoryHeader() {
        final SoftAssertions softly = new SoftAssertions();
        for (final MessageHeaderDefinition mandatoryHeaderDefinition : MessageHeadersBuilder.MANDATORY_HEADERS) {
            final String key = mandatoryHeaderDefinition.getKey();
            softly.assertThatThrownBy(() -> underTest.removeHeader(key))
                    .isInstanceOf(IllegalArgumentException.class)
                    .withFailMessage("Mandatory header with key <%s> cannot be removed!", key)
                    .hasNoCause();
        }
        softly.assertAll();
    }

    @Test
    public void tryToRemoveMandatoryHeaderBySettingToNull() {
        final SoftAssertions softly = new SoftAssertions();
        for (final MessageHeaderDefinition mandatoryHeaderDefinition : MessageHeadersBuilder.MANDATORY_HEADERS) {
            final String key = mandatoryHeaderDefinition.getKey();
            softly.assertThatThrownBy(() -> underTest.putHeader(key, null))
                    .isInstanceOf(NullPointerException.class)
                    .withFailMessage("The value must not be null!")
                    .hasNoCause();
        }
        softly.assertAll();
    }

    @Test
    public void tryToPutInvalidHeader() {
        final String key = MessageHeaderDefinition.TIMESTAMP.getKey();
        final String invalidValue = "foo";

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.putHeader(key, invalidValue))
                .withMessageContaining(invalidValue)
                .withMessageEndingWith("is not a valid timestamp.");
    }

    @Test
    public void setTimestampAsString() {
        final OffsetDateTime expectedTimestamp = OffsetDateTime.of(2016, 1, 12, 17, 11, 0, 0, ZoneOffset.ofHours(1));

        final MessageHeaders messageHeaders = underTest.timestamp("2016-01-12T17:11:00+01:00").build();

        assertThat(messageHeaders.getTimestamp()).contains(expectedTimestamp);
    }

}
