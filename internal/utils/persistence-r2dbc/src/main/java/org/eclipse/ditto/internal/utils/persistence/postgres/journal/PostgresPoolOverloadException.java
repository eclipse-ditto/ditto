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
package org.eclipse.ditto.internal.utils.persistence.postgres.journal;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import io.r2dbc.spi.R2dbcTransientResourceException;
import reactor.pool.PoolAcquirePendingLimitException;

/**
 * A journal write was shed by the PostgreSQL connection pool because it was overloaded: every pooled connection was in
 * use and the pending-acquire queue was full ({@code pool.pending-acquire-max-size}).
 * <p>
 * It fails the write Future — a lost write that stops the persistent actor, like an acquire timeout — but under a name
 * that says what happened, instead of the pool's {@link PoolAcquirePendingLimitException} (which is not an
 * {@code R2dbcException}) being reported as an unclassified store error. Classified as an
 * {@link R2dbcTransientResourceException}: the store is healthy, it is only out of capacity right now. The original
 * exception is kept as the cause.
 * </p>
 */
public final class PostgresPoolOverloadException extends R2dbcTransientResourceException {

    private static final long serialVersionUID = -3794012567730958427L;

    private PostgresPoolOverloadException(final String reason, final PoolAcquirePendingLimitException cause) {
        super(reason, cause);
    }

    /**
     * Wraps the pool's pending-limit rejection.
     *
     * @param cause the rejection raised by reactor-pool.
     * @return the classified overload exception.
     * @throws NullPointerException if {@code cause} is {@code null}.
     */
    public static PostgresPoolOverloadException of(final PoolAcquirePendingLimitException cause) {
        checkNotNull(cause, "cause");
        return new PostgresPoolOverloadException("PostgreSQL pool overload: all pooled connections are in use and "
                + "the pending-acquire queue is full (pool.pending-acquire-max-size=" + cause.getAcquirePendingLimit()
                + "). The write was failed fast instead of waiting past its caller's deadline.", cause);
    }

}
