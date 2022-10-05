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
package org.eclipse.ditto.edge.service.headers;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoHeadersTooLargeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Default implementation for {@link DittoHeadersValidator}.
 */
@Immutable
public final class DefaultDittoHeadersValidator implements DittoHeadersValidator {

    private static final String MAX_BYTES = "max-bytes";
    private static final String MAX_AUTH_SUBJECTS = "max-auth-subjects";

    private final long maxBytes;
    private final int maxAuthSubjects;

    @SuppressWarnings("unused")
    public DefaultDittoHeadersValidator(final ActorSystem actorSystem, final Config config) {
        maxBytes = config.getBytes(MAX_BYTES);
        maxAuthSubjects = config.getInt(MAX_AUTH_SUBJECTS);
    }

    @Override
    public CompletionStage<DittoHeaders> validate(DittoHeaders dittoHeaders) {
        checkNotNull(dittoHeaders, "dittoHeaders");
        return validateSize(dittoHeaders).thenCompose(this::validateAuthorizationContext);
    }

    /**
     * Validates {@code dittoHeaders} against {@code maximum-size} defined in the extension configuration.
     *
     * @param dittoHeaders the headers to validate.
     * @return a completion stage which completes successfully with the valid headers. Raises a
     * {@link org.eclipse.ditto.base.model.exceptions.DittoHeadersTooLargeException} if {@code dittoHeaders} are not
     * valid.
     */
    public CompletionStage<DittoHeaders> validateSize(final DittoHeaders dittoHeaders) {
        if (dittoHeaders.isEntriesSizeGreaterThan(maxBytes)) {
            return CompletableFuture.failedStage(DittoHeadersTooLargeException.newSizeLimitBuilder(maxBytes)
                    .dittoHeaders(dittoHeaders)
                    .build());
        }
        return CompletableFuture.completedStage(dittoHeaders);
    }

    private CompletionStage<DittoHeaders> validateAuthorizationContext(final DittoHeaders dittoHeaders) {
        final int authSubjectsCount = dittoHeaders.getAuthorizationContext().getSize();
        if (authSubjectsCount > maxAuthSubjects) {
            return CompletableFuture.failedStage(
                    DittoHeadersTooLargeException.newAuthSubjectsLimitBuilder(authSubjectsCount, maxAuthSubjects)
                            .dittoHeaders(dittoHeaders)
                            .build());
        }
        return CompletableFuture.completedStage(dittoHeaders);
    }

    @Override
    public DittoHeaders truncate(DittoHeaders dittoHeaders) {
        return dittoHeaders.truncate(maxBytes);
    }

}
