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
 * Command for cancelling a subscription of search results.
 * Corresponds to the reactive-streams signal {@code Subscription#cancel()}.
 *
 * @since 1.1.0
 */
@Immutable
@JsonParsableCommand(typePrefix = ThingSearchCommand.TYPE_PREFIX, name = CancelSubscription.NAME)
public final class CancelSubscription extends AbstractCommand<CancelSubscription>
        implements ThingSearchCommand<CancelSubscription>, WithSubscriptionId<CancelSubscription> {

    /**
     * Name of the command.
     */
    public static final String NAME = "cancel";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final String subscriptionId;

    private CancelSubscription(final String subscriptionId, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.subscriptionId = subscriptionId;
    }

    /**
     * Returns a new instance of the command.
     *
     * @param subscriptionId ID of the subscription to cancel.
     * @param dittoHeaders the headers of the command.
     * @return a new command to cancel a subscription.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static CancelSubscription of(final String subscriptionId, final DittoHeaders dittoHeaders) {
        return new CancelSubscription(subscriptionId, dittoHeaders);
    }

    /**
     * Creates a new {@code CancelSubscription} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static CancelSubscription fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<CancelSubscription>(TYPE, jsonObject).deserialize(() -> {
            final String subscriptionId = jsonObject.getValueOrThrow(JsonFields.SUBSCRIPTION_ID);
            return new CancelSubscription(subscriptionId, dittoHeaders);
        });
    }

    @Override
    public String getSubscriptionId() {
        return subscriptionId;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        jsonObjectBuilder.set(JsonFields.SUBSCRIPTION_ID, subscriptionId);
    }

    @Override
    public Category getCategory() {
        // this is a query command because its execution does not affect the persistence.
        return Category.QUERY;
    }

    @Override
    public CancelSubscription setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new CancelSubscription(subscriptionId, dittoHeaders);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o)
            return true;
        if (!(o instanceof CancelSubscription))
            return false;
        if (!super.equals(o))
            return false;
        final CancelSubscription that = (CancelSubscription) o;
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

    /**
     * Json fields of this command.
     */
    public static final class JsonFields {

        /**
         * JSON field for the subscription ID. Should be equal to {@code SubscriptionEvent.JsonFields.SUBSCRIPTION_ID}.
         */
        public static final JsonFieldDefinition<String> SUBSCRIPTION_ID =
                JsonFactory.newStringFieldDefinition("subscriptionId");

        JsonFields() {
            throw new AssertionError();
        }

    }
}
