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
 * The MongoDB implementation of the backend-neutral
 * {@link org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedPayloadStore}: streams and rewrites the
 * snapshot and journal documents the {@code pekko-persistence-mongodb} plugin writes, rendering their payloads as JSON,
 * and keeps the progress record of a bulk rewrite. Obtained from
 * {@link org.eclipse.ditto.internal.utils.persistence.mongo.MongoPersistenceBackendProvider#payloadStore(String)}.
 */
package org.eclipse.ditto.internal.utils.persistence.mongo.payload;
