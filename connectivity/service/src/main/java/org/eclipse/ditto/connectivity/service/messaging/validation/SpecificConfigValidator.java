/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.validation;

import java.util.function.Supplier;

import org.eclipse.ditto.base.model.headers.DittoHeaders;

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
