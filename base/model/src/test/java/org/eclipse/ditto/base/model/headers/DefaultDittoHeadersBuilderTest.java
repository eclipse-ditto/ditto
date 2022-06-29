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
package org.eclipse.ditto.base.model.headers;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.entry;
import static org.eclipse.ditto.base.model.headers.DefaultDittoHeadersBuilder.of;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.assertions.DittoBaseAssertions;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeader;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeaderKey;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelectorInvalidException;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.headers.DefaultDittoHeadersBuilder}.
 */
public final class DefaultDittoHeadersBuilderTest {

    private static final List<String> AUTHORIZATION_SUBJECTS = Arrays.asList("Foo", "Bar");
    private static final String CORRELATION_ID = "correlationId";
    private static final JsonSchemaVersion JSON_SCHEMA_VERSION = JsonSchemaVersion.V_2;
    private static final String CHANNEL = "twin";
    private static final Collection<AuthorizationSubject> READ_SUBJECTS = Arrays.asList(
            AuthorizationSubject.newInstance("read"), AuthorizationSubject.newInstance("subjects"));

    private DefaultDittoHeadersBuilder underTest = null;

    @Before
    public void setUp() {
        underTest = DefaultDittoHeadersBuilder.newInstance();
    }

    @Test
    public void emptyReturnsEmptyDittoHeaders() {
        final DittoHeaders dittoHeaders = DittoHeaders.empty();

        DittoBaseAssertions.assertThat(dittoHeaders)
                .hasNoCorrelationId()
                .hasNoSchemaVersion()
                .hasNoAuthorizationSubjects()
                .hasNoReadGrantedSubjects();
    }

    @Test
    public void setNullCorrelationIdRemovesCorrelationId() {
        underTest.correlationId(CORRELATION_ID);
        underTest.correlationId(null);
        final DittoHeaders dittoHeaders = underTest.build();

        assertThat(dittoHeaders.getCorrelationId()).isEmpty();
    }

    @Test(expected = IllegalArgumentException.class)
    public void tryToSetEmptyCorrelationId() {
        underTest.correlationId("");
    }

    @Test
    public void setNullSchemaVersionRemovesSchemaVersion() {
        underTest.schemaVersion(JSON_SCHEMA_VERSION);
        underTest.schemaVersion(null);
        final DittoHeaders dittoHeaders = underTest.build();

        assertThat(dittoHeaders.getSchemaVersion()).isEmpty();
    }

    @Test
    public void buildReturnsExpected() {
        final DittoHeaders dittoHeaders = underTest.correlationId(CORRELATION_ID)
                .readGrantedSubjects(READ_SUBJECTS)
                .schemaVersion(JSON_SCHEMA_VERSION)
                .build();

        DittoBaseAssertions.assertThat(dittoHeaders)
                .hasCorrelationId(CORRELATION_ID)
                .hasSchemaVersion(JSON_SCHEMA_VERSION)
                .hasReadGrantedSubject(AuthorizationSubject.newInstance("read"),
                        AuthorizationSubject.newInstance("subjects"));
    }

    @Test
    public void constructBuilderFromHeadersWorksExpected() {
        final DittoHeaders dittoHeaders = underTest.correlationId(CORRELATION_ID)
                .readGrantedSubjects(READ_SUBJECTS)
                .schemaVersion(JSON_SCHEMA_VERSION)
                .build();

        final DittoHeaders anotherDittoHeaders = of(dittoHeaders).build();

        assertThat(dittoHeaders).isEqualTo(anotherDittoHeaders);
    }

