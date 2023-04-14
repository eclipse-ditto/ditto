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

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.DittoDuration;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;

/**
 * Enumeration of definitions of well known Ditto Headers including their key and Java type.
 * Note: All header keys must be lower-case;
 */
public enum DittoHeaderDefinition implements HeaderDefinition {

    /**
     * Header definition for the authorization context value.
     * <p>
     * Key: {@code "ditto-auth-context"}, Java type: {@link JsonObject}.
     * </p>
     */
    AUTHORIZATION_CONTEXT("ditto-auth-context",
            JsonObject.class,
            false,
            false,
            HeaderValueValidators.getJsonObjectValidator()),

    /**
     * Header definition for correlation ID value which MUST NOT be empty.
     * <p>
     * Key: {@code "correlation-id"}, Java type: {@link String}.
     * </p>
     */
    CORRELATION_ID("correlation-id", String.class, true, true, HeaderValueValidators.getNonEmptyValidator()),

    /**
     * Header definition for schema version value.
     * <p>
     * Key: {@code "version"}, Java type: {@code int}.
     * </p>
     */
    SCHEMA_VERSION("version", int.class, true, true, HeaderValueValidators.getIntValidator()),

    /**
     * Header definition for response required value.
     * <p>
     * Key: {@code "response-required"}, Java type: {@code boolean}.
     * </p>
     */
    RESPONSE_REQUIRED("response-required", boolean.class, true, true, HeaderValueValidators.getBooleanValidator()),

    /**
     * Header definition for dry run value.
     * <p>
     * Key: {@code "ditto-dry-run"}, Java type: {@code boolean}.
     * </p>
     */
    DRY_RUN("ditto-dry-run", boolean.class, false, false, HeaderValueValidators.getBooleanValidator()),

    /**
     * Header definition for read subjects value.
     * <p>
     * Key: {@code "read-subjects"}, Java type: {@link JsonArray}.
     * </p>
     */
    READ_SUBJECTS("ditto-read-subjects", JsonArray.class, false, false, HeaderValueValidators.getJsonArrayValidator()),

    /**
     * Header definition for subjects with revoked READ subjects.
     *
     * <p>
     * Key: {@code "read-revoked-subjects"}, Java type: {@link JsonArray}.
     * </p>
     *
     * @since 1.1.0
     */
    READ_REVOKED_SUBJECTS("ditto-read-revoked-subjects",
            JsonArray.class,
            false,
            false,
            HeaderValueValidators.getJsonArrayValidator()),

    /**
     * Header definition for a signal's content-type.
     * <p>
     * Key: {@code "content-type"}, Java type: {@link String}.
     * </p>
     */
    CONTENT_TYPE("content-type", String.class, true, true, HeaderValueValidators.getNoOpValidator()),

    /**
     * Header definition for the accept header of a command.
     * <p>
     * Key: {@code "accept"}, Java type: {@link String}.
     * </p>
     * @since 2.4.0
     */
    ACCEPT("accept", String.class, true, true, HeaderValueValidators.getNoOpValidator()),

    /**
     * Header definition for the reply to address. MUST be lower-case.
     * "reply-to" is a standard internet message header (RFC-5322).
     * <p>
     * Key: {@code "reply-to"}, Java type: String.
     * </p>
     *
     * @since 1.1.0
     */
    REPLY_TO("reply-to", String.class, true, true, HeaderValueValidators.getNoOpValidator()),

    /**
     * Header definition for channel value meaning distinguishing between live/twin.
     * <p>
     * Key: {@code "channel"}, Java type: {@link String}.
     * </p>
     *
     * @since 2.3.0
     */
    CHANNEL("channel", String.class, true, true, HeaderValueValidators.getDittoChannelValidator()),

    /**
     * Header definition for "live" {@link #CHANNEL} commands defining the {@link LiveChannelTimeoutStrategy} to apply
     * when a live command timed out.
     * <p>
     * Key: {@code "live-channel-timeout-strategy"}, Java type: {@code String}.
     * </p>
     *
     * @since 2.3.0
     */
    LIVE_CHANNEL_TIMEOUT_STRATEGY("live-channel-timeout-strategy",
            LiveChannelTimeoutStrategy.class,
            String.class,
            true,
            false,
            HeaderValueValidators.getEnumValidator(LiveChannelTimeoutStrategy.values())),

