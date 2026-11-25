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

import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocol.Adaptable;

/**
 * Applies {@link AdaptablePartialAccessFilter} for one subscriber as {@link PartialAccessSignalFilter} does.
 */
final class SubscriberFilters {

    private SubscriberFilters() {
        throw new AssertionError();
    }

    static Adaptable filter(final Adaptable adaptable, @Nullable final AuthorizationContext subscriberAuthContext) {
        return AdaptablePartialAccessFilter.filter(adaptable, access(adaptable, subscriberAuthContext));
    }

    static Adaptable filter(final Adaptable adaptable, final PartialAccessPathResolver.AccessiblePathsResult partial) {
        return AdaptablePartialAccessFilter.filter(adaptable, new SubscriberAccess(partial, Set.of(), false, null));
    }

    static Adaptable removeRevokedPaths(final Adaptable adaptable, final Set<JsonPointer> revokedPaths) {
        return AdaptablePartialAccessFilter.filter(adaptable, new SubscriberAccess(
                PartialAccessPathResolver.AccessiblePathsResult.unrestricted(), revokedPaths, false, null));
    }

    static boolean isPathReadable(final Adaptable adaptable,
            @Nullable final AuthorizationContext subscriberAuthContext) {
        return !AdaptablePartialAccessFilter.isPayloadFilterable(adaptable.getTopicPath()) ||
                AdaptablePartialAccessFilter.isPathReadable(AdaptablePartialAccessFilter.eventPath(adaptable),
                        access(adaptable, subscriberAuthContext));
    }

    private static SubscriberAccess access(final Adaptable adaptable,
            @Nullable final AuthorizationContext subscriberAuthContext) {
        return SubscriberAccess.resolve(adaptable.getDittoHeaders(),
                subscriberAuthContext != null ? subscriberAuthContext : AuthorizationContext.empty());
    }

}
