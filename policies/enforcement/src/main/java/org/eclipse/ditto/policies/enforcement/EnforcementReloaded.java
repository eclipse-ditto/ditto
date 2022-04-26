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
package org.eclipse.ditto.policies.enforcement;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;

/**
 * TODO TJ doc
 *
 * @param <S>
 * @param <R>
 */
public interface EnforcementReloaded<S extends Signal<?>, R extends CommandResponse<?>> {

    /**
     *
     * @param signal
     * @param enforcer
     * @return
     * @throws org.eclipse.ditto.base.model.exceptions.DittoRuntimeException if the passed in {@code signal} could not
     * be authorized.
     */
    S authorizeSignal(S signal, PolicyEnforcer enforcer);

    /**
     *
     * @param signal
     * @return
     * @throws org.eclipse.ditto.base.model.exceptions.DittoRuntimeException if the passed in {@code signal} could not
     * be authorized.
     */
    S authorizeSignalWithMissingEnforcer(S signal);

    /**
     * Filters the given {@code commandResponse} by using the given {@code enforcer}.
     *
     * @param commandResponse the command response that needs  to be filtered.
     * @param enforcer the enforcer that should be used for filtering.
     * @return the filtered command response.
     */
    R filterResponse(R commandResponse, PolicyEnforcer enforcer);

}
