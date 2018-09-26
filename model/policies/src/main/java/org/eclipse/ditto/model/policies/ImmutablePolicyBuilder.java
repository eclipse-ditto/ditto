/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.policies;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * A mutable builder for a {@link ImmutablePolicy} with a fluent API.
 */
@NotThreadSafe
final class ImmutablePolicyBuilder implements PolicyBuilder {

    private final Map<Label, Map<SubjectId, Subject>> subjects;
    private final Map<Label, Map<ResourceKey, Permissions>> grantedPermissions;
    private final Map<Label, Map<ResourceKey, Permissions>> revokedPermissions;
    @Nullable private String id;
    @Nullable private PolicyLifecycle lifecycle;
    @Nullable private PolicyRevision revision;
    @Nullable private Instant modified;

    private ImmutablePolicyBuilder() {
        subjects = new LinkedHashMap<>();
        grantedPermissions = new LinkedHashMap<>();
        revokedPermissions = new LinkedHashMap<>();
        id = null;
        lifecycle = null;
        revision = null;
        modified = null;
    }

    /**
     * Returns a new empty builder for a {@code Policy}.
     *
     * @param id the ID of the new Policy.
     * @return the new builder.
     * @throws PolicyIdInvalidException if {@code policyId} did not comply to {@link Policy#ID_REGEX}.
     */
    public static ImmutablePolicyBuilder of(final CharSequence id) {
        return new ImmutablePolicyBuilder().setId(id);
    }

    /**
     * Returns a new builder for a {@code Policy} which is initialised with the given entries. Be aware: if there are
     * several entries with the same {@link Label} in the given Iterable, later entries will replace earlier ones.
     *
     * @param id the ID of the new Policy.
     * @param policyEntries the initials entries of the new builder.
     * @return the new builder.
     * @throws NullPointerException if {@code policyEntries} is null;
     * @throws PolicyIdInvalidException if {@code policyId} did not comply to {@link Policy#ID_REGEX}.
     */
    public static PolicyBuilder of(final CharSequence id, final Iterable<PolicyEntry> policyEntries) {
        checkNotNull(policyEntries, "initial Policy entries");

        final ImmutablePolicyBuilder result = new ImmutablePolicyBuilder();
        result.setId(id);
        policyEntries.forEach(result::set);

        return result;
    }

    /**
     * Returns a new builder for a {@code Policy} based on the given {@code existingPolicy}.
     *
     * @param existingPolicy the existing Policy to instantiate the builder with.
     * @return the new builder.
     * @throws NullPointerException if {@code existingPolicy} is {@code null}.
     * @throws PolicyIdInvalidException if {@code policyId} did not comply to {@link Policy#ID_REGEX}.
     */
    public static PolicyBuilder of(final Policy existingPolicy) {
        checkNotNull(existingPolicy, "existing Policy");

        @SuppressWarnings("ConstantConditions")
        final ImmutablePolicyBuilder result = new ImmutablePolicyBuilder()
                .setId(existingPolicy.getId().orElse(null))
                .setLifecycle(existingPolicy.getLifecycle().orElse(null))
                .setRevision(existingPolicy.getRevision().orElse(null))
                .setModified(existingPolicy.getModified().orElse(null));

        existingPolicy.forEach(result::set);

        return result;
    }

    @Override
    public LabelScoped forLabel(final Label label) {
        return ImmutablePolicyBuilderLabelScoped.newInstance(this, label);
    }

    @Override
    public ImmutablePolicyBuilder setId(final CharSequence id) {
        PolicyIdValidator.getInstance().accept(id, DittoHeaders.empty());
        this.id = String.valueOf(id);
        return this;
    }

    @Override
    public ImmutablePolicyBuilder setLifecycle(@Nullable final PolicyLifecycle lifecycle) {
        this.lifecycle = lifecycle;
        return this;
    }

    @Override
    public ImmutablePolicyBuilder setRevision(@Nullable final PolicyRevision revision) {
        this.revision = revision;
        return this;
    }

    @Override
    public ImmutablePolicyBuilder setModified(@Nullable final Instant modified) {
        this.modified = modified;
        return this;
    }

    @Override
    public ImmutablePolicyBuilder set(final PolicyEntry entry) {
        setPolicyEntry(checkNotNull(entry, "entry to be set"));
        return this;
    }

