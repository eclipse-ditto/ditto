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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.assertj.core.util.Maps;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableDittoHeaders}.
 */
public final class ImmutableDittoHeadersTest {

    private static final Collection<String> AUTH_SUBJECTS = Arrays.asList("JohnOldman", "FrankGrimes");
    private static final String KNOWN_CORRELATION_ID = "knownCorrelationId";
    private static final JsonSchemaVersion KNOWN_SCHEMA_VERSION = JsonSchemaVersion.V_2;
    private static final String KNOWN_READ_SUBJECT_WITHOUT_ISSUER = "knownReadSubject";
    private static final String KNOWN_READ_SUBJECT = KNOWN_READ_SUBJECT_WITHOUT_ISSUER;
    private static final String KNOWN_SOURCE = "knownSource";
    private static final String KNOWN_CHANNEL = "live";
    private static final boolean KNOWN_RESPONSE_REQUIRED = true;
    private static final EntityTagMatchers KNOWN_IF_MATCH =
            EntityTagMatchers.fromCommaSeparatedString("\"oneValue\",\"anotherValue\"");
    private static final EntityTagMatchers KNOWN_IF_NONE_MATCH =
            EntityTagMatchers.fromCommaSeparatedString("\"notOneValue\",\"notAnotherValue\"");
    private static final EntityTag KNOWN_ETAG = EntityTag.fromString("\"-12124212\"");
    private static final Collection<String> KNOWN_READ_SUBJECTS = Collections.singleton(KNOWN_READ_SUBJECT);
    private static final String KNOWN_CONTENT_TYPE = "application/json";
    private static final String KNOWN_ORIGIN = "knownOrigin";
    private static final String KNOWN_MAPPER = "knownMapper";

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
                .authorizationSubjects(AUTH_SUBJECTS)
                .correlationId(KNOWN_CORRELATION_ID)
                .readSubjects(KNOWN_READ_SUBJECTS)
                .responseRequired(KNOWN_RESPONSE_REQUIRED)
                .dryRun(false)
                .schemaVersion(KNOWN_SCHEMA_VERSION)
                .source(KNOWN_SOURCE)
                .eTag(KNOWN_ETAG)
                .ifMatch(KNOWN_IF_MATCH)
                .ifNoneMatch(KNOWN_IF_NONE_MATCH)
                .origin(KNOWN_ORIGIN)
                .contentType(KNOWN_CONTENT_TYPE)
                .inboundPayloadMapper(KNOWN_MAPPER)
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
    public void getSourceReturnsExpected() {
        final DittoHeaders underTest = DittoHeaders.newBuilder().source(KNOWN_SOURCE).build();

        assertThat(underTest.getSource()).contains(KNOWN_SOURCE);
    }

    @Test
    public void getSchemaVersionReturnsExpected() {
        final DittoHeaders underTest = DittoHeaders.newBuilder().schemaVersion(KNOWN_SCHEMA_VERSION).build();

        assertThat(underTest.getSchemaVersion()).contains(KNOWN_SCHEMA_VERSION);
    }

    @Test
    public void getAuthorizationSubjectsReturnsExpected() {
        final DittoHeaders underTest = DittoHeaders.newBuilder().authorizationSubjects(AUTH_SUBJECTS).build();

        assertThat(underTest.getAuthorizationSubjects()).isEqualTo(AUTH_SUBJECTS);
    }

    @Test
    public void getAuthorizationContextReturnsExpected() {
        final List<AuthorizationSubject> authSubjects = AUTH_SUBJECTS.stream()
                .map(AuthorizationModelFactory::newAuthSubject)
                .collect(Collectors.toList());
        final AuthorizationContext authContext = AuthorizationModelFactory.newAuthContext(authSubjects);

        final DittoHeaders underTest = DittoHeaders.newBuilder().authorizationContext(authContext).build();

        assertThat(underTest.getAuthorizationContext()).isEqualTo(authContext);
    }

    @Test
    public void getReadSubjectsReturnsExpected() {
        final DittoHeaders underTest = DittoHeaders.newBuilder().readSubjects(KNOWN_READ_SUBJECTS).build();

        assertThat(underTest.getReadSubjects()).isEqualTo(KNOWN_READ_SUBJECTS);
    }

    @Test
    public void isResponseRequiredIsTrueByDefault() {
        final DittoHeaders underTest = DittoHeaders.empty();

        assertThat(underTest.isResponseRequired()).isTrue();
    }

