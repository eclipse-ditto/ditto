/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model.signals.commands.exceptions;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandToExceptionRegistry;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.actions.ActivateTokenIntegration;
import org.eclipse.ditto.policies.model.signals.commands.actions.DeactivateTokenIntegration;
import org.eclipse.ditto.policies.model.signals.commands.actions.TopLevelPolicyActionCommand;

/**
 * Registry to map policy commands to their according policy actions exception.
 */
public final class PolicyCommandToActionsExceptionRegistry
        extends AbstractCommandToExceptionRegistry<PolicyCommand<?>, DittoRuntimeException> {

    private static final PolicyCommandToActionsExceptionRegistry INSTANCE = createInstance();

    private PolicyCommandToActionsExceptionRegistry(
            final Map<String, Function<PolicyCommand<?>, DittoRuntimeException>> mappingStrategies) {
        super(mappingStrategies);
    }

    /**
     * Returns an instance of {@code PolicyCommandToModifyExceptionRegistry}.
     *
     * @return the instance.
     */
    public static PolicyCommandToActionsExceptionRegistry getInstance() {
        return INSTANCE;
    }

    private static PolicyCommandToActionsExceptionRegistry createInstance() {
        final Map<String, Function<PolicyCommand<?>, DittoRuntimeException>> mappingStrategies = new HashMap<>();

        final String insufficientPermissions = "The requester has insufficient permissions. 'EXECUTE' is required.";
        mappingStrategies.put(TopLevelPolicyActionCommand.TYPE,
                command -> PolicyActionFailedException.newBuilder()
                        .action(((TopLevelPolicyActionCommand) command).getPolicyActionCommand().getName())
                        .status(HttpStatus.FORBIDDEN)
                        .description(insufficientPermissions)
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(ActivateTokenIntegration.TYPE,
                command -> PolicyActionFailedException.newBuilderForActivateTokenIntegration()
                        .status(HttpStatus.FORBIDDEN)
                        .description(insufficientPermissions)
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(DeactivateTokenIntegration.TYPE,
                command -> PolicyActionFailedException.newBuilderForDeactivateTokenIntegration()
                        .status(HttpStatus.FORBIDDEN)
                        .description(insufficientPermissions)
                        .dittoHeaders(command.getDittoHeaders())
                        .build());

        return new PolicyCommandToActionsExceptionRegistry(mappingStrategies);
    }

}
