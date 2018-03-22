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

import java.util.Optional;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommand;

/**
 * Authorize {@code PolicyCommand}.
 */
public final class PolicyCommandEnforcement extends Enforcement {


    public PolicyCommandEnforcement(final Data data) {
        super(data);
    }

    /**
     * Authorize a policy-command by a policy enforcer.
     *
     * @param <T> type of the policy-command.
     * @param enforcer the policy enforcer.
     * @param command the command to authorize.
     * @return optionally the authorized command extended by read subjects.
     */
    public <T extends PolicyCommand> Optional<T> authorizePolicyCommand(final PolicyCommand<T> command,
            final Enforcer enforcer) {

        final ResourceKey policyResourceKey = PoliciesResourceType.policyResource(command.getResourcePath());
        final AuthorizationContext authorizationContext = command.getDittoHeaders().getAuthorizationContext();
        final boolean authorized;
        if (command instanceof PolicyModifyCommand) {
            final String permission = Permission.WRITE;
            authorized = enforcer.hasUnrestrictedPermissions(policyResourceKey, authorizationContext, permission);
        } else {
            final String permission = Permission.READ;
            authorized = enforcer.hasPartialPermissions(policyResourceKey, authorizationContext, permission);
        }
        return authorized
                ? Optional.of(Enforcement.addReadSubjectsToCommand(command, enforcer))
                : Optional.empty();
    }
}
