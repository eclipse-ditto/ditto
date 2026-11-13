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

/**
 * The payload codecs of the PostgreSQL journal and snapshot-store plugins
 * ({@link org.eclipse.ditto.internal.utils.persistence.postgres.payload.PostgresPayloadCodec} and its two
 * implementations) and the PostgreSQL
 * {@link org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedPayloadStore}
 * ({@link org.eclipse.ditto.internal.utils.persistence.postgres.payload.PostgresPersistedPayloadStore}), which streams
 * and rewrites the stored JSONB payloads of one entity type for a bulk rewrite such as connectivity's connection-secret
 * encryption migration.
 */
package org.eclipse.ditto.internal.utils.persistence.postgres.payload;