    /**
     * Header definition for origin value that is set to the id of the originating session.
     * <p>
     * Key: {@code "ditto-origin"}, Java type: {@link String}.
     * </p>
     */
    ORIGIN("ditto-origin", String.class, false, false, HeaderValueValidators.getNoOpValidator()),

    /**
     * Header definition for "ETag".
     * <p>
     * Key: {@code "ETag"}, Java type: {@link String}.
     * </p>
     */
    ETAG("etag", EntityTag.class, String.class, false, true, HeaderValueValidators.getEntityTagValidator()),

    /**
     * Header definition for "If-Match".
     * <p>
     * Key: {@code "If-Match"}, Java type: {@link String}.
     * </p>
     */
    IF_MATCH("if-match",
            EntityTagMatchers.class,
            String.class,
            true,
            false,
            HeaderValueValidators.getEntityTagMatchersValidator()),

    /**
     * Header definition for "If-None-Match".
     * <p>
     * Key: {@code "If-None-Match"}, Java type: {@link String}.
     * </p>
     */
    IF_NONE_MATCH("if-none-match",
            EntityTagMatchers.class,
            String.class,
            true,
            false,
            HeaderValueValidators.getEntityTagMatchersValidator()),

    /**
     * Header definition for "If-Equal".
     * Can hold one of the values: {@code update}, {@code skip}, {@code update-metadata-only}.
     * <p>
     * Key: {@code "If-Equal"}, Java type: {@link String}.
     * </p>
     * @since 3.3.0
     */
    IF_EQUAL("if-equal",
            IfEqualOption.class,
            String.class,
            true,
            false,
            HeaderValueValidators.getEnumValidator(IfEqualOption.values())),

    /**
     * Header definition for the internal header "ditto-reply-target". This header is evaluated for responses to be
     * published.
     * <p>
     * Key: {@code "ditto-reply-target"}, Java type: {@link Integer}.
     * </p>
     */
    REPLY_TARGET("ditto-reply-target", Integer.class, false, false, HeaderValueValidators.getIntValidator()),

    /**
     * Header definition for the internal header "ditto-expected-response-types".
     * This header is evaluated to distinguish if a response should published or not.
     *
     * @since 1.2.0
     */
    EXPECTED_RESPONSE_TYPES("ditto-expected-response-types",
            JsonArray.class,
            false,
            false,
            HeaderValueValidators.getJsonArrayValidator()),

    /**
     * Header definition for "ditto-inbound-payload-mapper".
     * <p>
     * Key: {@code "ditto-inbound-payload-mapper"}, Java type: {@link String}.
     * </p>
     */
    INBOUND_PAYLOAD_MAPPER("ditto-inbound-payload-mapper",
            String.class,
            false,
            false,
            HeaderValueValidators.getNoOpValidator()),

    /**
     * Header definition for the authorization subject that caused an event.
     * External header of the same name is always discarded.
     * <p>
     * Key: {@code "ditto-originator"}, Java type: {@link String}.
     * </p>
     */
    ORIGINATOR("ditto-originator", String.class, false, true, HeaderValueValidators.getNoOpValidator()),

    /**
     * Header definition for defining which acknowledgements ("ack") are requested for a command processed by Ditto.
     * <p>
     * Key: {@code "requested-acks"}, Java type: {@link JsonArray}.
     * </p>
     *
     * @since 1.1.0
     */
    REQUESTED_ACKS("requested-acks",
            JsonArray.class,
            true,
            true,
            HeaderValueValidators.getRequestedAcksValueValidator()),

