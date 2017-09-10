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
package org.eclipse.ditto.model.policies;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * A mutable builder for a {@link ImmutablePolicy} with a fluent API scoped to a specified {@link Label}.
 */
@NotThreadSafe
final class ImmutablePolicyBuilderLabelScoped implements PolicyBuilder.LabelScoped {

    private final PolicyBuilder delegate;
    private final Label label;

    private ImmutablePolicyBuilderLabelScoped(final PolicyBuilder delegate, final Label label) {
        this.delegate = delegate;
        this.label = label;
    }

    /**
     * Returns a new empty builder for a {@code Policy} but scoped to the provided {@code label}.
     *
     * @return the new builder.
     */
    public static PolicyBuilder.LabelScoped newInstance(final PolicyBuilder delegate, final Label label) {
        return new ImmutablePolicyBuilderLabelScoped(checkNotNull(delegate, "delegate"), checkNotNull(label, "label"));
    }

    @Override
    public LabelScoped forLabel(final Label label) {
        return newInstance(delegate, label);
    }

    @Override
    public Label getLabel() {
        return label;
    }

    @Override
    public PolicyBuilder exitLabel() {
        return delegate;
    }

    @Override
    public ImmutablePolicyBuilderLabelScoped setId(@Nullable final CharSequence id) {
        delegate.setId(id);
        return this;
    }

    @Override
    public ImmutablePolicyBuilderLabelScoped setLifecycle(@Nullable final PolicyLifecycle lifecycle) {
        delegate.setLifecycle(lifecycle);
        return this;
    }

    @Override
    public ImmutablePolicyBuilderLabelScoped setRevision(@Nullable final PolicyRevision revision) {
        delegate.setRevision(revision);
        return this;
    }

    @Override
    public ImmutablePolicyBuilderLabelScoped setModified(@Nullable final Instant modified) {
        delegate.setModified(modified);
        return this;
    }

    @Override
    public ImmutablePolicyBuilderLabelScoped set(final PolicyEntry entry) {
        delegate.set(entry);
        return this;
    }

    @Override
    public ImmutablePolicyBuilderLabelScoped setAll(final Iterable<PolicyEntry> entries) {
        delegate.setAll(entries);
        return this;
    }

    @Override
    public ImmutablePolicyBuilderLabelScoped remove(final CharSequence label) {
        delegate.remove(label);
        return this;
    }

    @Override
    public ImmutablePolicyBuilderLabelScoped remove(final PolicyEntry entry) {
        delegate.remove(entry);
        return this;
    }

    @Override
    public ImmutablePolicyBuilderLabelScoped removeAll(final Iterable<PolicyEntry> entries) {
        delegate.removeAll(entries);
        return this;
    }

    @Override
    public ImmutablePolicyBuilderLabelScoped setSubjectsFor(final CharSequence label, final Subjects subjects) {
        delegate.setSubjectsFor(label, subjects);
        return this;
    }

    @Override
    public ImmutablePolicyBuilderLabelScoped setSubjectFor(final CharSequence label, final Subject subject) {
        delegate.setSubjectFor(label, subject);
        return this;
    }

    @Override
    public ImmutablePolicyBuilderLabelScoped removeSubjectFor(final CharSequence label, final CharSequence subjectId) {
        delegate.removeSubjectFor(label, subjectId);
        return this;
    }

    @Override
    public ImmutablePolicyBuilderLabelScoped removeSubjectFor(final CharSequence label, final Subject subject) {
        delegate.removeSubjectFor(label, subject);
        return this;
    }

    @Override
    public ImmutablePolicyBuilderLabelScoped setResourcesFor(final CharSequence label, final Resources resources) {
        delegate.setResourcesFor(label, resources);
        return this;
    }

    @Override
    public ImmutablePolicyBuilderLabelScoped setResourceFor(final CharSequence label, final Resource resource) {
        delegate.setResourceFor(label, resource);
        return this;
    }

    @Override
    public ImmutablePolicyBuilderLabelScoped removeResourceFor(final CharSequence label,
            final ResourceKey resourceKey) {

        delegate.removeResourceFor(label, resourceKey);
        return this;
    }

    @Override
    public ImmutablePolicyBuilderLabelScoped removeResourceFor(final CharSequence label, final Resource resource) {
        delegate.removeResourceFor(label, resource);
        return this;
    }

    @Override
    public ImmutablePolicyBuilderLabelScoped setPermissionsFor(final CharSequence label, final ResourceKey resourceKey,
            final EffectedPermissions effectedPermissions) {

        delegate.setPermissionsFor(label, resourceKey, effectedPermissions);
        return this;
    }

    @Override
    public ImmutablePolicyBuilderLabelScoped setGrantedPermissionsFor(final CharSequence label,
            final ResourceKey resourceKey, final Permissions grantedPermissions) {

        delegate.setGrantedPermissionsFor(label, resourceKey, grantedPermissions);
        return this;
    }

    @Override
    public ImmutablePolicyBuilderLabelScoped setGrantedPermissionsFor(final CharSequence label,
            final ResourceKey resourceKey,
            final String grantedPermission,
            final String... furtherGrantedPermissions) {

        delegate.setGrantedPermissionsFor(label, resourceKey, grantedPermission, furtherGrantedPermissions);
        return this;
    }

    @Override
    public ImmutablePolicyBuilderLabelScoped setRevokedPermissionsFor(final CharSequence label,
            final ResourceKey resourceKey, final Permissions revokedPermissions) {

        delegate.setRevokedPermissionsFor(label, resourceKey, revokedPermissions);
        return this;
    }

    @Override
    public ImmutablePolicyBuilderLabelScoped setRevokedPermissionsFor(final CharSequence label,
            final ResourceKey resourceKey,
            final String revokedPermission,
            final String... furtherRevokedPermissions) {

        delegate.setRevokedPermissionsFor(label, resourceKey, revokedPermission, furtherRevokedPermissions);
        return this;
    }

    @Override
    public Policy build() {
        return delegate.build();
    }

}
