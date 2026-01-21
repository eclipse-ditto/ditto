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

    @Test
    public void resolveAccessiblePathsExcludesParentPathsWithRevokedChildren() {
        // GIVEN: A header where user has access to /attributes/complex/some but /attributes/complex/secret is revoked
        // The header should only contain /attributes/complex/some, not /attributes/complex (parent with revoked child)
        final AuthorizationSubject partialSubject = AuthorizationSubject.newInstance("tenant:partial");

        final String partialAccessHeader = JsonFactory.newObjectBuilder()
                .set("subjects", JsonFactory.newArrayBuilder()
                        .add(partialSubject.getId())
                        .build())
                .set("paths", JsonFactory.newObjectBuilder()
                        .set(JsonFactory.newKey("attributes/complex/some"), JsonFactory.newArrayBuilder().add(0).build())
                        .build())
                .build()
                .toString();

        final AuthorizationContext context = authContext(partialSubject);

        // WHEN: Resolving accessible paths
        final var result = PartialAccessPathResolver.resolveAccessiblePathsFromHeader(
                partialAccessHeader,
                context,
                readGranted(partialSubject));

        // THEN: Should only contain the specific accessible path, not the parent
        assertThat(result.hasUnrestrictedAccess()).isFalse();
        assertThat(result.shouldFilter()).isTrue();
        assertThat(result.getAccessiblePaths())
                .containsExactly(JsonPointer.of("/attributes/complex/some"));
        assertThat(result.getAccessiblePaths())
                .doesNotContain(JsonPointer.of("/attributes/complex"));
    }

    @Test
    public void resolveAccessiblePathsHandlesMultipleSubjectsWithDifferentPaths() {
        // GIVEN: Multiple subjects with different accessible paths
        final AuthorizationSubject user1Subject = AuthorizationSubject.newInstance("tenant:user1");
        final AuthorizationSubject user2Subject = AuthorizationSubject.newInstance("tenant:user2");

        final String partialAccessHeader = JsonFactory.newObjectBuilder()
                .set("subjects", JsonFactory.newArrayBuilder()
                        .add(user1Subject.getId())
                        .add(user2Subject.getId())
                        .build())
                .set("paths", JsonFactory.newObjectBuilder()
                        .set(JsonFactory.newKey("attributes/type"), JsonFactory.newArrayBuilder().add(0).build())
                        .set(JsonFactory.newKey("attributes/complex/some"), JsonFactory.newArrayBuilder().add(0).add(1).build())
                        .set(JsonFactory.newKey("features/some/properties/configuration/foo"),
                                JsonFactory.newArrayBuilder().add(0).build())
                        .set(JsonFactory.newKey("features/other/properties/public"),
                                JsonFactory.newArrayBuilder().add(1).build())
                        .build())
                .build()
                .toString();

        // WHEN: Resolving for user1
        final var user1Result = PartialAccessPathResolver.resolveAccessiblePathsFromHeader(
                partialAccessHeader,
                authContext(user1Subject),
                readGranted(user1Subject));

        // WHEN: Resolving for user2
        final var user2Result = PartialAccessPathResolver.resolveAccessiblePathsFromHeader(
                partialAccessHeader,
                authContext(user2Subject),
                readGranted(user2Subject));

        // THEN: Each user should get their own accessible paths
        assertThat(user1Result.getAccessiblePaths())
                .containsExactlyInAnyOrder(
                        JsonPointer.of("/attributes/type"),
                        JsonPointer.of("/attributes/complex/some"),
                        JsonPointer.of("/features/some/properties/configuration/foo"));

        assertThat(user2Result.getAccessiblePaths())
                .containsExactlyInAnyOrder(
                        JsonPointer.of("/attributes/complex/some"),
                        JsonPointer.of("/features/other/properties/public"));
    }

    private static Set<AuthorizationSubject> readGranted(final AuthorizationSubject... subjects) {
        return Set.of(subjects);
    }
}
