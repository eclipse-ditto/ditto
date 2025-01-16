/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.actors.enrichment;

import org.eclipse.ditto.base.model.signals.Signal;

/**
 * A message to enrich a {@link Signal} with pre-defined extra fields.
 *
 * @param signal the signal to enrich with configured pre-defined extra fields
 */
public record EnrichSignalWithPreDefinedExtraFields(Signal<?> signal) {}
