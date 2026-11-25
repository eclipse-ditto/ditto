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

import java.util.List;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

/**
 * Configuration of the operator-configured per-path {@code search_flat} indexes, under the HOCON namespace
 * {@code ditto.postgresql.search.path-indexes.*}.
 */
@Immutable
public interface PostgresSearchPathIndexConfig {

    /**
     * Whether the path list is configured at all. Unset (the default, also a blank environment variable) means the
     * per-path indexes are not managed: none is built or dropped. An explicitly configured list is reconciled; an
     * explicitly empty list therefore drops every managed per-path index.
     *
     * @return {@code true} if {@code paths} is set.
     */
    boolean isPathListConfigured();

    /**
     * @return the configured indexes, in configuration order, validated and without duplicates; empty if the list is
     * empty or not configured.
     */
    List<PathIndex> getPathIndexes();

    /**
     * The r2dbc URI of a direct (not transaction-pooled) connection to the search database, used only to maintain the
     * path indexes. Empty means the runtime {@code ditto.postgresql.uri}.
     *
     * @return the direct URI, if configured.
     */
    Optional<String> getDirectUri();

}
