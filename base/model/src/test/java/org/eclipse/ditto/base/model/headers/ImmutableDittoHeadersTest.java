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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.entry;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.assertj.core.util.Lists;
import org.assertj.core.util.Maps;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeader;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeaderKey;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.headers.ImmutableDittoHeaders}.
 */
public final class ImmutableDittoHeadersTest {

    private static final Collection<String> AUTH_SUBJECTS = Arrays.asList("test:JohnOldman", "test:FrankGrimes");
    private static final AuthorizationContext AUTH_CONTEXT =
            AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                    AUTH_SUBJECTS.stream()
                            .map(AuthorizationSubject::newInstance)
                            .collect(Collectors.toList()));
    private static final String KNOWN_CORRELATION_ID = "knownCorrelationId";
    private static final JsonSchemaVersion KNOWN_SCHEMA_VERSION = JsonSchemaVersion.V_2;
    private static final String KNOWN_CHANNEL = "live";
    private static final boolean KNOWN_RESPONSE_REQUIRED = true;
    private static final EntityTagMatchers KNOWN_IF_MATCH =
            EntityTagMatchers.fromCommaSeparatedString("\"oneValue\",\"anotherValue\"");
    private static final EntityTagMatchers KNOWN_IF_NONE_MATCH =
            EntityTagMatchers.fromCommaSeparatedString("\"notOneValue\",\"notAnotherValue\"");
    private static final IfEqualOption KNOWN_IF_EQUAL_OPTION = IfEqualOption.SKIP;
    private static final EntityTag KNOWN_ETAG = EntityTag.fromString("\"-12124212\"");
    private static final Collection<AuthorizationSubject> KNOWN_READ_GRANTED_SUBJECTS =
            Lists.list(AuthorizationModelFactory.newAuthSubject("knownGrantedSubject1"),
                    AuthorizationModelFactory.newAuthSubject("knownGrantedSubject2"));
    private static final Collection<AuthorizationSubject> KNOWN_READ_REVOKED_SUBJECTS =
            Lists.list(AuthorizationModelFactory.newAuthSubject("knownRevokedSubject1"),
                    AuthorizationModelFactory.newAuthSubject("knownRevokedSubject2"));
    private static final String KNOWN_CONTENT_TYPE = "application/json";
    private static final String KNOWN_ACCEPT = "application/json";
    private static final String KNOWN_REPLY_TO = "replies";
    private static final String KNOWN_ORIGIN = "knownOrigin";
    private static final String KNOWN_REPLY_TARGET = "5";
    private static final String KNOWN_MAPPER = "knownMapper";
    private static final String KNOWN_ORIGINATOR = "known:originator";
    private static final Duration KNOWN_TIMEOUT = Duration.ofSeconds(6);
    private static final AcknowledgementRequest KNOWN_ACK_REQUEST =
            AcknowledgementRequest.of(AcknowledgementLabel.of("ack-label-1"));
    private static final List<AcknowledgementRequest> KNOWN_ACK_REQUESTS = Lists.list(KNOWN_ACK_REQUEST);
    private static final List<AcknowledgementLabel> KNOWN_ACK_LABELS =
            Lists.list(AcknowledgementLabel.of("ack-label-2"));
    private static final List<ResponseType> KNOWN_EXPECTED_RESPONSE_TYPES =
            Lists.list(ResponseType.RESPONSE, ResponseType.NACK);
    private static final String KNOWN_ENTITY_ID = "thing:known:entityId";
    private static final String KNOWN_WWW_AUTHENTICATION = "known:www-authentication";
    private static final String KNOWN_LOCATION = "known:location";
    private static final String KNOWN_CONNECTION_ID = "known-connection-id";
    private static final MetadataHeaderKey KNOWN_METADATA_HEADER_KEY = MetadataHeaderKey.parse("/foo/bar");
    private static final JsonValue KNOWN_METADATA_VALUE = JsonValue.of("knownMetadata");
    private static final MetadataHeaders KNOWN_METADATA_HEADERS;
    private static final boolean KNOWN_ALLOW_POLICY_LOCKOUT = true;
    private static final boolean KNOWN_IS_WEAK_ACK = false;
    private static final List<String> KNOWN_JOURNAL_TAGS = Lists.list("tag-a", "tag-b");
    private static final boolean KNOWN_IS_SUDO = true;
    private static final String KNOWN_CONDITION = "eq(attributes/value)";
    private static final String KNOWN_LIVE_CHANNEL_CONDITION = "eq(attributes/value,\"livePolling\")";
    private static final boolean KNOWN_LIVE_CHANNEL_CONDITION_MATCHED = true;
    private static final String KNOWN_TRACEPARENT = "00-dfca0d990402884d22e909a87ac677ec-94fc4da95e842f96-01";
    private static final String KNOWN_TRACESTATE = "eclipse=ditto";
    private static final boolean KNOWN_DITTO_RETRIEVE_DELETED = true;
    private static final String KNOWN_DITTO_ACKREGATOR_ADDRESS = "here!";

    private static final String KNOWN_DITTO_GET_METADATA = "attributes/*/key";

    private static final String KNOWN_DITTO_DELETE_METADATA = "features/f1/properties/p1/unit";

    private static final JsonObject KNOWN_DITTO_METADATA = JsonObject.newBuilder()
            .set("attributes", JsonObject.newBuilder()
                    .set("a1", JsonObject.newBuilder()
                            .set("key", "bombolombo")
                            .build())
                    .build())
            .build();
    private static final Long KNOWN_AT_HISTORICAL_REVISION = 42L;
    private static final Instant KNOWN_AT_HISTORICAL_TIMESTAMP = Instant.now();

    private static final JsonObject KNOWN_HISTORICAL_HEADERS = JsonObject.newBuilder()
            .set(DittoHeaderDefinition.ORIGINATOR.getKey(), "foo:bar")
            .build();


    static {
        KNOWN_METADATA_HEADERS = MetadataHeaders.newInstance();
        KNOWN_METADATA_HEADERS.add(MetadataHeader.of(KNOWN_METADATA_HEADER_KEY, KNOWN_METADATA_VALUE));
    }

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
                .readGrantedSubjects(KNOWN_READ_GRANTED_SUBJECTS)
                .readRevokedSubjects(KNOWN_READ_REVOKED_SUBJECTS)
                .responseRequired(KNOWN_RESPONSE_REQUIRED)
                .dryRun(false)
                .schemaVersion(KNOWN_SCHEMA_VERSION)
                .eTag(KNOWN_ETAG)
                .ifMatch(KNOWN_IF_MATCH)
                .ifNoneMatch(KNOWN_IF_NONE_MATCH)
                .ifEqual(KNOWN_IF_EQUAL_OPTION)
                .origin(KNOWN_ORIGIN)
                .contentType(KNOWN_CONTENT_TYPE)
                .replyTarget(Integer.valueOf(KNOWN_REPLY_TARGET))
                .inboundPayloadMapper(KNOWN_MAPPER)
                .putHeader(DittoHeaderDefinition.ENTITY_ID.getKey(), KNOWN_ENTITY_ID)
                .putHeader(DittoHeaderDefinition.ORIGINATOR.getKey(), KNOWN_ORIGINATOR)
                .putHeader(DittoHeaderDefinition.REPLY_TO.getKey(), KNOWN_REPLY_TO)
                .putHeader(DittoHeaderDefinition.WWW_AUTHENTICATE.getKey(), KNOWN_WWW_AUTHENTICATION)
                .putHeader(DittoHeaderDefinition.LOCATION.getKey(), KNOWN_LOCATION)
                .putHeader(DittoHeaderDefinition.DECLARED_ACKS.getKey(),
                        charSequencesToJsonArray(KNOWN_ACK_LABELS).toString())
                .acknowledgementRequests(KNOWN_ACK_REQUESTS)
                .putMetadata(KNOWN_METADATA_HEADER_KEY, KNOWN_METADATA_VALUE)
                .timeout(KNOWN_TIMEOUT)
                .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_TIMEOUT_STRATEGY.getKey(), "use-twin")
                .putHeader(DittoHeaderDefinition.CONNECTION_ID.getKey(), KNOWN_CONNECTION_ID)
                .expectedResponseTypes(KNOWN_EXPECTED_RESPONSE_TYPES)
                .allowPolicyLockout(KNOWN_ALLOW_POLICY_LOCKOUT)
                .putHeader(DittoHeaderDefinition.WEAK_ACK.getKey(), String.valueOf(KNOWN_IS_WEAK_ACK))
                .putHeader(DittoHeaderDefinition.EVENT_JOURNAL_TAGS.getKey(),
                        charSequencesToJsonArray(KNOWN_JOURNAL_TAGS).toString())
                .putHeader(DittoHeaderDefinition.DITTO_SUDO.getKey(), String.valueOf(KNOWN_IS_SUDO))
                .putHeader(DittoHeaderDefinition.DITTO_RETRIEVE_DELETED.getKey(),
                        String.valueOf(KNOWN_DITTO_RETRIEVE_DELETED))
                .putHeader(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(), KNOWN_DITTO_ACKREGATOR_ADDRESS)
                .putHeader(DittoHeaderDefinition.W3C_TRACEPARENT.getKey(), KNOWN_TRACEPARENT)
                .putHeader(DittoHeaderDefinition.W3C_TRACESTATE.getKey(), KNOWN_TRACESTATE)
                .condition(KNOWN_CONDITION)
                .liveChannelCondition(KNOWN_LIVE_CHANNEL_CONDITION)
                .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_CONDITION_MATCHED.getKey(),
                        String.valueOf(KNOWN_LIVE_CHANNEL_CONDITION_MATCHED))
                .accept(KNOWN_ACCEPT)
                .putHeader(DittoHeaderDefinition.GET_METADATA.getKey(), KNOWN_DITTO_GET_METADATA )
                .putHeader(DittoHeaderDefinition.DELETE_METADATA.getKey(), KNOWN_DITTO_DELETE_METADATA )
                .putHeader(DittoHeaderDefinition.DITTO_METADATA.getKey(), KNOWN_DITTO_METADATA.formatAsString())
                .putHeader(DittoHeaderDefinition.AT_HISTORICAL_REVISION.getKey(), String.valueOf(KNOWN_AT_HISTORICAL_REVISION))
                .putHeader(DittoHeaderDefinition.AT_HISTORICAL_TIMESTAMP.getKey(), String.valueOf(KNOWN_AT_HISTORICAL_TIMESTAMP))
                .putHeader(DittoHeaderDefinition.HISTORICAL_HEADERS.getKey(), KNOWN_HISTORICAL_HEADERS.formatAsString())
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
    public void isSudoIsFalseOnMissingHeader() {
        final DittoHeaders underTest = DittoHeaders.empty();

        assertThat(underTest.isSudo()).isFalse();
    }

    @Test
    public void isSudoIsFalseWhenFalse() {
        final DittoHeaders underTest = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.DITTO_SUDO.getKey(), "false")
                .build();

        assertThat(underTest.isSudo()).isFalse();
    }

    @Test
    public void isSudoIsTrueWhenTrue() {
        final DittoHeaders underTest = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.DITTO_SUDO.getKey(), "true")
                .build();

        assertThat(underTest.isSudo()).isTrue();
    }

    @Test
    public void didLiveChannelConditionMatchIsFalseOnMissingHeader() {
        final DittoHeaders underTest = DittoHeaders.empty();

        assertThat(underTest.didLiveChannelConditionMatch()).isFalse();
    }

    @Test
    public void didLiveChannelConditionMatchIsFalseWhenFalse() {
        final DittoHeaders underTest = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_CONDITION_MATCHED.getKey(), "false")
                .build();

        assertThat(underTest.didLiveChannelConditionMatch()).isFalse();
    }

    @Test
    public void didLiveChannelConditionMatchIsTrueWhenTrue() {
        final DittoHeaders underTest = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_CONDITION_MATCHED.getKey(), "true")
                .build();

        assertThat(underTest.didLiveChannelConditionMatch()).isTrue();
    }

    @Test
    public void liveChannelConditionNotEmptyWhenSet() {
        final String testValue = "some-condition";
        final DittoHeaders underTest = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_CONDITION.getKey(), testValue)
                .build();

        assertThat(underTest.getLiveChannelCondition()).hasValue(testValue);
    }

    @Test
    public void getRequestedAckLabelsReturnsExpected() {
        final DittoHeaders underTest = DittoHeaders.newBuilder()
                .acknowledgementRequests(KNOWN_ACK_REQUESTS)
                .build();

        assertThat(underTest.getAcknowledgementRequests()).containsExactlyInAnyOrderElementsOf(KNOWN_ACK_REQUESTS);
    }

    @Test
    public void getLiveChannelTimeoutStrategy() {
        assertThat(DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_TIMEOUT_STRATEGY.getKey(), "use-twin")
                .build()
                .getLiveChannelTimeoutStrategy())
                .contains(LiveChannelTimeoutStrategy.USE_TWIN);

        assertThat(DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_TIMEOUT_STRATEGY.getKey(), "fail")
                .build()
                .getLiveChannelTimeoutStrategy())
                .contains(LiveChannelTimeoutStrategy.FAIL);
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
    public void metadataHeadersToPutReturnsEmptySet() {
        final ImmutableDittoHeaders underTest = ImmutableDittoHeaders.of(Collections.emptyMap());

        assertThat(underTest.getMetadataHeadersToPut()).isEmpty();
    }

    @Test
    public void metadataHeadersToPutReturnsExpected() {
        final MetadataHeaderKey specificMetadataKey = MetadataHeaderKey.parse("/foo/bar/baz");
        final JsonValue metadataValue1 = JsonValue.of(1);
        final MetadataHeaderKey wildcardMetadataKey = MetadataHeaderKey.parse("/*/aValue");
        final JsonValue metadataValue2 = JsonValue.of(2);

        final MetadataHeaders expected = MetadataHeaders.newInstance();
        expected.add(MetadataHeader.of(wildcardMetadataKey, metadataValue2));
        expected.add(MetadataHeader.of(specificMetadataKey, metadataValue1));

        final Map<String, String> headerMap = new HashMap<>();
        headerMap.put(DittoHeaderDefinition.PUT_METADATA.getKey(), expected.toJsonString());
        final ImmutableDittoHeaders underTest = ImmutableDittoHeaders.of(headerMap);

        assertThat(underTest.getMetadataHeadersToPut()).isEqualTo(expected);
    }

    @Test
    public void metadataFieldsToGetReturnsEmptySet() {
        final ImmutableDittoHeaders underTest = ImmutableDittoHeaders.of(Collections.emptyMap());

        assertThat(underTest.getMetadataFieldsToGet()).isEmpty();
    }

    @Test
    public void metadataFieldsToGetReturnsExpected() {
        final String expr1 = "attributes";
        final String expr2 = "features/f1/properties/p1/key";
        final String givenMetadataExpression = expr1 + "," + expr2;

        final Set<JsonPointer> expected = new HashSet<>(Arrays.asList(JsonPointer.of(expr1),JsonPointer.of(expr2)));

        final Map<String, String> headerMap = new HashMap<>();
        headerMap.put(DittoHeaderDefinition.GET_METADATA.getKey(), givenMetadataExpression);
        final ImmutableDittoHeaders underTest = ImmutableDittoHeaders.of(headerMap);

        assertThat(underTest.getMetadataFieldsToGet()).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject expectedHeadersJsonObject = JsonFactory.newObjectBuilder()
                .set(DittoHeaderDefinition.AUTHORIZATION_CONTEXT.getKey(), AUTH_CONTEXT.toJson())
                .set(DittoHeaderDefinition.CORRELATION_ID.getKey(), KNOWN_CORRELATION_ID)
                .set(DittoHeaderDefinition.SCHEMA_VERSION.getKey(), KNOWN_SCHEMA_VERSION.toInt())
                .set(DittoHeaderDefinition.CHANNEL.getKey(), KNOWN_CHANNEL)
                .set(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey(), KNOWN_RESPONSE_REQUIRED)
                .set(DittoHeaderDefinition.DRY_RUN.getKey(), false)
                .set(DittoHeaderDefinition.READ_SUBJECTS.getKey(), charSequencesToJsonArray(
                        KNOWN_READ_GRANTED_SUBJECTS.stream().map(Object::toString).collect(Collectors.toList())))
                .set(DittoHeaderDefinition.READ_REVOKED_SUBJECTS.getKey(),
                        authorizationSubjectsToJsonArray(KNOWN_READ_REVOKED_SUBJECTS))
                .set(DittoHeaderDefinition.IF_MATCH.getKey(), KNOWN_IF_MATCH.toString())
                .set(DittoHeaderDefinition.IF_NONE_MATCH.getKey(), KNOWN_IF_NONE_MATCH.toString())
                .set(DittoHeaderDefinition.IF_EQUAL.getKey(), KNOWN_IF_EQUAL_OPTION.toString())
                .set(DittoHeaderDefinition.ETAG.getKey(), KNOWN_ETAG.toString())
                .set(DittoHeaderDefinition.ORIGIN.getKey(), KNOWN_ORIGIN)
                .set(DittoHeaderDefinition.CONTENT_TYPE.getKey(), KNOWN_CONTENT_TYPE)
                .set(DittoHeaderDefinition.ACCEPT.getKey(), KNOWN_ACCEPT)
                .set(DittoHeaderDefinition.REPLY_TARGET.getKey(), Integer.parseInt(KNOWN_REPLY_TARGET))
                .set(DittoHeaderDefinition.INBOUND_PAYLOAD_MAPPER.getKey(), KNOWN_MAPPER)
                .set(DittoHeaderDefinition.ORIGINATOR.getKey(), KNOWN_ORIGINATOR)
                .set(DittoHeaderDefinition.REQUESTED_ACKS.getKey(), ackRequestsToJsonArray(KNOWN_ACK_REQUESTS))
                .set(DittoHeaderDefinition.DECLARED_ACKS.getKey(), charSequencesToJsonArray(KNOWN_ACK_LABELS))
                .set(DittoHeaderDefinition.TIMEOUT.getKey(), JsonValue.of(KNOWN_TIMEOUT.toMillis() + "ms"))
                .set(DittoHeaderDefinition.LIVE_CHANNEL_TIMEOUT_STRATEGY.getKey(), JsonValue.of("use-twin"))
                .set(DittoHeaderDefinition.ENTITY_ID.getKey(), KNOWN_ENTITY_ID)
                .set(DittoHeaderDefinition.REPLY_TO.getKey(), KNOWN_REPLY_TO)
                .set(DittoHeaderDefinition.WWW_AUTHENTICATE.getKey(), KNOWN_WWW_AUTHENTICATION)
                .set(DittoHeaderDefinition.LOCATION.getKey(), KNOWN_LOCATION)
                .set(DittoHeaderDefinition.CONNECTION_ID.getKey(), KNOWN_CONNECTION_ID)
                .set(DittoHeaderDefinition.EXPECTED_RESPONSE_TYPES.getKey(),
                        charSequencesToJsonArray(KNOWN_EXPECTED_RESPONSE_TYPES))
                .set(DittoHeaderDefinition.PUT_METADATA.getKey(), KNOWN_METADATA_HEADERS.toJson())
                .set(DittoHeaderDefinition.ALLOW_POLICY_LOCKOUT.getKey(), KNOWN_ALLOW_POLICY_LOCKOUT)
                .set(DittoHeaderDefinition.WEAK_ACK.getKey(), KNOWN_IS_WEAK_ACK)
                .set(DittoHeaderDefinition.EVENT_JOURNAL_TAGS.getKey(),
                        charSequencesToJsonArray(KNOWN_JOURNAL_TAGS))
                .set(DittoHeaderDefinition.DITTO_SUDO.getKey(), KNOWN_IS_SUDO)
                .set(DittoHeaderDefinition.DITTO_RETRIEVE_DELETED.getKey(), KNOWN_DITTO_RETRIEVE_DELETED)
                .set(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(), KNOWN_DITTO_ACKREGATOR_ADDRESS)
                .set(DittoHeaderDefinition.W3C_TRACEPARENT.getKey(), KNOWN_TRACEPARENT)
                .set(DittoHeaderDefinition.W3C_TRACESTATE.getKey(), KNOWN_TRACESTATE)
                .set(DittoHeaderDefinition.CONDITION.getKey(), KNOWN_CONDITION)
                .set(DittoHeaderDefinition.LIVE_CHANNEL_CONDITION.getKey(), KNOWN_LIVE_CHANNEL_CONDITION)
                .set(DittoHeaderDefinition.LIVE_CHANNEL_CONDITION_MATCHED.getKey(),
                        KNOWN_LIVE_CHANNEL_CONDITION_MATCHED)
                .set(DittoHeaderDefinition.GET_METADATA.getKey(), KNOWN_DITTO_GET_METADATA)
                .set(DittoHeaderDefinition.DELETE_METADATA.getKey(), KNOWN_DITTO_DELETE_METADATA)
                .set(DittoHeaderDefinition.DITTO_METADATA.getKey(), KNOWN_DITTO_METADATA)
                .set(DittoHeaderDefinition.AT_HISTORICAL_REVISION.getKey(), KNOWN_AT_HISTORICAL_REVISION)
                .set(DittoHeaderDefinition.AT_HISTORICAL_TIMESTAMP.getKey(), KNOWN_AT_HISTORICAL_TIMESTAMP.toString())
                .set(DittoHeaderDefinition.HISTORICAL_HEADERS.getKey(), KNOWN_HISTORICAL_HEADERS)
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

        final List<String> knownHeaderNames = Arrays.stream(DittoHeaderDefinition.values())
                .map(DittoHeaderDefinition::getKey)
                .distinct()
                .collect(Collectors.toList());

        assertThat(testedHeaderNames).containsAll(knownHeaderNames);
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
        oversizeMap.put("x", "1234"); // header size=5, total size=15
        final DittoHeaders oversizeHeaders = ImmutableDittoHeaders.of(oversizeMap);

        final DittoHeaders truncatedHeaders = oversizeHeaders.truncate(8L);

        final Map<String, String> expected = new HashMap<>(oversizeMap);
        expected.remove("d");
        expected.remove("x");

        assertThat(truncatedHeaders).isEqualTo(expected);
    }

    @Test
    public void areCaseInsensitiveAndCasePreserving() {
        final Map<String, String> headers = new HashMap<>();
        headers.put("hElLo", "world");
        final DittoHeaders underTest = ImmutableDittoHeaders.of(headers);
        assertThat(underTest).containsEntry("hello", "world");
        final JsonObject serialized = underTest.toJson();
        assertThat(serialized).containsExactly(JsonField.newInstance("hElLo", JsonValue.of("world")));
        final DittoHeaders deserialized = DittoHeaders.newBuilder(serialized).build();
        assertThat(deserialized).isEqualTo(underTest);
        assertThat(deserialized.toJson()).isEqualTo(serialized);
    }

    @Test
    public void respectInsertionOrder() {
        final Map<String, String> initialHeaders = new HashMap<>();
        initialHeaders.put("Response-Required", "true");
        final Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put("response-required", "false");
        assertThat(DittoHeaders.of(initialHeaders)
                .toBuilder()
                .responseRequired(false)
                .build()
                .asCaseSensitiveMap()).isEqualTo(expectedHeaders);
    }

    @Test
    public void preserveCapitalizationOfCorrelationId() {
        final Map<String, String> initialHeaders = new HashMap<>();
        initialHeaders.put("Correlation-Id", "true");
        final Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put("Correlation-Id", "false");
        assertThat(DittoHeaders.of(initialHeaders)
                .toBuilder()
                .correlationId("false")
                .build()
                .asCaseSensitiveMap()).isEqualTo(expectedHeaders);
    }

    private static Map<String, String> createMapContainingAllKnownHeaders() {
        final Map<String, String> result = new LinkedHashMap<>();
        result.put(DittoHeaderDefinition.AUTHORIZATION_CONTEXT.getKey(), AUTH_CONTEXT.toJsonString());
        result.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), KNOWN_CORRELATION_ID);
        result.put(DittoHeaderDefinition.SCHEMA_VERSION.getKey(), KNOWN_SCHEMA_VERSION.toString());
        result.put(DittoHeaderDefinition.CHANNEL.getKey(), KNOWN_CHANNEL);
        result.put(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey(), String.valueOf(KNOWN_RESPONSE_REQUIRED));
        result.put(DittoHeaderDefinition.DRY_RUN.getKey(), String.valueOf(false));
        result.put(DittoHeaderDefinition.READ_SUBJECTS.getKey(),
                charSequencesToJsonArray(KNOWN_READ_GRANTED_SUBJECTS.stream()
                        .map(Object::toString)
                        .collect(Collectors.toList())).toString());
        result.put(DittoHeaderDefinition.READ_REVOKED_SUBJECTS.getKey(),
                authorizationSubjectsToJsonArray(KNOWN_READ_REVOKED_SUBJECTS).toString());
        result.put(DittoHeaderDefinition.IF_MATCH.getKey(), KNOWN_IF_MATCH.toString());
        result.put(DittoHeaderDefinition.IF_NONE_MATCH.getKey(), KNOWN_IF_NONE_MATCH.toString());
        result.put(DittoHeaderDefinition.IF_EQUAL.getKey(), KNOWN_IF_EQUAL_OPTION.toString());
        result.put(DittoHeaderDefinition.ETAG.getKey(), KNOWN_ETAG.toString());
        result.put(DittoHeaderDefinition.CONTENT_TYPE.getKey(), KNOWN_CONTENT_TYPE);
        result.put(DittoHeaderDefinition.ACCEPT.getKey(), KNOWN_ACCEPT);
        result.put(DittoHeaderDefinition.ORIGIN.getKey(), KNOWN_ORIGIN);
        result.put(DittoHeaderDefinition.REPLY_TARGET.getKey(), KNOWN_REPLY_TARGET);
        result.put(DittoHeaderDefinition.INBOUND_PAYLOAD_MAPPER.getKey(), KNOWN_MAPPER);
        result.put(DittoHeaderDefinition.ORIGINATOR.getKey(), KNOWN_ORIGINATOR);
        result.put(DittoHeaderDefinition.REQUESTED_ACKS.getKey(),
                ackRequestsToJsonArray(KNOWN_ACK_REQUESTS).toString());
        result.put(DittoHeaderDefinition.DECLARED_ACKS.getKey(),
                charSequencesToJsonArray(KNOWN_ACK_LABELS).toString());
        result.put(DittoHeaderDefinition.TIMEOUT.getKey(), KNOWN_TIMEOUT.toMillis() + "ms");
        result.put(DittoHeaderDefinition.LIVE_CHANNEL_TIMEOUT_STRATEGY.getKey(), "use-twin");
        result.put(DittoHeaderDefinition.ENTITY_ID.getKey(), KNOWN_ENTITY_ID);
        result.put(DittoHeaderDefinition.REPLY_TO.getKey(), KNOWN_REPLY_TO);
        result.put(DittoHeaderDefinition.WWW_AUTHENTICATE.getKey(), KNOWN_WWW_AUTHENTICATION);
        result.put(DittoHeaderDefinition.LOCATION.getKey(), KNOWN_LOCATION);
        result.put(DittoHeaderDefinition.CONNECTION_ID.getKey(), KNOWN_CONNECTION_ID);
        result.put(DittoHeaderDefinition.EXPECTED_RESPONSE_TYPES.getKey(),
                charSequencesToJsonArray(KNOWN_EXPECTED_RESPONSE_TYPES).toString());
        result.put(DittoHeaderDefinition.PUT_METADATA.getKey(), KNOWN_METADATA_HEADERS.toJsonString());
        result.put(DittoHeaderDefinition.ALLOW_POLICY_LOCKOUT.getKey(), String.valueOf(KNOWN_ALLOW_POLICY_LOCKOUT));
        result.put(DittoHeaderDefinition.WEAK_ACK.getKey(), String.valueOf(KNOWN_IS_WEAK_ACK));
        result.put(DittoHeaderDefinition.EVENT_JOURNAL_TAGS.getKey(),
                charSequencesToJsonArray(KNOWN_JOURNAL_TAGS).toString());
        result.put(DittoHeaderDefinition.DITTO_SUDO.getKey(), String.valueOf(KNOWN_IS_SUDO));
        result.put(DittoHeaderDefinition.DITTO_RETRIEVE_DELETED.getKey(), String.valueOf(KNOWN_DITTO_RETRIEVE_DELETED));
        result.put(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(), KNOWN_DITTO_ACKREGATOR_ADDRESS);
        result.put(DittoHeaderDefinition.W3C_TRACEPARENT.getKey(), KNOWN_TRACEPARENT);
        result.put(DittoHeaderDefinition.W3C_TRACESTATE.getKey(), KNOWN_TRACESTATE);
        result.put(DittoHeaderDefinition.CONDITION.getKey(), KNOWN_CONDITION);
        result.put(DittoHeaderDefinition.LIVE_CHANNEL_CONDITION.getKey(), KNOWN_LIVE_CHANNEL_CONDITION);
        result.put(DittoHeaderDefinition.LIVE_CHANNEL_CONDITION_MATCHED.getKey(),
                String.valueOf(KNOWN_LIVE_CHANNEL_CONDITION_MATCHED));
        result.put(DittoHeaderDefinition.GET_METADATA.getKey(), KNOWN_DITTO_GET_METADATA);
        result.put(DittoHeaderDefinition.DELETE_METADATA.getKey(), KNOWN_DITTO_DELETE_METADATA);
        result.put(DittoHeaderDefinition.DITTO_METADATA.getKey(), KNOWN_DITTO_METADATA.formatAsString());
        result.put(DittoHeaderDefinition.AT_HISTORICAL_REVISION.getKey(), String.valueOf(KNOWN_AT_HISTORICAL_REVISION));
        result.put(DittoHeaderDefinition.AT_HISTORICAL_TIMESTAMP.getKey(), String.valueOf(KNOWN_AT_HISTORICAL_TIMESTAMP));
        result.put(DittoHeaderDefinition.HISTORICAL_HEADERS.getKey(), KNOWN_HISTORICAL_HEADERS.formatAsString());

        return result;
    }

    private static JsonArray authorizationSubjectsToJsonArray(
            final Collection<AuthorizationSubject> authorizationSubjects) {

        return collectionToJsonArray(authorizationSubjects, AuthorizationSubject::getId);
    }

    private static JsonArray ackRequestsToJsonArray(final Collection<AcknowledgementRequest> ackRequests) {
        return collectionToJsonArray(ackRequests, AcknowledgementRequest::toString);
    }

    private static JsonArray charSequencesToJsonArray(final Collection<? extends CharSequence> charSequences) {
        return collectionToJsonArray(charSequences, Function.identity());
    }

    private static <T> JsonArray collectionToJsonArray(final Collection<T> collection,
            final Function<T, ? extends CharSequence> converter) {
        return collection.stream()
                .map(converter)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());
    }

    private static JsonObject toJsonObject(final Map<String, String> stringMap) {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        stringMap.forEach(jsonObjectBuilder::set);
        return jsonObjectBuilder.build();
    }

}
