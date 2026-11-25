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
 * Backend-neutral access to persisted payloads for bulk rewrites: a
 * {@link org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedPayloadStore} streams the snapshots and
 * journal entries of one entity type as JSON, writes rewritten entries back and keeps a JSON progress record. Obtained
 * per entity type from
 * {@link org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendProvider#payloadStore(String)}; each
 * backend ships an implementation in its own module.
 */
package org.eclipse.ditto.internal.utils.persistence.api.payload;
