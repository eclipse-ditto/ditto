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
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;

/**
 * This {@link HeaderEntryFilter} checks if the given key references {@link DittoHeaderDefinition#REQUESTED_ACKS} and
 * removes {@link DittoAcknowledgementLabel Ditto-internal acknowledgement requests} from the returned value.
 */
@Immutable
final class DittoAckRequestsFilter extends AbstractHeaderEntryFilter {

    // Representation of the supposedly most often occurring ack request header value.
    private static final String TWIN_PERSISTED_ONLY_VALUE = "[\"" + DittoAcknowledgementLabel.TWIN_PERSISTED + "\"]";
    private static final String LIVE_RESPONSE_ONLY_VALUE = "[\"" + DittoAcknowledgementLabel.LIVE_RESPONSE + "\"]";

    private static final JsonValue EMPTY_JSON_STRING = JsonValue.of("");

    private static final DittoAckRequestsFilter INSTANCE = new DittoAckRequestsFilter();

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
        return INSTANCE;
    }

    @Override
    @Nullable
    public String filterValue(final String key, final String value) {
        String result = value;
        if (Objects.equals(DittoHeaderDefinition.REQUESTED_ACKS.getKey(), key)) {
            if (isTwinPersistedOnly(value) || isLiveResponseOnly(value) || value.isEmpty()) {
                result = null;
            } else {
                result = parseAsJsonArrayAndFilter(value);
            }
        }
        return result;
    }

    private static boolean isTwinPersistedOnly(final String value) {
        return TWIN_PERSISTED_ONLY_VALUE.equals(value);
    }

    private static boolean isLiveResponseOnly(final String value) {
        return LIVE_RESPONSE_ONLY_VALUE.equals(value);
    }

    private static String parseAsJsonArrayAndFilter(final String value) {
        final JsonArray originalAckRequestsJsonArray = JsonArray.of(value);
        final JsonArray filteredAckRequestsJsonArray = originalAckRequestsJsonArray.stream()
                .filter(jsonValue -> !jsonValue.equals(EMPTY_JSON_STRING))
                .filter(jsonValue -> !isDittoInternal(jsonValue))
                .collect(JsonCollectors.valuesToArray());

        return filteredAckRequestsJsonArray.toString();
    }

    private static boolean isDittoInternal(final JsonValue ackRequestLabelJsonValue) {
        for (final AcknowledgementLabel label : DittoAcknowledgementLabel.values()) {
            if (Objects.equals(label.toString(), ackRequestLabelJsonValue.asString())) {
                return true;
            }
        }
        return false;
    }

}
