/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.protocoladapter;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;

/**
 * Contract for the Ditto protocol adapter library.
 */
public final class DittoProtocolAdapter extends AbstractProtocolAdapter {

    private DittoProtocolAdapter() {
        super();
    }

    /**
     * Creates a new {@code DittoProtocolAdapter} instance.
     *
     * @return the instance.
     */
    public static DittoProtocolAdapter newInstance() {
        return new DittoProtocolAdapter();
    }

    @Override
    protected ThingErrorResponse thingErrorResponseFromAdaptable(final Adaptable adaptable) {
        final JsonObjectBuilder jsonObjectBuilder =
                JsonObject.newBuilder().set(ThingCommandResponse.JsonFields.TYPE, ThingErrorResponse.TYPE);

        adaptable.getPayload().getStatus()
                .ifPresent(status -> jsonObjectBuilder.set(ThingCommandResponse.JsonFields.STATUS, status.toInt()));

        adaptable.getPayload().getValue()
                .ifPresent(value -> jsonObjectBuilder.set(ThingCommandResponse.JsonFields.PAYLOAD, value));

        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, adaptable.getTopicPath().getNamespace()
                + ":" + adaptable.getTopicPath().getId());

        final DittoHeaders dittoHeaders = adaptable.getHeaders().orElse(DittoHeaders.empty());
        final DittoHeaders adjustedHeaders = dittoHeaders.toBuilder()
                .channel(adaptable.getTopicPath().getChannel().getName())
                .build();

        return ThingErrorResponse.fromJson(jsonObjectBuilder.build(), adjustedHeaders);
    }

}