    /**
     * Header definition for defining the acknowledgement labels a subscriber may send.
     * Not defined as DittoHeaders accessor method or in the DittoHeadersBuilder as this header is specific for
     * WebSocket sessions only and has also not a "Signal" scope which DittoHeaders normally have.
     * <p>
     * Key: {@code "declared-acks"}, Java type: {@link JsonArray}.
     * </p>
     *
     * @since 1.4.0
     */
    DECLARED_ACKS("declared-acks", JsonArray.class, true, false, HeaderValueValidators.getJsonArrayValidator()),

    /**
     * Header definition for the timeout of a command or message.
     * <p>
     * Key: {@code "timeout"}, Java type: {@code String}.
     * </p>
     *
     * @since 1.1.0
     */
    TIMEOUT("timeout", DittoDuration.class, String.class, true, true, HeaderValueValidators.getTimeoutValueValidator()),

    /**
     * Header definition for the entity ID related to the command/event/response/error.
     * <p>
     * Key: {@code "ditto-entity-id"}, Java type: {@link String}.
     * </p>
     *
     * @since 1.1.0
     */
    ENTITY_ID("ditto-entity-id", String.class, false, false, HeaderValueValidators.getNoOpValidator()),

    /**
     * Header definition for the response header defining which authentication method that should be used
     * to gain access to a resource
     * <p>
     * Key: {@code "www-authenticate"}, Java type: {@link String}.
     * </p>
     *
     * @since 1.1.0
     */
    WWW_AUTHENTICATE("www-authenticate", String.class, false, true, HeaderValueValidators.getNoOpValidator()),

    /**
     * Header definition for the response header defining the HTTP "Location" where a new resource was created.
     * <p>
     * Key: {@code "location"}, Java type: {@link String}.
     * </p>
     *
     * @since 1.1.0
     */
    LOCATION("location", String.class, true, true, HeaderValueValidators.getNoOpValidator()),

    /**
     * Header definition for the Ditto connection ID in automatic acknowledgements.
     *
     * @since 1.2.0
     */
    CONNECTION_ID("ditto-connection-id", String.class, false, true, HeaderValueValidators.getNoOpValidator()),

    /**
     * Header definition for setting metadata relatively to the resource of a modified entity.
     * <p>
     * Key {@code "put-metadata"}, Java type: {@link JsonArray}.
     * </p>
     *
     * @since 1.2.0
     */
    PUT_METADATA("put-metadata", JsonArray.class, true, false, HeaderValueValidators.getMetadataHeadersValidator()),

    /**
     * Header definition for getting metadata relatively to the resource of an entity.
     * <p>
     * Key {@code "get-metadata"}, Java type: {@link String}.
     * </p>
     *
     * @since 3.0.0
     */
    GET_METADATA("get-metadata", String.class, true, false, HeaderValueValidators.getJsonFieldSelectorValidator()),

    /**
     * Header definition for deleting metadata relatively to the resource of an entity.
     * <p>
     * Key {@code "delete-metadata"}, Java type: {@link String}.
     * </p>
     *
     * @since 3.0.0
     */
    DELETE_METADATA("delete-metadata", String.class, true, false, HeaderValueValidators.getJsonFieldSelectorValidator()),

    /**
     * Header definition for metadata relatively to the resource of an entity.
     * <p>
     * Key {@code "ditto-metadata"}, Java type: {@link JsonObject}.
     * </p>
     *
     * @since 3.0.0
     */
    DITTO_METADATA("ditto-metadata", JsonObject.class, false, true, HeaderValueValidators.getJsonObjectValidator()),

    /**
     * Header definition for allowing the policy lockout (i.e. a subject can create a policy without having WRITE
     * permission on the policy resource for itself, by default a subject making the request must have
     * WRITE permission on policy resource).
     *
     * <p>
     * Key {@code "allow-policy-lockout"}, Java type: {@link boolean}.
     * </p>
     *
     * @since 1.3.0
     */
    ALLOW_POLICY_LOCKOUT("allow-policy-lockout",
            boolean.class,
            true,
            false,
            HeaderValueValidators.getBooleanValidator()),

