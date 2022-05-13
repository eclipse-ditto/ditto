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
 * TODO TJ add javadoc
 */
interface ThingEnforcementStrategy {

    boolean isApplicable(Signal<?> signal);

    boolean responseIsApplicable(CommandResponse<?> signal);

    <S extends Signal<?>, R extends CommandResponse<?>> EnforcementReloaded<S, R> getEnforcement();
}
