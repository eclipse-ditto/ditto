/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.common.model;

import java.time.Instant;
import java.util.Optional;

import org.eclipse.ditto.things.model.ThingId;

/**
 * Thing ID with an optional timestamp.
 */
public record TimestampedThingId(ThingId thingId, Optional<Instant> lastModified) {}
