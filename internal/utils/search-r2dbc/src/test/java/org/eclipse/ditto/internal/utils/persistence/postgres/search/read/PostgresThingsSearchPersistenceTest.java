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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Unit tests for {@link PostgresThingsSearchPersistence#toRowLimit(int)} — the {@code findAllUnlimited} row-limit
 * sentinel normalization. Pure/static, no database: guards the LIMIT-0 unlimited-sentinel fix directly at the
 * choke point (a parser-produced {@code StreamThings} query has {@code Query.getLimit() == 0}, the Mongo-transcribed
 * {@code cursor.limit(0)} "no limit" default, which SQL would otherwise render as a literal zero-row {@code LIMIT 0}).
 */
public final class PostgresThingsSearchPersistenceTest {

    @Test
    public void maxValueSentinelIsUnbounded() {
        assertThat(PostgresThingsSearchPersistence.toRowLimit(Integer.MAX_VALUE)).isNull();
    }

    @Test
    public void zeroSentinelIsUnbounded() {
        assertThat(PostgresThingsSearchPersistence.toRowLimit(0)).isNull();
    }

    @Test
    public void negativeIsUnbounded() {
        assertThat(PostgresThingsSearchPersistence.toRowLimit(-1)).isNull();
    }

    @Test
    public void positiveLimitIsBoxedAsIs() {
        assertThat(PostgresThingsSearchPersistence.toRowLimit(42)).isEqualTo(42L);
    }
}
