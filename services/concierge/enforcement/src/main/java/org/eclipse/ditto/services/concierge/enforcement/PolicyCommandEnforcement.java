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
package org.eclipse.ditto.services.concierge.enforcement;

import static java.util.Objects.requireNonNull;

import java.util.Optional;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.services.concierge.cache.IdentityCache;
import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.models.concierge.cache.Entry;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.signals.commands.base.CommandToExceptionRegistry;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyCommandToAccessExceptionRegistry;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyCommandToModifyExceptionRegistry;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyUnavailableException;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicy;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommand;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommand;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

import akka.actor.ActorRef;
import akka.event.DiagnosticLoggingAdapter;
import akka.pattern.AskTimeoutException;
import akka.pattern.PatternsCS;

/**
 * Authorize {@link PolicyCommand}.
 */
public final class PolicyCommandEnforcement extends AbstractEnforcement<PolicyCommand> {

    /**
     * Json fields that are always shown regardless of authorization.
     */
    private static final JsonFieldSelector POLICY_QUERY_COMMAND_RESPONSE_WHITELIST =
            JsonFactory.newFieldSelector(Policy.JsonFields.ID);
    private final ActorRef policiesShardRegion;
    private final EnforcerRetriever enforcerRetriever;
    private final Cache<EntityId, Entry<Enforcer>> enforcerCache;

    private PolicyCommandEnforcement(final Context data, final ActorRef policiesShardRegion,
            final Cache<EntityId, Entry<Enforcer>> enforcerCache) {

        super(data);
        this.policiesShardRegion = requireNonNull(policiesShardRegion);
        this.enforcerCache = requireNonNull(enforcerCache);
        enforcerRetriever = new EnforcerRetriever(IdentityCache.INSTANCE, enforcerCache);
    }

    /**
     * Authorize a policy-command by a policy enforcer.
     *
     * @param <T> type of the policy-command.
     * @param enforcer the policy enforcer.
     * @param command the command to authorize.
     * @return optionally the authorized command.
     */
    public static <T extends PolicyCommand> Optional<T> authorizePolicyCommand(final T command,
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
                ? Optional.of(command)
                : Optional.empty();
    }

    /**
     * Limit view on entity of {@code PolicyQueryCommandResponse} by enforcer.
     *
     * @param response the response.
     * @param enforcer the enforcer.
     * @return response with view on entity restricted by enforcer..
     */
    public static <T extends PolicyQueryCommandResponse> T buildJsonViewForPolicyQueryCommandResponse(
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
            final PolicyQueryCommandResponse response,
            final Enforcer enforcer) {


        final ResourceKey resourceKey =
                ResourceKey.newInstance(PolicyCommand.RESOURCE_TYPE, response.getResourcePath());
        final AuthorizationContext authorizationContext = response.getDittoHeaders().getAuthorizationContext();

        return enforcer.buildJsonView(resourceKey, responseEntity, authorizationContext,
                POLICY_QUERY_COMMAND_RESPONSE_WHITELIST, Permissions.newInstance(Permission.READ));
    }

    private static PolicyCommand transformModifyPolicyToCreatePolicy(final PolicyCommand receivedCommand) {
        if (receivedCommand instanceof ModifyPolicy) {
            final ModifyPolicy modifyPolicy = (ModifyPolicy) receivedCommand;
            return CreatePolicy.of(modifyPolicy.getPolicy(), modifyPolicy.getDittoHeaders());
        } else {
            return receivedCommand;
        }
    }

    /**
     * Create error due to failing to execute a policy-command in the expected way.
     *
     * @param policyCommand the command.
     * @return the error.
     */
    private static DittoRuntimeException errorForPolicyCommand(final PolicyCommand policyCommand) {
        final CommandToExceptionRegistry<PolicyCommand, DittoRuntimeException> registry =
                policyCommand instanceof PolicyModifyCommand
                        ? PolicyCommandToModifyExceptionRegistry.getInstance()
                        : PolicyCommandToAccessExceptionRegistry.getInstance();
        return registry.exceptionFrom(policyCommand);
    }

    /**
     * Authorize a policy command. Either the command is forwarded to policies-shard-region for execution or
     * the sender is told of an error.
     *
     * @param signal the command to authorize.
     * @param sender sender of the command.
     * @param log the logger to use for logging.
     */
    @Override
    public void enforce(final PolicyCommand signal, final ActorRef sender, final DiagnosticLoggingAdapter log) {
        enforcerRetriever.retrieve(entityId(), (idEntry, enforcerEntry) -> {
            if (enforcerEntry.exists()) {
                enforcePolicyCommandByEnforcer(signal, enforcerEntry.getValue(), sender);
            } else {
                enforcePolicyCommandByNonexistentEnforcer(signal, sender);
            }
        });
    }

    private void enforcePolicyCommandByEnforcer(final PolicyCommand<?> policyCommand, final Enforcer enforcer,
            final ActorRef sender) {
        final Optional<? extends PolicyCommand> authorizedCommandOpt = authorizePolicyCommand(policyCommand, enforcer);
        if (authorizedCommandOpt.isPresent()) {
            final PolicyCommand authorizedCommand = authorizedCommandOpt.get();
            if (authorizedCommand instanceof PolicyQueryCommand) {
                final PolicyQueryCommand policyQueryCommand = (PolicyQueryCommand) authorizedCommand;
                askPoliciesShardRegionAndBuildJsonView(policyQueryCommand, enforcer, sender);
            } else {
                forwardToPoliciesShardRegion(authorizedCommand, sender);
            }
        } else {
            respondWithError(policyCommand, sender);
        }
    }

