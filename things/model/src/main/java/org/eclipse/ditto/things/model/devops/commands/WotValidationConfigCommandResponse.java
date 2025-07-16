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
package org.eclipse.ditto.things.model.devops.commands;

import org.eclipse.ditto.base.model.signals.commands.CommandResponse;

/**
 * Base interface for all WoT validation config command responses.
 *
 * @param <T> the type of the implementing class.
 * @since 3.8.0
 */
public interface WotValidationConfigCommandResponse<T extends WotValidationConfigCommandResponse<T>>
        extends CommandResponse<T> {

    /**
     * Type prefix of WoT validation config command responses.
     */
    String TYPE_PREFIX = "wot.validation.config." + TYPE_QUALIFIER + ":";

    /**
     * Resource type of WoT validation config command responses.
     */
    String RESOURCE_TYPE = "wot-validation-config";
} 