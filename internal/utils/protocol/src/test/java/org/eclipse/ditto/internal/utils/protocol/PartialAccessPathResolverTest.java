/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Set;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.junit.Test;

/**
 * Tests for {@link PartialAccessPathResolver}.
 */
public final class PartialAccessPathResolverTest {

    private static final AuthorizationSubject SUBJECT_PARTIAL =
            AuthorizationSubject.newInstance("tenant:partial");
    private static final AuthorizationSubject SUBJECT_FULL =
            AuthorizationSubject.newInstance("tenant:full");

    private static final String PARTIAL_ACCESS_HEADER = JsonFactory.newObjectBuilder()
            .set("subjects", JsonFactory.newArrayBuilder()
                    .add(SUBJECT_PARTIAL.getId())
                    .build())
            .set("paths", JsonFactory.newObjectBuilder()
                    .set(JsonFactory.newKey("attributes/public"), JsonFactory.newArrayBuilder().add(0).build())
                    .set(JsonFactory.newKey("features/temp/properties/value"),
                            JsonFactory.newArrayBuilder().add(0).build())
                    .build())
            .build()
            .toString();

    @Test
    public void unrestrictedWhenHeaderMissing() {
        final AuthorizationContext context = authContext(SUBJECT_FULL);

        final var result = PartialAccessPathResolver.resolveAccessiblePathsFromHeader(
                null,
                context,
                readGranted(SUBJECT_FULL));

        assertThat(result.hasUnrestrictedAccess()).isTrue();
        assertThat(result.shouldFilter()).isFalse();
        assertThat(result.getAccessiblePaths()).isEmpty();
    }

    @Test
    public void unrestrictedWhenSubjectNotIndexedButReadGranted() {
        final AuthorizationContext context = authContext(SUBJECT_FULL);

        final var result = PartialAccessPathResolver.resolveAccessiblePathsFromHeader(
                PARTIAL_ACCESS_HEADER,
                context,
                readGranted(SUBJECT_FULL));

        assertThat(result.hasUnrestrictedAccess()).isTrue();
        assertThat(result.shouldFilter()).isFalse();
        assertThat(result.getAccessiblePaths()).isEmpty();
    }

    @Test
    public void filteredPathsReturnedForPartialSubject() {
        final AuthorizationContext context = authContext(SUBJECT_PARTIAL);

        final var result = PartialAccessPathResolver.resolveAccessiblePathsFromHeader(
                PARTIAL_ACCESS_HEADER,
                context,
                readGranted(SUBJECT_PARTIAL));

        assertThat(result.hasUnrestrictedAccess()).isFalse();
        assertThat(result.shouldFilter()).isTrue();
        assertThat(result.getAccessiblePaths())
                .containsExactlyInAnyOrder(
                        JsonPointer.of("/attributes/public"),
                        JsonPointer.of("/features/temp/properties/value"));
    }

    @Test
    public void noAccessReturnedWhenSubscriberNotGranted() {
        final AuthorizationContext context = authContext(SUBJECT_PARTIAL);

        final var result = PartialAccessPathResolver.resolveAccessiblePathsFromHeader(
                PARTIAL_ACCESS_HEADER,
                context,
                Set.of());

        assertThat(result.hasUnrestrictedAccess()).isFalse();
        assertThat(result.shouldFilter()).isFalse();
        assertThat(result.getAccessiblePaths()).isEmpty();
    }

    @Test
    public void unrestrictedWhenAuthorizationContextMissing() {
        final var result = PartialAccessPathResolver.resolveAccessiblePathsFromHeader(
                PARTIAL_ACCESS_HEADER,
                null,
                readGranted(SUBJECT_PARTIAL));

        assertThat(result.hasUnrestrictedAccess()).isTrue();
        assertThat(result.shouldFilter()).isFalse();
        assertThat(result.getAccessiblePaths()).isEmpty();
    }

    @Test
    public void unrestrictedWhenAuthorizationContextEmpty() {
        final AuthorizationContext emptyContext =
                AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED, Collections.emptyList());

        final var result = PartialAccessPathResolver.resolveAccessiblePathsFromHeader(
                PARTIAL_ACCESS_HEADER,
                emptyContext,
                readGranted(SUBJECT_PARTIAL));

        assertThat(result.hasUnrestrictedAccess()).isTrue();
        assertThat(result.shouldFilter()).isFalse();
        assertThat(result.getAccessiblePaths()).isEmpty();
    }

    private static AuthorizationContext authContext(final AuthorizationSubject subject) {
        return AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED, subject);
    }

    private static Set<AuthorizationSubject> readGranted(final AuthorizationSubject... subjects) {
        return Set.of(subjects);
    }
}
