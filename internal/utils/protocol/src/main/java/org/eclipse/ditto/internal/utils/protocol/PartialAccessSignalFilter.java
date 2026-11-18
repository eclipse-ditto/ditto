/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.protocol;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Determines the part of a thing event a subscriber may read, as a signal. Everything evaluated on behalf of a
 * subscriber (RQL filters, placeholders, the {@code _context} of SSE) must use this signal instead of the original
 * one, as the original one may contain fields the subscriber must not read.
 *
 * @since 3.9.9
 */
public final class PartialAccessSignalFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartialAccessSignalFilter.class);
    private static final ProtocolAdapter PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();

    private PartialAccessSignalFilter() {
        throw new AssertionError();
    }

    /**
     * Returns the part of the signal the subscriber may read.
     *
     * @param signal the signal.
     * @param subscriberAuthContext the authorization context of the subscriber.
     * @return the signal itself if it carries no access restrictions for thing events, the filtered signal otherwise,
     * or an empty Optional if the subscriber may read nothing at the path of the event or the filtered signal cannot be
     * represented; the signal must then not be delivered to the subscriber, nor evaluated on its behalf.
     */
    public static Optional<Signal<?>> filterForSubscriber(final Signal<?> signal,
            @Nullable final AuthorizationContext subscriberAuthContext) {

        if (subscriberAuthContext == null || !hasAccessRestrictions(signal.getDittoHeaders())) {
            return Optional.of(signal);
        }
        final Adaptable adaptable;
        try {
            adaptable = PROTOCOL_ADAPTER.toAdaptable(signal, ProtocolAdapter.determineChannel(signal),
                    subscriberAuthContext);
        } catch (final RuntimeException e) {
            return notReadable(signal, e);
        }
        return filterForSubscriber(signal, adaptable, subscriberAuthContext);
    }

    /**
     * Like {@link #filterForSubscriber(Signal, AuthorizationContext)}, for a caller which already converted the signal
     * into an adaptable.
     *
     * @param signal the signal.
     * @param adaptable the signal as adaptable, including its internal headers.
     * @param subscriberAuthContext the authorization context of the subscriber.
     * @return see {@link #filterForSubscriber(Signal, AuthorizationContext)}.
     */
    public static Optional<Signal<?>> filterForSubscriber(final Signal<?> signal, final Adaptable adaptable,
            @Nullable final AuthorizationContext subscriberAuthContext) {

        if (subscriberAuthContext == null || !hasAccessRestrictions(signal.getDittoHeaders())) {
            return Optional.of(signal);
        }
        try {
            return readableSignal(signal, adaptable,
                    AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(adaptable, subscriberAuthContext),
                    subscriberAuthContext);
        } catch (final RuntimeException e) {
            return notReadable(signal, e);
        }
    }

    /**
     * Like {@link #filterForSubscriber(Signal, AuthorizationContext)}, for a caller which already filtered the adaptable
     * with {@link AdaptablePartialAccessFilter#filterAdaptableForPartialAccess(Adaptable, AuthorizationContext)}.
     *
     * @param signal the signal.
     * @param adaptable the signal as adaptable, including its internal headers.
     * @param filteredAdaptable the adaptable filtered for the subscriber.
     * @param subscriberAuthContext the authorization context of the subscriber.
     * @return see {@link #filterForSubscriber(Signal, AuthorizationContext)}.
     */
    public static Optional<Signal<?>> readableSignal(final Signal<?> signal, final Adaptable adaptable,
            final Adaptable filteredAdaptable, @Nullable final AuthorizationContext subscriberAuthContext) {

        if (!AdaptablePartialAccessFilter.isEventPathReadable(adaptable, subscriberAuthContext)) {
            return Optional.empty();
        }
        if (filteredAdaptable == adaptable) {
            return Optional.of(signal);
        }
        try {
            final Signal<?> filteredSignal = PROTOCOL_ADAPTER.fromAdaptable(filteredAdaptable);
            return Optional.of(filteredSignal.setDittoHeaders(signal.getDittoHeaders()));
        } catch (final RuntimeException e) {
            return notReadable(signal, e);
        }
    }

    private static Optional<Signal<?>> notReadable(final Signal<?> signal, final RuntimeException e) {
        LOGGER.info("Not delivering signal of type <{}>, as its readable part cannot be represented: {}",
                signal.getType(), e.getMessage());
        return Optional.empty();
    }

    /**
     * @param dittoHeaders the headers of a signal.
     * @return whether the headers restrict the READ access of subscribers to the signal's payload.
     */
    public static boolean hasAccessRestrictions(final DittoHeaders dittoHeaders) {
        return isNotEmpty(dittoHeaders.get(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey())) ||
                isNotEmpty(dittoHeaders.get(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey()));
    }

    private static boolean isNotEmpty(@Nullable final String headerValue) {
        return headerValue != null && !headerValue.isEmpty();
    }

}
