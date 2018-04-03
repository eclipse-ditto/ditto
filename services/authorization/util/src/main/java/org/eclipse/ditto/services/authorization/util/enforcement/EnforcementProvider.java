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
package org.eclipse.ditto.services.authorization.util.enforcement;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;

/**
 * Provider interface for {@link Enforcement}.
 *
 * @param <T> the type of commands which are enforced.
 */
public interface EnforcementProvider<T extends WithDittoHeaders> {

    /**
     * The base class of the commands to which this enforcement applies.
     *
     * @return the command class.
     */
    Class<T> getCommandClass();

    /**
     * Creates an {@link Enforcement} for the given {@code context}.
     * @param context the context.
     *
     * @return the {@link Enforcement}.
     */
    Enforcement<T> createEnforcement(final Enforcement.Context context);
}