    private void setPolicyEntry(final PolicyEntry entry) {
        putAllSubjects(entry);

        final Label label = entry.getLabel();
        grantedPermissions.put(label, new LinkedHashMap<>());
        revokedPermissions.put(label, new LinkedHashMap<>());

        setResourcesFor(entry.getLabel(), entry.getResources());
    }

    private void putAllSubjects(final PolicyEntry policyEntry) {
        final Subjects entrySubjects = policyEntry.getSubjects();
        final Map<SubjectId, Subject> subjectsMap = new LinkedHashMap<>(entrySubjects.getSize());
        entrySubjects.forEach(entrySubject -> subjectsMap.put(entrySubject.getId(), entrySubject));

        subjects.put(policyEntry.getLabel(), subjectsMap);
    }

    @Override
    public ImmutablePolicyBuilder setAll(final Iterable<PolicyEntry> entries) {
        checkNotNull(entries, "entries to be set");
        entries.forEach(this::setPolicyEntry);
        return this;
    }

    @Override
    public ImmutablePolicyBuilder remove(final CharSequence label) {
        checkNotNull(label, "label of the entry to be removed");
        removePolicyEntryFor(Label.of(label));
        return this;
    }

    private void removePolicyEntryFor(final Label label) {
        subjects.remove(label);
        grantedPermissions.remove(label);
        revokedPermissions.remove(label);
    }

    @Override
    public ImmutablePolicyBuilder removeAll(final Iterable<PolicyEntry> entries) {
        checkNotNull(entries, "entries to be removed");
        entries.forEach(this::remove);
        return this;
    }

    @Override
    public ImmutablePolicyBuilder setSubjectsFor(final CharSequence label, final Subjects subjects) {
        checkNotNull(subjects, "Subjects to be set");

        final Map<SubjectId, Subject> existingSubject = retrieveExistingSubjects(label);
        subjects.forEach(subject -> existingSubject.put(subject.getId(), subject));
        return this;
    }

    private Map<SubjectId, Subject> retrieveExistingSubjects(final CharSequence label) {
        return subjects.computeIfAbsent(Label.of(label), l -> new LinkedHashMap<>());
    }

    @Override
    public ImmutablePolicyBuilder setSubjectFor(final CharSequence label, final Subject subject) {
        checkNotNull(subject, "Subject to be set");

        final Map<SubjectId, Subject> existingSubject = retrieveExistingSubjects(label);
        existingSubject.put(subject.getId(), subject);

        return this;
    }

    @Override
    public ImmutablePolicyBuilder removeSubjectFor(final CharSequence label,
            final CharSequence subjectIssuerWithId) {

        final Map<SubjectId, Subject> existingSubject = retrieveExistingSubjects(label);
        existingSubject.remove(SubjectId.newInstance(subjectIssuerWithId));
        return this;
    }

    @Override
    public ImmutablePolicyBuilder removeSubjectFor(final CharSequence label, final Subject subject) {
        checkNotNull(subject, "Subject");

        final Map<SubjectId, Subject> existingSubject = retrieveExistingSubjects(label);
        existingSubject.remove(subject.getId());

        return this;
    }

    @Override
    public ImmutablePolicyBuilder setResourcesFor(final CharSequence label, final Resources resources) {
        checkNotNull(resources, "Resources to be set");

        final Map<ResourceKey, Permissions> grantedMap = retrieveGrantedPermissions(label);
        final Map<ResourceKey, Permissions> revokedMap = retrieveRevokedPermissions(label);
        resources.forEach(resource -> {
            final ResourceKey resourceKey = resource.getResourceKey();
            final EffectedPermissions effectedPermissions = resource.getEffectedPermissions();
            grantedMap.put(resourceKey, effectedPermissions.getGrantedPermissions());
            revokedMap.put(resourceKey, effectedPermissions.getRevokedPermissions());
        });
        return this;
    }

    private Map<ResourceKey, Permissions> retrieveGrantedPermissions(final CharSequence label) {
        return getPermissions(label, grantedPermissions);
    }

    private static Map<ResourceKey, Permissions> getPermissions(final CharSequence l,
            final Map<Label, Map<ResourceKey, Permissions>> permissionsMap) {

        return permissionsMap.computeIfAbsent(Label.of(l), k -> new LinkedHashMap<>());
    }