    @Test
    public void buildWithEmptyCorrelationIdThrowsIllegalArgumentException() {
        DittoBaseAssertions.assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DittoHeaders.newBuilder().correlationId(""));
    }

    @Test
    public void buildWithEmptyCorrelationIdFromMapThrowsDittoHeaderInvalidException() {
        final Map<String, String> headerMap = new HashMap<>();
        headerMap.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), "");
        DittoBaseAssertions.assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> DittoHeaders.of(headerMap));
    }

    @Test
    public void jsonRepresentationOfEmptyDittoHeadersIsExpected() {
        final DittoHeaders emptyDittoHeaders = DittoHeaders.empty();
        final JsonObject jsonObject = emptyDittoHeaders.toJson();

        assertThat(jsonObject).isEmpty();
    }

    @Test
    public void jsonRepresentationOfDittoHeadersWithNullCorrelationIdHasNoCorrelationId() {
        final DittoHeaders expectedDittoHeaders = DittoHeaders.newBuilder()
                .correlationId(null)
                .build();
        final JsonObject jsonHeadersWithNullCorrelationId = JsonObject.newBuilder()
                .set(DittoHeaderDefinition.CORRELATION_ID.getKey(), JsonValue.nullLiteral())
                .build();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder(jsonHeadersWithNullCorrelationId).build();

        assertThat(dittoHeaders).isEqualTo(expectedDittoHeaders);
        assertThat(dittoHeaders.getCorrelationId()).isEmpty();
    }

    @Test
    public void jsonRepresentationOfDittoHeadersWithCorrelationIdOnlyIsExpected() {
        final DittoHeaders dittoHeaders = underTest.correlationId(CORRELATION_ID).build();
        final JsonObject jsonObject = dittoHeaders.toJson();

        assertThat(jsonObject)
                .hasSize(1)
                .contains(JsonFactory.newKey(DittoHeaderDefinition.CORRELATION_ID.getKey()), CORRELATION_ID);
    }

    @Test
    public void jsonRepresentationOfDittoHeadersWithSchemaVersionOnlyIsExpected() {
        final DittoHeaders dittoHeaders = underTest.schemaVersion(JSON_SCHEMA_VERSION).build();
        final JsonObject jsonObject = dittoHeaders.toJson();

        assertThat(jsonObject)
                .hasSize(1)
                .contains(JsonFactory.newKey(DittoHeaderDefinition.SCHEMA_VERSION.getKey()),
                        JsonFactory.newValue(JSON_SCHEMA_VERSION.toInt()));
    }

    @Test
    public void jsonRepresentationOfDittoHeadersWithChannelOnlyIsExpected() {
        final DittoHeaders dittoHeaders = underTest.channel(CHANNEL).build();
        final JsonObject jsonObject = dittoHeaders.toJson();

        assertThat(jsonObject)
                .hasSize(1)
                .contains(JsonFactory.newKey(DittoHeaderDefinition.CHANNEL.getKey()), CHANNEL);
    }

    @Test
    public void jsonRepresentationOfDittoHeadersWithResponseRequiredOnlyIsExpected() {
        final DittoHeaders dittoHeaders = underTest.responseRequired(false).build();
        final JsonObject jsonObject = dittoHeaders.toJson();

        assertThat(jsonObject)
                .hasSize(1)
                .contains(JsonKey.of(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey()), JsonFactory.newValue(false));
    }

    @Test
    public void jsonRepresentationOfDittoHeadersWithReadSubjectsOnlyIsExpected() {
        final DittoHeaders dittoHeaders = underTest.readGrantedSubjects(READ_SUBJECTS).build();
        final JsonObject jsonObject = dittoHeaders.toJson();
        final JsonArray expectedReadSubjects = READ_SUBJECTS.stream()
                .map(AuthorizationSubject::getId)
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray());

        assertThat(jsonObject)
                .hasSize(1)
                .contains(JsonFactory.newKey(DittoHeaderDefinition.READ_SUBJECTS.getKey()), expectedReadSubjects);
    }

    @Test
    public void tryToSetAuthSubjectsAsGenericKeyValuePairWithInvalidValue() {
        final String key = DittoHeaderDefinition.AUTHORIZATION_CONTEXT.getKey();
        final String value = AUTHORIZATION_SUBJECTS.get(0);

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.putHeader(key, value))
                .withMessage("The value '%s' of the header '%s' is not a valid JSON object.", value, key)
                .withNoCause();
    }

    @Test
    public void putValidHeaderWorksAsExpected() {
        final String key = "foo";
        final String value = "bar";
        underTest.putHeader(key, value);

        final DittoHeaders dittoHeaders = underTest.build();

        assertThat(dittoHeaders).containsOnly(entry(key, value));
    }

    @Test
    public void tryToCreateInstanceWithMapContainingInvalidHeader() {
        final String readSubjectsKey = DittoHeaderDefinition.READ_SUBJECTS.getKey();
        final String invalidJsonArrayString = "['Frank',42,'Grimes']";
        final Map<String, String> initialHeaders = new HashMap<>();
        initialHeaders.put(DittoHeaderDefinition.DRY_RUN.getKey(), "true");
        initialHeaders.put("foo", "bar");
        initialHeaders.put(readSubjectsKey, invalidJsonArrayString);

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> of(initialHeaders))
                .withMessage("The value '%s' of the header '%s' is not a valid JSON array.", invalidJsonArrayString,
                        readSubjectsKey)
                .withNoCause();
    }

    @Test
    public void tryToCreateInstanceWithJsonObjectContainingInvalidHeader() {
        final String schemaVersionKey = DittoHeaderDefinition.SCHEMA_VERSION.getKey();
        final String invalidSchemaVersionValue = "meh";

        final JsonObject headersJsonObject = JsonFactory.newObjectBuilder()
                .set(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey(), false)
                .set(schemaVersionKey, invalidSchemaVersionValue)
                .set("foo", "bar")
                .build();

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> of(headersJsonObject))
                .withMessage("The value '%s' of the header '%s' is not a valid int.", invalidSchemaVersionValue,
                        schemaVersionKey)
                .withNoCause();
    }

    @Test
    public void removeValueWorksAsExpected() {
        final String rsKey = DittoHeaderDefinition.READ_SUBJECTS.getKey();

        final DittoHeaders dittoHeaders = underTest.readGrantedSubjects(READ_SUBJECTS)
                .dryRun(true)
                .correlationId(CORRELATION_ID)
                .removeHeader(rsKey)
                .build();

        assertThat(dittoHeaders)
                .hasSize(2)
                .doesNotContainKeys(rsKey);
    }

    @Test
    public void removePreconditionHeaders() {
        final DittoHeaders dittoHeaders = underTest
                .ifMatch(EntityTagMatchers.fromStrings("\"test\""))
                .ifNoneMatch(EntityTagMatchers.fromStrings("\"test2\""))
                .removePreconditionHeaders()
                .build();

        assertThat(dittoHeaders).isEmpty();
    }

    @Test
    public void tryToPutMapWithInvalidMessageHeader() {
        final String key = DittoHeaderDefinition.SCHEMA_VERSION.getKey();
        final String invalidValue = "bar";

        final Map<String, String> invalidHeaders = new HashMap<>();
        invalidHeaders.put(DittoHeaderDefinition.CONTENT_TYPE.getKey(), "application/json");
        invalidHeaders.put(key, invalidValue);

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.putHeaders(invalidHeaders))
                .withMessage("The value '%s' of the header '%s' is not a valid int.", invalidValue, key)
                .withNoCause();
    }

    @Test
    public void ensureResponseRequiredIsFalseWhenSet() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .responseRequired(false)
                .build();

        DittoBaseAssertions.assertThat(dittoHeaders)
                .hasIsResponseRequired(false);
    }

    @Test
    public void ensureAllowPolicyLockoutIsFalseWhenSet() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .allowPolicyLockout(true)
                .build();
        DittoBaseAssertions.assertThat(dittoHeaders).hasAllowPolicyLockout(true);
    }

    @Test
    public void ensureAllowPolicyLockoutIsFalseWhenNotSet() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().build();
        DittoBaseAssertions.assertThat(dittoHeaders).hasAllowPolicyLockout(false);
    }

    @Test
    public void ensureResponseRequiredStaysFalseEvenWhenAcksAreRequested() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .responseRequired(false)
                .acknowledgementRequest(AcknowledgementRequest.of(AcknowledgementLabel.of("some-ack")))
                .build();

        assertThat(dittoHeaders)
                .containsEntry(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey(), Boolean.FALSE.toString());
    }

    @Test
    public void putValidMetadata() {
        final MetadataHeaderKey metadataKey = MetadataHeaderKey.parse("*/issuedAt");
        final JsonValue metadataValue = JsonValue.of(String.valueOf(Instant.now()));
        final MetadataHeaders expected = MetadataHeaders.newInstance();
        expected.add(MetadataHeader.of(metadataKey, metadataValue));

        final DittoHeaders dittoHeaders = underTest.putMetadata(metadataKey, metadataValue).build();

        assertThat(dittoHeaders.getMetadataHeadersToPut()).isEqualTo(expected);
    }

    @Test
    public void putValidMetadataAsCharSequence() {
        final MetadataHeaders metadataHeaders = MetadataHeaders.newInstance();
        metadataHeaders.add(
                MetadataHeader.of(MetadataHeaderKey.parse("*/issuedAt"), JsonValue.of(String.valueOf(Instant.now()))));

        final DittoHeaders dittoHeaders = underTest
                .putHeader(DittoHeaderDefinition.PUT_METADATA.getKey(), metadataHeaders.toJsonString())
                .build();

        assertThat(dittoHeaders.getMetadataHeadersToPut()).isEqualTo(metadataHeaders);
    }

    @Test
    public void putHeadersContainingValidMetadata() {
        final MetadataHeaderKey metadataKey = MetadataHeaderKey.parse("*/issuedAt");
        final JsonValue metadataValue = JsonValue.of(String.valueOf(Instant.now()));
        final Map<String, String> headerMap = new HashMap<>();
        headerMap.put("foo", "bar");
        headerMap.put(metadataKey.toString(), metadataValue.toString());
        headerMap.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), String.valueOf(UUID.randomUUID()));

        final DittoHeaders dittoHeaders = underTest.putHeaders(headerMap).build();

        assertThat(dittoHeaders.asCaseSensitiveMap()).isEqualTo(headerMap);
    }

    @Test
    public void createBuilderFromMapContainingValidMetadata() {
        final MetadataHeaderKey metadataKey = MetadataHeaderKey.parse("*/issuedAt");
        final JsonValue metadataValue = JsonValue.of(String.valueOf(Instant.now()));
        final Map<String, String> headerMap = new HashMap<>();
        headerMap.put("foo", "bar");
        headerMap.put(metadataKey.toString(), metadataValue.toString());
        headerMap.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), String.valueOf(UUID.randomUUID()));

        final DefaultDittoHeadersBuilder underTest = of(headerMap);

        assertThat(underTest.build().asCaseSensitiveMap()).isEqualTo(headerMap);
    }

    @Test
    public void createBuilderFromMapContainingInvalidMetadataValue() {
        final String invalidValue = String.valueOf(Instant.now());
        final Map<String, String> headerMap = new HashMap<>();
        headerMap.put("foo", "bar");
        headerMap.put(DittoHeaderDefinition.PUT_METADATA.getKey(), invalidValue);
        headerMap.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), String.valueOf(UUID.randomUUID()));

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> DefaultDittoHeadersBuilder.of(headerMap))
                .withMessage("The value '%s' of the header '%s' is not a valid MetadataHeaders.", invalidValue,
                        DittoHeaderDefinition.PUT_METADATA.getKey())
                .satisfies(dittoHeaderInvalidException -> assertThat(dittoHeaderInvalidException.getDescription())
                        .hasValueSatisfying(description -> assertThat(description)
                                .startsWith("Failed to parse JSON string '" + invalidValue + "'!")))
                .withCauseInstanceOf(JsonParseException.class);
    }

    @Test
    public void putInvalidGetMetadataHeaderAsCharSequence() {
        final String invalidGetMetadata = "features(f1,f2";

        final Map<String, String> headerMap = new HashMap<>();
        headerMap.put("foo", "bar");
        headerMap.put(DittoHeaderDefinition.GET_METADATA.getKey(), invalidGetMetadata);
        headerMap.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), String.valueOf(UUID.randomUUID()));

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.putHeaders(headerMap))
                .withMessage("The value '%s' of the header 'get-metadata' is not a valid field selector.", invalidGetMetadata)
                .satisfies(dittoHeaderInvalidException -> assertThat(dittoHeaderInvalidException.getDescription())
                        .hasValueSatisfying(description -> assertThat(description)
                                .startsWith("The field selector <" + invalidGetMetadata + "> is invalid!")))
                .withCauseInstanceOf(JsonFieldSelectorInvalidException.class);

    }

    @Test
    public void putValidGetMetadataHeaderAsCharSequence() {
        final String getMetadata = "features/f1/properties/p1/key";

        final DittoHeaders dittoHeaders = underTest
                .putHeader(DittoHeaderDefinition.GET_METADATA.getKey(), getMetadata)
                .build();

        final Set<JsonPointer> expected = new HashSet<>(Collections.singletonList(JsonPointer.of(getMetadata)));

        assertThat(dittoHeaders.getMetadataFieldsToGet()).isEqualTo(expected);
    }

    @Test
    public void setValidTwinChannelCharSequence() {
        final DittoHeaders dittoHeaders = DefaultDittoHeadersBuilder.newInstance()
                .channel(" twIn ")
                .build();

        assertThat(dittoHeaders).containsEntry(DittoHeaderDefinition.CHANNEL.getKey(), "twin");
    }

    @Test
    public void setValidLiveChannelCharSequence() {
        final DittoHeaders dittoHeaders = DefaultDittoHeadersBuilder.newInstance()
                .channel("LIve   ")
                .build();

        assertThat(dittoHeaders).containsEntry(DittoHeaderDefinition.CHANNEL.getKey(), "live");
    }

    @Test
    public void setInvalidChannelCharSequence() {
        final String invalidChannelCharSequence = "quuz";
        final DefaultDittoHeadersBuilder defaultDittoHeadersBuilder = DefaultDittoHeadersBuilder.newInstance();

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> defaultDittoHeadersBuilder.channel(invalidChannelCharSequence))
                .withMessage("The value '%s' of the header '%s' is not a valid channel.",
                        invalidChannelCharSequence,
                        DittoHeaderDefinition.CHANNEL)
                .withNoCause();
    }

    @Test
    public void putInvalidChannelCharSequenceAsHeader() {
        final String channelKey = DittoHeaderDefinition.CHANNEL.getKey();
        final String invalidChannelCharSequence = "qux";
        final DefaultDittoHeadersBuilder defaultDittoHeadersBuilder = DefaultDittoHeadersBuilder.newInstance();

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> defaultDittoHeadersBuilder.putHeader(channelKey, invalidChannelCharSequence))
                .withMessage("The value '%s' of the header '%s' is not a valid channel.",
                        invalidChannelCharSequence,
                        channelKey)
                .withNoCause();
    }

}
