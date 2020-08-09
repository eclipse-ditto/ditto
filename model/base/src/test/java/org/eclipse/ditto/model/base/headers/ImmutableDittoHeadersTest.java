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
package org.eclipse.ditto.model.base.headers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.entry;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.assertj.core.util.Lists;
import org.assertj.core.util.Maps;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.model.base.common.ResponseType;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableDittoHeaders}.
 */
public final class ImmutableDittoHeadersTest {

    private static final Collection<String>
            AUTH_SUBJECTS_WITHOUT_DUPLICATES = Arrays.asList("test:JohnOldman", "test:FrankGrimes");
    private static final AuthorizationContext AUTH_CONTEXT_WITHOUT_DUPLICATES =
            AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                    AUTH_SUBJECTS_WITHOUT_DUPLICATES.stream()
                            .map(AuthorizationSubject::newInstance)
                            .collect(Collectors.toList()));
    private static final Collection<String> AUTH_SUBJECTS =
            Arrays.asList("test:JohnOldman", "test:FrankGrimes", "JohnOldman", "FrankGrimes");
    private static final AuthorizationContext AUTH_CONTEXT =
            AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                    AUTH_SUBJECTS.stream()
                            .map(AuthorizationSubject::newInstance)
                            .collect(Collectors.toList()));
    private static final String KNOWN_CORRELATION_ID = "knownCorrelationId";
    private static final JsonSchemaVersion KNOWN_SCHEMA_VERSION = JsonSchemaVersion.V_2;
    private static final String KNOWN_READ_SUBJECT_WITHOUT_ISSUER = "knownReadSubject";
    private static final String KNOWN_READ_SUBJECT = KNOWN_READ_SUBJECT_WITHOUT_ISSUER;
    private static final String KNOWN_CHANNEL = "live";
    private static final boolean KNOWN_RESPONSE_REQUIRED = true;
    private static final EntityTagMatchers KNOWN_IF_MATCH =
            EntityTagMatchers.fromCommaSeparatedString("\"oneValue\",\"anotherValue\"");
    private static final EntityTagMatchers KNOWN_IF_NONE_MATCH =
            EntityTagMatchers.fromCommaSeparatedString("\"notOneValue\",\"notAnotherValue\"");
    private static final EntityTag KNOWN_ETAG = EntityTag.fromString("\"-12124212\"");
    private static final Collection<String> KNOWN_READ_SUBJECTS = Lists.list(KNOWN_READ_SUBJECT);
    private static final Collection<AuthorizationSubject> KNOWN_READ_GRANTED_SUBJECTS =
            Lists.list(AuthorizationModelFactory.newAuthSubject("knownGrantedSubject1"),
                    AuthorizationModelFactory.newAuthSubject("knownGrantedSubject2"));
    private static final Collection<AuthorizationSubject> KNOWN_READ_REVOKED_SUBJECTS =
            Lists.list(AuthorizationModelFactory.newAuthSubject("knownRevokedSubject1"),
                    AuthorizationModelFactory.newAuthSubject("knownRevokedSubject2"));
    private static final String KNOWN_CONTENT_TYPE = "application/json";
    private static final String KNOWN_REPLY_TO = "replies";
    private static final String KNOWN_ORIGIN = "knownOrigin";
    private static final String KNOWN_REPLY_TARGET = "5";
    private static final String KNOWN_MAPPER = "knownMapper";
    private static final String KNOWN_ORIGINATOR = "known:originator";
    private static final Duration KNOWN_TIMEOUT = Duration.ofSeconds(6);
    private static final AcknowledgementRequest KNOWN_ACK_REQUEST =
            AcknowledgementRequest.of(AcknowledgementLabel.of("ack-label-1"));
    private static final List<AcknowledgementRequest> KNOWN_ACK_REQUESTS = Lists.list(KNOWN_ACK_REQUEST);
    private static final List<ResponseType> KNOWN_EXPECTED_RESPONSE_TYPES =
            Lists.list(ResponseType.RESPONSE, ResponseType.NACK);
    private static final String KNOWN_ENTITY_ID = "known:entityId";
    private static final String KNOWN_WWW_AUTHENTICATION = "known:www-authentication";
    private static final String KNOWN_LOCATION = "known:location";
    private static final String KNOWN_CONNECTION_ID = "known-connection-id";

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableDittoHeaders.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableDittoHeaders.class)
                .withNonnullFields("headers")
                .verify();
    }

    @Test
    public void settingAllKnownHeadersWorksAsExpected() {
        final Map<String, String> expectedHeaderMap = createMapContainingAllKnownHeaders();

        final DittoHeaders underTest = DefaultDittoHeadersBuilder.newInstance()
                .channel(KNOWN_CHANNEL)
                .authorizationContext(AUTH_CONTEXT)
                .correlationId(KNOWN_CORRELATION_ID)
                .readSubjects(KNOWN_READ_SUBJECTS)
                .readRevokedSubjects(KNOWN_READ_REVOKED_SUBJECTS)
                .responseRequired(KNOWN_RESPONSE_REQUIRED)
                .dryRun(false)
                .schemaVersion(KNOWN_SCHEMA_VERSION)
                .eTag(KNOWN_ETAG)
                .ifMatch(KNOWN_IF_MATCH)
                .ifNoneMatch(KNOWN_IF_NONE_MATCH)
                .origin(KNOWN_ORIGIN)
                .contentType(KNOWN_CONTENT_TYPE)
                .replyTarget(Integer.valueOf(KNOWN_REPLY_TARGET))
                .inboundPayloadMapper(KNOWN_MAPPER)
                .putHeader(DittoHeaderDefinition.ENTITY_ID.getKey(), KNOWN_ENTITY_ID)
                .putHeader(DittoHeaderDefinition.ORIGINATOR.getKey(), KNOWN_ORIGINATOR)
                .putHeader(DittoHeaderDefinition.REPLY_TO.getKey(), KNOWN_REPLY_TO)
                .putHeader(DittoHeaderDefinition.WWW_AUTHENTICATE.getKey(), KNOWN_WWW_AUTHENTICATION)
                .putHeader(DittoHeaderDefinition.LOCATION.getKey(), KNOWN_LOCATION)
                .acknowledgementRequests(KNOWN_ACK_REQUESTS)
                .timeout(KNOWN_TIMEOUT)
                .putHeader(DittoHeaderDefinition.CONNECTION_ID.getKey(), KNOWN_CONNECTION_ID)
                .expectedResponseTypes(KNOWN_EXPECTED_RESPONSE_TYPES)
                .build();

        assertThat(underTest).isEqualTo(expectedHeaderMap);
    }

    @Test
    public void createInstanceOfValidHeaderMapWorksAsExpected() {
        final Map<String, String> initialHeaders = createMapContainingAllKnownHeaders();

        final DittoHeaders underTest = DittoHeaders.newBuilder(initialHeaders).build();

        assertThat(underTest).isEqualTo(initialHeaders);
    }

    @Test
    public void createInstanceOfValidHeaderJsonObjectWorksAsExpected() {
        final Map<String, String> allKnownHeaders = createMapContainingAllKnownHeaders();
        final JsonObject headersJsonObject = toJsonObject(allKnownHeaders);

        final DittoHeaders underTest = DittoHeaders.newBuilder(headersJsonObject).build();

        assertThat(underTest).isEqualTo(allKnownHeaders);
    }

    @Test
    public void createInstanceContainingArbitraryKeyValuePair() {
        final String fooKey = "foo";
        final String barValue = "bar";

        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().putHeader(fooKey, barValue).build();

        assertThat(dittoHeaders).containsOnly(entry(fooKey, barValue));
    }

    @Test
    public void getCorrelationIdReturnsExpected() {
        final DittoHeaders underTest = DittoHeaders.newBuilder().correlationId(KNOWN_CORRELATION_ID).build();

        assertThat(underTest.getCorrelationId()).contains(KNOWN_CORRELATION_ID);
    }

    @Test
    public void getSchemaVersionReturnsExpected() {
        final DittoHeaders underTest = DittoHeaders.newBuilder().schemaVersion(KNOWN_SCHEMA_VERSION).build();

        assertThat(underTest.getSchemaVersion()).contains(KNOWN_SCHEMA_VERSION);
    }

    @Test
    public void getAuthorizationContextReturnsExpected() {
        final DittoHeaders underTest = DittoHeaders.newBuilder().authorizationContext(AUTH_CONTEXT).build();

        assertThat(underTest.getAuthorizationContext()).isEqualTo(AUTH_CONTEXT);
    }

    @Test
    public void getAuthorizationSubjectsReturnsExpected() {
        final List<AuthorizationSubject> authSubjects = AUTH_SUBJECTS.stream()
                .map(AuthorizationModelFactory::newAuthSubject)
                .collect(Collectors.toList());
        final AuthorizationContext authContext = AuthorizationModelFactory.newAuthContext(
                DittoAuthorizationContextType.UNSPECIFIED, authSubjects);

        final DittoHeaders underTest = DittoHeaders.newBuilder().authorizationContext(authContext).build();

        assertThat(underTest.getAuthorizationContext()).containsExactlyElementsOf(authSubjects);
    }

    @Test
    public void getReadSubjectsReturnsExpected() {
        final DittoHeaders underTest = DittoHeaders.newBuilder().readSubjects(KNOWN_READ_SUBJECTS).build();

        assertThat(underTest.getReadSubjects()).containsExactlyInAnyOrderElementsOf(KNOWN_READ_SUBJECTS);
    }

    @Test
    public void getReadGrantedSubjectsReturnsExpected() {
        final DittoHeaders underTest = DittoHeaders.newBuilder()
                .readGrantedSubjects(KNOWN_READ_GRANTED_SUBJECTS)
                .build();

        assertThat(underTest.getReadGrantedSubjects()).containsExactlyInAnyOrderElementsOf(KNOWN_READ_GRANTED_SUBJECTS);
    }

    @Test
    public void getRevokedSubjectsReturnsExpected() {
        final DittoHeaders underTest = DittoHeaders.newBuilder()
                .readRevokedSubjects(KNOWN_READ_REVOKED_SUBJECTS)
                .build();

        assertThat(underTest.getReadRevokedSubjects()).containsExactlyInAnyOrderElementsOf(KNOWN_READ_REVOKED_SUBJECTS);
    }

    @Test
    public void isResponseRequiredIsTrueByDefault() {
        final DittoHeaders underTest = DittoHeaders.empty();

        assertThat(underTest.isResponseRequired()).isTrue();
    }

    @Test
    public void isResponseRequiredReturnsFalseLikeSet() {
        final DittoHeaders underTest = DittoHeaders.newBuilder().responseRequired(false).build();

        assertThat(underTest.isResponseRequired()).isFalse();
    }

    @Test
    public void isResponseRequiredReturnsTrueLikeSet() {
        final DittoHeaders underTest = DittoHeaders.newBuilder().responseRequired(true).build();

        assertThat(underTest.isResponseRequired()).isTrue();
    }

    @Test
    public void isResponseRequiredStaysFalseEvenIfRequiredAckLabelsAreSet() {
        final DittoHeaders underTest = DittoHeaders.newBuilder()
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.TWIN_PERSISTED))
                .responseRequired(false)
                .build();

        assertThat(underTest.isResponseRequired()).isFalse();
    }

    @Test
    public void isDryRunIsFalseByDefault() {
        final DittoHeaders underTest = DittoHeaders.empty();

        assertThat(underTest.isDryRun()).isFalse();
    }

    @Test
    public void isDryRunReturnsExpected() {
        final DittoHeaders underTest = DittoHeaders.newBuilder().dryRun(true).build();

        assertThat(underTest.isDryRun()).isTrue();
    }

    @Test
    public void getRequestedAckLabelsReturnsExpected() {
        final DittoHeaders underTest = DittoHeaders.newBuilder()
                .acknowledgementRequests(KNOWN_ACK_REQUESTS)
                .build();

        assertThat(underTest.getAcknowledgementRequests()).containsExactlyInAnyOrderElementsOf(KNOWN_ACK_REQUESTS);
    }

    @Test
    public void timeoutIsSerializedAsString() {
        final int durationAmountSeconds = 2;
        final Duration timeout = Duration.ofSeconds(durationAmountSeconds);

        final DittoHeaders underTest = DittoHeaders.newBuilder()
                .timeout(timeout)
                .build();

        final JsonObject jsonObject = underTest.toJson();

        assertThat(jsonObject.getValue(DittoHeaderDefinition.TIMEOUT.getKey()))
                .contains(JsonValue.of(durationAmountSeconds * 1000 + "ms"));
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject expectedHeadersJsonObject = JsonFactory.newObjectBuilder()
                .set(DittoHeaderDefinition.AUTHORIZATION_CONTEXT.getKey(), AUTH_CONTEXT_WITHOUT_DUPLICATES.toJson())
                .set(DittoHeaderDefinition.CORRELATION_ID.getKey(), KNOWN_CORRELATION_ID)
                .set(DittoHeaderDefinition.SCHEMA_VERSION.getKey(), KNOWN_SCHEMA_VERSION.toInt())
                .set(DittoHeaderDefinition.CHANNEL.getKey(), KNOWN_CHANNEL)
                .set(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey(), KNOWN_RESPONSE_REQUIRED)
                .set(DittoHeaderDefinition.DRY_RUN.getKey(), false)
                .set(DittoHeaderDefinition.READ_SUBJECTS.getKey(), stringCollectionToJsonArray(KNOWN_READ_SUBJECTS))
                .set(DittoHeaderDefinition.READ_REVOKED_SUBJECTS.getKey(),
                        authorizationSubjectsToJsonArray(KNOWN_READ_REVOKED_SUBJECTS))
                .set(DittoHeaderDefinition.IF_MATCH.getKey(), KNOWN_IF_MATCH.toString())
                .set(DittoHeaderDefinition.IF_NONE_MATCH.getKey(), KNOWN_IF_NONE_MATCH.toString())
                .set(DittoHeaderDefinition.ETAG.getKey(), KNOWN_ETAG.toString())
                .set(DittoHeaderDefinition.ORIGIN.getKey(), KNOWN_ORIGIN)
                .set(DittoHeaderDefinition.CONTENT_TYPE.getKey(), KNOWN_CONTENT_TYPE)
                .set(DittoHeaderDefinition.REPLY_TARGET.getKey(), Integer.parseInt(KNOWN_REPLY_TARGET))
                .set(DittoHeaderDefinition.INBOUND_PAYLOAD_MAPPER.getKey(), KNOWN_MAPPER)
                .set(DittoHeaderDefinition.ORIGINATOR.getKey(), KNOWN_ORIGINATOR)
                .set(DittoHeaderDefinition.REQUESTED_ACKS.getKey(), ackRequestsToJsonArray(KNOWN_ACK_REQUESTS))
                .set(DittoHeaderDefinition.TIMEOUT.getKey(), JsonValue.of(KNOWN_TIMEOUT.toMillis() + "ms"))
                .set(DittoHeaderDefinition.ENTITY_ID.getKey(), KNOWN_ENTITY_ID)
                .set(DittoHeaderDefinition.REPLY_TO.getKey(), KNOWN_REPLY_TO)
                .set(DittoHeaderDefinition.WWW_AUTHENTICATE.getKey(), KNOWN_WWW_AUTHENTICATION)
                .set(DittoHeaderDefinition.LOCATION.getKey(), KNOWN_LOCATION)
                .set(DittoHeaderDefinition.CONNECTION_ID.getKey(), KNOWN_CONNECTION_ID)
                .set(DittoHeaderDefinition.EXPECTED_RESPONSE_TYPES.getKey(),
                        expectedResponseTypesToJsonArray(KNOWN_EXPECTED_RESPONSE_TYPES))
                .build();
        final Map<String, String> allKnownHeaders = createMapContainingAllKnownHeaders();

        final DittoHeaders underTest = DittoHeaders.newBuilder(allKnownHeaders).build();
        final JsonObject actualHeadersJsonObject = underTest.toJson();

        assertThat(actualHeadersJsonObject).isEqualTo(expectedHeadersJsonObject);
    }

    @Test
    public void removesDuplicatedAuthSubjectsFromHeaderMap() {
        final String expectedWithoutDups = AUTH_CONTEXT_WITHOUT_DUPLICATES.toJsonString();
        final String withDups = AUTH_CONTEXT.toJsonString();

        final Map<String, String> headersWithDuplicatedAuthSubjects = new HashMap<>();
        headersWithDuplicatedAuthSubjects.put(DittoHeaderDefinition.AUTHORIZATION_CONTEXT.getKey(), withDups);

        final ImmutableDittoHeaders dittoHeaders = ImmutableDittoHeaders.of(headersWithDuplicatedAuthSubjects);

        assertThat(dittoHeaders.get(DittoHeaderDefinition.AUTHORIZATION_CONTEXT.getKey())).isEqualTo(
                expectedWithoutDups);

    }

    @Test
    public void removesDuplicatedAuthSubjectsFromJsonRepresentation() {
        final String withDups = AUTH_CONTEXT.toJsonString();
        final JsonObject expected = JsonFactory.newObjectBuilder()
                .set(DittoHeaderDefinition.AUTHORIZATION_CONTEXT.getKey(), AUTH_CONTEXT_WITHOUT_DUPLICATES.toJson())
                .build();

        final Map<String, String> headersWithDuplicatedAuthSubjects = new HashMap<>();
        headersWithDuplicatedAuthSubjects.put(DittoHeaderDefinition.AUTHORIZATION_CONTEXT.getKey(), withDups);

        final ImmutableDittoHeaders dittoHeaders = ImmutableDittoHeaders.of(headersWithDuplicatedAuthSubjects);

        assertThat(dittoHeaders.toJson()).isEqualTo(expected);

    }

    @Test
    public void putThrowsUnsupportedOperationException() {
        final DittoHeaders underTest = DittoHeaders.empty();

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> underTest.put("foo", "bar"))
                .withMessage("Ditto Headers are immutable!")
                .withNoCause();
    }

    @Test
    public void putAllThrowsUnsupportedOperationException() {
        final DittoHeaders underTest = DittoHeaders.empty();

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> underTest.putAll(createMapContainingAllKnownHeaders()))
                .withMessage("Ditto Headers are immutable!")
                .withNoCause();
    }

    @Test
    public void removeThrowsUnsupportedOperationException() {
        final DittoHeaders underTest = DittoHeaders.empty();

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> underTest.remove("foo"))
                .withMessage("Ditto Headers are immutable!")
                .withNoCause();
    }

    @Test
    public void clearThrowsUnsupportedOperationException() {
        final DittoHeaders underTest = DittoHeaders.empty();

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(underTest::clear)
                .withMessage("Ditto Headers are immutable!")
                .withNoCause();
    }

    @Test
    public void entrySetIsUnmodifiable() {
        final DittoHeaders underTest = DittoHeaders.empty();

        final Set<Map.Entry<String, String>> entrySet = underTest.entrySet();

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(entrySet::clear)
                .withMessage(null)
                .withNoCause();
    }

    /**
     * Verifies that really all known headers are in the map created by {@link #createMapContainingAllKnownHeaders()}
     */
    @Test
    public void allKnownHeadersAreTested() {
        final Set<String> testedHeaderNames = createMapContainingAllKnownHeaders().keySet();

        final String[] knownHeaderNames = Arrays.stream(DittoHeaderDefinition.values())
                .map(DittoHeaderDefinition::getKey)
                .distinct()
                .toArray(String[]::new);

        assertThat(testedHeaderNames).containsExactlyInAnyOrder(knownHeaderNames);
    }

    @Test
    public void tryToCheckIfEntriesSizeIsGreaterThanNegativeLong() {
        final long negativeLong = -3L;
        final ImmutableDittoHeaders underTest = ImmutableDittoHeaders.of(
                Maps.newHashMap(DittoHeaderDefinition.CORRELATION_ID.getKey(), KNOWN_CORRELATION_ID));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.isEntriesSizeGreaterThan(negativeLong))
                .withMessage("The size to compare to must not be negative but it was <%s>!", negativeLong)
                .withNoCause();
    }

    @Test
    public void entriesSizeIsGreaterThanZeroIfHeadersAreNotEmpty() {
        final ImmutableDittoHeaders underTest = ImmutableDittoHeaders.of(
                Maps.newHashMap(DittoHeaderDefinition.CORRELATION_ID.getKey(), KNOWN_CORRELATION_ID));

        assertThat(underTest.isEntriesSizeGreaterThan(0)).isTrue();
    }

    @Test
    public void entriesSizeIsNotGreaterThanZeroIfHeadersAreEmpty() {
        final ImmutableDittoHeaders underTest = ImmutableDittoHeaders.of(new HashMap<>());

        assertThat(underTest.isEntriesSizeGreaterThan(0)).isFalse();
    }

    @Test
    public void entriesSizeIsNotGreaterThanComparedSize() {
        final String key = DittoHeaderDefinition.CORRELATION_ID.getKey();
        final String value = KNOWN_CORRELATION_ID;
        final long comparisonSize = key.length() + value.length();
        final ImmutableDittoHeaders underTest = ImmutableDittoHeaders.of(Maps.newHashMap(key, value));

        assertThat(underTest.isEntriesSizeGreaterThan(comparisonSize)).isFalse();
    }

    @Test
    public void entriesSizeIsGreaterThanComparedSize() {
        final String key = DittoHeaderDefinition.CORRELATION_ID.getKey();
        final String value = KNOWN_CORRELATION_ID;
        final long entrySize = key.length() + value.length();
        final ImmutableDittoHeaders underTest = ImmutableDittoHeaders.of(Maps.newHashMap(key, value));
        final long comparisonSize = entrySize - 1;

        assertThat(underTest.isEntriesSizeGreaterThan(comparisonSize)).isTrue();
    }

    @Test
    public void truncateLargeHeaders() {
        final HashMap<String, String> oversizeMap = new HashMap<>();
        oversizeMap.put("k", ""); // header size=1, total size=1
        oversizeMap.put("m", "1"); // header size=2, total size=3
        oversizeMap.put("f", "12"); // header size=3, total size=6
        oversizeMap.put("d", "123"); // header size=4, total size=10
        oversizeMap.put("M", "1234"); // header size=5, total size=15
        final DittoHeaders oversizeHeaders = ImmutableDittoHeaders.of(oversizeMap);

        final DittoHeaders truncatedHeaders = oversizeHeaders.truncate(8L);

        final Map<String, String> expected = new HashMap<>(oversizeMap);
        expected.remove("d");
        expected.remove("M");

        assertThat(truncatedHeaders).isEqualTo(expected);
    }

    private static Map<String, String> createMapContainingAllKnownHeaders() {
        final Map<String, String> result = new HashMap<>();
        result.put(DittoHeaderDefinition.AUTHORIZATION_CONTEXT.getKey(),
                AUTH_CONTEXT_WITHOUT_DUPLICATES.toJsonString());
        result.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), KNOWN_CORRELATION_ID);
        result.put(DittoHeaderDefinition.SCHEMA_VERSION.getKey(), KNOWN_SCHEMA_VERSION.toString());
        result.put(DittoHeaderDefinition.CHANNEL.getKey(), KNOWN_CHANNEL);
        result.put(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey(), String.valueOf(KNOWN_RESPONSE_REQUIRED));
        result.put(DittoHeaderDefinition.DRY_RUN.getKey(), String.valueOf(false));
        result.put(DittoHeaderDefinition.READ_SUBJECTS.getKey(),
                stringCollectionToJsonArray(KNOWN_READ_SUBJECTS).toString());
        result.put(DittoHeaderDefinition.READ_REVOKED_SUBJECTS.getKey(),
                authorizationSubjectsToJsonArray(KNOWN_READ_REVOKED_SUBJECTS).toString());
        result.put(DittoHeaderDefinition.IF_MATCH.getKey(), KNOWN_IF_MATCH.toString());
        result.put(DittoHeaderDefinition.IF_NONE_MATCH.getKey(), KNOWN_IF_NONE_MATCH.toString());
        result.put(DittoHeaderDefinition.ETAG.getKey(), KNOWN_ETAG.toString());
        result.put(DittoHeaderDefinition.CONTENT_TYPE.getKey(), KNOWN_CONTENT_TYPE);
        result.put(DittoHeaderDefinition.ORIGIN.getKey(), KNOWN_ORIGIN);
        result.put(DittoHeaderDefinition.REPLY_TARGET.getKey(), KNOWN_REPLY_TARGET);
        result.put(DittoHeaderDefinition.INBOUND_PAYLOAD_MAPPER.getKey(), KNOWN_MAPPER);
        result.put(DittoHeaderDefinition.ORIGINATOR.getKey(), KNOWN_ORIGINATOR);
        result.put(DittoHeaderDefinition.REQUESTED_ACKS.getKey(),
                ackRequestsToJsonArray(KNOWN_ACK_REQUESTS).toString());
        result.put(DittoHeaderDefinition.TIMEOUT.getKey(), KNOWN_TIMEOUT.toMillis() + "ms");
        result.put(DittoHeaderDefinition.ENTITY_ID.getKey(), KNOWN_ENTITY_ID);
        result.put(DittoHeaderDefinition.REPLY_TO.getKey(), KNOWN_REPLY_TO);
        result.put(DittoHeaderDefinition.WWW_AUTHENTICATE.getKey(), KNOWN_WWW_AUTHENTICATION);
        result.put(DittoHeaderDefinition.LOCATION.getKey(), KNOWN_LOCATION);
        result.put(DittoHeaderDefinition.CONNECTION_ID.getKey(), KNOWN_CONNECTION_ID);
        result.put(DittoHeaderDefinition.EXPECTED_RESPONSE_TYPES.getKey(),
                expectedResponseTypesToJsonArray(KNOWN_EXPECTED_RESPONSE_TYPES).toString());

        return result;
    }

    private static JsonArray stringCollectionToJsonArray(final Iterable<String> stringCollection) {
        final JsonArrayBuilder jsonArrayBuilder = JsonFactory.newArrayBuilder();
        stringCollection.forEach(jsonArrayBuilder::add);
        return jsonArrayBuilder.build();
    }

    private static JsonArray authorizationSubjectsToJsonArray(
            final Collection<AuthorizationSubject> authorizationSubjects) {

        return authorizationSubjects.stream()
                .map(AuthorizationSubject::getId)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());
    }

    private static JsonArray ackRequestsToJsonArray(final Collection<AcknowledgementRequest> ackRequests) {
        return ackRequests.stream()
                .map(AcknowledgementRequest::toString)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());
    }

    private static JsonArray expectedResponseTypesToJsonArray(final Collection<ResponseType> responseTypes) {
        return responseTypes.stream()
                .map(ResponseType::getName)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());
    }

    private static JsonObject toJsonObject(final Map<String, String> stringMap) {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        stringMap.forEach(jsonObjectBuilder::set);
        return jsonObjectBuilder.build();
    }

}
