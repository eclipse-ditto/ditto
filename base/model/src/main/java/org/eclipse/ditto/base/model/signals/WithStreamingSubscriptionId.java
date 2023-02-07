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
package org.eclipse.ditto.base.model.signals;

import org.eclipse.ditto.base.model.signals.commands.streaming.StreamingSubscriptionCommand;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;

/**
 * Interface of streaming commands/events addressing a particular session identified by a subscription ID.
 *
 * @since 3.2.0
 */
public interface WithStreamingSubscriptionId<T extends WithStreamingSubscriptionId<T>> extends
        StreamingSubscriptionCommand<T> {

    /**
     * Returns the subscriptionId identifying the session of this streaming signal.
     *
     * @return the subscriptionId.
     */
    String getSubscriptionId();

    /**
     * Json fields of this command.
     */
    final class JsonFields {

        /**
         * JSON field for the streaming subscription ID.
         */
        public static final JsonFieldDefinition<String> SUBSCRIPTION_ID =
                JsonFactory.newStringFieldDefinition("subscriptionId");

        JsonFields() {
            throw new AssertionError();
        }

    }
}
