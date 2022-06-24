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
package org.eclipse.ditto.policies.service.enforcement;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandToExceptionRegistry;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.enforcement.AbstractEnforcementReloaded;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.actions.PolicyActionCommand;
import org.eclipse.ditto.policies.model.signals.commands.actions.TopLevelPolicyActionCommand;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyCommandToAccessExceptionRegistry;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyCommandToActionsExceptionRegistry;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyCommandToModifyExceptionRegistry;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.PolicyModifyCommand;
import org.eclipse.ditto.policies.model.signals.commands.query.PolicyQueryCommandResponse;

/**
 * Authorizes {@link PolicyCommand}s and filters {@link PolicyCommandResponse}s.
 */
public final class PolicyCommandEnforcement
        extends AbstractEnforcementReloaded<PolicyCommand<?>, PolicyCommandResponse<?>> {

    /**
     * Json fields that are always shown regardless of authorization.
     */
    private static final JsonFieldSelector POLICY_QUERY_COMMAND_RESPONSE_ALLOWLIST =
            JsonFactory.newFieldSelector(Policy.JsonFields.ID);

    @Override
    public CompletionStage<PolicyCommand<?>> authorizeSignal(final PolicyCommand<?> command,
            final PolicyEnforcer policyEnforcer) {

        if (command.getCategory() == Command.Category.QUERY && !command.getDittoHeaders().isResponseRequired()) {
            // ignore query command with response-required=false
            return CompletableFuture.completedStage(null);
        }

        final Enforcer enforcer = policyEnforcer.getEnforcer();
        final var policyResourceKey = PoliciesResourceType.policyResource(command.getResourcePath());
        final var authorizationContext = command.getDittoHeaders().getAuthorizationContext();
        final PolicyCommand<?> authorizedCommand;
        if (command instanceof CreatePolicy createPolicy) {
            authorizedCommand = authorizeCreatePolicy(enforcer, createPolicy, policyResourceKey, authorizationContext);
        } else if (command instanceof PolicyActionCommand) {
            authorizedCommand = authorizeActionCommand(policyEnforcer, command, policyResourceKey, authorizationContext)
                    .orElseThrow(() -> errorForPolicyCommand(command));
        } else if (command instanceof PolicyModifyCommand) {
            if (hasUnrestrictedWritePermission(enforcer, policyResourceKey, authorizationContext)) {
                authorizedCommand = command;
            } else {
                throw errorForPolicyCommand(command);
            }
        } else {
            final String permission = Permission.READ;
            if (enforcer.hasPartialPermissions(policyResourceKey, authorizationContext, permission)) {
                authorizedCommand = command;
            } else {
                throw errorForPolicyCommand(command);
            }
        }

        return CompletableFuture.completedStage(authorizedCommand);
    }

    private PolicyCommand<?> authorizeCreatePolicy(final Enforcer enforcer,
            final CreatePolicy createPolicy,
            final ResourceKey policyResourceKey,
            final AuthorizationContext authorizationContext) {

        final PolicyCommand<?> authorizedCommand;
        if (createPolicy.getDittoHeaders().isAllowPolicyLockout()
                || hasUnrestrictedWritePermission(enforcer, policyResourceKey, authorizationContext)) {
            authorizedCommand = createPolicy;
        } else {
            throw errorForPolicyCommand(createPolicy);
        }
        return authorizedCommand;
    }

    @Override
    public CompletionStage<PolicyCommand<?>> authorizeSignalWithMissingEnforcer(final PolicyCommand<?> command) {
        throw PolicyNotAccessibleException.newBuilder(command.getEntityId())
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    @Override
    public boolean shouldFilterCommandResponse(final PolicyCommandResponse<?> commandResponse) {
        return commandResponse instanceof PolicyQueryCommandResponse<?>;
    }

    @Override
    public CompletionStage<PolicyCommandResponse<?>> filterResponse(final PolicyCommandResponse<?> commandResponse,
            final PolicyEnforcer policyEnforcer) {

        if (commandResponse instanceof PolicyQueryCommandResponse<?> policyQueryCommandResponse) {
            try {
                return CompletableFuture.completedStage(
                        buildJsonViewForPolicyQueryCommandResponse(policyQueryCommandResponse,
                                policyEnforcer.getEnforcer())
                );
            } catch (final RuntimeException e) {
                throw reportError("Error after building JsonView", e, commandResponse.getDittoHeaders());
            }
        } else {
            // no filtering required for non PolicyQueryCommandResponses:
            return CompletableFuture.completedStage(commandResponse);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends PolicyCommand<?>> Optional<T> authorizeActionCommand(final PolicyEnforcer enforcer,
            final T command, final ResourceKey resourceKey, final AuthorizationContext authorizationContext) {

        if (command instanceof TopLevelPolicyActionCommand topLevelPolicyActionCommand) {
            return (Optional<T>) authorizeTopLevelAction(enforcer, topLevelPolicyActionCommand, authorizationContext);
        } else {
            return authorizeEntryLevelAction(enforcer.getEnforcer(), command, resourceKey, authorizationContext);
        }
    }

    private static <T extends PolicyCommand<?>> Optional<T> authorizeEntryLevelAction(final Enforcer enforcer,
            final T command, final ResourceKey resourceKey, final AuthorizationContext authorizationContext) {
        return enforcer.hasUnrestrictedPermissions(resourceKey, authorizationContext, Permission.EXECUTE)
                ? Optional.of(command)
                : Optional.empty();
    }

    private static Optional<TopLevelPolicyActionCommand> authorizeTopLevelAction(final PolicyEnforcer policyEnforcer,
            final TopLevelPolicyActionCommand command, final AuthorizationContext authorizationContext) {
        final var enforcer = policyEnforcer.getEnforcer();
        final List<Label> authorizedLabels = policyEnforcer.getPolicy()
                .map(policy -> policy.getEntriesSet().stream()
                        .map(PolicyEntry::getLabel)
                        .filter(label -> enforcer.hasUnrestrictedPermissions(asResourceKey(label, command),
                                authorizationContext, Permission.EXECUTE))
                        .toList())
                .orElse(List.of());
        if (authorizedLabels.isEmpty()) {
            return Optional.empty();
        } else {
            final var adjustedCommand =
                    TopLevelPolicyActionCommand.of(command.getPolicyActionCommand(), authorizedLabels);
            return Optional.of(adjustedCommand);
        }
    }

    private static boolean hasUnrestrictedWritePermission(final Enforcer enforcer, final ResourceKey policyResourceKey,
            final AuthorizationContext authorizationContext) {
        return enforcer.hasUnrestrictedPermissions(policyResourceKey, authorizationContext, Permission.WRITE);
    }

    /**
     * Limit view on entity of {@code PolicyQueryCommandResponse} by enforcer.
     *
     * @param response the response.
     * @param enforcer the enforcer.
     * @return response with view on entity restricted by enforcer.
     */
    public static <T extends PolicyQueryCommandResponse<T>> T buildJsonViewForPolicyQueryCommandResponse(
            final PolicyQueryCommandResponse<T> response,
            final Enforcer enforcer) {

        final JsonValue entity = response.getEntity();
        if (entity.isObject()) {
            final JsonObject filteredView =
                    getJsonViewForPolicyQueryCommandResponse(entity.asObject(), response, enforcer);
            return response.setEntity(filteredView);
        } else {
            return response.setEntity(entity);
        }
    }

    private static JsonObject getJsonViewForPolicyQueryCommandResponse(final JsonObject responseEntity,
            final PolicyQueryCommandResponse<?> response,
            final Enforcer enforcer) {

        final var resourceKey = ResourceKey.newInstance(PolicyCommand.RESOURCE_TYPE, response.getResourcePath());
        final var authorizationContext = response.getDittoHeaders().getAuthorizationContext();

        return enforcer.buildJsonView(resourceKey, responseEntity, authorizationContext,
                POLICY_QUERY_COMMAND_RESPONSE_ALLOWLIST, Permissions.newInstance(Permission.READ));
    }

    /**
     * Create error due to failing to execute a policy-command in the expected way.
     *
     * @param policyCommand the command.
     * @return the error.
     */
    private static DittoRuntimeException errorForPolicyCommand(final PolicyCommand<?> policyCommand) {
        final CommandToExceptionRegistry<PolicyCommand<?>, DittoRuntimeException> registry;
        if (policyCommand instanceof PolicyActionCommand) {
            registry = PolicyCommandToActionsExceptionRegistry.getInstance();
        } else if (policyCommand instanceof PolicyModifyCommand) {
            registry = PolicyCommandToModifyExceptionRegistry.getInstance();
        } else {
            registry = PolicyCommandToAccessExceptionRegistry.getInstance();
        }
        return registry.exceptionFrom(policyCommand);
    }

    /**
     * Convert a policy entry label for a top-level policy action into a resource path for authorization check.
     *
     * @param label the policy entry label.
     * @param command the top-level policy action command.
     * @return the resource key.
     */
    private static ResourceKey asResourceKey(final Label label, final PolicyCommand<?> command) {
        return ResourceKey.newInstance(PoliciesResourceType.POLICY,
                Policy.JsonFields.ENTRIES.getPointer().addLeaf(JsonKey.of(label)).append(command.getResourcePath()));
    }

}
