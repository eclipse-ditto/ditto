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
package org.eclipse.ditto.services.authorization.util.actors;

import java.util.Optional;

import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;

/**
 * Mixin to authorize {@code PolicyCommand}.
 */
interface PolicyCommandEnforcement extends Enforcement {

    default <T extends PolicyCommand> Optional<T> authorizePolicyCommand(final PolicyCommand<T> command,
            final Enforcer enforcer) {

        // TODO
        return Optional.empty();
    }
}
