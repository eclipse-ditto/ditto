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
package org.eclipse.ditto.protocoladapter;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;

/**
 * This {@link HeaderEntryFilter} checks if the given key references {@link DittoHeaderDefinition#REQUESTED_ACKS} and
 * removes {@link DittoAcknowledgementLabel Ditto-internal acknowledgement requests} from the returned value.
 */
@Immutable
final class DittoAckRequestsFilter extends AbstractHeaderEntryFilter {

    private DittoAckRequestsFilter() {
        super();
    }

    /**
     * Returns an instance of {@code DittoAckRequestsFilter} which adjusts the value of the header entry identified by
     * {@link DittoHeaderDefinition#REQUESTED_ACKS} by removing
     * {@link DittoAcknowledgementLabel Ditto-internal acknowledgement requests} from it.
     *
     * @return the instance.
     */
    public static DittoAckRequestsFilter getInstance() {
        return new DittoAckRequestsFilter();
    }

    @Override
    @Nullable
    public String filterValue(final String key, final String value) {
        String result = value;
        if (Objects.equals(DittoHeaderDefinition.REQUESTED_ACKS.getKey(), key)) {
            final JsonArray originalAckRequestsJsonArray = JsonArray.of(value);
            final JsonArray filteredAckRequestsJsonArray = originalAckRequestsJsonArray.stream()
                    .map(JsonValue::asString)
                    .filter(string -> !string.isEmpty())
                    .map(AcknowledgementRequest::parseAcknowledgementRequest)
                    .filter(ackRequest -> !isDittoInternal(ackRequest))
                    .map(AcknowledgementRequest::toString)
                    .map(JsonValue::of)
                    .collect(JsonCollectors.valuesToArray());

            result = filteredAckRequestsJsonArray.toString();
        }
        return result;
    }

    private static boolean isDittoInternal(final AcknowledgementRequest acknowledgementRequest) {
        return DittoAcknowledgementLabel.contains(acknowledgementRequest.getLabel());
    }

}
