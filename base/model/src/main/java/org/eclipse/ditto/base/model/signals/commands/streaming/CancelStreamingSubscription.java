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
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Command for cancelling a subscription of streaming results.
 * Corresponds to the reactive-streams signal {@code Subscription#cancel()}.
 *
 * @since 3.2.0
 */
@Immutable
@JsonParsableCommand(typePrefix = StreamingSubscriptionCommand.TYPE_PREFIX, name = CancelStreamingSubscription.NAME)
public final class CancelStreamingSubscription extends AbstractStreamingSubscriptionCommand<CancelStreamingSubscription>
        implements WithStreamingSubscriptionId<CancelStreamingSubscription> {

    /**
     * Name of the command.
     */
    public static final String NAME = "cancel";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final String subscriptionId;

    private CancelStreamingSubscription(final EntityId entityId,
            final JsonPointer resourcePath,
            final String subscriptionId,
            final DittoHeaders dittoHeaders) {
        super(TYPE, entityId, resourcePath, dittoHeaders);
        this.subscriptionId = subscriptionId;
    }

    /**
     * Returns a new instance of the command.
     *
     * @param entityId the entityId that should be streamed.
     * @param resourcePath the resource path for which to stream.
     * @param subscriptionId ID of the subscription to cancel.
     * @param dittoHeaders the headers of the command.
     * @return a new command to cancel a subscription.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static CancelStreamingSubscription of(final EntityId entityId,
            final JsonPointer resourcePath,
            final String subscriptionId,
            final DittoHeaders dittoHeaders) {
        return new CancelStreamingSubscription(entityId, resourcePath, subscriptionId, dittoHeaders);
    }

    /**
     * Creates a new {@code CancelStreamingSubscription} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static CancelStreamingSubscription fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<CancelStreamingSubscription>(TYPE, jsonObject).deserialize(() ->
                new CancelStreamingSubscription(deserializeEntityId(jsonObject),
                        JsonPointer.of(
                                jsonObject.getValueOrThrow(StreamingSubscriptionCommand.JsonFields.JSON_RESOURCE_PATH)),
                        jsonObject.getValueOrThrow(WithStreamingSubscriptionId.JsonFields.SUBSCRIPTION_ID),
                        dittoHeaders
                )
        );
    }

    @Override
    public String getSubscriptionId() {
        return subscriptionId;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);
        jsonObjectBuilder.set(WithStreamingSubscriptionId.JsonFields.SUBSCRIPTION_ID, subscriptionId);
    }

    @Override
    public CancelStreamingSubscription setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new CancelStreamingSubscription(entityId, resourcePath, subscriptionId, dittoHeaders);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CancelStreamingSubscription)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final CancelStreamingSubscription that = (CancelStreamingSubscription) o;
        return Objects.equals(subscriptionId, that.subscriptionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), subscriptionId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                super.toString() +
                ", subscriptionId=" + subscriptionId +
                ']';
    }
}