    private void enforcePolicyCommandByNonexistentEnforcer(final PolicyCommand receivedCommand, final ActorRef sender) {
        final PolicyCommand policyCommand = transformModifyPolicyToCreatePolicy(receivedCommand);
        if (policyCommand instanceof CreatePolicy) {
            final CreatePolicy createPolicy = (CreatePolicy) policyCommand;
            final Enforcer enforcer = PolicyEnforcers.defaultEvaluator(createPolicy.getPolicy());
            final Optional<CreatePolicy> authorizedCommand = authorizePolicyCommand(createPolicy, enforcer);
            if (authorizedCommand.isPresent()) {
                forwardToPoliciesShardRegion(createPolicy, sender);
            } else {
                respondWithError(receivedCommand, sender);
            }
        } else {
            final PolicyNotAccessibleException policyNotAccessibleException =
                    PolicyNotAccessibleException.newBuilder(receivedCommand.getId())
                            .dittoHeaders(receivedCommand.getDittoHeaders())
                            .build();
            replyToSender(policyNotAccessibleException, sender);
        }
    }

    private void forwardToPoliciesShardRegion(final PolicyCommand command, final ActorRef sender) {
        if (command instanceof PolicyModifyCommand) {
            invalidateCaches(command.getId());
        }
        policiesShardRegion.tell(command, sender);
    }

    /**
     * Whenever a Command changed the authorization, the caches must be invalidated - otherwise a directly following
     * Command targeted for the same entity will probably fail as the enforcer was not yet updated.
     *
     * @param policyId the ID of the Policy to invalidate caches for.
     */
    private void invalidateCaches(final String policyId) {
        final EntityId entityId = EntityId.of(PolicyCommand.RESOURCE_TYPE, policyId);
        enforcerCache.invalidate(entityId);
    }

    private void respondWithError(final PolicyCommand policyCommand, final ActorRef sender) {
        sender.tell(errorForPolicyCommand(policyCommand), self());
    }

    private void askPoliciesShardRegionAndBuildJsonView(
            final PolicyQueryCommand commandWithReadSubjects,
            final Enforcer enforcer,
            final ActorRef sender) {

        PatternsCS.ask(policiesShardRegion, commandWithReadSubjects, getAskTimeout().toMillis())
                .handleAsync((response, error) -> {
                    if (response instanceof PolicyQueryCommandResponse) {
                        reportJsonViewForPolicyQuery(sender, (PolicyQueryCommandResponse) response, enforcer);
                    } else if (response instanceof DittoRuntimeException) {
                        replyToSender(response, sender);
                    } else if (isAskTimeoutException(response, error)) {
                        reportTimeoutForPolicyQuery(commandWithReadSubjects, sender, (AskTimeoutException) response);
                    } else if (error != null) {
                        reportUnexpectedError("before building JsonView", sender, error,
                                commandWithReadSubjects.getDittoHeaders());
                    } else {
                        reportUnknownResponse("before building JsonView", sender, response,
                                commandWithReadSubjects.getDittoHeaders());
                    }
                    return null;
                });
    }

    private void reportTimeoutForPolicyQuery(
            final PolicyQueryCommand command,
            final ActorRef sender,
            final AskTimeoutException askTimeoutException) {
        log(command).error(askTimeoutException, "Timeout before building JsonView");
        replyToSender(PolicyUnavailableException.newBuilder(command.getId())
                .dittoHeaders(command.getDittoHeaders())
                .build(), sender);
    }

    private void reportJsonViewForPolicyQuery(final ActorRef sender,
            final PolicyQueryCommandResponse<?> thingQueryCommandResponse,
            final Enforcer enforcer) {

        try {
            final PolicyQueryCommandResponse responseWithLimitedJsonView =
                    buildJsonViewForPolicyQueryCommandResponse(thingQueryCommandResponse, enforcer);
            replyToSender(responseWithLimitedJsonView, sender);
        } catch (final RuntimeException e) {
            reportError("Error after building JsonView", sender, e, thingQueryCommandResponse.getDittoHeaders());
        }
    }

    /**
     * Provides {@link AbstractEnforcement} for commands of type {@link PolicyCommand}.
     */
    public static final class Provider implements EnforcementProvider<PolicyCommand> {

        private final Cache<EntityId, Entry<Enforcer>> enforcerCache;
        private ActorRef policiesShardRegion;

        /**
         * Constructor.
         *
         * @param policiesShardRegion the ActorRef to the Policies shard region.
         * @param enforcerCache the enforcer cache.
         */
        public Provider(final ActorRef policiesShardRegion,
                final Cache<EntityId, Entry<Enforcer>> enforcerCache) {
            this.policiesShardRegion = requireNonNull(policiesShardRegion);
            this.enforcerCache = requireNonNull(enforcerCache);
        }

        @Override
        public Class<PolicyCommand> getCommandClass() {
            return PolicyCommand.class;
        }

        @Override
        public AbstractEnforcement<PolicyCommand> createEnforcement(final AbstractEnforcement.Context context) {
            return new PolicyCommandEnforcement(context, policiesShardRegion, enforcerCache);
        }
    }
}
