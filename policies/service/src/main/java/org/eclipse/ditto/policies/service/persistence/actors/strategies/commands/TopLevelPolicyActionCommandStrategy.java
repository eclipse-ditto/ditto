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
package org.eclipse.ditto.policies.service.persistence.actors.strategies.commands;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultVisitor;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.actions.ActivateTokenIntegration;
import org.eclipse.ditto.policies.model.signals.commands.actions.DeactivateTokenIntegration;
import org.eclipse.ditto.policies.model.signals.commands.actions.PolicyActionCommand;
import org.eclipse.ditto.policies.model.signals.commands.actions.TopLevelPolicyActionCommand;
import org.eclipse.ditto.policies.model.signals.commands.actions.TopLevelPolicyActionCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyActionFailedException;
import org.eclipse.ditto.policies.model.signals.events.PolicyActionEvent;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

import akka.actor.ActorSystem;

/**
 * This strategy handles the {@link org.eclipse.ditto.policies.model.signals.commands.actions.TopLevelPolicyActionCommand} command.
 */
final class TopLevelPolicyActionCommandStrategy
        extends AbstractPolicyCommandStrategy<TopLevelPolicyActionCommand, PolicyEvent<?>> {

    private final Map<String, AbstractPolicyActionCommandStrategy<?>> policyActionCommandStrategyMap;

    TopLevelPolicyActionCommandStrategy(final PolicyConfig policyConfig, final ActorSystem system) {
        super(TopLevelPolicyActionCommand.class, policyConfig);
        policyActionCommandStrategyMap = instantiatePolicyActionCommandStrategies(policyConfig, system);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final TopLevelPolicyActionCommand command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyActionCommand<?> actionCommand = command.getPolicyActionCommand();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final List<PolicyEntry> entries = command.getAuthorizedLabels()
                .stream()
                .map(nonNullPolicy::getEntryFor)
                .flatMap(Optional::stream)
                .filter(policyEntry -> actionCommand.isApplicable(policyEntry, dittoHeaders.getAuthorizationContext()))
                .toList();
        final AbstractPolicyActionCommandStrategy<?> strategy =
                policyActionCommandStrategyMap.get(actionCommand.getName());

        if (strategy == null) {
            // builds an internal server error, 500
            final PolicyActionFailedException exception = PolicyActionFailedException.newBuilder()
                    .action(actionCommand.getName())
                    .dittoHeaders(dittoHeaders)
                    .build();
            context.getLog()
                    .withCorrelationId(command)
                    .error(exception, "Strategy not found for top-level action <{}>", actionCommand.getName());
            return ResultFactory.newErrorResult(exception, command);
        } else if (entries.isEmpty()) {
            return ResultFactory.newErrorResult(command.getNotApplicableException(dittoHeaders), command);
        } else {
            final List<PolicyActionCommand<?>> commands = entries.stream()
                    .map(PolicyEntry::getLabel)
                    .map(actionCommand::setLabel)
                    .collect(Collectors.toList());
            final ResultCollectionVisitor visitor =
                    collectResults(strategy, context, policy, nextRevision, commands);
            if (visitor.error != null) {
                return ResultFactory.newErrorResult(visitor.error, command);
            } else {
                final Optional<PolicyEvent<?>> event = visitor.aggregateEvents();
                if (event.isPresent()) {
                    final TopLevelPolicyActionCommandResponse response =
                            TopLevelPolicyActionCommandResponse.of(context.getState(), dittoHeaders);
                    return ResultFactory.newMutationResult(command, event.get(), response);
                } else {
                    // builds an internal server error, 500
                    final PolicyActionFailedException exception = PolicyActionFailedException.newBuilder()
                            .action(actionCommand.getName())
                            .dittoHeaders(dittoHeaders)
                            .build();
                    context.getLog()
                            .withCorrelationId(command)
                            .error(exception, "Visitor could not aggregate events for action <{}>", actionCommand.getName());
                    return ResultFactory.newErrorResult(exception, command);
                }
            }
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final TopLevelPolicyActionCommand command,
            @Nullable final Policy previousEntity) {
        // top level policy action commands do not support entity tag
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final TopLevelPolicyActionCommand command,
            @Nullable final Policy newEntity) {
        // top level policy action commands do not support entity tag
        return Optional.empty();
    }

    private static Map<String, AbstractPolicyActionCommandStrategy<?>> instantiatePolicyActionCommandStrategies(
            final PolicyConfig policyConfig, final ActorSystem system) {

        return Map.of(
                ActivateTokenIntegration.NAME, new ActivateTokenIntegrationStrategy(policyConfig, system),
                DeactivateTokenIntegration.NAME, new DeactivateTokenIntegrationStrategy(policyConfig, system)
        );
    }

    private static ResultCollectionVisitor collectResults(
            final AbstractPolicyActionCommandStrategy<?> strategy,
            final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final Collection<PolicyActionCommand<?>> commands) {
        final ResultCollectionVisitor visitor = new ResultCollectionVisitor();
        for (final PolicyActionCommand<?> command : commands) {
            strategy.typeCheckAndApply(context, policy, nextRevision, command)
                    .ifPresent(result -> result.accept(visitor));
        }
        return visitor;
    }

    private static final class ResultCollectionVisitor implements ResultVisitor<PolicyActionEvent<?>> {

        @Nullable private DittoRuntimeException error;
        @Nullable private PolicyActionEvent<?> firstEvent;
        private final List<PolicyActionEvent<?>> otherEvents = new ArrayList<>();

        private Optional<PolicyEvent<?>> aggregateEvents() {
            if (firstEvent == null) {
                return Optional.empty();
            } else {
                return Optional.of(firstEvent.aggregateWith(otherEvents));
            }
        }

        @Override
        public void onMutation(final Command<?> command, final PolicyActionEvent<?> event,
                final WithDittoHeaders response, final boolean becomeCreated, final boolean becomeDeleted) {
            if (firstEvent == null) {
                firstEvent = event;
            } else {
                otherEvents.add(event);
            }
        }

        @Override
        public void onQuery(final Command<?> command, final WithDittoHeaders response) {
            // do nothing
        }

        @Override
        public void onError(final DittoRuntimeException error, final Command<?> errorCausingCommand) {
            this.error = error;
        }
    }
}
