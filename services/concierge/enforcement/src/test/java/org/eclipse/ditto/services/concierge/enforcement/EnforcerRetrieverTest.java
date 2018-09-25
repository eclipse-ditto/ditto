/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.concierge.enforcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.models.concierge.cache.Entry;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EnforcerRetrieverTest {

    @Mock
    private Cache<EntityId, Entry<EntityId>> idCache;
    @Mock
    private Cache<EntityId, Entry<Enforcer>> enforcerCache;

    private EnforcerRetriever retriever;

    @Before
    public void setUp() {
        retriever = new EnforcerRetriever(idCache, enforcerCache);
    }

    @Test
    public void verifyLookupRevealsInnerException() throws ExecutionException, InterruptedException {
        final DittoRuntimeException expectedException =
                DittoRuntimeException.newBuilder("this should be happening", HttpStatusCode.HTTPVERSION_NOT_SUPPORTED)
                        .build();
        final EntityId entityId = EntityId.of("any", "id");
        when(idCache.get(any(EntityId.class))).thenReturn(
                CompletableFuture.completedFuture(Optional.of(Entry.nonexistent())));

        final CompletionStage<Void> result = retriever.retrieve(entityId, (entityIdEntry, enforcerEntry) -> {
            throw expectedException;
        });

        verify(idCache).get(entityId);
        verifyZeroInteractions(enforcerCache);
        verifyException(result, expectedException);
    }

    @Test
    public void verifyLookupRevealsInnermostException() throws ExecutionException, InterruptedException {
        final DittoRuntimeException expectedException =
                DittoRuntimeException.newBuilder("this should be happening", HttpStatusCode.HTTPVERSION_NOT_SUPPORTED)
                        .build();
        final EntityId entityId = EntityId.of("any", "id");
        final EntityId innerEntityId = EntityId.of("other", "randomId");
        when(idCache.get(any(EntityId.class))).thenReturn(
                CompletableFuture.completedFuture(Optional.of(Entry.permanent(innerEntityId))));
        when(enforcerCache.get(any(EntityId.class))).thenReturn(
                CompletableFuture.completedFuture(Optional.of(Entry.nonexistent())));
        final CompletionStage<Void> result = retriever.retrieve(entityId, (entityIdEntry, enforcerEntry) -> {
            throw expectedException;
        });

        verify(idCache).get(entityId);
        verify(enforcerCache).get(innerEntityId);
        verifyException(result, expectedException);
    }

    @Test
    public void verifyLookupEnforcerRevealsException() throws ExecutionException, InterruptedException {
        final DittoRuntimeException expectedException =
                DittoRuntimeException.newBuilder("this should be happening", HttpStatusCode.HTTPVERSION_NOT_SUPPORTED)
                        .build();
        final EntityId entityId = EntityId.of("any", "id");
        when(enforcerCache.get(any(EntityId.class))).thenReturn(
                CompletableFuture.completedFuture(Optional.of(Entry.nonexistent())));

        final CompletionStage<Void> result = retriever.retrieveByEnforcerKey(entityId, enforcerEntry -> {
            throw expectedException;
        });

        verify(enforcerCache).get(entityId);
        verifyException(result, expectedException);
    }

    private void verifyException(final CompletionStage<Void> completionStage, final Throwable expectedException)
            throws ExecutionException, InterruptedException {
        assertThat(completionStage.thenApply(_void -> new RuntimeException("this should not be happening"))
                .exceptionally(executionException -> (RuntimeException) executionException.getCause())
                .toCompletableFuture()
                .get())
                .isEqualTo(expectedException);
    }

}