    private Map<ResourceKey, Permissions> retrieveRevokedPermissions(final CharSequence label) {
        return getPermissions(label, revokedPermissions);
    }

    @Override
    public ImmutablePolicyBuilder setResourceFor(final CharSequence label, final Resource resource) {
        return setResourcesFor(label, Resources.newInstance(resource));
    }

    @Override
    public ImmutablePolicyBuilder removeResourceFor(final CharSequence label, final ResourceKey resourceKey) {
        checkNotNull(resourceKey, "key of the resource to be removed");
        retrieveGrantedPermissions(label).remove(resourceKey);
        retrieveRevokedPermissions(label).remove(resourceKey);
        return this;
    }

    @Override
    public ImmutablePolicyBuilder removeResourceFor(final CharSequence label, final Resource resource) {
        checkNotNull(resource, "the resource to be removed");
        return removeResourceFor(label, resource.getResourceKey());
    }

    @Override
    public ImmutablePolicyBuilder setPermissionsFor(final CharSequence label, final ResourceKey resourceKey,
            final EffectedPermissions effectedPermissions) {

        checkResourceKey(resourceKey);
        checkNotNull(effectedPermissions, "permissions to be set");

        retrieveGrantedPermissions(label).put(resourceKey, effectedPermissions.getGrantedPermissions());
        retrieveRevokedPermissions(label).put(resourceKey, effectedPermissions.getRevokedPermissions());
        return this;
    }

    private static void checkResourceKey(final ResourceKey resourceKey) {
        checkNotNull(resourceKey, "resource key");
    }

    @Override
    public ImmutablePolicyBuilder setGrantedPermissionsFor(final CharSequence label, final ResourceKey resourceKey,
            final Permissions grantedPermissions) {

        checkResourceKey(resourceKey);
        checkNotNull(revokedPermissions, "granted permissions");
        retrieveGrantedPermissions(label).put(resourceKey, grantedPermissions);
        return this;
    }

    @Override
    public ImmutablePolicyBuilder setRevokedPermissionsFor(final CharSequence label, final ResourceKey resourceKey,
            final Permissions revokedPermissions) {

        checkResourceKey(resourceKey);
        checkNotNull(revokedPermissions, "revoked permissions");
        retrieveRevokedPermissions(label).put(resourceKey, revokedPermissions);
        return this;
    }

    @Override
    public Policy build() {
        final Collection<Label> allLabels = getAllLabels();

        final Collection<PolicyEntry> policyEntries = allLabels.stream()
                .map(lbl -> PoliciesModelFactory.newPolicyEntry(lbl, getFinalSubjects(lbl), getFinalResources(lbl)))
                .collect(Collectors.toList());

        return ImmutablePolicy.of(id, lifecycle, revision, modified, policyEntries);
    }

    @Nonnull
    private Collection<Label> getAllLabels() {
        final Collection<Label> result = new LinkedHashSet<>(subjects.keySet());
        result.addAll(grantedPermissions.keySet());
        result.addAll(revokedPermissions.keySet());
        return result;
    }

    @Nonnull
    private Subjects getFinalSubjects(final CharSequence label) {
        return PoliciesModelFactory.newSubjects(retrieveExistingSubjects(label).values());
    }

    @Nonnull
    private Resources getFinalResources(final CharSequence label) {
        final Map<ResourceKey, Permissions> grantedMap = retrieveGrantedPermissions(label);
        final Map<ResourceKey, Permissions> revokedMap = retrieveRevokedPermissions(label);

        final Collection<ResourceKey> allResourceKeys = new LinkedHashSet<>(grantedMap.keySet());
        allResourceKeys.addAll(revokedMap.keySet());

        final Collection<Resource> resourcesList = allResourceKeys.stream()
                .map(resourceKey -> {
                    final Permissions granted = grantedMap.get(resourceKey);
                    final Permissions revoked = revokedMap.get(resourceKey);
                    final EffectedPermissions ep = PoliciesModelFactory.newEffectedPermissions(granted, revoked);
                    return PoliciesModelFactory.newResource(resourceKey, ep);
                })
                .collect(Collectors.toList());

        return PoliciesModelFactory.newResources(resourcesList);
    }

}
