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
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Determines the part of a signal a subscriber may read, see {@link ReadableSignalView}.
 *
 * @since 3.9.9
 */
public final class PartialAccessSignalFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartialAccessSignalFilter.class);
    private static final ProtocolAdapter PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();
    private static final JsonKey THING_ID_KEY = JsonKey.of("thingId");

    private PartialAccessSignalFilter() {
        throw new AssertionError();
    }

    /**
     * Returns the part of the signal the subscriber may read.
     *
     * @param signal the signal.
     * @param subscriberAuthContext the authorization context of the subscriber.
     * @return the view of the signal for the subscriber, or an empty Optional if the signal must neither be delivered
     * to the subscriber nor evaluated on its behalf.
     */
    public static Optional<ReadableSignalView> viewFor(final Signal<?> signal,
            @Nullable final AuthorizationContext subscriberAuthContext) {
        return viewFor(signal, null, subscriberAuthContext);
    }

    /**
     * Like {@link #viewFor(Signal, AuthorizationContext)}, for a caller which already converted the signal into an
     * adaptable including its internal headers.
     *
     * @param signal the signal.
     * @param adaptable the signal as adaptable including its internal headers, or {@code null}.
     * @param subscriberAuthContext the authorization context of the subscriber.
     * @return see {@link #viewFor(Signal, AuthorizationContext)}.
     */
    public static Optional<ReadableSignalView> viewFor(final Signal<?> signal, @Nullable final Adaptable adaptable,
            @Nullable final AuthorizationContext subscriberAuthContext) {

        if (!hasAccessRestrictions(signal.getDittoHeaders())) {
            return Optional.of(ReadableSignalView.unrestricted(signal, adaptable));
        }
        try {
            return computeView(signal, adaptable, subscriberAuthContext);
        } catch (final RuntimeException e) {
            LOGGER.warn("Not delivering signal <{}> with correlation-id <{}>, as its readable part cannot be " +
                    "determined: {}", signal.getType(), correlationId(signal), e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * @param dittoHeaders the headers of a signal.
     * @return whether the headers restrict the READ access of subscribers to the signal's payload.
     */
    public static boolean hasAccessRestrictions(final DittoHeaders dittoHeaders) {
        return isNotEmpty(dittoHeaders.get(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey())) ||
                isNotEmpty(dittoHeaders.get(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey()));
    }

    private static Optional<ReadableSignalView> computeView(final Signal<?> signal,
            @Nullable final Adaptable givenAdaptable, @Nullable final AuthorizationContext subscriberAuthContext) {

        if (subscriberAuthContext == null || subscriberAuthContext.isEmpty()) {
            final Adaptable adaptable = givenAdaptable != null ? givenAdaptable :
                    toAdaptable(signal, AuthorizationContext.empty());
            if (AdaptablePartialAccessFilter.isPayloadFilterable(adaptable.getTopicPath())) {
                return notReadable(signal, "the subscriber has no authorization context");
            }
            return Optional.of(ReadableSignalView.unrestricted(signal, adaptable));
        }
        final SubscriberAccess access = SubscriberAccess.resolve(signal.getDittoHeaders(), subscriberAuthContext);
        final Optional<String> malformedReason = access.getMalformedReason();
        if (malformedReason.isPresent()) {
            LOGGER.warn("Not delivering signal <{}> with correlation-id <{}>, as its access paths headers are " +
                    "malformed: {}", signal.getType(), correlationId(signal), malformedReason.get());
            return Optional.empty();
        }
        if (access.isUnrestricted()) {
            return Optional.of(ReadableSignalView.unrestricted(signal, givenAdaptable));
        }
        final Adaptable adaptable = givenAdaptable != null ? givenAdaptable :
                toAdaptable(signal, subscriberAuthContext);
        final TopicPath topicPath = adaptable.getTopicPath();
        if (!AdaptablePartialAccessFilter.isPayloadFilterable(topicPath)) {
            return Optional.of(ReadableSignalView.restricted(signal,
                    AdaptablePartialAccessFilter.filter(adaptable, access), access));
        }
        final JsonPointer path = AdaptablePartialAccessFilter.eventPath(adaptable);
        if (!AdaptablePartialAccessFilter.isPathReadable(path, access)) {
            return notReadable(signal, "the subscriber may not read its path");
        }
        final Adaptable filtered = AdaptablePartialAccessFilter.filter(adaptable, access);
        if (AdaptablePartialAccessFilter.isLiveCommand(topicPath)) {
            if (AdaptablePartialAccessFilter.isPayloadChanged(adaptable, filtered)) {
                return notReadable(signal, "the subscriber may not read all of its payload");
            }
            return Optional.of(ReadableSignalView.restricted(signal, filtered, access));
        }
        if (isEmptiedByFiltering(adaptable, filtered, path, access)) {
            return notReadable(signal, "the subscriber may read nothing of its payload");
        }
        if (filtered == adaptable) {
            return Optional.of(ReadableSignalView.restricted(signal, adaptable, access));
        }
        final Signal<?> readableSignal =
                PROTOCOL_ADAPTER.fromAdaptable(filtered).setDittoHeaders(signal.getDittoHeaders());
        return Optional.of(ReadableSignalView.restricted(readableSignal, filtered, access));
    }

    private static Adaptable toAdaptable(final Signal<?> signal, final AuthorizationContext subscriberAuthContext) {
        return PROTOCOL_ADAPTER.toAdaptable(signal, ProtocolAdapter.determineChannel(signal), subscriberAuthContext);
    }

    private static boolean isEmptiedByFiltering(final Adaptable original, final Adaptable filtered,
            final JsonPointer path, final SubscriberAccess access) {

        final boolean rootEventOfRootRevoked = access.isRootRevoked() && path.isEmpty();
        final Optional<JsonValue> filteredValue = filtered.getPayload().getValue();
        final boolean filteredEmpty = filteredValue
                .map(value -> isEmptyObject(value) || rootEventOfRootRevoked && isThingIdOnly(value))
                .orElse(true);
        if (rootEventOfRootRevoked && filteredEmpty) {
            return true;
        }
        final Optional<JsonValue> originalValue = original.getPayload().getValue();
        return originalValue.isPresent() && filteredValue.isPresent() && filteredEmpty &&
                !isEmptyObject(originalValue.get());
    }

    private static boolean isEmptyObject(final JsonValue value) {
        return value.isObject() && !value.isNull() && value.asObject().isEmpty();
    }

    private static boolean isThingIdOnly(final JsonValue value) {
        return value.isObject() && !value.isNull() && value.asObject().getSize() == 1 &&
                value.asObject().contains(THING_ID_KEY);
    }

    private static Optional<ReadableSignalView> notReadable(final Signal<?> signal, final String reason) {
        LOGGER.debug("Not delivering signal <{}> with correlation-id <{}>, as {}.", signal.getType(),
                correlationId(signal), reason);
        return Optional.empty();
    }

    private static String correlationId(final Signal<?> signal) {
        return signal.getDittoHeaders().getCorrelationId().orElse("");
    }

    private static boolean isNotEmpty(@Nullable final String headerValue) {
        return headerValue != null && !headerValue.isEmpty();
    }

}
