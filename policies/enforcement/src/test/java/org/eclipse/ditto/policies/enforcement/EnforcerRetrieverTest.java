/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EnforcerRetrieverTest {

    @Mock
    private Cache<EnforcementCacheKey, Entry<EnforcementCacheKey>> idCache;
    @Mock
    private Cache<EnforcementCacheKey, Entry<Enforcer>> enforcerCache;

    private EnforcerRetriever<Enforcer> retriever;

    @Before
    public void setUp() {
        retriever = new EnforcerRetriever<Enforcer>(idCache, enforcerCache);
    }

    @Test
    public void verifyLookupRevealsInnerException() throws ExecutionException, InterruptedException {
        final DittoRuntimeException expectedException = DittoInternalErrorException.newBuilder().build();
        final EnforcementCacheKey entityId = EnforcementCacheKey.of(EntityId.of(EntityType.of("any"), "id"));
        when(idCache.get(any(EnforcementCacheKey.class))).thenReturn(
                CompletableFuture.completedFuture(Optional.of(Entry.nonexistent())));

        final CompletionStage<Contextual<WithDittoHeaders>> result =
                retriever.retrieve(entityId, (entityIdEntry, enforcerEntry) -> {
                    throw expectedException;
                });

        verify(idCache).get(entityId);
        verifyNoInteractions(enforcerCache);
        verifyException(result, expectedException);
    }

    @Test
    public void verifyLookupRevealsInnermostException() throws ExecutionException, InterruptedException {
        final DittoRuntimeException expectedException = DittoInternalErrorException.newBuilder().build();
        final EnforcementCacheKey entityId = EnforcementCacheKey.of(EntityId.of(EntityType.of("any"), "id"));
        final EnforcementCacheKey innerEntityId =
                EnforcementCacheKey.of(EntityId.of(EntityType.of("other"), "randomId"));
        when(idCache.get(any(EnforcementCacheKey.class))).thenReturn(
                CompletableFuture.completedFuture(Optional.of(Entry.permanent(innerEntityId))));
        when(enforcerCache.get(any(EnforcementCacheKey.class))).thenReturn(
                CompletableFuture.completedFuture(Optional.of(Entry.nonexistent())));
        final CompletionStage<Contextual<WithDittoHeaders>> result =
                retriever.retrieve(entityId, (entityIdEntry, enforcerEntry) -> {
                    throw expectedException;
                });

        verify(idCache).get(entityId);
        verify(enforcerCache).get(innerEntityId);
        verifyException(result, expectedException);
    }

    @Test
    public void verifyLookupEnforcerRevealsException() throws ExecutionException, InterruptedException {
        final DittoRuntimeException expectedException = DittoInternalErrorException.newBuilder().build();
        final EnforcementCacheKey entityId = EnforcementCacheKey.of(EntityId.of(EntityType.of("any"), "id"));
        when(enforcerCache.get(any(EnforcementCacheKey.class))).thenReturn(
                CompletableFuture.completedFuture(Optional.of(Entry.nonexistent())));

        final CompletionStage<Contextual<WithDittoHeaders>> result =
                retriever.retrieveByEnforcerKey(entityId, enforcerEntry -> {
                    throw expectedException;
                });

        verify(enforcerCache).get(entityId);
        verifyException(result, expectedException);
    }

    private static void verifyException(final CompletionStage<Contextual<WithDittoHeaders>> completionStage,
            final Throwable expectedException) throws ExecutionException, InterruptedException {
        assertThat(completionStage.thenApply(unused -> new RuntimeException("this should not be happening"))
                .exceptionally(executionException -> (RuntimeException) executionException.getCause())
                .toCompletableFuture()
                .get())
                .isEqualTo(expectedException);
    }

}
