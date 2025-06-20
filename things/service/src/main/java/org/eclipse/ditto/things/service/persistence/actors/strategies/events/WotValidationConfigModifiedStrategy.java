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
package org.eclipse.ditto.things.service.persistence.actors.strategies.events;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.persistentactors.events.EventStrategy;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigModified;

/**
 * This strategy handles {@link WotValidationConfigModified} events.
 *
 * @since 3.8.0
 */
final class WotValidationConfigModifiedStrategy
        implements EventStrategy<WotValidationConfigModified, WotValidationConfig> {

    @Override
    public WotValidationConfig handle(final WotValidationConfigModified event,
            @Nullable final WotValidationConfig entity,
            final long revision) {
        return event.getConfig();
    }
} 