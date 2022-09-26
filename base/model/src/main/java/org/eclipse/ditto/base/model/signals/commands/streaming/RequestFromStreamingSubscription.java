/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.signals.commands.streaming;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.WithStreamingSubscriptionId;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Command for requesting items from a subscription of streaming results.
 * Corresponds to the reactive-streams signal {@code Subscription#request(long)}.
 *
 * @since 3.2.0
 */
@Immutable
@JsonParsableCommand(typePrefix = StreamingSubscriptionCommand.TYPE_PREFIX, name = RequestFromStreamingSubscription.NAME)
public final class RequestFromStreamingSubscription extends AbstractStreamingSubscriptionCommand<RequestFromStreamingSubscription>
        implements WithStreamingSubscriptionId<RequestFromStreamingSubscription> {

    /**
     * Name of the command.
     */
    public static final String NAME = "request";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final String subscriptionId;
    private final long demand;

    private RequestFromStreamingSubscription(final EntityId entityId,
            final JsonPointer resourcePath,
            final String subscriptionId,
            final long demand,
            final DittoHeaders dittoHeaders) {

        super(TYPE, entityId, resourcePath, dittoHeaders);
        this.subscriptionId = subscriptionId;
        this.demand = demand;
    }

    /**
     * Returns a new instance of the command.
     *
     * @param entityId the entityId that should be streamed.
     * @param resourcePath the resource path for which to stream.
     * @param subscriptionId ID of the subscription to request from.
     * @param demand how many pages to request.
     * @param dittoHeaders the headers of the command.
     * @return a new command to request from a subscription.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static RequestFromStreamingSubscription of(final EntityId entityId,
            final JsonPointer resourcePath,
            final String subscriptionId,
            final long demand,
            final DittoHeaders dittoHeaders) {
        return new RequestFromStreamingSubscription(entityId, resourcePath, subscriptionId, demand, dittoHeaders);
    }

    /**
     * Creates a new {@code RequestSubscription} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RequestFromStreamingSubscription fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<RequestFromStreamingSubscription>(TYPE, jsonObject).deserialize(() ->
                new RequestFromStreamingSubscription(deserializeEntityId(jsonObject),
                        JsonPointer.of(
                                jsonObject.getValueOrThrow(StreamingSubscriptionCommand.JsonFields.JSON_RESOURCE_PATH)),
                        jsonObject.getValueOrThrow(WithStreamingSubscriptionId.JsonFields.SUBSCRIPTION_ID),
                        jsonObject.getValueOrThrow(JsonFields.DEMAND),
                        dittoHeaders
                )
        );
    }


    @Override
    public String getSubscriptionId() {
        return subscriptionId;
    }


    /**
     * Returns the demand which is to be included in the JSON of the retrieved entity.
     *
     * @return the demand.
     */
    public long getDemand() {
        return demand;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);
        jsonObjectBuilder.set(WithStreamingSubscriptionId.JsonFields.SUBSCRIPTION_ID, subscriptionId);
        jsonObjectBuilder.set(JsonFields.DEMAND, demand);
    }

    @Override
    public String getResourceType() {
        return getEntityType().toString();
    }

    @Override
    public RequestFromStreamingSubscription setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new RequestFromStreamingSubscription(entityId, resourcePath, subscriptionId, demand, dittoHeaders);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RequestFromStreamingSubscription)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final RequestFromStreamingSubscription that = (RequestFromStreamingSubscription) o;
        return Objects.equals(subscriptionId, that.subscriptionId) && demand == that.demand;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), subscriptionId, demand);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                super.toString() +
                ", subscriptionId=" + subscriptionId +
                ", demand=" + demand +
                ']';
    }

    /**
     * JSON fields of this command.
     */
    public static final class JsonFields {

        /**
         * JSON field for number of pages demanded by this command.
         */
        public static final JsonFieldDefinition<Long> DEMAND = JsonFactory.newLongFieldDefinition("demand");

        JsonFields() {
            throw new AssertionError();
        }
    }
}
