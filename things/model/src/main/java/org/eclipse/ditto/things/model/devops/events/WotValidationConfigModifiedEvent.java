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
package org.eclipse.ditto.things.model.devops.events;

import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;

import java.util.Optional;

/**
 * Interface for WoT validation config events that have an entity (created and modified events).
 *
 * @param <T> the type of the implementing class.
 * @since 3.8.0
 */
public interface WotValidationConfigModifiedEvent<T extends WotValidationConfigModifiedEvent<T>> 
        extends WotValidationConfigEvent<T>, WithOptionalEntity<T> {

    /**
     * Returns the WoT validation config of this event.
     *
     * @return the WoT validation config.
     */
    WotValidationConfig getConfig();

    @Override
    Optional<JsonValue> getEntity(JsonSchemaVersion schemaVersion);

    @Override
    T setEntity(JsonValue entity);
} 