    /**
     * Header definition to identify a weak acknowledgement.
     * Weak acknowledgements are issued by the service in case a subscriber could not provide the acknowledgement
     * because of missing permissions or rql filtering.
     *
     * @since 1.5.0
     */
    WEAK_ACK("ditto-weak-ack", boolean.class, false, true, HeaderValueValidators.getBooleanValidator()),

    /**
     * Internal header which may be set by PersistenceActors in order to declare tags to be stored by the event
     * journaling {@code EventAdapter} as {@code tag} fields in the journal persistence.
     *
     * @since 2.0.0
     */
    EVENT_JOURNAL_TAGS("ditto-event-journal-tags",
            JsonArray.class,
            false,
            false,
            HeaderValueValidators.getJsonArrayValidator()),

    /**
     * W3C traceparent header.
     *
     * @see <a href="https://www.w3.org/TR/trace-context/#traceparent-header">https://www.w3.org/TR/trace-context/#traceparent-header</a>
     * @since 2.1.0
     */
    W3C_TRACEPARENT("traceparent", String.class, true, true, HeaderValueValidators.getNoOpValidator()),

    /**
     * W3C tracestate header.
     *
     * @see <a href="https://www.w3.org/TR/trace-context/#tracestate-header">https://www.w3.org/TR/trace-context/#tracestate-header</a>
     * @since 2.1.0
     */
    W3C_TRACESTATE("tracestate", String.class, true, true, HeaderValueValidators.getNoOpValidator()),

    /**
     * Internal header which may be set to ignore some preventions in the service. Can only be used in piggy-back
     * commands issued by the OPs of the service.
     *
     * @since 2.1.0
     */
    DITTO_SUDO("ditto-sudo", boolean.class, false, false, HeaderValueValidators.getBooleanValidator()),


    /**
     * Internal header to indicate that the query command is meant to also retrieve deleted entities.
     *
     * @since 2.1.0
     */
    DITTO_RETRIEVE_DELETED("ditto-retrieve-deleted",
            boolean.class,
            false,
            false,
            HeaderValueValidators.getBooleanValidator()),

    /**
     * Internal header passing through the Akka address of an {@code AcknowledgementAggregatorActor} which was started
     * at an edge (gateway/connectivity) and is awaiting {@code Acknowledgement}s which can be sent to it by using
     * this address in an {@code ActorSelection}.
     *
     * @since 3.0.0
     */
    DITTO_ACKREGATOR_ADDRESS("ditto-ackgregator-address",
            String.class,
            false,
            false,
            HeaderValueValidators.getNonEmptyValidator()),

    /**
     * Header definition to identify a request for a conditional update.
     * Condition to update the thing is evaluated in the things service. Conditions are based on RQL expressions.
     *
     * <p>
     * Key {@code "condition"}, Java type: {@link String}.
     * </p>
     *
     * @since 2.1.0
     */
    CONDITION("condition", String.class, true, false, HeaderValueValidators.getNoOpValidator()),

    /**
     * Header definition containing a condition which, when evaluating to {@code true}, shall switch the
     * {@link #CHANNEL} to use to the {@code "live"} channel.
     * The condition is defined via a RQL expressions, which is evaluated in the things persistence. If the condition is
     * evaluated to 'true', then the live-channel is used instead of the regular twin-channel.
     *
     * <p>
     * Key {@code "live-channel-condition"}, Java type: {@link String}.
     * </p>
     *
     * @since 2.3.0
     */
    LIVE_CHANNEL_CONDITION("live-channel-condition",
            String.class,
            true,
            false,
            HeaderValueValidators.getNoOpValidator()),

    /**
     * Header containing the result of the {@link #LIVE_CHANNEL_CONDITION} evaluated in the things persistence.
     * If this contains {@code true}, the {@code "live"} channel shall be used in order to handle a processed command.
     * If this header is missing or contains value {@code false}, the live channel shall not be used and the twin
     * response this header is transported with shall be returned instead.
     *
     * <p>
     * Key {@code "live-channel-condition-matched"}, Java type: {@link Boolean}.
     * </p>
     *
     * @since 2.3.0
     */
    LIVE_CHANNEL_CONDITION_MATCHED("live-channel-condition-matched",
            Boolean.class,
            false,
            true,
            HeaderValueValidators.getBooleanValidator()),

