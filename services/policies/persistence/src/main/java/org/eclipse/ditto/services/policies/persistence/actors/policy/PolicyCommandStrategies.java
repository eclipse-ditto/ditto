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
package org.eclipse.ditto.services.policies.persistence.actors.policy;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyBuilder;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.PolicyLifecycle;
import org.eclipse.ditto.model.policies.PolicyTooLargeException;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.Resources;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.Subjects;
import org.eclipse.ditto.services.models.policies.PoliciesValidator;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.services.utils.headers.conditional.ConditionalHeadersValidator;
import org.eclipse.ditto.services.utils.persistentactors.commands.AbstractReceiveStrategy;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.services.utils.persistentactors.etags.AbstractConditionHeaderCheckingCommandStrategy;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.policies.PolicyCommandSizeValidator;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyConflictException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyEntryModificationInvalidException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyEntryNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyModificationInvalidException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.ResourceNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.SubjectNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicyEntry;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicyEntryResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteResource;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteResourceResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteSubject;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteSubjectResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicy;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntries;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntriesResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntry;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntryResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResource;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResourceResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResources;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResourcesResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubject;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubjectResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubjects;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubjectsResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicy;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntries;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntriesResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntry;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntryResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResource;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResourceResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResources;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResourcesResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubject;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubjectResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubjects;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubjectsResponse;
import org.eclipse.ditto.signals.events.policies.PolicyCreated;
import org.eclipse.ditto.signals.events.policies.PolicyDeleted;
import org.eclipse.ditto.signals.events.policies.PolicyEntriesModified;
import org.eclipse.ditto.signals.events.policies.PolicyEntryCreated;
import org.eclipse.ditto.signals.events.policies.PolicyEntryDeleted;
import org.eclipse.ditto.signals.events.policies.PolicyEntryModified;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.eclipse.ditto.signals.events.policies.PolicyModified;
import org.eclipse.ditto.signals.events.policies.ResourceCreated;
import org.eclipse.ditto.signals.events.policies.ResourceDeleted;
import org.eclipse.ditto.signals.events.policies.ResourceModified;
import org.eclipse.ditto.signals.events.policies.ResourcesModified;
import org.eclipse.ditto.signals.events.policies.SubjectCreated;
import org.eclipse.ditto.signals.events.policies.SubjectDeleted;
import org.eclipse.ditto.signals.events.policies.SubjectModified;
import org.eclipse.ditto.signals.events.policies.SubjectsModified;

/**
 * Command strategies of {@code PolicyPersistenceActor}.
 */
