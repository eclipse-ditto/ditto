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
package org.eclipse.ditto.model.messages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link MessageHeadersBuilder}.
 */
public final class MessageHeadersBuilderTest {

    private static final MessageDirection DIRECTION = MessageDirection.TO;
    private final static String THING_ID = "bla:foo-bar";
    private static final String SUBJECT = KnownMessageSubjects.CLAIM_SUBJECT;
    private static final String FEATURE_ID = "flux-condensator-0815";
    private static final String CONTENT_TYPE = "application/json";
    private static final long TIMEOUT = 5000;
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
                .withMessage("The %s must not be null!", MessageHeaderDefinition.THING_ID.getKey())
                .withNoCause();
    }

    @Test
    public void tryToCreateInstanceWithEmptyThingId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> MessageHeadersBuilder.newInstance(DIRECTION, "", SUBJECT))
                .withMessage("The argument '%s' must not be empty!", MessageHeaderDefinition.THING_ID.getKey())
                .withNoCause();
    }

    @Test
    public void tryToCreateInstanceWithNullSubject() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> MessageHeadersBuilder.newInstance(DIRECTION, THING_ID, null))
                .withMessage("The %s must not be null!", MessageHeaderDefinition.SUBJECT.getKey())
                .withNoCause();
    }

    @Test
    public void tryToCreateInstanceWithEmptySubject() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> MessageHeadersBuilder.newInstance(DIRECTION, THING_ID, ""))
                .withMessage("The argument '%s' must not be empty!", MessageHeaderDefinition.SUBJECT.getKey())
                .withNoCause();
    }

    @Test
    public void tryToCreateInstanceWithInvalidSubject() {
        final String invalidSubject = "{foo}";

        assertThatExceptionOfType(SubjectInvalidException.class)
                .isThrownBy(() -> MessageHeadersBuilder.newInstance(DIRECTION, THING_ID, invalidSubject))
                .withMessageStartingWith(
                        "The subject <" + invalidSubject + "> is invalid because it did not match the pattern")
                .withNoCause();
    }

    @Test
    public void createInstanceFromMinimalMap() {
        final Map<String, String> validHeaders = new HashMap<>();
        validHeaders.put(MessageHeaderDefinition.DIRECTION.getKey(), MessageDirection.TO.toString());
        validHeaders.put(MessageHeaderDefinition.SUBJECT.getKey(), SUBJECT);
        validHeaders.put(MessageHeaderDefinition.THING_ID.getKey(), THING_ID);

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
    public void tryToSetUnknownStatusCode() {
        final int unknownCode = 42;

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.statusCode(unknownCode))
                .withMessage("HTTP status code <%s> is unknown!", unknownCode)
                .withNoCause();
    }

    @Test
    public void setStatusCodeAsIntWorksAsExpected() {
        final int knownCode = 404;

        final MessageHeaders messageHeaders = underTest.statusCode(knownCode).build();

        assertThat(messageHeaders.getStatusCode()).contains(HttpStatusCode.NOT_FOUND);
    }

    @Test
    public void putValidHeadersWorksAsExpected() {
        final Map<String, String> validHeaders = new HashMap<>();
        validHeaders.put(DittoHeaderDefinition.CONTENT_TYPE.getKey(), CONTENT_TYPE);
        validHeaders.put(MessageHeaderDefinition.STATUS_CODE.getKey(), String.valueOf(HttpStatusCode.CREATED.toInt()));
        validHeaders.put(MessageHeaderDefinition.TIMEOUT.getKey(), String.valueOf(TIMEOUT));
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
        invalidHeaders.put(MessageHeaderDefinition.STATUS_CODE.getKey(), String.valueOf(HttpStatusCode.CREATED.toInt()));
        invalidHeaders.put(MessageHeaderDefinition.TIMEOUT.getKey(), String.valueOf(TIMEOUT));
        invalidHeaders.put(key, invalidValue);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.putHeaders(invalidHeaders))
                .withMessage("Value <%s> for key <%s> is not a valid boolean!", invalidValue, key)
                .withNoCause();
    }

    @Test
    public void tryToPutMapWithInvalidMessageHeader() {
        final String key = MessageHeaderDefinition.TIMEOUT.getKey();
        final String invalidValue = "bar";

        final Map<String, String> invalidHeaders = new HashMap<>();
        invalidHeaders.put(DittoHeaderDefinition.CONTENT_TYPE.getKey(), CONTENT_TYPE);
        invalidHeaders.put(MessageHeaderDefinition.STATUS_CODE.getKey(), String.valueOf(HttpStatusCode.CREATED.toInt()));
        invalidHeaders.put(key, invalidValue);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.putHeaders(invalidHeaders))
                .withMessage("Value <%s> for key <%s> is not a valid long!", invalidValue, key)
                .withCauseInstanceOf(NumberFormatException.class);
    }

    @Test
    public void tryToOverwriteMandatoryHeader() {
        final String key = MessageHeaderDefinition.SUBJECT.getKey();
        final String value = "mySubject";

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.putHeader(key, value))
                .withMessage("Value for mandatory header with key <%s> cannot be overwritten!", key)
                .withNoCause();
    }

    @Test
    public void tryToPutInvalidHeader() {
        final String key = MessageHeaderDefinition.TIMESTAMP.getKey();
        final String invalidValue = "foo";

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.putHeader(key, invalidValue))
                .withMessage("<foo> is not a valid timestamp!")
                .withCauseInstanceOf(DateTimeParseException.class);
    }

    @Test
    public void setTimestampAsString() {
        final OffsetDateTime expectedTimestamp = OffsetDateTime.of(2016, 1, 12, 17, 11, 0, 0, ZoneOffset.ofHours(1));

        final MessageHeaders messageHeaders = underTest.timestamp("2016-01-12T17:11:00+01:00").build();

        assertThat(messageHeaders.getTimestamp()).contains(expectedTimestamp);
    }

}
