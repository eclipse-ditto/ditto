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
package org.eclipse.ditto.connectivity.model;

import java.util.Optional;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.FilteredAcknowledgementRequest;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;

/**
 * A {@link Connection} source contains several addresses to consume external messages from.
 */
public interface Source extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * @return the addresses of this source
     */
    Set<String> getAddresses();

    /**
     * @return number of consumers (connections) that will be opened to the remote server, default is {@code 1}
     */
    int getConsumerCount();

    /**
     * Returns the Authorization Context of this {@code Source}. If an authorization context is set on a {@link Source}
     * it overrides the authorization context set on the enclosing {@link Connection}.
     *
     * @return the Authorization Context of this {@link Source}.
     */
    AuthorizationContext getAuthorizationContext();

    /**
     * @return an index to distinguish between sources that would otherwise be different
     */
    int getIndex();

    /**
     * @return the optional qos value of this source - only applicable for certain {@link ConnectionType}s.
     */
    Optional<Integer> getQos();

    /**
     * @return the enforcement options that should be applied to this source
     */
    Optional<Enforcement> getEnforcement();

    /**
     * Returns the acknowledgement requests which should be added to each by the source consumed message
     * with an optional filter.
     *
     * @return the acknowledgements that are requested from messages consumed in this source
     * @since 1.2.0
     */
    Optional<FilteredAcknowledgementRequest> getAcknowledgementRequests();

    /**
     * Defines an optional header mapping e.g. rename, combine etc. headers for inbound message. Mapping is
     * applied after payload mapping is applied. The mapping may contain {@code thing:*} and {@code header:*}
     * placeholders.
     *
     * @return the header mappings
     */
    HeaderMapping getHeaderMapping();


    /**
     * The payload mappings that should be applied for messages received on this source. Each
     * mapping can produce multiple signals on its own that are then forwarded independently.
     *
     * @return the payload mappings to execute
     */
    PayloadMapping getPayloadMapping();

    /**
     * The target to handle Ditto command responses to commands sent by this source.
     * If undefined, responses are published for commands with {@code response-required=true}
     * at the address defined by the header {@code reply-to} without header or payload mapping.
     *
     * @return an optional reply-target.
     */
    Optional<ReplyTarget> getReplyTarget();

    /**
     * Whether reply-target is enabled for this source.
     *
     * @return whether reply-target is enabled.
     */
    boolean isReplyTargetEnabled();

    /**
     * The declared acknowledgement labels are those of acknowledgements this source is allowed to send.
     *
     * @return the declared acknowledgement labels.
     * @since 1.4.0
     */
    Set<AcknowledgementLabel> getDeclaredAcknowledgementLabels();

    /**
     * Returns all non-hidden marked fields of this {@code Source}.
     *
     * @return a JSON object representation of this Source including only non-hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.notHidden()).get(fieldSelector);
    }

    /**
     * An enumeration of the known {@code JsonField}s of a {@code Source} configuration.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the {@code Source} addresses.
         */
        public static final JsonFieldDefinition<JsonArray> ADDRESSES =
                JsonFactory.newJsonArrayFieldDefinition("addresses", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Source} consumer count.
         */
        public static final JsonFieldDefinition<Integer> CONSUMER_COUNT =
                JsonFactory.newIntFieldDefinition("consumerCount", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Source} qos.
         */
        public static final JsonFieldDefinition<Integer> QOS =
                JsonFactory.newIntFieldDefinition("qos", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Source} authorization context (list of authorization subjects).
         */
        public static final JsonFieldDefinition<JsonArray> AUTHORIZATION_CONTEXT =
                JsonFactory.newJsonArrayFieldDefinition("authorizationContext",
                        FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Source} enforcement options.
         */
        public static final JsonFieldDefinition<JsonObject> ENFORCEMENT =
                JsonFactory.newJsonObjectFieldDefinition("enforcement", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Source} acknowledgements that are requested from messages consumed in this
         * source.
         *
         * @since 1.2.0
         */
        public static final JsonFieldDefinition<JsonObject> ACKNOWLEDGEMENT_REQUESTS =
                JsonFactory.newJsonObjectFieldDefinition("acknowledgementRequests",
                        FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Source} header mapping.
         */
        public static final JsonFieldDefinition<JsonObject> HEADER_MAPPING =
                JsonFactory.newJsonObjectFieldDefinition("headerMapping", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Source} payload mapping.
         */
        public static final JsonFieldDefinition<JsonArray> PAYLOAD_MAPPING =
                JsonFactory.newJsonArrayFieldDefinition("payloadMapping", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the reply-target.
         */
        public static final JsonFieldDefinition<JsonObject> REPLY_TARGET =
                JsonFactory.newJsonObjectFieldDefinition("replyTarget", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field for whether reply-target is enabled. Set to false explicitly to disable reply sending.
         * Otherwise it is assumed that the connection was created before reply-target was introduced and
         * live migration will occur. The field is within the "replyTarget" block but is treated as a part of
         * Source instead.
         */
        public static final JsonFieldDefinition<Boolean> REPLY_TARGET_ENABLED =
                JsonFactory.newBooleanFieldDefinition("replyTarget/enabled", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field for declared acknowledgement labels, namely the labels of acknowledgements the connection
         * source is allowed to send.
         *
         * @since 1.4.0
         */
        public static final JsonFieldDefinition<JsonArray> DECLARED_ACKS =
                JsonFactory.newJsonArrayFieldDefinition("declaredAcks", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
