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
package org.eclipse.ditto.base.model.signals;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.WithManifest;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;

/**
 * A service message that incites to action or conveys notice or warning.
 *
 * @param <T> the type of the implementing class.
 */
public interface Signal<T extends Signal<T>> extends Jsonifiable.WithPredicate<JsonObject, JsonField>,
        DittoHeadersSettable<T>, WithManifest, WithType, WithName, WithResource {

    /**
     * The Signal {@code channel} "live".
     * @since 3.0.0
     */
    String CHANNEL_LIVE = "live";

    /**
     * The Signal {@code channel} "twin".
     * @since 3.0.0
     */
    String CHANNEL_TWIN = "twin";

    /**
     * Returns the name of the signal. This is gathered by the type of the signal by default.
     *
     * @return the name.
     */
    @Override
    default String getName() {
        return getType().contains(":") ? getType().split(":")[1] : getType();
    }

    /**
     * Checks whether the passed in {@code signal} (being {@link WithType}) contains the passed in {@code typePrefix}
     * in its {@link WithType#getType()} response.
     *
     * @param signal the signal to check in.
     * @param typePrefix the type prefix to check for.
     * @return {@code true} when the passed in signal's type starts with the passed in type prefix.
     * @since 3.0.0
     */
    static boolean hasTypePrefix(@Nullable final WithType signal, final String typePrefix) {
        final boolean result;
        if (null != signal) {
            final String signalType = signal.getType();
            result = signalType.startsWith(typePrefix);
        } else {
            result = false;
        }
        return result;
    }

    /**
     * Indicates whether the headers of the specified signal argument contain channel {@value CHANNEL_LIVE}.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if the headers of {@code signal} contain the channel {@value CHANNEL_LIVE}.
     * @since 3.0.0
     */
    static boolean isChannelLive(@Nullable final WithDittoHeaders signal) {
        final boolean result;
        if (null != signal) {
            final DittoHeaders dittoHeaders = signal.getDittoHeaders();
            result = dittoHeaders.getChannel().filter(CHANNEL_LIVE::equals).isPresent();
        } else {
            result = false;
        }

        return result;
    }

}
