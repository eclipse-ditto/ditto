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
package org.eclipse.ditto.concierge.service.enforcement;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.CommandToExceptionRegistry;
import org.eclipse.ditto.concierge.api.ConciergeMessagingConstants;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.internal.utils.cacheloaders.PolicyEnforcer;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.actions.PolicyActionCommand;
import org.eclipse.ditto.policies.model.signals.commands.actions.TopLevelPolicyActionCommand;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyCommandToAccessExceptionRegistry;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyCommandToActionsExceptionRegistry;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyCommandToModifyExceptionRegistry;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyUnavailableException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.PolicyModifyCommand;
import org.eclipse.ditto.policies.model.signals.commands.query.PolicyQueryCommand;
import org.eclipse.ditto.policies.model.signals.commands.query.PolicyQueryCommandResponse;

import akka.actor.ActorRef;

/**
 * Authorize {@link PolicyCommand}.
 */
public final class PolicyCommandEnforcement
        extends AbstractEnforcementWithAsk<PolicyCommand<?>, PolicyQueryCommandResponse<?>> {

    /**
     * Json fields that are always shown regardless of authorization.
     */
    private static final JsonFieldSelector POLICY_QUERY_COMMAND_RESPONSE_ALLOWLIST =
            JsonFactory.newFieldSelector(Policy.JsonFields.ID);

    private final ActorRef policiesShardRegion;
    private final EnforcerRetriever<PolicyEnforcer> enforcerRetriever;
    private final Cache<EnforcementCacheKey, Entry<PolicyEnforcer>> enforcerCache;
    private final CreationRestrictionEnforcer creationRestrictionEnforcer;

    private PolicyCommandEnforcement(final Contextual<PolicyCommand<?>> context, final ActorRef policiesShardRegion,
            final Cache<EnforcementCacheKey, Entry<PolicyEnforcer>> enforcerCache,
            final CreationRestrictionEnforcer creationRestrictionEnforcer) {

        super(context, PolicyQueryCommandResponse.class);
        this.policiesShardRegion = requireNonNull(policiesShardRegion);
        this.enforcerCache = requireNonNull(enforcerCache);
        this.enforcerRetriever = new EnforcerRetriever<>(IdentityCache.INSTANCE, enforcerCache);
        this.creationRestrictionEnforcer = creationRestrictionEnforcer;
    }

    /**
     * Authorize a policy-command by a policy enforcer.
     *
     * @param <T> type of the policy-command.
     * @param policyEnforcer the policy enforcer.
     * @param command the command to authorize.
     * @param creationRestrictionEnforcer the enforcer for restricting entity creation.
     * @return optionally the authorized command.
     */
    public static <T extends PolicyCommand<?>> Optional<T> authorizePolicyCommand(final T command,
            final PolicyEnforcer policyEnforcer,
            final CreationRestrictionEnforcer creationRestrictionEnforcer
    ) {

        final var enforcer = policyEnforcer.getEnforcer();
        final var policyResourceKey = PoliciesResourceType.policyResource(command.getResourcePath());
        final var authorizationContext = command.getDittoHeaders().getAuthorizationContext();
        final Optional<T> authorizedCommand;
        if (command instanceof CreatePolicy) {
            final var enforcerContext = new CreationRestrictionEnforcer.Context(command.getResourceType(),
                    command.getEntityId().getNamespace(),
                    command.getDittoHeaders()
            );
            if (creationRestrictionEnforcer.canCreate(enforcerContext)
                    && (
                    command.getDittoHeaders().isAllowPolicyLockout()
                            || hasUnrestrictedWritePermission(enforcer, policyResourceKey, authorizationContext)
            )
            ) {
                authorizedCommand = Optional.of(command);
            } else {
                authorizedCommand = Optional.empty();
            }
        } else if (command instanceof PolicyActionCommand) {
            authorizedCommand =
                    authorizeActionCommand(policyEnforcer, command, policyResourceKey, authorizationContext);
        } else if (command instanceof PolicyModifyCommand) {
            authorizedCommand = hasUnrestrictedWritePermission(enforcer, policyResourceKey, authorizationContext)
                    ? Optional.of(command)
                    : Optional.empty();
        } else {
            final String permission = Permission.READ;
            return enforcer.hasPartialPermissions(policyResourceKey, authorizationContext, permission)
                    ? Optional.of(command)
                    : Optional.empty();
        }

        return authorizedCommand;
    }

    @SuppressWarnings("unchecked")
    private static <T extends PolicyCommand<?>> Optional<T> authorizeActionCommand(final PolicyEnforcer enforcer,
            final T command, final ResourceKey resourceKey, final AuthorizationContext authorizationContext) {

        if (command instanceof TopLevelPolicyActionCommand) {
            final var topLevelPolicyActionCommand = (TopLevelPolicyActionCommand) command;
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
                        .collect(Collectors.toList()))
                .orElse(List.of());
        if (authorizedLabels.isEmpty()) {
            return Optional.empty();
        } else {
            final var adjustedCommand = TopLevelPolicyActionCommand.of(command.getPolicyActionCommand(), authorizedLabels);
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

    private static PolicyCommand<?> transformModifyPolicyToCreatePolicy(final PolicyCommand<?> receivedCommand) {
        if (receivedCommand instanceof ModifyPolicy) {
            final var modifyPolicy = (ModifyPolicy) receivedCommand;
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

    @Override
    public CompletionStage<Contextual<WithDittoHeaders>> enforce() {
        return enforcerRetriever.retrieve(entityId(), (idEntry, enforcerEntry) -> {
            try {
                return CompletableFuture.completedFuture(doEnforce(enforcerEntry));
            } catch (final RuntimeException e) {
                return CompletableFuture.failedStage(e);
            }
        });
    }

    private Contextual<WithDittoHeaders> doEnforce(final Entry<PolicyEnforcer> enforcerEntry) {
        if (enforcerEntry.exists()) {
            return enforcePolicyCommandByEnforcer(enforcerEntry.getValueOrThrow());
        } else {
            return forwardToPoliciesShardRegion(enforcePolicyCommandByNonexistentEnforcer());
        }
    }

    private Contextual<WithDittoHeaders> enforcePolicyCommandByEnforcer(final PolicyEnforcer policyEnforcer) {
        final PolicyCommand<?> policyCommand = signal();
        final Optional<? extends PolicyCommand<?>> authorizedCommandOpt =
                authorizePolicyCommand(policyCommand, policyEnforcer, this.creationRestrictionEnforcer);
        if (authorizedCommandOpt.isPresent()) {
            final PolicyCommand<?> authorizedCommand = authorizedCommandOpt.get();
            if (authorizedCommand instanceof PolicyQueryCommand) {
                final PolicyQueryCommand<?> policyQueryCommand = (PolicyQueryCommand<?>) authorizedCommand;
                if (!policyQueryCommand.getDittoHeaders().isResponseRequired()) {
                    // drop query command with response-required=false
                    return withMessageToReceiver(null, ActorRef.noSender());
                } else {
                    return withMessageToReceiverViaAskFuture(policyQueryCommand, sender(),
                            () -> askAndBuildJsonView(policiesShardRegion, policyQueryCommand,
                                    policyEnforcer.getEnforcer(), context.getScheduler(), context.getExecutor()));
                }
            } else {
                return forwardToPoliciesShardRegion(authorizedCommand);
            }
        } else {
            throw errorForPolicyCommand(signal());
        }
    }

    private CreatePolicy enforcePolicyCommandByNonexistentEnforcer() {
        final PolicyCommand<?> policyCommand = transformModifyPolicyToCreatePolicy(signal());
        if (policyCommand instanceof CreatePolicy) {
            final var createPolicy = (CreatePolicy) policyCommand;
            final var enforcer = PolicyEnforcers.defaultEvaluator(createPolicy.getPolicy());
            final Optional<CreatePolicy> authorizedCommand =
                    authorizePolicyCommand(createPolicy, PolicyEnforcer.of(enforcer), this.creationRestrictionEnforcer);
            if (authorizedCommand.isPresent()) {
                return createPolicy;
            } else {
                throw errorForPolicyCommand(signal());
            }
        } else {
            throw PolicyNotAccessibleException.newBuilder(policyCommand.getEntityId())
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
    private Contextual<WithDittoHeaders> forwardToPoliciesShardRegion(final PolicyCommand<?> command) {
        final PolicyCommand<?> commandToForward;
        if (command instanceof PolicyModifyCommand) {
            invalidateCaches(command.getEntityId());
            final DittoHeaders adjustedHeaders = command.getDittoHeaders().toBuilder()
                    .putHeader(DittoHeaderDefinition.POLICY_ENFORCER_INVALIDATED_PREEMPTIVELY.getKey(),
                            Boolean.TRUE.toString())
                    .build();
            commandToForward = command.setDittoHeaders(adjustedHeaders);
        } else {
            commandToForward = command;
        }
        return withMessageToReceiver(commandToForward, policiesShardRegion);
    }

    /**
     * Whenever a Command changed the authorization, the caches must be invalidated - otherwise a directly following
     * Command targeted for the same entity will probably fail as the enforcer was not yet updated.
     *
     * @param policyId the ID of the Policy to invalidate caches for.
     */
    private void invalidateCaches(final PolicyId policyId) {
        final var entityId = EnforcementCacheKey.of(policyId);
        enforcerCache.invalidate(entityId);
        pubSubMediator().tell(DistPubSubAccess.sendToAll(
                        ConciergeMessagingConstants.ENFORCER_ACTOR_PATH,
                        InvalidateCacheEntry.of(entityId),
                        true),
                self());
    }

    @Override
    protected DittoRuntimeException handleAskTimeoutForCommand(final PolicyCommand<?> command,
            final Throwable askTimeout) {
        log(command).error(askTimeout, "Timeout before building JsonView");
        return PolicyUnavailableException.newBuilder(command.getEntityId())
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    @Override
    protected PolicyQueryCommandResponse<?> filterJsonView(
            final PolicyQueryCommandResponse<?> policyQueryCommandResponse,
            final Enforcer enforcer) {
        try {
            return buildJsonViewForPolicyQueryCommandResponse(policyQueryCommandResponse, enforcer);
        } catch (final RuntimeException e) {
            throw reportError("Error after building JsonView", e);
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

    /**
     * Provides {@link AbstractEnforcement} for commands of type {@link PolicyCommand}.
     */
    public static final class Provider implements EnforcementProvider<PolicyCommand<?>> {

        private final Cache<EnforcementCacheKey, Entry<PolicyEnforcer>> enforcerCache;
        private final ActorRef policiesShardRegion;
        private final CreationRestrictionEnforcer creationRestrictionEnforcer;

        /**
         * Constructor.
         *
         * @param policiesShardRegion the ActorRef to the Policies shard region.
         * @param enforcerCache the enforcer cache.
         * @param creationRestrictionEnforcer the enforcer for restricting entity creation.
         */
        public Provider(final ActorRef policiesShardRegion,
                final Cache<EnforcementCacheKey, Entry<PolicyEnforcer>> enforcerCache,
                @Nullable final CreationRestrictionEnforcer creationRestrictionEnforcer
        ) {
            this.policiesShardRegion = requireNonNull(policiesShardRegion);
            this.enforcerCache = requireNonNull(enforcerCache);
            this.creationRestrictionEnforcer = Optional.ofNullable(creationRestrictionEnforcer)
                    .orElse(CreationRestrictionEnforcer.NULL);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes", "java:S3740"})
        public Class<PolicyCommand<?>> getCommandClass() {
            return (Class) PolicyCommand.class;
        }

        @Override
        public boolean changesAuthorization(final PolicyCommand<?> signal) {
            return signal instanceof PolicyModifyCommand;
        }

        @Override
        public AbstractEnforcement<PolicyCommand<?>> createEnforcement(final Contextual<PolicyCommand<?>> context) {
            return new PolicyCommandEnforcement(context, policiesShardRegion, enforcerCache, creationRestrictionEnforcer);
        }

    }

}
