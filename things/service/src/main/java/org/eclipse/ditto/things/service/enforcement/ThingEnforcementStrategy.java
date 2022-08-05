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
package org.eclipse.ditto.things.service.enforcement;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.policies.enforcement.EnforcementReloaded;

/**
 * Package internal strategy of different {@link EnforcementReloaded} implementations, e.g. enforcing
 * <ul>
 * <li>Thing commands on "twin" channel</li>
 * <li>Live messages / live commands</li>
 * </ul>
 */
interface ThingEnforcementStrategy {

    /**
     * Checks whether the passed {@code signal} can be handled by this strategy.
     *
     * @param signal the Signal to check whether it is applicable via this strategy.
     * @return {@code true} when this strategy can handle the passed signal.
     */
    boolean isApplicable(Signal<?> signal);

    /**
     * Checks whether the passed {@code commandResponse} can be handled by this strategy.
     *
     * @param commandResponse the CommandResponse to check whether it is applicable via this strategy.
     * @return {@code true} when this strategy can handle the passed command response.
     */
    boolean responseIsApplicable(CommandResponse<?> commandResponse);

    /**
     * Returns the {@link EnforcementReloaded} to use for this strategy.
     *
     * @return the enforcement to use for this strategy.
     * @param <S> the type of the signal of the EnforcementReloaded
     * @param <R> the type of the command response of the EnforcementReloaded
     */
    <S extends Signal<?>, R extends CommandResponse<?>> EnforcementReloaded<S, R> getEnforcement();
}
