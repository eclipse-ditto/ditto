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

import java.util.ArrayList;
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
        final CompletionStage<PolicyCommand<?>> authorizedCommand;
        if (command instanceof CreatePolicy createPolicy) {
            authorizedCommand = authorizeCreatePolicy(enforcer, createPolicy, policyResourceKey, authorizationContext);
        } else if (command instanceof PolicyActionCommand) {
            authorizedCommand = authorizeActionCommand(policyEnforcer, command, policyResourceKey,
                    authorizationContext).thenApply(
                    optional -> optional.orElseThrow(() -> errorForPolicyCommand(command)));
        } else if (command instanceof PolicyModifyCommand) {
            authorizedCommand =
                    hasUnrestrictedWritePermission(enforcer, policyResourceKey, authorizationContext).thenApply(
                            hasPermission -> {
                                if (Boolean.TRUE.equals(hasPermission)) {
                                    return command;
                                } else {
                                    throw errorForPolicyCommand(command);
                                }
                            });
        } else {
            final String permission = Permission.READ;
            authorizedCommand = CompletableFuture.supplyAsync(() -> enforcer.hasPartialPermissions(policyResourceKey,
                    authorizationContext,
                    permission)).thenApply(hasPermission -> {
                if (Boolean.TRUE.equals(hasPermission)) {
                    return command;
                } else {
                    throw errorForPolicyCommand(command);
                }
            });
        }

        return authorizedCommand;
    }

    private CompletionStage<PolicyCommand<?>> authorizeCreatePolicy(final Enforcer enforcer,
            final CreatePolicy createPolicy,
            final ResourceKey policyResourceKey,
            final AuthorizationContext authorizationContext) {

        return hasUnrestrictedWritePermission(enforcer, policyResourceKey, authorizationContext).thenApply(hasPermission -> {
            if (Boolean.TRUE.equals(hasPermission) || createPolicy.getDittoHeaders().isAllowPolicyLockout()) {
                return createPolicy;
            } else {
                throw errorForPolicyCommand(createPolicy);
            }
        });

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

        final CompletionStage<PolicyCommandResponse<?>> result;
        if (commandResponse instanceof PolicyQueryCommandResponse<?> policyQueryCommandResponse) {
            try {
                result = buildJsonViewForPolicyQueryCommandResponse(policyQueryCommandResponse,
                        policyEnforcer.getEnforcer()).thenApply(cr -> cr);
            } catch (final RuntimeException e) {
                throw reportError("Error after building JsonView", e, commandResponse.getDittoHeaders());
            }
        } else {
            // no filtering required for non PolicyQueryCommandResponses:
            result = CompletableFuture.completedStage(commandResponse);
        }
        return result;
    }

    private <T extends PolicyCommand<?>> CompletionStage<Optional<T>> authorizeActionCommand(
            final PolicyEnforcer enforcer,
            final T command, final ResourceKey resourceKey, final AuthorizationContext authorizationContext) {

        if (command instanceof TopLevelPolicyActionCommand topLevelPolicyActionCommand) {
            return authorizeTopLevelAction(enforcer, topLevelPolicyActionCommand,
                    authorizationContext);
        } else {
            return authorizeEntryLevelAction(enforcer.getEnforcer(), command, resourceKey, authorizationContext);
        }
    }

    private <T extends PolicyCommand<?>> CompletionStage<Optional<T>> authorizeEntryLevelAction(final Enforcer enforcer,
            final T command, final ResourceKey resourceKey, final AuthorizationContext authorizationContext) {
        return CompletableFuture.supplyAsync(() -> enforcer.hasUnrestrictedPermissions(resourceKey,
                authorizationContext,
                Permission.EXECUTE)
                ? Optional.of(command)
                : Optional.empty());
    }

    private <T extends PolicyCommand<?>> CompletionStage<Optional<T>> authorizeTopLevelAction(
            final PolicyEnforcer policyEnforcer,
            final TopLevelPolicyActionCommand command, final AuthorizationContext authorizationContext) {

        final var enforcer = policyEnforcer.getEnforcer();

        final List<CompletionStage<Label>> labels = getLabelsFromPolicyEnforcer(policyEnforcer);
        final var enforcedLabels = enforcePolicyLabels(labels, enforcer, command, authorizationContext);
        final var authorizedLabels = filterAuthorizedLabels(enforcedLabels);

        return authorizedLabels.thenApply(labelList -> {
            if (labelList.isEmpty()) {
                return Optional.empty();
            } else {
                final var adjustedCommand =
                        TopLevelPolicyActionCommand.of(command.getPolicyActionCommand(), labelList);
                return (Optional<T>) Optional.of(adjustedCommand);
            }
        });
    }

    private static List<CompletionStage<Label>> getLabelsFromPolicyEnforcer(final PolicyEnforcer policyEnforcer) {
        return policyEnforcer.getPolicy()
                .map(policy -> policy.getEntriesSet().stream()
                        .map(PolicyEntry::getLabel)
                        .map(CompletableFuture::completedStage)
                        .toList()).orElse(List.of());
    }

    private List<CompletionStage<Label>> enforcePolicyLabels(final List<CompletionStage<Label>> labels,
            final Enforcer enforcer,
            final TopLevelPolicyActionCommand command,
            final AuthorizationContext authorizationContext) {

        return labels.stream().map(labelStage ->
                labelStage.thenCompose(label -> CompletableFuture.supplyAsync(
                        () -> enforcer.hasUnrestrictedPermissions(asResourceKey(label, command), authorizationContext,
                                Permission.EXECUTE)).thenApply(result -> {
                    if (Boolean.TRUE.equals(result)) {
                        return label;
                    } else {
                        return null;
                    }
                }))).toList();
    }

    private CompletableFuture<List<Label>> filterAuthorizedLabels(final List<CompletionStage<Label>> enforcedLabels) {
        // Wait for all labels to finish enforced.
        return CompletableFuture.allOf(enforcedLabels.toArray(new CompletableFuture[0]))
                .thenCompose(voidValue -> {
                    final CompletionStage<List<Label>> labelList = CompletableFuture.completedStage(new ArrayList<>());
                    for (final CompletionStage<Label> l : enforcedLabels) {
                        l.thenCompose(label -> labelList.thenApply(list -> {
                            if (null != label) {
                                list.add(label);
                            }
                            return list;
                        }));
                    }
                    return labelList;
                });
    }

    private CompletionStage<Boolean> hasUnrestrictedWritePermission(final Enforcer enforcer,
            final ResourceKey policyResourceKey,
            final AuthorizationContext authorizationContext) {
        return CompletableFuture.supplyAsync(() -> enforcer.hasUnrestrictedPermissions(policyResourceKey,
                authorizationContext,
                Permission.WRITE));
    }

    /**
     * Limit view on entity of {@code PolicyQueryCommandResponse} by enforcer.
     *
     * @param response the response.
     * @param enforcer the enforcer.
     * @return a {@code CompletionStage} containing the response with view on entity restricted by enforcer.
     */
    public <T extends PolicyQueryCommandResponse<T>> CompletionStage<T> buildJsonViewForPolicyQueryCommandResponse(
            final PolicyQueryCommandResponse<T> response,
            final Enforcer enforcer) {

        final JsonValue entity = response.getEntity();
        final CompletionStage<T> result;
        if (entity.isObject()) {
            final CompletionStage<JsonObject> filteredView =
                    getJsonViewForPolicyQueryCommandResponse(entity.asObject(), response, enforcer);
            result = filteredView.thenApply(response::setEntity);
        } else {
            result = CompletableFuture.completedStage(response.setEntity(entity));
        }
        return result;
    }

    private CompletableFuture<JsonObject> getJsonViewForPolicyQueryCommandResponse(final JsonObject responseEntity,
            final PolicyQueryCommandResponse<?> response,
            final Enforcer enforcer) {

        final var resourceKey = ResourceKey.newInstance(PolicyCommand.RESOURCE_TYPE, response.getResourcePath());
        final var authorizationContext = response.getDittoHeaders().getAuthorizationContext();

        return CompletableFuture.supplyAsync(() -> enforcer.buildJsonView(resourceKey, responseEntity,
                        authorizationContext,
                        POLICY_QUERY_COMMAND_RESPONSE_ALLOWLIST, Permissions.newInstance(Permission.READ)));
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
