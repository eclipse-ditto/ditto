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
import static org.assertj.core.data.MapEntry.entry;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableMessageHeaders}.
 */
public final class ImmutableMessageHeadersTest {

    private static final MessageDirection DIRECTION = MessageDirection.TO;
    private static final String THING_ID = "test.ns:theThingId";
    private static final String SUBJECT = KnownMessageSubjects.CLAIM_SUBJECT;
    private static final Collection<String> AUTH_SUBJECTS = Arrays.asList("JohnOldman", "FrankGrimes");
    private static final String KNOWN_CORRELATION_ID = "knownCorrelationId";
    private static final JsonSchemaVersion KNOWN_SCHEMA_VERSION = JsonSchemaVersion.V_2;
    private static final String KNOWN_READ_SUBJECT_WITHOUT_ISSUER = "knownReadSubject";
    private static final String KNOWN_READ_SUBJECT = KNOWN_READ_SUBJECT_WITHOUT_ISSUER;
    private static final String KNOWN_SOURCE = "knownSource";
    private static final String KNOWN_CHANNEL = "twin";
    private static final boolean KNOWN_RESPONSE_REQUIRED = true;
    private static final Collection<String> KNOWN_READ_SUBJECTS = Collections.singletonList(KNOWN_READ_SUBJECT);
    private static final String FEATURE_ID = "flux-condensator-0815";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String TIMESTAMP = "2017-09-22T09:47:23+01:00";
    private static final HttpStatusCode STATUS_CODE = HttpStatusCode.OK;
    private static final String CONTENT_TYPE = "application/json";
    private static final String VALIDATION_URL = "https://example.com/message/validate";

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableMessageHeaders.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableMessageHeaders.class)
                .withNonnullFields("headers")
                .verify();
    }

    @Test
    public void settingAllKnownHeadersWorksAsExpected() {
        final Map<String, String> expectedHeaderMap = createMapContainingAllKnownHeaders();

        final MessageHeaders messageHeaders = MessageHeadersBuilder.newInstance(DIRECTION, THING_ID, SUBJECT)
                .authorizationSubjects(AUTH_SUBJECTS)
                .correlationId(KNOWN_CORRELATION_ID)
                .schemaVersion(KNOWN_SCHEMA_VERSION)
                .source(KNOWN_SOURCE)
                .channel(KNOWN_CHANNEL)
                .responseRequired(KNOWN_RESPONSE_REQUIRED)
                .dryRun(false)
                .readSubjects(KNOWN_READ_SUBJECTS)
                .featureId(FEATURE_ID)
                .timeout(TIMEOUT)
                .timestamp(TIMESTAMP)
                .statusCode(STATUS_CODE)
                .contentType(CONTENT_TYPE)
                .validationUrl(VALIDATION_URL)
                .build();

        assertThat(messageHeaders).isEqualTo(expectedHeaderMap);
    }

    @Test
    public void timeoutIsSerializedAsInteger() {
        final long timeout = Long.MAX_VALUE;

        final MessageHeaders underTest = MessageHeadersBuilder.newInstance(DIRECTION, THING_ID, SUBJECT)
                .timeout(timeout)
                .build();

        final JsonObject jsonObject = underTest.toJson();

        assertThat(jsonObject.getValue(MessageHeaderDefinition.TIMEOUT.getKey()))
                .contains(JsonFactory.newValue(timeout));
    }

    @Test
    public void createInstanceOfValidHeaderMapWorksAsExpected() {
        final Map<String, String> initialHeaders = createMapContainingAllKnownHeaders();

        final MessageHeaders underTest = MessageHeadersBuilder.of(initialHeaders).build();

        assertThat(underTest).isEqualTo(initialHeaders);
    }

    @Test
    public void createInstanceOfValidHeaderJsonObjectWorksAsExpected() {
        final Map<String, String> allKnownHeaders = createMapContainingAllKnownHeaders();
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        allKnownHeaders.forEach(jsonObjectBuilder::set);
        final JsonObject headersJsonObject = jsonObjectBuilder.build();

        final MessageHeaders underTest = MessageHeadersBuilder.of(headersJsonObject).build();

        assertThat(underTest).isEqualTo(allKnownHeaders);
    }

    @Test
    public void putKeyValuePairWorksAsExpected() {
        final String key = "foo";
        final String value = "bar";

        final MessageHeaders underTest = MessageHeadersBuilder.newInstance(DIRECTION, THING_ID, SUBJECT)
                .putHeader(key, value)
                .build();

        assertThat(underTest).hasSize(4).contains(entry(key, value));
    }

    @Test
    public void tryToPutKnownHeaderWithInvalidValue() {
        final String key = MessageHeaderDefinition.STATUS_CODE.getKey();
        final String value = String.valueOf(42);

        final MessageHeadersBuilder underTest = MessageHeadersBuilder.newInstance(DIRECTION, THING_ID, SUBJECT);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.putHeader(key, value))
                .withMessage("<%s> is not a HTTP status code!", value, key)
                .withNoCause();
    }

    @Test
    public void getDirectionReturnsExpected() {
        final MessageHeaders underTest = MessageHeadersBuilder.newInstance(DIRECTION, THING_ID, SUBJECT).build();

        assertThat(underTest.getDirection()).isEqualTo(DIRECTION);
    }

    @Test
    public void getSubjectReturnsExpected() {
        final MessageHeaders underTest = MessageHeadersBuilder.newInstance(DIRECTION, THING_ID, SUBJECT).build();

        assertThat(underTest.getSubject()).isEqualTo(SUBJECT);
    }

    @Test
    public void getThingIdReturnsExpected() {
        final MessageHeaders underTest = MessageHeadersBuilder.newInstance(DIRECTION, THING_ID, SUBJECT).build();

        assertThat(underTest.getThingId()).isEqualTo(THING_ID);
    }

    @Test
    public void getFeatureIdReturnsExpected() {
        final MessageHeaders underTest = MessageHeadersBuilder.newInstance(DIRECTION, THING_ID, SUBJECT)
                .featureId(FEATURE_ID)
                .build();

        assertThat(underTest.getFeatureId()).contains(FEATURE_ID);
    }

    @Test
    public void getContentTypeReturnsExpected() {
        final MessageHeaders underTest = MessageHeadersBuilder.newInstance(DIRECTION, THING_ID, SUBJECT)
                .contentType(CONTENT_TYPE)
                .build();

        assertThat(underTest.getContentType()).contains(CONTENT_TYPE);
    }

    @Test
    public void getTimeoutReturnsExpected() {
        final MessageHeaders underTest = MessageHeadersBuilder.newInstance(DIRECTION, THING_ID, SUBJECT)
                .timeout(TIMEOUT)
                .build();

        assertThat(underTest.getTimeout()).contains(TIMEOUT);
    }

    @Test
    public void getTimestampReturnsExpected() {
        final MessageHeaders underTest = MessageHeadersBuilder.newInstance(DIRECTION, THING_ID, SUBJECT)
                .timestamp(TIMESTAMP)
                .build();

        assertThat(underTest.getTimestamp()).contains(OffsetDateTime.parse(TIMESTAMP));
    }

    @Test
    public void getStatusCodeReturnsExpected() {
        final MessageHeaders underTest = MessageHeadersBuilder.newInstance(DIRECTION, THING_ID, SUBJECT)
                .statusCode(STATUS_CODE)
                .build();

        assertThat(underTest.getStatusCode()).contains(STATUS_CODE);
    }

    @Test
    public void getValidationUrlReturnsExpected() {
        final MessageHeaders underTest = MessageHeadersBuilder.newInstance(DIRECTION, THING_ID, SUBJECT)
                .validationUrl(VALIDATION_URL)
                .build();

        assertThat(underTest.getValidationUrl()).contains(VALIDATION_URL);
    }

    @Test
    public void getDirectionAlthoughItIsNotSet() {
        final MessageHeaders underTest = ImmutableMessageHeaders.of(DittoHeaders.empty());

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(underTest::getDirection)
                .withMessage("MessageHeaders did not contain a value for key <%s>!",
                        MessageHeaderDefinition.DIRECTION.getKey())
                .withNoCause();
    }

    @Test
    public void getSubjectAlthoughItIsNotSet() {
        final MessageHeaders underTest = ImmutableMessageHeaders.of(DittoHeaders.empty());

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(underTest::getSubject)
                .withMessage("MessageHeaders did not contain a value for key <%s>!",
                        MessageHeaderDefinition.SUBJECT.getKey())
                .withNoCause();
    }

    @Test
    public void getThingIdAlthoughItIsNotSet() {
        final MessageHeaders underTest = ImmutableMessageHeaders.of(DittoHeaders.empty());

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(underTest::getThingId)
                .withMessage("MessageHeaders did not contain a value for key <%s>!",
                        MessageHeaderDefinition.THING_ID.getKey())
                .withNoCause();
    }

    private static Map<String, String> createMapContainingAllKnownHeaders() {
        final Map<String, String> result = new HashMap<>();
        result.put(DittoHeaderDefinition.AUTHORIZATION_SUBJECTS.getKey(), toJsonArray(AUTH_SUBJECTS).toString());
        result.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), "knownCorrelationId");
        result.put(DittoHeaderDefinition.SCHEMA_VERSION.getKey(), KNOWN_SCHEMA_VERSION.toString());
        result.put(DittoHeaderDefinition.SOURCE.getKey(), KNOWN_SOURCE);
        result.put(DittoHeaderDefinition.CHANNEL.getKey(), KNOWN_CHANNEL);
        result.put(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey(), String.valueOf(KNOWN_RESPONSE_REQUIRED));
        result.put(DittoHeaderDefinition.DRY_RUN.getKey(), String.valueOf(false));
        result.put(DittoHeaderDefinition.READ_SUBJECTS.getKey(), toJsonArray(KNOWN_READ_SUBJECTS).toString());
        result.put(MessageHeaderDefinition.DIRECTION.getKey(), DIRECTION.toString());
        result.put(MessageHeaderDefinition.SUBJECT.getKey(), SUBJECT);
        result.put(MessageHeaderDefinition.THING_ID.getKey(), THING_ID);
        result.put(MessageHeaderDefinition.FEATURE_ID.getKey(), FEATURE_ID);
        result.put(MessageHeaderDefinition.TIMEOUT.getKey(), String.valueOf(TIMEOUT.getSeconds()));
        result.put(MessageHeaderDefinition.TIMESTAMP.getKey(), TIMESTAMP);
        result.put(MessageHeaderDefinition.STATUS_CODE.getKey(), String.valueOf(STATUS_CODE.toInt()));
        result.put(DittoHeaderDefinition.CONTENT_TYPE.getKey(), CONTENT_TYPE);
        result.put(MessageHeaderDefinition.VALIDATION_URL.getKey(), VALIDATION_URL);

        return result;
    }

    private static JsonArray toJsonArray(final Iterable<String> stringCollection) {
        final JsonArrayBuilder jsonArrayBuilder = JsonFactory.newArrayBuilder();
        stringCollection.forEach(jsonArrayBuilder::add);
        return jsonArrayBuilder.build();
    }

}
