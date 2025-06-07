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
package org.eclipse.ditto.things.model.devops;

import org.eclipse.ditto.base.model.entity.Revision;

/**
 * Public API for the revision of a WoT validation configuration.
 * <p>
 * Represents a version or revision number for a WoT validation config.
 * </p>
 *
 * @since 3.8.0
 */
public interface WotValidationConfigRevision extends Revision<WotValidationConfigRevision> {

    /**
     * Creates a {@link WotValidationConfigRevision} with the given revision value.
     *
     * @param value the revision number.
     * @return a new {@code WotValidationConfigRevision} instance representing the given value.
     */
    static WotValidationConfigRevision of(long value) {
        return new ImmutableWotValidationConfigRevision(value);
    }
} 