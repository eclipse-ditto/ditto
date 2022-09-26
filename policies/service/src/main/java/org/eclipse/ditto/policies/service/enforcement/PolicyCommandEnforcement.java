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
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.Signal;
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
import org.eclipse.ditto.policies.model.PolicyId;
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
        extends AbstractEnforcementReloaded<Signal<?>, PolicyCommandResponse<?>> {

    /**
     * Json fields that are always shown regardless of authorization.
     */
    private static final JsonFieldSelector POLICY_QUERY_COMMAND_RESPONSE_ALLOWLIST =
            JsonFactory.newFieldSelector(Policy.JsonFields.ID);

    @Override
    public CompletionStage<Signal<?>> authorizeSignal(final Signal<?> signal,
            final PolicyEnforcer policyEnforcer) {

        if (signal instanceof Command<?> command &&
                command.getCategory() == Command.Category.QUERY && !command.getDittoHeaders().isResponseRequired()) {
            // ignore query command with response-required=false
            return CompletableFuture.completedStage(null);
        }

        final Enforcer enforcer = policyEnforcer.getEnforcer();
        final var policyResourceKey = PoliciesResourceType.policyResource(signal.getResourcePath());
        final var authorizationContext = signal.getDittoHeaders().getAuthorizationContext();
        final Signal<?> authorizedCommand;
        if (signal instanceof CreatePolicy createPolicy) {
            authorizedCommand = authorizeCreatePolicy(enforcer, createPolicy, policyResourceKey, authorizationContext);
        } else if (signal instanceof PolicyActionCommand) {
            authorizedCommand = authorizeActionCommand(policyEnforcer, signal, policyResourceKey,
                    authorizationContext).orElseThrow(() -> errorForPolicyCommand(signal));
        } else if (signal instanceof PolicyModifyCommand) {
            if (hasUnrestrictedWritePermission(enforcer, policyResourceKey, authorizationContext)) {
                authorizedCommand = signal;
            } else {
                throw errorForPolicyCommand(signal);
            }
        } else {
            final String permission = Permission.READ;
            if (enforcer.hasPartialPermissions(policyResourceKey,
                    authorizationContext,
                    permission)) {
                authorizedCommand = signal;
            } else {
                throw errorForPolicyCommand(signal);
            }
        }

        return CompletableFuture.completedStage(authorizedCommand);
    }

    private PolicyCommand<?> authorizeCreatePolicy(final Enforcer enforcer,
            final CreatePolicy createPolicy,
            final ResourceKey policyResourceKey,
            final AuthorizationContext authorizationContext) {

        if (hasUnrestrictedWritePermission(enforcer, policyResourceKey, authorizationContext) ||
                createPolicy.getDittoHeaders().isAllowPolicyLockout()) {
            return createPolicy;
        } else {
            throw errorForPolicyCommand(createPolicy);
        }
    }

    @Override
    public CompletionStage<Signal<?>> authorizeSignalWithMissingEnforcer(final Signal<?> command) {
        final PolicyId policyId;
        if (command instanceof WithEntityId withEntityId) {
            policyId = PolicyId.of(withEntityId.getEntityId());
        } else {
            LOGGER.warn("Processed signal which does not have an entityId: {}", command);
            throw DittoInternalErrorException.newBuilder()
                    .dittoHeaders(command.getDittoHeaders())
                    .build();
        }
        throw PolicyNotAccessibleException.newBuilder(policyId)
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

        final PolicyCommandResponse<?> result;
        if (commandResponse instanceof PolicyQueryCommandResponse<?> policyQueryCommandResponse) {
            try {
                result = buildJsonViewForPolicyQueryCommandResponse(policyQueryCommandResponse,
                        policyEnforcer.getEnforcer());
            } catch (final RuntimeException e) {
                throw reportError("Error after building JsonView", e, commandResponse.getDittoHeaders());
            }
        } else {
            // no filtering required for non PolicyQueryCommandResponses:
            result = commandResponse;
        }
        return CompletableFuture.completedStage(result);
    }

    @SuppressWarnings("unchecked")
    private <T extends Signal<?>> Optional<T> authorizeActionCommand(
            final PolicyEnforcer enforcer,
            final T command, final ResourceKey resourceKey, final AuthorizationContext authorizationContext) {

        if (command instanceof TopLevelPolicyActionCommand topLevelPolicyActionCommand) {
            return (Optional<T>) authorizeTopLevelAction(enforcer, topLevelPolicyActionCommand, authorizationContext);
        } else {
            return authorizeEntryLevelAction(enforcer.getEnforcer(), command, resourceKey, authorizationContext);
        }
    }

    private <T extends Signal<?>> Optional<T> authorizeEntryLevelAction(final Enforcer enforcer,
            final T command, final ResourceKey resourceKey, final AuthorizationContext authorizationContext) {
        return enforcer.hasUnrestrictedPermissions(resourceKey, authorizationContext, Permission.EXECUTE) ?
                Optional.of(command) : Optional.empty();
    }

    private Optional<TopLevelPolicyActionCommand> authorizeTopLevelAction(
            final PolicyEnforcer policyEnforcer,
            final TopLevelPolicyActionCommand command,
            final AuthorizationContext authorizationContext) {

        final var enforcer = policyEnforcer.getEnforcer();

        final List<Label> labels = getLabelsFromPolicyEnforcer(policyEnforcer);
        final var authorizedLabels = labels.stream()
                .filter(label -> enforcer.hasUnrestrictedPermissions(asResourceKey(label, command),
                        authorizationContext, Permission.EXECUTE))
                .toList();

        if (authorizedLabels.isEmpty()) {
            return Optional.empty();
        } else {
            final var adjustedCommand =
                    TopLevelPolicyActionCommand.of(command.getPolicyActionCommand(), authorizedLabels);
            return Optional.of(adjustedCommand);
        }
    }

    private static List<Label> getLabelsFromPolicyEnforcer(final PolicyEnforcer policyEnforcer) {
        return policyEnforcer.getPolicy()
                .map(policy -> policy.getEntriesSet().stream()
                        .map(PolicyEntry::getLabel)
                        .toList()).orElse(List.of());
    }

    private boolean hasUnrestrictedWritePermission(final Enforcer enforcer,
            final ResourceKey policyResourceKey,
            final AuthorizationContext authorizationContext) {
        return enforcer.hasUnrestrictedPermissions(policyResourceKey, authorizationContext, Permission.WRITE);
    }

    /**
     * Limit view on entity of {@code PolicyQueryCommandResponse} by enforcer.
     *
     * @param response the response.
     * @param enforcer the enforcer.
     * @return a {@code CompletionStage} containing the response with view on entity restricted by enforcer.
     */
    public <T extends PolicyQueryCommandResponse<T>> T buildJsonViewForPolicyQueryCommandResponse(
            final PolicyQueryCommandResponse<T> response,
            final Enforcer enforcer) {

        final JsonValue entity = response.getEntity();
        final T result;
        if (entity.isObject()) {
            final JsonObject filteredView =
                    getJsonViewForPolicyQueryCommandResponse(entity.asObject(), response, enforcer);
            result = response.setEntity(filteredView);
        } else {
            result = response.setEntity(entity);
        }
        return result;
    }

    private JsonObject getJsonViewForPolicyQueryCommandResponse(final JsonObject responseEntity,
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
     * @param policySignal the signal.
     * @return the error.
     */
    private static DittoRuntimeException errorForPolicyCommand(final Signal<?> policySignal) {

        if (policySignal instanceof PolicyCommand<?> policyCommand) {
            final CommandToExceptionRegistry<PolicyCommand<?>, DittoRuntimeException> registry;
            if (policyCommand instanceof PolicyActionCommand) {
                registry = PolicyCommandToActionsExceptionRegistry.getInstance();
            } else if (policyCommand instanceof PolicyModifyCommand) {
                registry = PolicyCommandToModifyExceptionRegistry.getInstance();
            } else {
                registry = PolicyCommandToAccessExceptionRegistry.getInstance();
            }
            return registry.exceptionFrom(policyCommand);
        } else if (policySignal instanceof WithEntityId withEntityId) {
            return PolicyNotAccessibleException.newBuilder(PolicyId.of(withEntityId.getEntityId()))
                    .dittoHeaders(policySignal.getDittoHeaders())
                    .build();
        } else {
            LOGGER.error("Received signal for which no DittoRuntimeException due to lack of access " +
                    "could be determined: {}", policySignal);
            return DittoInternalErrorException.newBuilder()
                    .dittoHeaders(policySignal.getDittoHeaders())
                    .build();
        }
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
