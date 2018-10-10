/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.services.connectivity.messaging.validation;

import java.util.function.Supplier;

import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Validator for a specific config value.
 */
@FunctionalInterface
public interface SpecificConfigValidator {

    /**
     * Validate a specific config value.
     *
     * @param specificConfigValue value of a specific config.
     * @param dittoHeaders headers of the command that triggered the validation.
     * @param errorSiteDescription description of the error site.
     */
    void validate(final String specificConfigValue,
            final DittoHeaders dittoHeaders,
            final Supplier<String> errorSiteDescription);
}
