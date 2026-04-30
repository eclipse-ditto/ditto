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
package org.eclipse.ditto.policies.service.persistence.actors.strategies.commands;

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

import org.eclipse.ditto.base.model.common.DittoDuration;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.headers.conditional.ConditionalHeadersValidator;
import org.eclipse.ditto.internal.utils.persistentactors.etags.AbstractConditionHeaderCheckingCommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.policies.api.commands.sudo.PolicySudoCommand;
import org.eclipse.ditto.policies.model.AllowedAddition;
import org.eclipse.ditto.policies.model.EntryReference;
import org.eclipse.ditto.policies.model.ImportableType;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectAnnouncement;
import org.eclipse.ditto.policies.model.SubjectExpiry;
import org.eclipse.ditto.policies.model.SubjectExpiryInvalidException;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.policies.model.PolicyEntryInvalidException;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyEntryModificationInvalidException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyEntryNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyImportNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyImportReferenceConflictException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyImportsNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyModificationInvalidException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.ResourceNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.SubjectNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * Abstract base class for {@link org.eclipse.ditto.policies.model.signals.commands.PolicyCommand} strategies.
 *
 * @param <C> the type of the handled command - of type {@code Command} as also
 * {@link org.eclipse.ditto.policies.api.commands.sudo.PolicySudoCommand} are handled which are no PolicyCommands.
 * @param <E> the type of the emitted events
 */
