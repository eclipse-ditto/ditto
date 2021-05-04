/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.model.signals.commands.subscription;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;
import org.eclipse.ditto.thingsearch.model.signals.commands.WithSubscriptionId;

/**
 * Command for requesting pages from a subscription of search results.
 * Corresponds to the reactive-streams signal {@code Subscription#request(long)}.
 *
 * @since 1.1.0
 */
@Immutable
@JsonParsableCommand(typePrefix = ThingSearchCommand.TYPE_PREFIX, name = RequestFromSubscription.NAME)
public final class RequestFromSubscription extends AbstractCommand<RequestFromSubscription>
        implements ThingSearchCommand<RequestFromSubscription>, WithSubscriptionId<RequestFromSubscription> {

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

    private RequestFromSubscription(final String subscriptionId, final long demand, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.subscriptionId = subscriptionId;
        this.demand = demand;
    }

    /**
     * Returns a new instance of the command.
     *
     * @param subscriptionId ID of the subscription to request from.
     * @param demand how many pages to request.
     * @param dittoHeaders the headers of the command.
     * @return a new command to request from a subscription.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static RequestFromSubscription of(final String subscriptionId, final long demand,
            final DittoHeaders dittoHeaders) {
        return new RequestFromSubscription(subscriptionId, demand, dittoHeaders);
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
    public static RequestFromSubscription fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<RequestFromSubscription>(TYPE, jsonObject).deserialize(() -> {
            final String subscriptionId = jsonObject.getValueOrThrow(JsonFields.SUBSCRIPTION_ID);
            final long demand = jsonObject.getValueOrThrow(JsonFields.DEMAND);
            return new RequestFromSubscription(subscriptionId, demand, dittoHeaders);
        });
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
        jsonObjectBuilder.set(JsonFields.SUBSCRIPTION_ID, subscriptionId);
        jsonObjectBuilder.set(JsonFields.DEMAND, demand);
    }

    @Override
    public Category getCategory() {
        // this is a query command because its execution does not affect the persistence.
        return Category.QUERY;
    }

    @Override
    public RequestFromSubscription setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new RequestFromSubscription(subscriptionId, demand, dittoHeaders);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o)
            return true;
        if (!(o instanceof RequestFromSubscription))
            return false;
        if (!super.equals(o))
            return false;
        final RequestFromSubscription that = (RequestFromSubscription) o;
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
         * JSON field for the subscription ID. Should be equal to {@code SubscriptionEvent.JsonFields.SUBSCRIPTION_ID}.
         */
        public static final JsonFieldDefinition<String> SUBSCRIPTION_ID =
                JsonFactory.newStringFieldDefinition("subscriptionId");

        /**
         * JSON field for number of pages demanded by this command.
         */
        public static final JsonFieldDefinition<Long> DEMAND = JsonFactory.newLongFieldDefinition("demand");

        JsonFields() {
            throw new AssertionError();
        }
    }
}
