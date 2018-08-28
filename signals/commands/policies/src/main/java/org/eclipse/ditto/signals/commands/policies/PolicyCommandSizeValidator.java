/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */

package org.eclipse.ditto.signals.commands.policies;


import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.PolicyTooLargeException;
import org.eclipse.ditto.signals.commands.base.AbstractCommandSizeValidator;

public class PolicyCommandSizeValidator extends AbstractCommandSizeValidator {

    @Nullable private static PolicyCommandSizeValidator instance;

    private PolicyCommandSizeValidator(@Nullable final Long maxSize) {
        super(maxSize);
    }

    @Override
    protected DittoRuntimeException newInvalidSizeException(final long maxSize, final long actualSize,
            final DittoHeaders headers) {
        return PolicyTooLargeException.newBuilder(actualSize, maxSize).dittoHeaders(headers).build();
    }

    public static PolicyCommandSizeValidator getInstance() {
        if (null == instance) {
            long maxSize = Long.parseLong(System.getProperty("ditto.limits.policies.max-size.bytes", "-1"));
            instance = (maxSize > 0) ? new PolicyCommandSizeValidator(maxSize) : new PolicyCommandSizeValidator(null) ;
        }

        return instance;
    }


}