    @Test
    public void isResponseRequiredReturnsExpected() {
        final DittoHeaders underTest = DittoHeaders.newBuilder().responseRequired(false).build();

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
    public void toJsonReturnsExpected() {
        final JsonObject expectedHeadersJsonObject = JsonFactory.newObjectBuilder()
                .set(DittoHeaderDefinition.AUTHORIZATION_SUBJECTS.getKey(), toJsonArray(AUTH_SUBJECTS))
                .set(DittoHeaderDefinition.CORRELATION_ID.getKey(), KNOWN_CORRELATION_ID)
                .set(DittoHeaderDefinition.SCHEMA_VERSION.getKey(), KNOWN_SCHEMA_VERSION.toInt())
                .set(DittoHeaderDefinition.SOURCE.getKey(), KNOWN_SOURCE)
                .set(DittoHeaderDefinition.CHANNEL.getKey(), KNOWN_CHANNEL)
                .set(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey(), KNOWN_RESPONSE_REQUIRED)
                .set(DittoHeaderDefinition.DRY_RUN.getKey(), false)
                .set(DittoHeaderDefinition.READ_SUBJECTS.getKey(), toJsonArray(KNOWN_READ_SUBJECTS))
                .set(DittoHeaderDefinition.IF_MATCH.getKey(), KNOWN_IF_MATCH.toString())
                .set(DittoHeaderDefinition.IF_NONE_MATCH.getKey(), KNOWN_IF_NONE_MATCH.toString())
                .set(DittoHeaderDefinition.ETAG.getKey(), KNOWN_ETAG.toString())
                .set(DittoHeaderDefinition.ORIGIN.getKey(), KNOWN_ORIGIN)
                .set(DittoHeaderDefinition.CONTENT_TYPE.getKey(), KNOWN_CONTENT_TYPE)
                .set(DittoHeaderDefinition.INBOUND_PAYLOAD_MAPPER.getKey(), KNOWN_MAPPER)
                .build();
        final Map<String, String> allKnownHeaders = createMapContainingAllKnownHeaders();

        final DittoHeaders underTest = DittoHeaders.newBuilder(allKnownHeaders).build();
        final JsonObject actualHeadersJsonObject = underTest.toJson();

        assertThat(actualHeadersJsonObject).isEqualTo(expectedHeadersJsonObject);
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
        final ImmutableDittoHeaders underTest = ImmutableDittoHeaders.of(Collections.emptyMap());

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
        result.put(DittoHeaderDefinition.AUTHORIZATION_SUBJECTS.getKey(), toJsonArray(AUTH_SUBJECTS).toString());
        result.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), KNOWN_CORRELATION_ID);
        result.put(DittoHeaderDefinition.SCHEMA_VERSION.getKey(), KNOWN_SCHEMA_VERSION.toString());
        result.put(DittoHeaderDefinition.SOURCE.getKey(), KNOWN_SOURCE);
        result.put(DittoHeaderDefinition.CHANNEL.getKey(), KNOWN_CHANNEL);
        result.put(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey(), String.valueOf(KNOWN_RESPONSE_REQUIRED));
        result.put(DittoHeaderDefinition.DRY_RUN.getKey(), String.valueOf(false));
        result.put(DittoHeaderDefinition.READ_SUBJECTS.getKey(), toJsonArray(KNOWN_READ_SUBJECTS).toString());
        result.put(DittoHeaderDefinition.IF_MATCH.getKey(), KNOWN_IF_MATCH.toString());
        result.put(DittoHeaderDefinition.IF_NONE_MATCH.getKey(), KNOWN_IF_NONE_MATCH.toString());
        result.put(DittoHeaderDefinition.ETAG.getKey(), KNOWN_ETAG.toString());
        result.put(DittoHeaderDefinition.CONTENT_TYPE.getKey(), KNOWN_CONTENT_TYPE);
        result.put(DittoHeaderDefinition.ORIGIN.getKey(), KNOWN_ORIGIN);
        result.put(DittoHeaderDefinition.INBOUND_PAYLOAD_MAPPER.getKey(), KNOWN_MAPPER);

        return result;
    }

    private static JsonArray toJsonArray(final Iterable<String> stringCollection) {
        final JsonArrayBuilder jsonArrayBuilder = JsonFactory.newArrayBuilder();
        stringCollection.forEach(jsonArrayBuilder::add);
        return jsonArrayBuilder.build();
    }

    private static JsonObject toJsonObject(final Map<String, String> stringMap) {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        stringMap.forEach(jsonObjectBuilder::set);
        return jsonObjectBuilder.build();
    }

}
