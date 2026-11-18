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
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonPointer;

/**
 * What one subscriber may read of a signal according to its {@code ditto-partial-access-paths} and
 * {@code ditto-revoked-access-paths} headers.
 */
final class SubscriberAccess {

    private final PartialAccessPathResolver.AccessiblePathsResult partial;
    private final Set<JsonPointer> revokedPaths;
    private final boolean rootRevoked;
    @Nullable private final String malformedReason;

    SubscriberAccess(final PartialAccessPathResolver.AccessiblePathsResult partial,
            final Set<JsonPointer> revokedPaths, final boolean rootRevoked, @Nullable final String malformedReason) {
        this.partial = partial;
        this.revokedPaths = revokedPaths;
        this.rootRevoked = rootRevoked;
        this.malformedReason = malformedReason;
    }

    static SubscriberAccess resolve(final DittoHeaders headers, final AuthorizationContext subscriberAuthContext) {
        final AccessPathsHeader partialHeader =
                AccessPathsHeader.parse(headers.get(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey()));
        final AccessPathsHeader revokedHeader =
                AccessPathsHeader.parse(headers.get(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey()));
        final PartialAccessPathResolver.AccessiblePathsResult partial = PartialAccessPathResolver
                .resolveAccessiblePaths(partialHeader.getPathsBySubject(), subscriberAuthContext,
                        headers.getReadGrantedSubjects());
        final boolean rootRevoked = subscriberAuthContext.getAuthorizationSubjects().stream()
                .map(AuthorizationSubject::getId)
                .anyMatch(revokedHeader.getRootSubjects()::contains);
        return new SubscriberAccess(partial,
                PartialAccessPathResolver.resolveRevokedPaths(revokedHeader.getPathsBySubject(),
                        subscriberAuthContext),
                rootRevoked,
                partialHeader.getMalformedReason().or(revokedHeader::getMalformedReason).orElse(null));
    }

    PartialAccessPathResolver.AccessiblePathsResult getPartial() {
        return partial;
    }

    Set<JsonPointer> getRevokedPaths() {
        return revokedPaths;
    }

    /**
     * @return whether READ on the thing root is revoked for a subject of the subscriber, so that of the thing identity
     * fields only {@code thingId} is kept for it.
     */
    boolean isRootRevoked() {
        return rootRevoked;
    }

    boolean isMalformed() {
        return malformedReason != null;
    }

    Optional<String> getMalformedReason() {
        return Optional.ofNullable(malformedReason);
    }

    boolean isUnrestricted() {
        return malformedReason == null && !rootRevoked && revokedPaths.isEmpty() && partial.hasUnrestrictedAccess();
    }

}
