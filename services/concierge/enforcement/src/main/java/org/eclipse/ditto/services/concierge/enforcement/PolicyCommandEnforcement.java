/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.concierge.enforcement;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.services.models.concierge.ConciergeMessagingConstants;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.EntityId;
import org.eclipse.ditto.services.utils.cache.InvalidateCacheEntry;
import org.eclipse.ditto.services.utils.cache.entry.Entry;
import org.eclipse.ditto.services.utils.cacheloaders.IdentityCache;
import org.eclipse.ditto.services.utils.cluster.DistPubSubAccess;
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

import akka.actor.ActorRef;
import akka.pattern.AskTimeoutException;
import akka.pattern.Patterns;

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

    private PolicyCommandEnforcement(final Contextual<PolicyCommand> data, final ActorRef policiesShardRegion,
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

    @Override
    public CompletionStage<Contextual<WithDittoHeaders>> enforce() {
        final PolicyCommand command = signal();
        LogUtil.enhanceLogWithCorrelationIdOrRandom(command);
        return enforcerRetriever.retrieve(entityId(), (idEntry, enforcerEntry) -> {
            try {
                return doEnforce(enforcerEntry)
                        .exceptionally(this::handleExceptionally);
            } catch (final RuntimeException e) {
                return CompletableFuture.completedFuture(handleExceptionally(e));
            }
        });
    }

    private CompletionStage<Contextual<WithDittoHeaders>> doEnforce(final Entry<Enforcer> enforcerEntry) {
        if (enforcerEntry.exists()) {
            return enforcePolicyCommandByEnforcer(enforcerEntry.getValueOrThrow());
        } else {
            return CompletableFuture.completedFuture(
                    forwardToPoliciesShardRegion(enforcePolicyCommandByNonexistentEnforcer()));
        }
    }

    private CompletionStage<Contextual<WithDittoHeaders>> enforcePolicyCommandByEnforcer(final Enforcer enforcer) {
        final PolicyCommand policyCommand = signal();
        final Optional<? extends PolicyCommand> authorizedCommandOpt = authorizePolicyCommand(policyCommand, enforcer);
        if (authorizedCommandOpt.isPresent()) {
            final PolicyCommand authorizedCommand = authorizedCommandOpt.get();
            if (authorizedCommand instanceof PolicyQueryCommand) {
                final PolicyQueryCommand policyQueryCommand = (PolicyQueryCommand) authorizedCommand;
                return askPoliciesShardRegionAndBuildJsonView(policyQueryCommand, enforcer)
                        .thenApply(msg -> withMessageToReceiver(msg, sender()));
            } else {
                return CompletableFuture.completedFuture(forwardToPoliciesShardRegion(authorizedCommand));
            }
        } else {
            throw errorForPolicyCommand(signal());
        }
    }

    private CreatePolicy enforcePolicyCommandByNonexistentEnforcer() {
        final PolicyCommand policyCommand = transformModifyPolicyToCreatePolicy(signal());
        if (policyCommand instanceof CreatePolicy) {
            final CreatePolicy createPolicy = (CreatePolicy) policyCommand;
            final Enforcer enforcer = PolicyEnforcers.defaultEvaluator(createPolicy.getPolicy());
            final Optional<CreatePolicy> authorizedCommand = authorizePolicyCommand(createPolicy, enforcer);
            if (authorizedCommand.isPresent()) {
                return createPolicy;
            } else {
                throw errorForPolicyCommand(signal());
            }
        } else {
            throw PolicyNotAccessibleException.newBuilder(policyCommand.getId())
                            .dittoHeaders(policyCommand.getDittoHeaders())
                            .build();
        }
    }

    /**
     * Forward a command to policies-shard-region.
     *
     * @param command command to forward.
     * @return the contextual including message and receiver
     */
    private Contextual<WithDittoHeaders> forwardToPoliciesShardRegion(final PolicyCommand command) {
        if (command instanceof PolicyModifyCommand) {
            invalidateCaches(command.getId());
        }
        return withMessageToReceiver(command, policiesShardRegion);
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
        pubSubMediator().tell(DistPubSubAccess.sendToAll(
                        ConciergeMessagingConstants.ENFORCER_ACTOR_PATH,
                        InvalidateCacheEntry.of(entityId),
                        true),
                self());
    }

    private CompletionStage<WithDittoHeaders> askPoliciesShardRegionAndBuildJsonView(
            final PolicyQueryCommand commandWithReadSubjects,
            final Enforcer enforcer) {

        return Patterns.ask(policiesShardRegion, commandWithReadSubjects, getAskTimeout())
                .handle((response, error) -> {
                    if (response instanceof PolicyQueryCommandResponse) {
                        return reportJsonViewForPolicyQuery((PolicyQueryCommandResponse<?>) response, enforcer);
                    } else if (response instanceof DittoRuntimeException) {
                        throw (DittoRuntimeException) response;
                    } else if (isAskTimeoutException(response, error)) {
                        throw reportTimeoutForPolicyQuery(commandWithReadSubjects, (AskTimeoutException) response);
                    } else if (error != null) {
                        throw reportUnexpectedError("before building JsonView", error);
                    } else {
                        throw reportUnknownResponse("before building JsonView", response);
                    }
                });
    }

    private PolicyUnavailableException reportTimeoutForPolicyQuery(
            final PolicyQueryCommand command,
            final AskTimeoutException askTimeoutException) {
        log(command).error(askTimeoutException, "Timeout before building JsonView");
        return PolicyUnavailableException.newBuilder(command.getId())
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    private PolicyQueryCommandResponse reportJsonViewForPolicyQuery(
            final PolicyQueryCommandResponse<?> thingQueryCommandResponse,
            final Enforcer enforcer) {

        try {
            return buildJsonViewForPolicyQueryCommandResponse(thingQueryCommandResponse, enforcer);
        } catch (final RuntimeException e) {
            throw reportError("Error after building JsonView", e);
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
        public AbstractEnforcement<PolicyCommand> createEnforcement(final Contextual<PolicyCommand> context) {
            return new PolicyCommandEnforcement(context, policiesShardRegion, enforcerCache);
        }

    }

}
