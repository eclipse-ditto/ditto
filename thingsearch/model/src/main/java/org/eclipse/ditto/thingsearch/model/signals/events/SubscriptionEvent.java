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
package org.eclipse.ditto.thingsearch.model.signals.events;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.events.Event;

/**
 * Interface for all outgoing messages related to a subscription for search results..
 *
 * @param <T> the type of the implementing class.
 * @since 1.1.0
 */
public interface SubscriptionEvent<T extends SubscriptionEvent<T>> extends Event<T> {

    /**
     * Resource type of subscription events.
     */
    String RESOURCE_TYPE = "thing-search.subscription";

    /**
     * Type Prefix of Thing events.
     */
    String TYPE_PREFIX = RESOURCE_TYPE + "." + TYPE_QUALIFIER + ":";

    /**
     * Retrieve the subscription ID.
     *
     * @return the subscription ID.
     */
    String getSubscriptionId();

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    @Override
    default String getResourceType() {
        return RESOURCE_TYPE;
    }

    /**
     * Common JSON fields of subscription events.
     */
    final class JsonFields {

        /**
         * The JSON field for subscription ID.
         */
        public static final JsonFieldDefinition<String> SUBSCRIPTION_ID =
                JsonFactory.newStringFieldDefinition("subscriptionId");

        JsonFields() {
            throw new AssertionError();
        }
    }

}