    /**
     * Header containing a specific historical revision to retrieve when retrieving a persisted entity
     * (thing/policy/connection).
     *
     * @since 3.2.0
     */
    AT_HISTORICAL_REVISION("at-historical-revision",
            Long.class,
            true,
            false,
            HeaderValueValidators.getLongValidator()),

    /**
     * Header containing a specific historical timestamp to retrieve when retrieving a persisted entity
     * (thing/policy/connection).
     *
     * @since 3.2.0
     */
    AT_HISTORICAL_TIMESTAMP("at-historical-timestamp",
            String.class,
            true,
            false,
            HeaderValueValidators.getNoOpValidator()),

    /**
     * Header containing retrieved historical headers to be returned for e.g. a historical retrieve command.
     * Useful for audit-log information, e.g. which "originator" did a change to a thing/policy/connection.
     *
     * @since 3.2.0
     */
    HISTORICAL_HEADERS("historical-headers",
            JsonObject.class,
            false,
            true,
            HeaderValueValidators.getJsonObjectValidator());

    /**
     * Map to speed up lookup of header definition by key.
     */
    private static final Map<CharSequence, DittoHeaderDefinition> VALUES_BY_KEY = Arrays.stream(values())
            .collect(Collectors.toMap(DittoHeaderDefinition::getKey, Function.identity()));

    private final String key;
    private final Class<?> type;
    private final Class<?> serializationType;
    private final boolean readFromExternalHeaders;
    private final boolean writeToExternalHeaders;
    private final ValueValidator valueValidator;

    /**
     * @param theKey the key used as key for header map.
     * @param theType the Java type of the header value which is associated with this definition's key.
     * @param readFromExternalHeaders whether Ditto reads this header from headers sent by externals.
     * @param writeToExternalHeaders whether Ditto publishes this header to externals.
     */
    DittoHeaderDefinition(final String theKey,
            final Class<?> theType,
            final boolean readFromExternalHeaders,
            final boolean writeToExternalHeaders,
            final ValueValidator valueValidator) {

        this(theKey, theType, theType, readFromExternalHeaders, writeToExternalHeaders, valueValidator);
    }

    /**
     * @param theKey the key used as key for header map.
     * @param theType the Java type of the header value which is associated with this definition's key.
     * @param serializationType the type to which this header value should be serialized.
     * @param readFromExternalHeaders whether Ditto reads this header from headers sent by externals.
     * @param writeToExternalHeaders whether Ditto publishes this header to externals.
     */
    DittoHeaderDefinition(final String theKey,
            final Class<?> theType,
            final Class<?> serializationType,
            final boolean readFromExternalHeaders,
            final boolean writeToExternalHeaders,
            final ValueValidator valueValidator) {

        key = theKey.toLowerCase();
        type = theType;
        this.serializationType = serializationType;
        this.readFromExternalHeaders = readFromExternalHeaders;
        this.writeToExternalHeaders = writeToExternalHeaders;
        this.valueValidator = valueValidator;
    }

    /**
     * Finds an appropriate {@code DittoHeaderKey} for the specified key.
     *
     * @param key the key to look up.
     * @return the DittoHeaderKey or an empty Optional.
     */
    public static Optional<HeaderDefinition> forKey(@Nullable final CharSequence key) {
        return Optional.ofNullable(VALUES_BY_KEY.get(key));
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public Class<?> getJavaType() {
        return type;
    }

    @Override
    public Class<?> getSerializationType() {
        return serializationType;
    }

    @Override
    public boolean shouldReadFromExternalHeaders() {
        return readFromExternalHeaders;
    }

    @Override
    public boolean shouldWriteToExternalHeaders() {
        return writeToExternalHeaders;
    }

    @Override
    public void validateValue(@Nullable final CharSequence value) {
        valueValidator.accept(this, value);
    }

    @Override
    public String toString() {
        return getKey();
    }

}
