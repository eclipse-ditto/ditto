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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoHeadersTooLargeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;

import akka.actor.ActorSystem;
import com.typesafe.config.Config;

/**
 * Provider for a {@link DittoHeadersValidator}.
 */
@Immutable
public final class DefaultDittoHeadersValidator implements DittoHeadersValidator {

    private static final String MAX_BYTES = "max-bytes";
    private static final String MAX_AUTH_SUBJECTS = "max-auth-subjects";

    private final int maxBytes;
    private final int maxAuthSubjects;

    @SuppressWarnings("unused")
    public DefaultDittoHeadersValidator(final ActorSystem actorSystem, final Config config) {
        maxBytes = getIntOrMaxValue(config, MAX_BYTES);
        maxAuthSubjects = getIntOrMaxValue(config, MAX_AUTH_SUBJECTS);
    }

    private static int getIntOrMaxValue(final Config config, final String configKey) {
        return config.hasPath(configKey) ? config.getInt(configKey) : Integer.MAX_VALUE;
    }

    @Override
    public void validate(DittoHeaders dittoHeaders) {
        checkNotNull(dittoHeaders, "dittoHeaders");
        validateAuthorizationContext(dittoHeaders);
        if (dittoHeaders.isEntriesSizeGreaterThan(maxBytes)) {
            throw DittoHeadersTooLargeException.newSizeLimitBuilder(maxBytes)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    @Override
    public DittoHeaders truncate(DittoHeaders dittoHeaders) {
        return dittoHeaders.truncate(maxBytes);
    }

    private void validateAuthorizationContext(final DittoHeaders dittoHeaders) {
        final int authSubjectsCount = dittoHeaders.getAuthorizationContext().getSize();
        if (authSubjectsCount > maxAuthSubjects) {
            throw DittoHeadersTooLargeException.newAuthSubjectsLimitBuilder(authSubjectsCount, maxAuthSubjects)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

}