public final class PolicyCommandStrategies
        extends AbstractReceiveStrategy<Command, Policy, PolicyId, Result<PolicyEvent>> {

    private static final PolicyCommandStrategies INSTANCE = new PolicyCommandStrategies();
    private static final CreatePolicyStrategy CREATE_POLICY_STRATEGY = new CreatePolicyStrategy();

    private PolicyCommandStrategies() {
        super(Command.class);

        // Policy level
        addStrategy(new PolicyConflictStrategy());
        addStrategy(new ModifyPolicyStrategy());
        addStrategy(new RetrievePolicyStrategy());
        addStrategy(new DeletePolicyStrategy());

        // Policy Entries
        addStrategy(new ModifyPolicyEntriesStrategy());
        addStrategy(new RetrievePolicyEntriesStrategy());

        // Policy Entry
        addStrategy(new ModifyPolicyEntryStrategy());
        addStrategy(new RetrievePolicyEntryStrategy());
        addStrategy(new DeletePolicyEntryStrategy());

        // Subjects
        addStrategy(new ModifySubjectsStrategy());
        addStrategy(new ModifySubjectStrategy());
        addStrategy(new RetrieveSubjectsStrategy());
        addStrategy(new RetrieveSubjectStrategy());
        addStrategy(new DeleteSubjectStrategy());

        // Resources
        addStrategy(new ModifyResourcesStrategy());
        addStrategy(new ModifyResourceStrategy());
        addStrategy(new RetrieveResourcesStrategy());
        addStrategy(new RetrieveResourceStrategy());
        addStrategy(new DeleteResourceStrategy());

        // Sudo
        addStrategy(new SudoRetrievePolicyStrategy());
    }

    /**
     * @return command strategies for policy persistence actor.
     */
    public static PolicyCommandStrategies getInstance() {
        return INSTANCE;
    }

    /**
     * @return command strategy to create a policy.
     */
    public static CommandStrategy<CreatePolicy, Policy, PolicyId, Result<PolicyEvent>> getCreatePolicyStrategy() {
        return CREATE_POLICY_STRATEGY;
    }

    @Override
    protected Result<PolicyEvent> getEmptyResult() {
        return ResultFactory.emptyResult();
    }

    private static DittoRuntimeException policyEntryNotFound(final PolicyId policyId, final Label label,
            final DittoHeaders dittoHeaders) {
        return PolicyEntryNotAccessibleException.newBuilder(policyId, label).dittoHeaders(dittoHeaders).build();
    }

    private static DittoRuntimeException subjectNotFound(final PolicyId policyId, final Label label,
            final CharSequence subjectId, final DittoHeaders dittoHeaders) {
        return SubjectNotAccessibleException.newBuilder(policyId, label.toString(), subjectId)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    private static DittoRuntimeException resourceNotFound(final PolicyId policyId, final Label label,
            final ResourceKey resourceKey, final DittoHeaders dittoHeaders) {
        return ResourceNotAccessibleException.newBuilder(policyId, label, resourceKey.toString())
                .dittoHeaders(dittoHeaders)
                .build();
    }

    private static DittoRuntimeException policyNotFound(final PolicyId policyId, final DittoHeaders dittoHeaders) {
        return PolicyNotAccessibleException.newBuilder(policyId).dittoHeaders(dittoHeaders).build();
    }

    private static DittoRuntimeException policyInvalid(final PolicyId policyId, @Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return PolicyModificationInvalidException.newBuilder(policyId)
                .description(message)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    private static DittoRuntimeException policyEntryInvalid(final PolicyId policyId, final Label label,
            @Nullable final String message, final DittoHeaders dittoHeaders) {
        return PolicyEntryModificationInvalidException.newBuilder(policyId, label)
                .description(message)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    private abstract static class AbstractStrategy<C extends Command>
            extends AbstractConditionHeaderCheckingCommandStrategy<C, Policy, PolicyId, PolicyEvent> {

        AbstractStrategy(final Class<C> theMatchingClass) {
            super(theMatchingClass);
        }

        @Override
        public ConditionalHeadersValidator getValidator() {
            return PoliciesConditionalHeadersValidatorProvider.getInstance();
        }

        @Override
        public boolean isDefined(final C command) {
            return true;
        }
    }

    private abstract static class AbstractQueryStrategy<C extends Command> extends AbstractStrategy<C> {

        AbstractQueryStrategy(final Class<C> theMatchingClass) {
            super(theMatchingClass);
        }

        @Override
        public Optional<?> previousETagEntity(final C command, @Nullable Policy policy) {
            return nextETagEntity(command, policy);
        }
    }

    /**
     * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy} command for a
     * new Policy.
     */
    private static final class CreatePolicyStrategy extends AbstractStrategy<CreatePolicy> {

        private CreatePolicyStrategy() {
            super(CreatePolicy.class);
        }

        @Override
        protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy entity,
                final long nextRevision, final CreatePolicy command) {

            // Policy not yet created - do so ..
            final Policy newPolicy = command.getPolicy();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final PolicyBuilder newPolicyBuilder = PoliciesModelFactory.newPolicyBuilder(newPolicy);

            if (!newPolicy.getLifecycle().isPresent()) {
                newPolicyBuilder.setLifecycle(PolicyLifecycle.ACTIVE);
            }

            final Policy newPolicyWithLifecycle = newPolicyBuilder.build();
            final PoliciesValidator validator = PoliciesValidator.newInstance(newPolicyWithLifecycle);
            if (validator.isValid()) {
                final Instant timestamp = getEventTimestamp();
                final Policy newPolicyWithTimestampAndRevision = newPolicyWithLifecycle.toBuilder()
                        .setModified(timestamp)
                        .setRevision(nextRevision)
                        .build();
                final PolicyCreated policyCreated =
                        PolicyCreated.of(newPolicyWithLifecycle, nextRevision, timestamp, dittoHeaders);
                final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                        CreatePolicyResponse.of(context.getState(), newPolicyWithTimestampAndRevision, dittoHeaders),
                        newPolicyWithTimestampAndRevision);
                context.getLog().debug("Created new Policy with ID <{}>.", context.getState());
                return ResultFactory.newMutationResult(command, policyCreated, response, true, false);
            } else {
                return ResultFactory.newErrorResult(
                        policyInvalid(context.getState(), validator.getReason().orElse(null), dittoHeaders));
            }
        }

        @Override
        public Optional<?> previousETagEntity(final CreatePolicy command, @Nullable final Policy previousEntity) {
            return Optional.ofNullable(previousEntity);
        }

        @Override
        public Optional<?> nextETagEntity(final CreatePolicy command, @Nullable final Policy newEntity) {
            return Optional.ofNullable(newEntity);
        }

        @Override
        public boolean isDefined(final Context<PolicyId> ctx, @Nullable final Policy policy, final CreatePolicy cmd) {
            return true;
        }
    }

    /**
     * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy} command for an
     * already existing Policy.
     */
    private static final class PolicyConflictStrategy extends AbstractStrategy<CreatePolicy> {

        private PolicyConflictStrategy() {
            super(CreatePolicy.class);
        }

        @Override
        protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy entity,
                final long nextRevision, final CreatePolicy command) {
            return ResultFactory.newErrorResult(PolicyConflictException.newBuilder(command.getEntityId())
                    .dittoHeaders(command.getDittoHeaders())
                    .build());
        }

        @Override
        public Optional<?> previousETagEntity(final CreatePolicy command, @Nullable final Policy previousEntity) {
            return Optional.ofNullable(previousEntity);
        }

        @Override
        public Optional<?> nextETagEntity(final CreatePolicy command, @Nullable final Policy newEntity) {
            return Optional.ofNullable(newEntity);
        }
    }

    /**
     * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicy} command for an already existing Policy.
     */
    private static final class ModifyPolicyStrategy extends AbstractStrategy<ModifyPolicy> {

        private ModifyPolicyStrategy() {
            super(ModifyPolicy.class);
        }

        @Override
        protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy entity,
                final long nextRevision, final ModifyPolicy command) {
            final Policy modifiedPolicy = command.getPolicy().toBuilder().setRevision(nextRevision).build();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            try {
                PolicyCommandSizeValidator.getInstance()
                        .ensureValidSize(() -> modifiedPolicy.toJsonString().length(), command::getDittoHeaders);
            } catch (final PolicyTooLargeException e) {
                return ResultFactory.newErrorResult(e);
            }

            final PoliciesValidator validator = PoliciesValidator.newInstance(modifiedPolicy);

            if (validator.isValid()) {
                final PolicyModified policyModified =
                        PolicyModified.of(modifiedPolicy, nextRevision, getEventTimestamp(), dittoHeaders);
                final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                        ModifyPolicyResponse.modified(context.getState(), dittoHeaders),
                        modifiedPolicy);
                return ResultFactory.newMutationResult(command, policyModified, response);
            } else {
                return ResultFactory.newErrorResult(
                        policyInvalid(context.getState(), validator.getReason().orElse(null), dittoHeaders));
            }
        }

        @Override
        public Optional<?> previousETagEntity(final ModifyPolicy command, @Nullable final Policy previousEntity) {
            return Optional.ofNullable(previousEntity);
        }

        @Override
        public Optional<?> nextETagEntity(final ModifyPolicy command, @Nullable final Policy newEntity) {
            return Optional.ofNullable(newEntity);
        }
    }

    /**
     * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.query.RetrievePolicy} command.
     */
    private static final class RetrievePolicyStrategy extends AbstractQueryStrategy<RetrievePolicy> {

        private RetrievePolicyStrategy() {
            super(RetrievePolicy.class);
        }

        @Override
        protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy entity,
                final long nextRevision, final RetrievePolicy command) {
            if (entity != null) {
                return ResultFactory.newQueryResult(command, appendETagHeaderIfProvided(command,
                        RetrievePolicyResponse.of(context.getState(), entity, command.getDittoHeaders()), entity));
            } else {
                return ResultFactory.newErrorResult(policyNotFound(context.getState(), command.getDittoHeaders()));
            }
        }

        @Override
        public Optional<?> nextETagEntity(final RetrievePolicy command, @Nullable final Policy newEntity) {
            return Optional.ofNullable(newEntity);
        }
    }

    /**
     * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.DeletePolicy} command.
     */
    private static final class DeletePolicyStrategy extends AbstractStrategy<DeletePolicy> {

        private DeletePolicyStrategy() {
            super(DeletePolicy.class);
        }

        @Override
        protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy entity,
                final long nextRevision, final DeletePolicy command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final PolicyDeleted policyDeleted =
                    PolicyDeleted.of(context.getState(), nextRevision, getEventTimestamp(), dittoHeaders);
            final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                    DeletePolicyResponse.of(context.getState(), dittoHeaders), entity);
            context.getLog().info("Deleted Policy with ID <{}>.", context.getState());
            return ResultFactory.newMutationResult(command, policyDeleted, response, false, true);
        }

        @Override
        public Optional<?> previousETagEntity(final DeletePolicy command, @Nullable final Policy previousEntity) {
            return Optional.ofNullable(previousEntity);
        }

        @Override
        public Optional<?> nextETagEntity(final DeletePolicy command, @Nullable final Policy newEntity) {
            return Optional.empty();
        }
    }

    /**
     * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntries} command.
     */
    private static final class ModifyPolicyEntriesStrategy extends AbstractStrategy<ModifyPolicyEntries> {

        private ModifyPolicyEntriesStrategy() {
            super(ModifyPolicyEntries.class);
        }

        @Override
        protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy entity,
                final long nextRevision, final ModifyPolicyEntries command) {
            final Iterable<PolicyEntry> policyEntries = command.getPolicyEntries();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            try {
                PolicyCommandSizeValidator.getInstance().ensureValidSize(
                        () -> StreamSupport.stream(policyEntries.spliterator(), false)
                                .map(PolicyEntry::toJson)
                                .collect(JsonCollectors.valuesToArray())
                                .toString()
                                .length(),
                        command::getDittoHeaders);
            } catch (final PolicyTooLargeException e) {
                return ResultFactory.newErrorResult(e);
            }

            final PolicyId policyId = context.getState();
            final PolicyEntriesModified policyEntriesModified = PolicyEntriesModified.of(policyId, policyEntries,
                    nextRevision, getEventTimestamp(), dittoHeaders);
            final WithDittoHeaders response =
                    appendETagHeaderIfProvided(command, ModifyPolicyEntriesResponse.of(policyId, dittoHeaders), entity);

            return ResultFactory.newMutationResult(command, policyEntriesModified, response);
        }

        @Override
        public Optional<?> previousETagEntity(final ModifyPolicyEntries command,
                @Nullable final Policy previousEntity) {
            return Optional.ofNullable(previousEntity).map(Policy::getEntriesSet);
        }

        @Override
        public Optional<?> nextETagEntity(final ModifyPolicyEntries command, @Nullable final Policy newEntity) {
            return Optional.of(command.getPolicyEntries());
        }
    }

    /**
     * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntry} command.
     */
    @NotThreadSafe
    private static final class ModifyPolicyEntryStrategy extends AbstractStrategy<ModifyPolicyEntry> {

        private ModifyPolicyEntryStrategy() {
            super(ModifyPolicyEntry.class);
        }

        @Override
        protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy policy,
                final long nextRevision, final ModifyPolicyEntry command) {

            checkNotNull(policy, "policy");
            final PolicyEntry policyEntry = command.getPolicyEntry();
            final Label label = policyEntry.getLabel();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            try {
                PolicyCommandSizeValidator.getInstance().ensureValidSize(() -> {
                    final long policyLength = policy.removeEntry(label).toJsonString().length();
                    final long entryLength =
                            policyEntry.toJsonString().length() + label.toString().length() + 5L;
                    return policyLength + entryLength;
                }, command::getDittoHeaders);
            } catch (final PolicyTooLargeException e) {
                return ResultFactory.newErrorResult(e);
            }

            final PoliciesValidator validator = PoliciesValidator.newInstance(policy.setEntry(policyEntry));
            final PolicyId policyId = context.getState();

            if (validator.isValid()) {
                final PolicyEvent eventToPersist;
                final ModifyPolicyEntryResponse createdOrModifiedResponse;
                if (policy.contains(label)) {
                    eventToPersist =
                            PolicyEntryModified.of(policyId, policyEntry, nextRevision, getEventTimestamp(),
                                    dittoHeaders);
                    createdOrModifiedResponse = ModifyPolicyEntryResponse.modified(policyId, dittoHeaders);
                } else {
                    eventToPersist =
                            PolicyEntryCreated.of(policyId, policyEntry, nextRevision, getEventTimestamp(),
                                    dittoHeaders);
                    createdOrModifiedResponse = ModifyPolicyEntryResponse.created(policyId, policyEntry, dittoHeaders);
                }
                final WithDittoHeaders response =
                        appendETagHeaderIfProvided(command, createdOrModifiedResponse, policy);

                return ResultFactory.newMutationResult(command, eventToPersist, response);
            } else {
                return ResultFactory.newErrorResult(
                        policyEntryInvalid(policyId, label, validator.getReason().orElse(null), dittoHeaders));
            }
        }

        @Override
        public Optional<?> previousETagEntity(final ModifyPolicyEntry command, @Nullable final Policy previousEntity) {
            return Optional.ofNullable(previousEntity).flatMap(p -> p.getEntryFor(command.getPolicyEntry().getLabel()));
        }

        @Override
        public Optional<?> nextETagEntity(final ModifyPolicyEntry command, @Nullable final Policy newEntity) {
            return Optional.of(command.getPolicyEntry());
        }
    }

    /**
     * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.DeletePolicyEntry} command.
     */
    private static final class DeletePolicyEntryStrategy extends AbstractStrategy<DeletePolicyEntry> {

        private DeletePolicyEntryStrategy() {
            super(DeletePolicyEntry.class);
        }


        @Override
        protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy policy,
                final long nextRevision, final DeletePolicyEntry command) {
            checkNotNull(policy, "policy");
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final Label label = command.getLabel();
            final PolicyId policyId = context.getState();

            if (policy.contains(label)) {
                final PoliciesValidator validator = PoliciesValidator.newInstance(policy.removeEntry(label));

                if (validator.isValid()) {
                    final PolicyEntryDeleted policyEntryDeleted =
                            PolicyEntryDeleted.of(policyId, label, nextRevision, getEventTimestamp(), dittoHeaders);
                    final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                            DeletePolicyEntryResponse.of(policyId, label, dittoHeaders), policy);
                    return ResultFactory.newMutationResult(command, policyEntryDeleted, response);
                } else {
                    return ResultFactory.newErrorResult(
                            policyEntryInvalid(policyId, label, validator.getReason().orElse(null), dittoHeaders));
                }
            } else {
                return ResultFactory.newErrorResult(policyEntryNotFound(policyId, label, dittoHeaders));
            }
        }

        @Override
        public Optional<?> previousETagEntity(final DeletePolicyEntry command, @Nullable final Policy previousEntity) {
            return Optional.ofNullable(previousEntity).flatMap(p -> p.getEntryFor(command.getLabel()));
        }

        @Override
        public Optional<?> nextETagEntity(final DeletePolicyEntry command, @Nullable final Policy newEntity) {
            return Optional.empty();
        }
    }

    /**
     * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntries}.
     */
    private static final class RetrievePolicyEntriesStrategy extends AbstractQueryStrategy<RetrievePolicyEntries> {

        private RetrievePolicyEntriesStrategy() {
            super(RetrievePolicyEntries.class);
        }

        @Override
        protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy policy,
                final long nextRevision, final RetrievePolicyEntries command) {
            final PolicyId policyId = context.getState();
            if (policy != null) {
                final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                        RetrievePolicyEntriesResponse.of(policyId, policy.getEntriesSet(), command.getDittoHeaders()),
                        policy);
                return ResultFactory.newQueryResult(command, response);
            } else {
                return ResultFactory.newErrorResult(policyNotFound(policyId, command.getDittoHeaders()));
            }
        }

        @Override
        public Optional<?> nextETagEntity(final RetrievePolicyEntries command, @Nullable final Policy newEntity) {
            return Optional.ofNullable(newEntity).map(Policy::getEntriesSet);
        }
    }

    /**
     * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntry} command.
     */
    private static final class RetrievePolicyEntryStrategy extends AbstractQueryStrategy<RetrievePolicyEntry> {

        private RetrievePolicyEntryStrategy() {
            super(RetrievePolicyEntry.class);
        }

        @Override
        protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy policy,
                final long nextRevision, final RetrievePolicyEntry command) {
            final PolicyId policyId = context.getState();
            if (policy != null) {
                final Optional<PolicyEntry> optionalEntry = policy.getEntryFor(command.getLabel());
                if (optionalEntry.isPresent()) {
                    final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                            RetrievePolicyEntryResponse.of(policyId, optionalEntry.get(), command.getDittoHeaders()),
                            policy);
                    return ResultFactory.newQueryResult(command, response);
                }
            }
            return ResultFactory.newErrorResult(
                    policyEntryNotFound(policyId, command.getLabel(), command.getDittoHeaders()));
        }

        @Override
        public Optional<?> nextETagEntity(final RetrievePolicyEntry command, @Nullable final Policy newEntity) {
            return Optional.ofNullable(newEntity).flatMap(p -> p.getEntryFor(command.getLabel()));
        }
    }

    /**
     * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.ModifySubjects} command.
     */
    private static final class ModifySubjectsStrategy extends AbstractStrategy<ModifySubjects> {

        /**
         * Constructs a new {@code ModifySubjectsStrategy} object.
         */
        private ModifySubjectsStrategy() {
            super(ModifySubjects.class);
        }

        @Override
        protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy policy,
                final long nextRevision, final ModifySubjects command) {
            checkNotNull(policy, "policy");
            final PolicyId policyId = context.getState();
            final Label label = command.getLabel();
            final Subjects subjects = command.getSubjects();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            if (policy.getEntryFor(label).isPresent()) {
                final PoliciesValidator validator =
                        PoliciesValidator.newInstance(policy.setSubjectsFor(label, subjects));

                if (validator.isValid()) {
                    final SubjectsModified subjectsModified =
                            SubjectsModified.of(policyId, label, subjects, nextRevision, getEventTimestamp(),
                                    command.getDittoHeaders());
                    final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                            ModifySubjectsResponse.of(policyId, label, dittoHeaders), policy);
                    return ResultFactory.newMutationResult(command, subjectsModified, response);
                } else {
                    return ResultFactory.newErrorResult(
                            policyEntryInvalid(policyId, label, validator.getReason().orElse(null), dittoHeaders));
                }
            } else {
                return ResultFactory.newErrorResult(policyEntryNotFound(policyId, label, dittoHeaders));
            }
        }

        @Override
        public Optional<?> previousETagEntity(final ModifySubjects command, @Nullable final Policy previousEntity) {
            return Optional.ofNullable(previousEntity)
                    .flatMap(p -> p.getEntryFor(command.getLabel()))
                    .map(PolicyEntry::getSubjects);
        }

        @Override
        public Optional<?> nextETagEntity(final ModifySubjects command, @Nullable final Policy newEntity) {
            return Optional.of(command.getSubjects());
        }
    }

    /**
     * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.query.RetrieveSubjects} command.
     */
    private static final class RetrieveSubjectsStrategy extends AbstractQueryStrategy<RetrieveSubjects> {

        private RetrieveSubjectsStrategy() {
            super(RetrieveSubjects.class);
        }

        @Override
        protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy policy,
                final long nextRevision, final RetrieveSubjects command) {
            checkNotNull(policy, "policy");
            final PolicyId policyId = context.getState();
            final Optional<PolicyEntry> optionalEntry = policy.getEntryFor(command.getLabel());
            if (optionalEntry.isPresent()) {
                final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                        RetrieveSubjectsResponse.of(policyId, command.getLabel(), optionalEntry.get().getSubjects(),
                                command.getDittoHeaders()),
                        policy);
                return ResultFactory.newQueryResult(command, response);
            } else {
                return ResultFactory.newErrorResult(
                        policyEntryNotFound(policyId, command.getLabel(), command.getDittoHeaders()));
            }
        }

        @Override
        public Optional<?> nextETagEntity(final RetrieveSubjects command, @Nullable final Policy newEntity) {
            return Optional.ofNullable(newEntity)
                    .flatMap(p -> p.getEntryFor(command.getLabel()))
                    .map(PolicyEntry::getSubjects);
        }
    }

    /**
     * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.ModifySubject} command.
     */
    private static final class ModifySubjectStrategy extends AbstractStrategy<ModifySubject> {

        private ModifySubjectStrategy() {
            super(ModifySubject.class);
        }

        @Override
        protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy policy,
                final long nextRevision, final ModifySubject command) {
            checkNotNull(policy, "policy");
            final PolicyId policyId = context.getState();
            final Label label = command.getLabel();
            final Subject subject = command.getSubject();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            final Optional<PolicyEntry> optionalEntry = policy.getEntryFor(label);
            if (optionalEntry.isPresent()) {
                final PolicyEntry policyEntry = optionalEntry.get();
                final PoliciesValidator validator = PoliciesValidator.newInstance(policy.setSubjectFor(label, subject));

                if (validator.isValid()) {
                    final PolicyEvent event;
                    final ModifySubjectResponse rawResponse;

                    if (policyEntry.getSubjects().getSubject(subject.getId()).isPresent()) {
                        rawResponse = ModifySubjectResponse.modified(policyId, label, dittoHeaders);
                        event = SubjectModified.of(policyId, label, subject, nextRevision, getEventTimestamp(),
                                command.getDittoHeaders());
                    } else {
                        rawResponse = ModifySubjectResponse.created(policyId, label, subject, dittoHeaders);
                        event = SubjectCreated.of(policyId, label, subject, nextRevision, getEventTimestamp(),
                                command.getDittoHeaders());
                    }
                    return ResultFactory.newMutationResult(command, event,
                            appendETagHeaderIfProvided(command, rawResponse, policy));
                } else {
                    return ResultFactory.newErrorResult(
                            policyEntryInvalid(policyId, label, validator.getReason().orElse(null), dittoHeaders));
                }
            } else {
                return ResultFactory.newErrorResult(policyEntryNotFound(policyId, label, dittoHeaders));
            }
        }

        @Override
        public Optional<?> previousETagEntity(final ModifySubject command, @Nullable final Policy previousEntity) {
            return Optional.ofNullable(previousEntity)
                    .flatMap(p -> p.getEntryFor(command.getLabel()))
                    .flatMap(entry -> entry.getSubjects().getSubject(command.getSubject().getId()));
        }

        @Override
        public Optional<?> nextETagEntity(final ModifySubject command, @Nullable final Policy newEntity) {
            return Optional.of(command.getSubject());
        }
    }

    /**
     * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.DeleteSubject} command.
     */
    private static final class DeleteSubjectStrategy extends AbstractStrategy<DeleteSubject> {

        private DeleteSubjectStrategy() {
            super(DeleteSubject.class);
        }

        @Override
        protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy policy,
                final long nextRevision, final DeleteSubject command) {
            checkNotNull(policy, "policy");
            final PolicyId policyId = context.getState();
            final Label label = command.getLabel();
            final SubjectId subjectId = command.getSubjectId();
            final DittoHeaders headers = command.getDittoHeaders();

            final Optional<PolicyEntry> optionalEntry = policy.getEntryFor(label);
            if (optionalEntry.isPresent()) {
                final PolicyEntry policyEntry = optionalEntry.get();
                if (policyEntry.getSubjects().getSubject(subjectId).isPresent()) {
                    final PoliciesValidator validator =
                            PoliciesValidator.newInstance(policy.removeSubjectFor(label, subjectId));

                    if (validator.isValid()) {
                        final SubjectDeleted subjectDeleted =
                                SubjectDeleted.of(policyId, label, subjectId, nextRevision, getEventTimestamp(),
                                        headers);
                        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                                DeleteSubjectResponse.of(policyId, label, subjectId, headers), policy);
                        return ResultFactory.newMutationResult(command, subjectDeleted, response);
                    } else {
                        return ResultFactory.newErrorResult(
                                policyEntryInvalid(policyId, label, validator.getReason().orElse(null), headers));
                    }
                } else {
                    return ResultFactory.newErrorResult(subjectNotFound(policyId, label, subjectId, headers));
                }
            } else {
                return ResultFactory.newErrorResult(policyEntryNotFound(policyId, label, headers));
            }
        }

        @Override
        public Optional<?> previousETagEntity(final DeleteSubject command, @Nullable final Policy previousEntity) {
            return Optional.ofNullable(previousEntity)
                    .flatMap(p -> p.getEntryFor(command.getLabel()))
                    .flatMap(entry -> entry.getSubjects().getSubject(command.getSubjectId()));
        }

        @Override
        public Optional<?> nextETagEntity(final DeleteSubject command, @Nullable final Policy newEntity) {
            return Optional.empty();
        }
    }

    /**
     * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.query.RetrieveSubject} command.
     */
    private static final class RetrieveSubjectStrategy extends AbstractQueryStrategy<RetrieveSubject> {

        private RetrieveSubjectStrategy() {
            super(RetrieveSubject.class);
        }


        @Override
        protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy policy,
                final long nextRevision, final RetrieveSubject command) {
            checkNotNull(policy, "policy");
            final PolicyId policyId = context.getState();
            final Optional<PolicyEntry> optionalEntry = policy.getEntryFor(command.getLabel());
            if (optionalEntry.isPresent()) {
                final PolicyEntry policyEntry = optionalEntry.get();
                final Optional<Subject> optionalSubject = policyEntry.getSubjects().getSubject(command.getSubjectId());
                if (optionalSubject.isPresent()) {
                    final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                            RetrieveSubjectResponse.of(policyId, command.getLabel(), optionalSubject.get(),
                                    command.getDittoHeaders()),
                            policy);
                    return ResultFactory.newQueryResult(command, response);
                } else {
                    return ResultFactory.newErrorResult(
                            subjectNotFound(policyId, command.getLabel(), command.getSubjectId(),
                                    command.getDittoHeaders()));
                }
            } else {
                return ResultFactory.newErrorResult(
                        policyEntryNotFound(policyId, command.getLabel(), command.getDittoHeaders()));
            }
        }

        @Override
        public Optional<?> nextETagEntity(final RetrieveSubject command, @Nullable final Policy newEntity) {
            return Optional.ofNullable(newEntity)
                    .flatMap(p -> p.getEntryFor(command.getLabel()))
                    .map(PolicyEntry::getSubjects)
                    .flatMap(s -> s.getSubject(command.getSubjectId()));
        }
    }

    /**
     * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.ModifyResources} command.
     */
    private static final class ModifyResourcesStrategy extends AbstractStrategy<ModifyResources> {

        private ModifyResourcesStrategy() {
            super(ModifyResources.class);
        }

        @Override
        protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy policy,
                final long nextRevision, final ModifyResources command) {
            checkNotNull(policy, "policy");
            final PolicyId policyId = context.getState();
            final Label label = command.getLabel();
            final Resources resources = command.getResources();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            try {
                PolicyCommandSizeValidator.getInstance().ensureValidSize(() -> {
                    final List<ResourceKey> rks = resources.stream()
                            .map(Resource::getResourceKey)
                            .collect(Collectors.toList());
                    Policy tmpPolicy = policy;
                    for (final ResourceKey rk : rks) {
                        tmpPolicy = tmpPolicy.removeResourceFor(label, rk);
                    }
                    final long policyLength = tmpPolicy.toJsonString().length();
                    final long resourcesLength = resources.toJsonString()
                            .length() + 5L;
                    return policyLength + resourcesLength;
                }, command::getDittoHeaders);
            } catch (final PolicyTooLargeException e) {
                return ResultFactory.newErrorResult(e);
            }

            if (policy.getEntryFor(label).isPresent()) {
                final PoliciesValidator validator =
                        PoliciesValidator.newInstance(policy.setResourcesFor(label, resources));

                if (validator.isValid()) {
                    final ResourcesModified event =
                            ResourcesModified.of(policyId, label, resources, nextRevision, getEventTimestamp(),
                                    dittoHeaders);
                    final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                            ModifyResourcesResponse.of(policyId, label, dittoHeaders), policy);
                    return ResultFactory.newMutationResult(command, event, response);
                } else {
                    return ResultFactory.newErrorResult(
                            policyEntryInvalid(policyId, label, validator.getReason().orElse(null), dittoHeaders));
                }
            } else {
                return ResultFactory.newErrorResult(policyEntryNotFound(policyId, label, dittoHeaders));
            }
        }

        @Override
        public Optional<?> previousETagEntity(final ModifyResources command, @Nullable final Policy previousEntity) {
            return Optional.ofNullable(previousEntity)
                    .flatMap(p -> p.getEntryFor(command.getLabel()))
                    .map(PolicyEntry::getResources);
        }

        @Override
        public Optional<?> nextETagEntity(final ModifyResources command, @Nullable final Policy newEntity) {
            return Optional.of(command.getResources());
        }
    }

    /**
     * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.query.RetrieveResources} command.
     */
    private static final class RetrieveResourcesStrategy extends AbstractQueryStrategy<RetrieveResources> {

        private RetrieveResourcesStrategy() {
            super(RetrieveResources.class);
        }

        @Override
        protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy policy,
                final long nextRevision, final RetrieveResources command) {
            final PolicyId policyId = context.getState();
            final Optional<PolicyEntry> optionalEntry = checkNotNull(policy, "policy").getEntryFor(command.getLabel());
            if (optionalEntry.isPresent()) {
                final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                        RetrieveResourcesResponse.of(policyId, command.getLabel(), optionalEntry.get().getResources(),
                                command.getDittoHeaders()),
                        policy);
                return ResultFactory.newQueryResult(command, response);
            } else {
                return ResultFactory.newErrorResult(
                        policyEntryNotFound(policyId, command.getLabel(), command.getDittoHeaders()));
            }
        }

        @Override
        public Optional<?> nextETagEntity(final RetrieveResources command, @Nullable final Policy newEntity) {
            return Optional.ofNullable(newEntity)
                    .flatMap(p -> p.getEntryFor(command.getLabel()))
                    .map(PolicyEntry::getResources);
        }
    }

    /**
     * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.ModifyResource} command.
     */
    private static final class ModifyResourceStrategy extends AbstractStrategy<ModifyResource> {

        ModifyResourceStrategy() {
            super(ModifyResource.class);
        }

        @Override
        protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy policy,
                final long nextRevision, final ModifyResource command) {
            checkNotNull(policy, "policy");
            final PolicyId policyId = context.getState();
            final Label label = command.getLabel();
            final Resource resource = command.getResource();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            final Optional<PolicyEntry> optionalEntry = policy.getEntryFor(label);
            if (optionalEntry.isPresent()) {
                final PoliciesValidator validator =
                        PoliciesValidator.newInstance(policy.setResourceFor(label, resource));

                if (validator.isValid()) {
                    final PolicyEntry policyEntry = optionalEntry.get();
                    final PolicyEvent eventToPersist;
                    final ModifyResourceResponse rawResponse;

                    if (policyEntry.getResources().getResource(resource.getResourceKey()).isPresent()) {
                        rawResponse = ModifyResourceResponse.modified(policyId, label, dittoHeaders);
                        eventToPersist =
                                ResourceModified.of(policyId, label, resource, nextRevision, getEventTimestamp(),
                                        dittoHeaders);
                    } else {
                        rawResponse = ModifyResourceResponse.created(policyId, label, resource, dittoHeaders);
                        eventToPersist =
                                ResourceCreated.of(policyId, label, resource, nextRevision, getEventTimestamp(),
                                        dittoHeaders);
                    }

                    return ResultFactory.newMutationResult(command, eventToPersist,
                            appendETagHeaderIfProvided(command, rawResponse, policy));
                } else {
                    return ResultFactory.newErrorResult(
                            policyEntryInvalid(policyId, label, validator.getReason().orElse(null), dittoHeaders));
                }
            } else {
                return ResultFactory.newErrorResult(policyEntryNotFound(policyId, label, dittoHeaders));
            }
        }

        @Override
        public Optional<?> previousETagEntity(final ModifyResource command, @Nullable final Policy previousEntity) {
            return Optional.ofNullable(previousEntity)
                    .flatMap(p -> p.getEntryFor(command.getLabel()))
                    .map(PolicyEntry::getResources)
                    .flatMap(r -> r.getResource(command.getResource().getResourceKey()));
        }

        @Override
        public Optional<?> nextETagEntity(final ModifyResource command, @Nullable final Policy newEntity) {
            return Optional.of(command.getResource());
        }
    }

    /**
     * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.DeleteResource} command.
     */
    private static final class DeleteResourceStrategy extends AbstractStrategy<DeleteResource> {

        private DeleteResourceStrategy() {
            super(DeleteResource.class);
        }

        @Override
        protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy policy,
                final long nextRevision, final DeleteResource command) {
            checkNotNull(policy, "policy");
            final PolicyId policyId = context.getState();
            final Label label = command.getLabel();
            final ResourceKey resourceKey = command.getResourceKey();
            final DittoHeaders headerrs = command.getDittoHeaders();

            final Optional<PolicyEntry> optionalEntry = policy.getEntryFor(label);
            if (optionalEntry.isPresent()) {
                final PolicyEntry policyEntry = optionalEntry.get();

                if (policyEntry.getResources().getResource(resourceKey).isPresent()) {
                    final PoliciesValidator validator =
                            PoliciesValidator.newInstance(policy.removeResourceFor(label, resourceKey));

                    if (validator.isValid()) {
                        final ResourceDeleted resourceDeleted =
                                ResourceDeleted.of(policyId, label, resourceKey, nextRevision, getEventTimestamp(),
                                        headerrs);
                        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                                DeleteResourceResponse.of(policyId, label, resourceKey, headerrs), policy);
                        return ResultFactory.newMutationResult(command, resourceDeleted, response);
                    } else {
                        return ResultFactory.newErrorResult(
                                policyEntryInvalid(policyId, label, validator.getReason().orElse(null), headerrs));
                    }
                } else {
                    return ResultFactory.newErrorResult(resourceNotFound(policyId, label, resourceKey, headerrs));
                }
            } else {
                return ResultFactory.newErrorResult(policyEntryNotFound(policyId, label, headerrs));
            }
        }

        @Override
        public Optional<?> previousETagEntity(final DeleteResource command, @Nullable final Policy previousEntity) {
            return Optional.ofNullable(previousEntity)
                    .flatMap(p -> p.getEntryFor(command.getLabel()))
                    .flatMap(entry -> entry.getResources().getResource(command.getResourceKey()));
        }

        @Override
        public Optional<?> nextETagEntity(final DeleteResource command, @Nullable final Policy newEntity) {
            return Optional.empty();
        }
    }

    /**
     * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.query.RetrieveResource} command.
     */
    private static final class RetrieveResourceStrategy extends AbstractQueryStrategy<RetrieveResource> {

        private RetrieveResourceStrategy() {
            super(RetrieveResource.class);
        }

        @Override
        protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy policy,
                final long nextRevision, final RetrieveResource command) {
            checkNotNull(policy, "policy");
            final PolicyId policyId = context.getState();
            final Optional<PolicyEntry> optionalEntry = policy.getEntryFor(command.getLabel());
            if (optionalEntry.isPresent()) {
                final PolicyEntry policyEntry = optionalEntry.get();

                final Optional<Resource> optionalResource =
                        policyEntry.getResources().getResource(command.getResourceKey());
                if (optionalResource.isPresent()) {
                    final RetrieveResourceResponse rawResponse =
                            RetrieveResourceResponse.of(policyId, command.getLabel(), optionalResource.get(),
                                    command.getDittoHeaders());
                    return ResultFactory.newQueryResult(command,
                            appendETagHeaderIfProvided(command, rawResponse, policy));
                } else {
                    return ResultFactory.newErrorResult(
                            resourceNotFound(policyId, command.getLabel(), command.getResourceKey(),
                                    command.getDittoHeaders()));
                }
            } else {
                return ResultFactory.newErrorResult(
                        policyEntryNotFound(policyId, command.getLabel(), command.getDittoHeaders()));
            }
        }

        @Override
        public Optional<?> nextETagEntity(final RetrieveResource command, @Nullable final Policy newEntity) {
            return Optional.ofNullable(newEntity)
                    .flatMap(p -> p.getEntryFor(command.getLabel()))
                    .map(PolicyEntry::getResources)
                    .flatMap(r -> r.getResource(command.getResourceKey()));
        }
    }

    /**
     * This strategy handles the {@link org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicy} command w/o valid authorization context.
     */
    private static final class SudoRetrievePolicyStrategy extends AbstractQueryStrategy<SudoRetrievePolicy> {

        private SudoRetrievePolicyStrategy() {
            super(SudoRetrievePolicy.class);
        }

        @Override
        protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy entity,
                final long nextRevision, final SudoRetrievePolicy command) {
            final SudoRetrievePolicyResponse rawResponse =
                    SudoRetrievePolicyResponse.of(context.getState(), entity, command.getDittoHeaders());
            return ResultFactory.newQueryResult(command, appendETagHeaderIfProvided(command, rawResponse, entity));
        }

        @Override
        public Optional<?> nextETagEntity(final SudoRetrievePolicy command, @Nullable final Policy newEntity) {
            return Optional.ofNullable(newEntity);
        }
    }
}
