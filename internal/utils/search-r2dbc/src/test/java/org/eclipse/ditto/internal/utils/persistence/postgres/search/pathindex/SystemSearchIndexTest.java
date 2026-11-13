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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Unit tests for {@link SystemSearchIndex}: the statements the maintenance and the offline loaders run, and the name
 * lookup that keeps system indexes apart from the per-path ones.
 */
public final class SystemSearchIndexTest {

    private static final SystemSearchIndex MULTI = SystemSearchIndex.MULTI_VALUED;

    @Test
    public void theMultiValuedIndexStatementsArePinned() {
        assertThat(MULTI.indexName()).isEqualTo("sf_multi");
        assertThat(MULTI.createConcurrentlyStatement())
                .isEqualTo("CREATE INDEX CONCURRENTLY IF NOT EXISTS sf_multi ON search_flat (wpath, thing_id) "
                        + "WHERE ord = 1");
        assertThat(MULTI.createStatement())
                .isEqualTo("CREATE INDEX IF NOT EXISTS sf_multi ON search_flat (wpath, thing_id) WHERE ord = 1");
        assertThat(MULTI.dropConcurrentlyStatement()).isEqualTo("DROP INDEX CONCURRENTLY IF EXISTS sf_multi");
    }

    @Test
    public void byIndexNameFindsOnlySystemIndexes() {
        assertThat(SystemSearchIndex.byIndexName("sf_multi")).contains(MULTI);
        assertThat(SystemSearchIndex.byIndexName("sf_path_x")).isEmpty();
        assertThat(SystemSearchIndex.byIndexName("sf_exists")).isEmpty();
    }

    @Test
    public void aSystemIndexIsNotAManagedPerPathIndexName() {
        assertThat(PathIndex.isManagedName(MULTI.indexName()))
                .as("per-path removal (sf_path_*) must never select a system index")
                .isFalse();
    }

}
