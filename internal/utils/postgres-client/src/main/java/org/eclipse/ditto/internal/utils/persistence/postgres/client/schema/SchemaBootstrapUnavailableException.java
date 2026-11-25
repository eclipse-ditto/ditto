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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.schema;

import java.time.Duration;

/**
 * Thrown when PostgreSQL stayed unreachable for the whole retry budget of the schema bootstrap
 * ({@code ditto.postgresql.bootstrap.retry.max-duration}). The cause is the last attempt's failure.
 */
public final class SchemaBootstrapUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    SchemaBootstrapUnavailableException(final String component, final int attempts, final Duration elapsed,
            final Duration maxDuration, final String lastReason, final Throwable lastFailure) {
        super("PostgreSQL schema bootstrap of component '" + component + "' gave up: PostgreSQL was not available "
                + "after " + attempts + " attempts in " + SchemaBootstrapRetry.seconds(elapsed)
                + " (ditto.postgresql.bootstrap.retry.max-duration = " + SchemaBootstrapRetry.seconds(maxDuration)
                + ", POSTGRES_BOOTSTRAP_RETRY_MAX_DURATION). Last failure: " + lastReason, lastFailure);
    }

}
