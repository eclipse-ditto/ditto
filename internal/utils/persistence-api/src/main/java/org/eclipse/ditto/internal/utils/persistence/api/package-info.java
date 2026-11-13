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
 * Backend-agnostic persistence SPI. Defines the contract that any event-sourcing backend
 * (MongoDB, PostgreSQL, ...) must satisfy to be plugged into Ditto's persistent actors.
 * <p>
 * Concrete backends live in sibling modules (e.g. {@code ditto-internal-utils-persistence}
 * for MongoDB, {@code ditto-internal-utils-persistence-postgres} for PostgreSQL) and are
 * selected at runtime via the {@link org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendProvider}
 * Pekko extension.
 */
package org.eclipse.ditto.internal.utils.persistence.api;
