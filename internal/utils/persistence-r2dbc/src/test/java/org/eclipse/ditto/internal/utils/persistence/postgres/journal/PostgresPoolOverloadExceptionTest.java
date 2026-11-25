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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import io.r2dbc.spi.R2dbcTransientResourceException;
import reactor.pool.PoolAcquirePendingLimitException;

/**
 * Tests that a shed journal write is reported as a transient resource error that names the pool limit and keeps the
 * pool's rejection as its cause.
 */
public final class PostgresPoolOverloadExceptionTest {

    @Test
    public void poolOverloadIsATransientResourceErrorNamingTheLimit() {
        final PoolAcquirePendingLimitException rejection = new PoolAcquirePendingLimitException(8);

        final PostgresPoolOverloadException overload = PostgresPoolOverloadException.of(rejection);

        assertThat(overload).isInstanceOf(R2dbcTransientResourceException.class);
        assertThat(overload).hasCause(rejection);
        assertThat(overload.getMessage()).contains("pool.pending-acquire-max-size=8");
    }

}
