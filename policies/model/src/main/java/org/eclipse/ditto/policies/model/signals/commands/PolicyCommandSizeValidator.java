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
package org.eclipse.ditto.policies.model.signals.commands;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.DittoSystemProperties;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandSizeValidator;
import org.eclipse.ditto.policies.model.PolicyTooLargeException;

/**
 * Command size validator for policy commands
 */
public final class PolicyCommandSizeValidator extends AbstractCommandSizeValidator<PolicyTooLargeException> {


    @Nullable private static PolicyCommandSizeValidator instance;

    private PolicyCommandSizeValidator(@Nullable final Long maxSize) {
        super(maxSize);
    }

    @Override
    protected PolicyTooLargeException newInvalidSizeException(final long maxSize, final long actualSize,
            final DittoHeaders headers) {
        return PolicyTooLargeException.newBuilder(actualSize, maxSize).dittoHeaders(headers).build();
    }

    /**
     * The singleton instance for this command size validator. Will be initialized at first call.
     *
     * @return Singleton instance
     */
    public static PolicyCommandSizeValidator getInstance() {
        if (null == instance) {
            final long maxSize =
                    Long.parseLong(System.getProperty(DittoSystemProperties.DITTO_LIMITS_POLICIES_MAX_SIZE_BYTES, DEFAULT_LIMIT));
            instance = (maxSize > 0) ? new PolicyCommandSizeValidator(maxSize) : new PolicyCommandSizeValidator(null);
        }
        return instance;
    }

}
