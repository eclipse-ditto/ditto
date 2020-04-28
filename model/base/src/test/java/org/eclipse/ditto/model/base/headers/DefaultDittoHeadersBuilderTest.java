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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.entry;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.eclipse.ditto.model.base.headers.DefaultDittoHeadersBuilder.of;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.assertions.DittoBaseAssertions;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link DefaultDittoHeadersBuilder}.
 */
public final class DefaultDittoHeadersBuilderTest {

    private static final List<String> AUTHORIZATION_SUBJECTS = Arrays.asList("Foo", "Bar");
    private static final String CORRELATION_ID = "correlationId";
    private static final JsonSchemaVersion JSON_SCHEMA_VERSION = JsonSchemaVersion.V_1;
    private static final String CHANNEL = "twin";
    private static final Collection<String> READ_SUBJECTS = Arrays.asList("read", "subjects");

    private DittoHeadersBuilder underTest = null;

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
                .hasNoReadSubjects();
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

    @Test(expected = NullPointerException.class)
    public void tryToSetNullAuthSubjects() {
        underTest.authorizationSubjects(null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetNullReadSubjects() {
        underTest.readSubjects(null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetNullAuthSubjectsMulti() {
        underTest.authorizationSubjects(null, "foo");
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetNullAuthSubjectsMulti2() {
        underTest.authorizationSubjects("foo", (String[]) null);
    }

    @Test
    public void buildReturnsExpected() {
        final DittoHeaders dittoHeaders = underTest.correlationId(CORRELATION_ID)
                .readSubjects(READ_SUBJECTS)
                .schemaVersion(JSON_SCHEMA_VERSION)
                .build();

        DittoBaseAssertions.assertThat(dittoHeaders)
                .hasCorrelationId(CORRELATION_ID)
                .hasSchemaVersion(JSON_SCHEMA_VERSION)
                .hasReadSubject("read", "subjects");
    }

    @Test
    public void constructBuilderFromHeadersWorksExpected() {
        final DittoHeaders dittoHeaders = underTest.correlationId(CORRELATION_ID)
                .readSubjects(READ_SUBJECTS)
                .schemaVersion(JSON_SCHEMA_VERSION)
                .build();

        final DittoHeaders anotherDittoHeaders = of(dittoHeaders).build();
    }

    @Test
    public void jsonRepresentationOfEmptyDittoHeadersIsExpected() {
        final DittoHeaders emptyDittoHeaders = DittoHeaders.empty();
        final JsonObject jsonObject = emptyDittoHeaders.toJson();

        assertThat(jsonObject).isEmpty();
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
        final DittoHeaders dittoHeaders = underTest.readSubjects(READ_SUBJECTS).build();
        final JsonObject jsonObject = dittoHeaders.toJson();
        final JsonArray expectedReadSubjects = READ_SUBJECTS.stream()
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

        final DittoHeaders dittoHeaders = underTest.readSubjects(READ_SUBJECTS)
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
        final String key = DittoHeaderDefinition.TIMEOUT.getKey();
        final String invalidValue = "bar";

        final Map<String, String> invalidHeaders = new HashMap<>();
        invalidHeaders.put(DittoHeaderDefinition.CONTENT_TYPE.getKey(), "application/json");
        invalidHeaders.put(key, invalidValue);

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.putHeaders(invalidHeaders))
                .withMessage("The value '%s' of the header '%s' is not a valid duration.", invalidValue, key)
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
    public void ensureResponseRequiredStaysFalseEvenWhenAcksAreRequested() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .responseRequired(false)
                .acknowledgementRequest(AcknowledgementRequest.of(AcknowledgementLabel.of("some-ack")))
                .build();

        assertThat(dittoHeaders)
                .containsEntry(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey(), Boolean.FALSE.toString());
    }

    @Test
    public void ensureResponseRequiredIsFalseForHeadersWithZeroTimeout() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .responseRequired(true)
                .timeout(Duration.ZERO)
                .build();

        assertThat(dittoHeaders)
                .containsEntry(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey(), Boolean.FALSE.toString());
    }

    @Test
    public void ensureResponseRequiredIsFalseEvenIfAcksAreRequestedWithTimeoutZero() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .acknowledgementRequest(AcknowledgementRequest.of(AcknowledgementLabel.of("some-ack")))
                .timeout("0ms")
                .build();

        assertThat(dittoHeaders)
                .containsEntry(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey(), Boolean.FALSE.toString());
    }


    @Test
    public void removesDuplicatedAuthSubjects() {
        final Collection<String> authSubjectsWithDuplicates = Arrays.asList("test:sub", "sub");
        final AuthorizationContext authorizationContextWithDuplicates =
                AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                        authSubjectsWithDuplicates.stream()
                                .map(AuthorizationSubject::newInstance)
                                .collect(Collectors.toList()));
        final AuthorizationContext authorizationContextWithoutDuplicates =
                AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationSubject.newInstance("test:sub"));
        final DittoHeaders dittoHeaders = underTest
                .authorizationContext(authorizationContextWithDuplicates)
                .build();

        assertThat(dittoHeaders.getAuthorizationContext()).isEqualTo(authorizationContextWithDuplicates);
        assertThat(dittoHeaders.get(DittoHeaderDefinition.AUTHORIZATION_CONTEXT.getKey()))
                .isEqualTo(authorizationContextWithoutDuplicates.toJsonString());
    }
}