abstract class AbstractPolicyCommandStrategy<C extends Command<C>, E extends PolicyEvent<?>>
        extends AbstractConditionHeaderCheckingCommandStrategy<C, Policy, PolicyId, E> {

    private final PolicyExpiryGranularity policyExpiryGranularity;
    private final Duration policyDeletionAnnouncementGranularity;

    AbstractPolicyCommandStrategy(final Class<C> theMatchingClass, final PolicyConfig policyConfig) {
        super(theMatchingClass);
        policyExpiryGranularity = calculateTemporalUnitAndAmount(policyConfig);
        policyDeletionAnnouncementGranularity = policyConfig.getSubjectDeletionAnnouncementGranularity();
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
            return new PolicyExpiryGranularity(ChronoUnit.MINUTES, amount, ChronoUnit.HOURS);
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
        return command instanceof PolicyCommand || command instanceof PolicySudoCommand;
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
     * {@link org.eclipse.ditto.policies.service.common.config.PolicyConfig#getSubjectExpiryGranularity()}.</li>
     * <li>If a {@link SubjectAnnouncement} was contained in the Subject, its before-expiry duration is rounded up
     * according to the configured {@link org.eclipse.ditto.policies.service.common.config.PolicyConfig#getSubjectDeletionAnnouncementGranularity()}.</li>
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
     * {@link org.eclipse.ditto.policies.service.common.config.PolicyConfig#getSubjectExpiryGranularity()}.</li>
     * <li>If a {@link SubjectAnnouncement} was contained in the Subject, its before-expiry duration is rounded up
     * according to the configured {@link org.eclipse.ditto.policies.service.common.config.PolicyConfig#getSubjectDeletionAnnouncementGranularity()}.</li>
     * </ul>
     *
     * @param policyEntry the PolicyEntry to be potentially adjusted.
     * @return the adjusted PolicyEntry or - if not adjusted - the original.
     */
    protected PolicyEntry potentiallyAdjustPolicyEntry(final PolicyEntry policyEntry) {
        final var adjustedSubjects = potentiallyAdjustSubjects(policyEntry.getSubjects());
        return PoliciesModelFactory.newPolicyEntry(policyEntry.getLabel(), adjustedSubjects,
                policyEntry.getResources(), policyEntry.getNamespaces().orElse(null),
                policyEntry.getImportableType(), policyEntry.getAllowedAdditions().orElse(null),
                policyEntry.getReferences().isEmpty() ? null : policyEntry.getReferences());
    }

    /**
     * Takes the passed in {@code subjects} and potentially adjusts the contained {@link Subject}s in the following way:
     * <ul>
     * <li>If a {@link SubjectExpiry} was contained in the Subject, it is rounded up according to the configured
     * {@link org.eclipse.ditto.policies.service.common.config.PolicyConfig#getSubjectExpiryGranularity()}.</li>
     * <li>If a {@link SubjectAnnouncement} was contained in the Subject, its before-expiry duration is rounded up
     * according to the configured {@link org.eclipse.ditto.policies.service.common.config.PolicyConfig#getSubjectDeletionAnnouncementGranularity()}.</li>
     * </ul>
     *
     * @param subjects the Subjects to be potentially adjusted.
     * @return the adjusted Subjects or - if not adjusted - the original.
     */
    protected Subjects potentiallyAdjustSubjects(final Subjects subjects) {
        return Subjects.newInstance(subjects.stream()
                .map(this::potentiallyAdjustSubject)
                .toList());
    }

    /**
     * Takes the passed in {@code subject} and potentially adjusts in the following way:
     * <ul>
     * <li>If a {@link SubjectExpiry} was contained in the Subject, it is rounded up according to the configured
     * {@link org.eclipse.ditto.policies.service.common.config.PolicyConfig#getSubjectExpiryGranularity()}.</li>
     * <li>If a {@link SubjectAnnouncement} was contained in the Subject, its before-expiry duration is rounded up
     * according to the configured {@link org.eclipse.ditto.policies.service.common.config.PolicyConfig#getSubjectDeletionAnnouncementGranularity()}.</li>
     * </ul>
     *
     * @param subject the Subject to be potentially adjusted.
     * @return the adjusted Subject or - if not adjusted - the original.
     */
    protected Subject potentiallyAdjustSubject(final Subject subject) {
        final Optional<SubjectExpiry> expiryOptional = subject.getExpiry();
        final Optional<SubjectAnnouncement> announcementOptional = subject.getAnnouncement();
        if (expiryOptional.isPresent() || announcementOptional.isPresent()) {
            final var subjectExpiry = expiryOptional.map(this::roundPolicySubjectExpiry).orElse(null);
            final var subjectAnnouncement =
                    announcementOptional.map(this::roundSubjectAnnouncement).orElse(null);
            return Subject.newInstance(subject.getId(), subject.getType(), subjectExpiry, subjectAnnouncement);
        } else {
            return subject;
        }
    }

    /**
     * Rounds up the provided {@code expiry} according to the configured
     * {@link org.eclipse.ditto.policies.service.common.config.PolicyConfig#getSubjectExpiryGranularity()}.
     *
     * @param expiry the SubjectExpiry to round up.
     * @return the rounded SubjectExpiry.
     */
    protected SubjectExpiry roundPolicySubjectExpiry(final SubjectExpiry expiry) {
        final Instant timestamp = expiry.getTimestamp();

        if (policyExpiryGranularity.amount <= 0) {
            return expiry;
        }

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
     * Rounds up the before-expiry duration of a {@link SubjectAnnouncement}
     * according to the configured {@link org.eclipse.ditto.policies.service.common.config.PolicyConfig#getSubjectDeletionAnnouncementGranularity()}.
     *
     * @param subjectAnnouncement the subject-announcement to adjust.
     * @return the result of adjustment.
     */
    @Nullable
    protected SubjectAnnouncement roundSubjectAnnouncement(@Nullable final SubjectAnnouncement subjectAnnouncement) {
        final Optional<DittoDuration> beforeExpiry =
                Optional.ofNullable(subjectAnnouncement).flatMap(SubjectAnnouncement::getBeforeExpiry);
        if (beforeExpiry.isPresent()) {
            final var dittoDuration = beforeExpiry.get();
            final var roundedUpDuration =
                    roundUpDuration(dittoDuration.getDuration(), policyDeletionAnnouncementGranularity);
            final var roundedUpDittoDuration = dittoDuration.setAmount(roundedUpDuration);
            return subjectAnnouncement.setBeforeExpiry(roundedUpDittoDuration);
        } else {
            return subjectAnnouncement;
        }
    }

    /**
     * Checks the passed {@code PolicyEntry}s whether there is an an already expired subject in there.
     * <p>
     * If there is, the Optional will contain an Result of type "error" containing the
     * {@link SubjectExpiryInvalidException}.
     *
     * @param entries the policy entries to check for an expiry date in the past.
     * @param dittoHeaders the DittoHeaders to use for building the exception.
     * @param command the command which caused the change of the policy entries.
     * @param <T> the type of the emitted event
     * @return an Optional with ErrorResponse if a subject was invalid, an empty Optional if everything was valid.
     */
    protected static <T extends PolicyEvent<?>> Optional<Result<T>> checkForAlreadyExpiredSubject(
            final Iterable<PolicyEntry> entries,
            final DittoHeaders dittoHeaders, final Command<?> command) {

        return StreamSupport.stream(entries.spliterator(), false)
                .map(PolicyEntry::getSubjects)
                .map(subjects -> AbstractPolicyCommandStrategy.<T>checkForAlreadyExpiredSubject(subjects, dittoHeaders,
                        command))
                .flatMap(Optional::stream)
                .findFirst();
    }

    protected static <T extends PolicyEvent<?>> Optional<Result<T>> checkForAlreadyExpiredSubject(Subjects subjects,
            DittoHeaders dittoHeaders, final Command<?> command) {
        return StreamSupport.stream(subjects.spliterator(), false)
                .map(subject -> AbstractPolicyCommandStrategy.<T>checkForAlreadyExpiredSubject(subject, dittoHeaders, command))
                .flatMap(Optional::stream)
                .findFirst();
    }

    protected static <T extends PolicyEvent<?>> Optional<Result<T>> checkForAlreadyExpiredSubject(Subject subject,
            DittoHeaders dittoHeaders, final Command<?> command) {
        return subject.getExpiry()
                .filter(SubjectExpiry::isExpired)
                .map(subjectExpiry -> {
                    final var expiryString = subjectExpiry.getTimestamp().toString();
                    return ResultFactory.newErrorResult(
                            SubjectExpiryInvalidException.newBuilderTimestampInThePast(expiryString)
                                    .dittoHeaders(dittoHeaders)
                                    .build(),
                            command);
                });
    }

    /**
     * Checks whether any entry in the given policy has an import reference pointing to the specified imported policy ID.
     *
     * @param policy the policy to check.
     * @param importedPolicyId the imported policy ID to look for in entry references.
     * @return {@code true} if at least one entry references the given import.
     */
    static boolean anyEntryReferencesImport(final Policy policy, final PolicyId importedPolicyId) {
        for (final PolicyEntry entry : policy) {
            for (final EntryReference ref : entry.getReferences()) {
                if (ref.isImportReference() &&
                        ref.getImportedPolicyId().filter(importedPolicyId::equals).isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks whether any entry in the given policy has any import reference at all.
     *
     * @param policy the policy to check.
     * @return {@code true} if at least one entry has an import reference.
     */
    static boolean anyEntryHasImportReferences(final Policy policy) {
        for (final PolicyEntry entry : policy) {
            for (final EntryReference ref : entry.getReferences()) {
                if (ref.isImportReference()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Finds the first entry that has a local reference pointing to the given label.
     *
     * @param policy the policy to check.
     * @param label the label to look for in local references.
     * @return an Optional containing the label of the first entry that locally references the given label,
     *         or empty if none.
     */
    static Optional<Label> findEntryWithLocalReferenceTo(final Policy policy, final Label label) {
        for (final PolicyEntry entry : policy) {
            for (final EntryReference ref : entry.getReferences()) {
                if (ref.isLocalReference() && ref.getEntryLabel().equals(label)) {
                    return Optional.of(entry.getLabel());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Validates the referential integrity of all entry references in the given policy entries.
     * <ul>
     *   <li>An entry must not reference itself.</li>
     *   <li>Duplicate references on the same entry are rejected.</li>
     *   <li>Local references must point to an entry that exists in {@code entries}.</li>
     *   <li>Local references to entries marked {@code importable=never} are rejected.</li>
     *   <li>Import references must point to an import declared in {@code policy}.</li>
     * </ul>
     *
     * @param policyId the policy ID (for error messages).
     * @param entries the entries to validate.
     * @param policy the policy providing the imports context.
     * @param dittoHeaders the headers of the originating command.
     * @param command the originating command.
     * @param <T> the event type.
     * @return an Optional containing an error result if validation fails, empty otherwise.
     */
    static <T extends PolicyEvent<?>> Optional<Result<T>> validateReferencesIntegrity(
            final PolicyId policyId,
            final Iterable<PolicyEntry> entries,
            final Policy policy,
            final DittoHeaders dittoHeaders,
            final Command<?> command) {

        final java.util.Map<Label, PolicyEntry> entriesByLabel = new java.util.LinkedHashMap<>();
        entries.forEach(e -> entriesByLabel.put(e.getLabel(), e));

        final Set<PolicyId> importIds = policy.getPolicyImports().stream()
                .map(PolicyImport::getImportedPolicyId)
                .collect(Collectors.toSet());

        for (final PolicyEntry entry : entries) {
            // Use the EntryReference itself as the dedup key — its equals/hashCode already compare
            // (importedPolicyId, entryLabel) as a structured pair, avoiding the string-concat
            // collision where a local label of "X:Y" would clash with import-ref to policy "X" entry "Y".
            final Set<EntryReference> seenRefs = new LinkedHashSet<>();
            for (final EntryReference ref : entry.getReferences()) {
                if (!seenRefs.add(ref)) {
                    return Optional.of(ResultFactory.newErrorResult(
                            policyEntryReferenceInvalid(policyId, entry.getLabel(),
                                    "Duplicate reference to entry '" + ref.getEntryLabel() + "'.",
                                    dittoHeaders),
                            command));
                }
                if (ref.isLocalReference() && ref.getEntryLabel().equals(entry.getLabel())) {
                    return Optional.of(ResultFactory.newErrorResult(
                            policyEntryReferenceInvalid(policyId, entry.getLabel(),
                                    "Entry must not reference itself.",
                                    dittoHeaders),
                            command));
                }
                if (ref.isLocalReference()) {
                    final PolicyEntry target = entriesByLabel.get(ref.getEntryLabel());
                    if (target == null) {
                        return Optional.of(ResultFactory.newErrorResult(
                                policyEntryReferenceInvalid(policyId, entry.getLabel(),
                                        "Local reference targets entry '" + ref.getEntryLabel() +
                                                "' which does not exist in the policy.",
                                        dittoHeaders),
                                command));
                    }
                    if (target.getImportableType() == ImportableType.NEVER) {
                        return Optional.of(ResultFactory.newErrorResult(
                                policyEntryReferenceInvalid(policyId, entry.getLabel(),
                                        "Local reference targets entry '" + ref.getEntryLabel() +
                                                "' which is marked importable=never and cannot be referenced.",
                                        dittoHeaders),
                                command));
                    }
                }
                if (ref.isImportReference()) {
                    final PolicyId refImport = ref.getImportedPolicyId().orElseThrow(() ->
                            new IllegalStateException("Import reference without imported policy ID"));
                    if (!importIds.contains(refImport)) {
                        return Optional.of(ResultFactory.newErrorResult(
                                policyEntryReferenceInvalid(policyId, entry.getLabel(),
                                        "Import reference targets policy '" + refImport +
                                                "' which is not declared in imports.",
                                        dittoHeaders),
                                command));
                    }
                }
            }
        }
        return Optional.empty();
    }

    // Sentinels for the three-tier allowedAdditions state. Hashed by EntityTag.fromEntity into the
    // ETag's opaque tag — distinct sentinels guarantee distinct ETags for distinct states. Used by
    // every strategy that touches /allowedAdditions so prev/next tags are consistent across
    // Modify, Retrieve, and Delete (and conditional reads/writes work end-to-end).
    private static final String ETAG_INPUT_ALLOWED_ADDITIONS_ABSENT =
            "policies:allowedAdditions:absent";
    private static final String ETAG_INPUT_ALLOWED_ADDITIONS_DENY_ALL =
            "policies:allowedAdditions:deny-all";

    /**
     * Returns an {@link EntityTag} for the {@code allowedAdditions} field of the entry on the given
     * policy. Distinguishes the three semantic tiers: absent (no restriction), present-empty
     * (deny all), and present-non-empty.
     */
    static Optional<EntityTag> allowedAdditionsEntityTag(@Nullable final Policy policy,
            final Label label) {
        return Optional.ofNullable(policy)
                .flatMap(p -> p.getEntryFor(label))
                .flatMap(entry -> EntityTag.fromEntity(
                        allowedAdditionsEtagInput(entry.getAllowedAdditions())));
    }

    /**
     * Returns an {@link EntityTag} for the value an upcoming Modify would write — same hashing
     * rules as {@link #allowedAdditionsEntityTag} so a PUT and a subsequent GET yield matching
     * ETags for the same state.
     */
    static Optional<EntityTag> allowedAdditionsEntityTagForModify(final Set<AllowedAddition> additions) {
        return EntityTag.fromEntity(allowedAdditionsEtagInput(Optional.of(additions)));
    }

    private static Object allowedAdditionsEtagInput(final Optional<Set<AllowedAddition>> additions) {
        if (additions.isEmpty()) {
            return ETAG_INPUT_ALLOWED_ADDITIONS_ABSENT;
        }
        final Set<AllowedAddition> set = additions.get();
        if (set.isEmpty()) {
            return ETAG_INPUT_ALLOWED_ADDITIONS_DENY_ALL;
        }
        return set;
    }

    /**
     * Rejects a label-filter narrowing on an import that would orphan an entry reference targeting
     * one of the dropped labels. Returns the conflict exception when an orphan would be created,
     * otherwise empty.
     *
     * @param policyId the policy whose import is being modified.
     * @param policy the current policy state (provides entries to scan for references).
     * @param importedPolicyId the imported policy whose label filter is being narrowed.
     * @param oldLabels labels currently in the import filter.
     * @param newLabels labels that will remain after the modification.
     * @param dittoHeaders the headers of the originating command.
     * @return optional conflict exception when a reference would be orphaned.
     */
    static Optional<DittoRuntimeException> checkLabelNarrowingDoesNotOrphan(final PolicyId policyId,
            final Policy policy,
            final PolicyId importedPolicyId,
            final Set<Label> oldLabels,
            final Set<Label> newLabels,
            final DittoHeaders dittoHeaders) {
        final Set<Label> removed = new LinkedHashSet<>(oldLabels);
        removed.removeAll(newLabels);
        if (removed.isEmpty()) {
            return Optional.empty();
        }
        for (final PolicyEntry entry : policy) {
            for (final EntryReference ref : entry.getReferences()) {
                if (ref.isImportReference() &&
                        importedPolicyId.equals(ref.getImportedPolicyId().orElse(null)) &&
                        removed.contains(ref.getEntryLabel())) {
                    return Optional.of(PolicyImportReferenceConflictException.newBuilder(policyId, importedPolicyId)
                            .description("Removing label '" + ref.getEntryLabel() +
                                    "' from this import's filter would orphan the reference " +
                                    "from entry '" + entry.getLabel() + "'.")
                            .dittoHeaders(dittoHeaders)
                            .build());
                }
            }
        }
        return Optional.empty();
    }

    static DittoRuntimeException policyEntryNotFound(final PolicyId policyId, final Label label,
            final DittoHeaders dittoHeaders) {
        return PolicyEntryNotAccessibleException.newBuilder(policyId, label).dittoHeaders(dittoHeaders).build();
    }

    static DittoRuntimeException policyImportsNotFound(final PolicyId policyId, final DittoHeaders dittoHeaders) {
        return PolicyImportsNotAccessibleException.newBuilder(policyId)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    static DittoRuntimeException policyImportNotFound(final PolicyId policyId, final PolicyId importedPolicyId,
            final DittoHeaders dittoHeaders) {
        return PolicyImportNotAccessibleException.newBuilder(policyId, importedPolicyId)
                .dittoHeaders(dittoHeaders)
                .build();
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

    static DittoRuntimeException policyEntryReferenceInvalid(final PolicyId policyId, final Label label,
            final String description, final DittoHeaders dittoHeaders) {
        return PolicyEntryInvalidException.newBuilder()
                .message("The references of PolicyEntry '" + label + "' on Policy '" + policyId + "' are invalid.")
                .description(description)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    private static Duration roundUpDuration(final Duration duration, final Duration granularity) {
        final long granularityMillis = Math.max(1L, granularity.toMillis());
        final long durationMillis = Math.max(granularityMillis, duration.toMillis());
        final long roundedUpMillis =
                ((durationMillis + granularityMillis - 1L) / granularityMillis) * granularityMillis;

        return Duration.ofMillis(roundedUpMillis);
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
