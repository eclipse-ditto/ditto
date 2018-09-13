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
package org.eclipse.ditto.services.concierge.enforcement.placeholders.strategies;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.concierge.enforcement.placeholders.PlaceholderSubstitution;
import org.junit.Before;
import org.junit.Test;

/**
 * Abstract base class for test of concrete implementations of {@link SubstitutionStrategy}.
 */
public abstract class AbstractSubstitutionStrategyTestBase {
    protected static final String SUBJECT_ID = "nginx:ditto";
    protected static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder()
            .authorizationContext(AuthorizationContext.newInstance(AuthorizationSubject.newInstance(SUBJECT_ID)))
            .build();

    protected PlaceholderSubstitution substitution;

    @Before
    public void init() {
        substitution = PlaceholderSubstitution.newInstance();
    }

    @Test
    public abstract void assertImmutability();

    protected final WithDittoHeaders applyBlocking(final WithDittoHeaders input) {
        final CompletionStage<WithDittoHeaders> responseFuture = substitution.apply(input);
        try {
            return responseFuture.toCompletableFuture().get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }
}
