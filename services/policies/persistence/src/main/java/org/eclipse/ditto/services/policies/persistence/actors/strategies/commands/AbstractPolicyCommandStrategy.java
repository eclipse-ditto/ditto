/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.policies.persistence.actors.strategies.commands;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.entity.metadata.Metadata;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectExpiry;
import org.eclipse.ditto.model.policies.SubjectExpiryInvalidException;
import org.eclipse.ditto.model.policies.Subjects;
import org.eclipse.ditto.services.policies.common.config.PolicyConfig;
import org.eclipse.ditto.services.utils.headers.conditional.ConditionalHeadersValidator;
import org.eclipse.ditto.services.utils.persistentactors.etags.AbstractConditionHeaderCheckingCommandStrategy;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyEntryModificationInvalidException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyEntryNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyModificationInvalidException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.ResourceNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.SubjectNotAccessibleException;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;

/**
 * Abstract base class for {@link org.eclipse.ditto.signals.commands.policies.PolicyCommand} strategies.
 *
 * @param <C> the type of the handled command - of type {@code Command} as also
 * {@link org.eclipse.ditto.services.models.policies.commands.sudo.SudoCommand} are handled which are no PolicyCommands.
 */
abstract class AbstractPolicyCommandStrategy<C extends Command<C>>
        extends AbstractConditionHeaderCheckingCommandStrategy<C, Policy, PolicyId, PolicyEvent> {

    private final PolicyExpiryGranularity policyExpiryGranularity;

    AbstractPolicyCommandStrategy(final Class<C> theMatchingClass, final PolicyConfig policyConfig) {
        super(theMatchingClass);
        policyExpiryGranularity = calculateTemporalUnitAndAmount(policyConfig);
    }

    static PolicyExpiryGranularity calculateTemporalUnitAndAmount(final PolicyConfig policyConfig) {
        final Duration granularity = policyConfig.getSubjectExpiryGranularity();
        final long amount;

        final Duration days = granularity.truncatedTo(ChronoUnit.DAYS);
        if (!days.isZero()) {
            amount = days.dividedBy(ChronoUnit.DAYS.getDuration());
            return new PolicyExpiryGranularity(ChronoUnit.DAYS, amount, ChronoUnit.MONTHS);
        }

        final Duration hours = granularity.truncatedTo(ChronoUnit.HOURS);
        if (!hours.isZero()) {
            amount = hours.dividedBy(ChronoUnit.HOURS.getDuration());
            return new PolicyExpiryGranularity(ChronoUnit.HOURS, amount, ChronoUnit.DAYS);
        }

        final Duration minutes = granularity.truncatedTo(ChronoUnit.MINUTES);
        if (!minutes.isZero()) {
            amount = minutes.dividedBy(ChronoUnit.MINUTES.getDuration());
            return new PolicyExpiryGranularity(ChronoUnit.MINUTES,  amount, ChronoUnit.HOURS);
        }

        final Duration seconds = granularity.truncatedTo(ChronoUnit.SECONDS);
        if (!seconds.isZero()) {
            amount = seconds.dividedBy(ChronoUnit.SECONDS.getDuration());
            return new PolicyExpiryGranularity(ChronoUnit.SECONDS, amount, ChronoUnit.MINUTES);
        }

        final Duration millis = granularity.truncatedTo(ChronoUnit.MILLIS);
        if (!millis.isZero()) {
            amount = millis.dividedBy(ChronoUnit.MILLIS.getDuration());
            return new PolicyExpiryGranularity(ChronoUnit.MILLIS, amount, ChronoUnit.SECONDS);
        }

        return new PolicyExpiryGranularity(ChronoUnit.NANOS, granularity.toNanos(), ChronoUnit.MILLIS);
    }

    @Override
    public ConditionalHeadersValidator getValidator() {
        return PoliciesConditionalHeadersValidatorProvider.getInstance();
    }

    @Override
    public boolean isDefined(final C command) {
        return true;
    }

    @Override
    protected Optional<Metadata> calculateRelativeMetadata(@Nullable final Policy entity, final C command) {
        return Optional.empty();
    }

    /**
     * Takes the passed in {@code policyEntries} and potentially adjusts the contained {@link Subjects} in the following
     * way:
     * <ul>
     * <li>If a {@link SubjectExpiry} was contained in the Subject, it is rounded up according to the configured
     * {@link PolicyConfig#getSubjectExpiryGranularity()}.</li>
     * </ul>
     *
     * @param policyEntries the PolicyEntries to be potentially adjusted.
     * @return the adjusted PolicyEntries or - if not adjusted - the original.
     */
    protected Set<PolicyEntry> potentiallyAdjustPolicyEntries(final Iterable<PolicyEntry> policyEntries) {
        return StreamSupport.stream(policyEntries.spliterator(), false)
                .map(this::potentiallyAdjustPolicyEntry)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Takes the passed in {@code policyEntry} and potentially adjusts the contained {@link Subjects} in the following
     * way:
     * <ul>
     * <li>If a {@link SubjectExpiry} was contained in the Subject, it is rounded up according to the configured
     * {@link PolicyConfig#getSubjectExpiryGranularity()}.</li>
     * </ul>
     *
     * @param policyEntry the PolicyEntry to be potentially adjusted.
     * @return the adjusted PolicyEntry or - if not adjusted - the original.
     */
    protected PolicyEntry potentiallyAdjustPolicyEntry(final PolicyEntry policyEntry) {
        final Subjects adjustedSubjects = potentiallyAdjustSubjects(policyEntry.getSubjects());
        return PolicyEntry.newInstance(policyEntry.getLabel(), adjustedSubjects, policyEntry.getResources());
    }

    /**
     * Takes the passed in {@code subjects} and potentially adjusts the contained {@link Subject}s in the following way:
     * <ul>
     * <li>If a {@link SubjectExpiry} was contained in the Subject, it is rounded up according to the configured
     * {@link PolicyConfig#getSubjectExpiryGranularity()}.</li>
     * </ul>
     *
     * @param subjects the Subjects to be potentially adjusted.
     * @return the adjusted Subjects or - if not adjusted - the original.
     */
    protected Subjects potentiallyAdjustSubjects(final Subjects subjects) {
        return Subjects.newInstance(subjects.stream()
                .map(this::potentiallyAdjustSubject)
                .collect(Collectors.toList())
        );
    }

    /**
     * Takes the passed in {@code subject} and potentially adjusts in the following way:
     * <ul>
     * <li>If a {@link SubjectExpiry} was contained in the Subject, it is rounded up according to the configured
     * {@link PolicyConfig#getSubjectExpiryGranularity()}.</li>
     * </ul>
     *
     * @param subject the Subject to be potentially adjusted.
     * @return the adjusted Subject or - if not adjusted - the original.
     */
    protected Subject potentiallyAdjustSubject(final Subject subject) {
        final Optional<SubjectExpiry> expiryOptional = subject.getExpiry();
        if (expiryOptional.isPresent()) {
            final SubjectExpiry subjectExpiry = expiryOptional.get();
            return Subject.newInstance(subject.getId(), subject.getType(), roundPolicySubjectExpiry(subjectExpiry));
        } else {
            return subject;
        }
    }

    /**
     * Roundgs up the provided {@code expiry} according to the configured
     * {@link PolicyConfig#getSubjectExpiryGranularity()}.
     *
     * @param expiry the SubjectExpiry to round up.
     * @return the rounded SubjectExpiry.
     */
    protected SubjectExpiry roundPolicySubjectExpiry(final SubjectExpiry expiry) {
        final Instant timestamp = expiry.getTimestamp();

        final Instant truncated = timestamp.truncatedTo(policyExpiryGranularity.temporalUnit);
        final Instant truncatedParent;
        if (policyExpiryGranularity.parentTemporalUnit == ChronoUnit.MONTHS) {
            final ZonedDateTime truncatedToMonth = ZonedDateTime.ofInstant(timestamp, ZoneId.systemDefault())
                    .truncatedTo(ChronoUnit.DAYS)
                    .withDayOfMonth(1);
            truncatedParent = truncatedToMonth.toInstant();
        } else {
            truncatedParent = timestamp.truncatedTo(policyExpiryGranularity.parentTemporalUnit);
        }

        final long amountBetween = policyExpiryGranularity.temporalUnit.between(truncatedParent, truncated);
        final long deltaModulo = amountBetween % policyExpiryGranularity.amount;

        if (truncated.equals(timestamp) && deltaModulo == 0) {
            // shortcut, when truncating leads to the given expiry timestamp, don't adjust the subjectExpiry at all:
            return expiry;
        }

        final long toAdd = policyExpiryGranularity.amount - deltaModulo;
        final Instant roundedUp = truncated.plus(toAdd, policyExpiryGranularity.temporalUnit);

        return SubjectExpiry.newInstance(roundedUp);
    }

    /**
     * TODO TJ doc
     * @param entries
     * @param dittoHeaders
     * @param command
     * @return
     */
    protected static Optional<Result<PolicyEvent>> checkForAlreadyExpiredSubject(final Iterable<PolicyEntry> entries,
            final DittoHeaders dittoHeaders, final Command<?> command) {

        return StreamSupport.stream(entries.spliterator(), false)
                .map(PolicyEntry::getSubjects)
                .flatMap(Subjects::stream)
                .map(Subject::getExpiry)
                .flatMap(Optional::stream)
                .filter(SubjectExpiry::isExpired)
                .findFirst()
                .map(subjectExpiry -> {
                    final String expiryString = subjectExpiry.getTimestamp().toString();
                    return ResultFactory.newErrorResult(
                            SubjectExpiryInvalidException.newBuilderTimestampInThePast(expiryString)
                                    .dittoHeaders(dittoHeaders)
                                    .build(),
                            command);
                });
    }

    static DittoRuntimeException policyEntryNotFound(final PolicyId policyId, final Label label,
            final DittoHeaders dittoHeaders) {
        return PolicyEntryNotAccessibleException.newBuilder(policyId, label).dittoHeaders(dittoHeaders).build();
    }

    static DittoRuntimeException subjectNotFound(final PolicyId policyId, final Label label,
            final CharSequence subjectId, final DittoHeaders dittoHeaders) {
        return SubjectNotAccessibleException.newBuilder(policyId, label.toString(), subjectId)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    static DittoRuntimeException resourceNotFound(final PolicyId policyId, final Label label,
            final ResourceKey resourceKey, final DittoHeaders dittoHeaders) {
        return ResourceNotAccessibleException.newBuilder(policyId, label, resourceKey.toString())
                .dittoHeaders(dittoHeaders)
                .build();
    }

    static DittoRuntimeException policyNotFound(final PolicyId policyId, final DittoHeaders dittoHeaders) {
        return PolicyNotAccessibleException.newBuilder(policyId).dittoHeaders(dittoHeaders).build();
    }

    static DittoRuntimeException policyInvalid(final PolicyId policyId, @Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return PolicyModificationInvalidException.newBuilder(policyId)
                .description(message)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    static DittoRuntimeException policyEntryInvalid(final PolicyId policyId, final Label label,
            @Nullable final String description, final DittoHeaders dittoHeaders) {
        return PolicyEntryModificationInvalidException.newBuilder(policyId, label)
                .description(description)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    private static class PolicyExpiryGranularity {
        private final ChronoUnit temporalUnit;
        private final long amount;
        private final ChronoUnit parentTemporalUnit;

        private PolicyExpiryGranularity(final ChronoUnit temporalUnit,
                final long amount,
                final ChronoUnit parentTemporalUnit) {
            this.temporalUnit = temporalUnit;
            this.amount = amount;
            this.parentTemporalUnit = parentTemporalUnit;
        }
    }
}
