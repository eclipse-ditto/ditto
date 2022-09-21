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
package org.eclipse.ditto.policies.model;

import java.time.Instant;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;

/**
 * Abstract implementation for a fully delegating label scoped policy builder.
 *
 * @since 2.4.0
 */
public abstract class AbstractPolicyBuilderLabelScoped implements PolicyBuilder.LabelScoped {

    private final PolicyBuilder delegate;
    private final Label label;

    protected AbstractPolicyBuilderLabelScoped(final PolicyBuilder delegate,
            final Label label) {
        this.delegate = delegate;
        this.label = label;
    }

    @Override
    public LabelScoped forLabel(final Label label) {
        return ImmutablePolicyBuilderLabelScoped.newInstance(delegate, label);
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
    public PolicyBuilder.LabelScoped setId(final PolicyId id) {
        delegate.setId(id);
        return this;
    }

    @Override
    public PolicyBuilder.LabelScoped setLifecycle(@Nullable final PolicyLifecycle lifecycle) {
        delegate.setLifecycle(lifecycle);
        return this;
    }

    @Override
    public PolicyBuilder.LabelScoped setRevision(@Nullable final PolicyRevision revision) {
        delegate.setRevision(revision);
        return this;
    }

    @Override
    public PolicyBuilder.LabelScoped setModified(@Nullable final Instant modified) {
        delegate.setModified(modified);
        return this;
    }

    @Override
    public PolicyBuilder.LabelScoped setCreated(@Nullable final Instant created) {
        delegate.setCreated(created);
        return this;
    }

    @Override
    public PolicyBuilder.LabelScoped setMetadata(@Nullable final Metadata metadata) {
        delegate.setMetadata(metadata);
        return this;
    }

    @Override
    public PolicyBuilder setPolicyImport(final PolicyImport policyImport) {
        delegate.setPolicyImport(policyImport);
        return this;
    }

    @Override
    public PolicyBuilder setPolicyImports(final PolicyImports imports) {
        delegate.setPolicyImports(imports);
        return this;
    }

    @Override
    public PolicyBuilder setImportableFor(final CharSequence label, final ImportableType importableType) {
        delegate.setImportableFor(label, importableType);
        return this;
    }

    @Override
    public PolicyBuilder.LabelScoped set(final PolicyEntry entry) {
        delegate.set(entry);
        return this;
    }

    @Override
    public PolicyBuilder.LabelScoped setAll(final Iterable<PolicyEntry> entries) {
        delegate.setAll(entries);
        return this;
    }

    @Override
    public PolicyBuilder.LabelScoped remove(final CharSequence label) {
        delegate.remove(label);
        return this;
    }

    @Override
    public PolicyBuilder.LabelScoped remove(final PolicyEntry entry) {
        delegate.remove(entry);
        return this;
    }

    @Override
    public PolicyBuilder.LabelScoped removeAll(final Iterable<PolicyEntry> entries) {
        delegate.removeAll(entries);
        return this;
    }

    @Override
    public PolicyBuilder.LabelScoped setSubjectsFor(final CharSequence label, final Subjects subjects) {
        delegate.setSubjectsFor(label, subjects);
        return this;
    }

    @Override
    public PolicyBuilder.LabelScoped setSubjectFor(final CharSequence label, final Subject subject) {
        delegate.setSubjectFor(label, subject);
        return this;
    }

    @Override
    public PolicyBuilder.LabelScoped removeSubjectFor(final CharSequence label, final CharSequence subjectId) {
        delegate.removeSubjectFor(label, subjectId);
        return this;
    }

    @Override
    public PolicyBuilder.LabelScoped removeSubjectFor(final CharSequence label, final Subject subject) {
        delegate.removeSubjectFor(label, subject);
        return this;
    }

    @Override
    public PolicyBuilder.LabelScoped setResourcesFor(final CharSequence label, final Resources resources) {
        delegate.setResourcesFor(label, resources);
        return this;
    }

    @Override
    public PolicyBuilder.LabelScoped setResourceFor(final CharSequence label, final Resource resource) {
        delegate.setResourceFor(label, resource);
        return this;
    }

    @Override
    public PolicyBuilder.LabelScoped removeResourceFor(final CharSequence label,
            final ResourceKey resourceKey) {

        delegate.removeResourceFor(label, resourceKey);
        return this;
    }

    @Override
    public PolicyBuilder.LabelScoped removeResourceFor(final CharSequence label, final Resource resource) {
        delegate.removeResourceFor(label, resource);
        return this;
    }

    @Override
    public PolicyBuilder.LabelScoped setPermissionsFor(final CharSequence label, final ResourceKey resourceKey,
            final EffectedPermissions effectedPermissions) {

        delegate.setPermissionsFor(label, resourceKey, effectedPermissions);
        return this;
    }

    @Override
    public PolicyBuilder.LabelScoped setGrantedPermissionsFor(final CharSequence label,
            final ResourceKey resourceKey, final Permissions grantedPermissions) {

        delegate.setGrantedPermissionsFor(label, resourceKey, grantedPermissions);
        return this;
    }

    @Override
    public PolicyBuilder.LabelScoped setGrantedPermissionsFor(final CharSequence label,
            final ResourceKey resourceKey,
            final String grantedPermission,
            final String... furtherGrantedPermissions) {

        delegate.setGrantedPermissionsFor(label, resourceKey, grantedPermission, furtherGrantedPermissions);
        return this;
    }

    @Override
    public PolicyBuilder.LabelScoped setRevokedPermissionsFor(final CharSequence label,
            final ResourceKey resourceKey, final Permissions revokedPermissions) {

        delegate.setRevokedPermissionsFor(label, resourceKey, revokedPermissions);
        return this;
    }

    @Override
    public PolicyBuilder.LabelScoped setRevokedPermissionsFor(final CharSequence label,
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
