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
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policiesenforcers.EffectedSubjectIds;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;

/**
 * This class combines the permissions on a particular features.
 */
@Immutable
final class FeatureResourcePermissions implements ResourcePermissions {

    private final ResourcePermissions baseResourcePermissions;
    private final String featureId;

    private FeatureResourcePermissions(final ResourcePermissions theBaseResourcePermissions,
            final String theFeatureId) {
        baseResourcePermissions = theBaseResourcePermissions;
        featureId = theFeatureId;
    }

    /**
     * Returns an instance of {@code FeatureResourcePermissions}.
     *
     * @param feature the feature with grants.
     * @param policyEnforcer the policy enforcer is used to gain the IDs of subjects with grants on the feature.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    @Nonnull
    static FeatureResourcePermissions getInstance(@Nonnull final Feature feature,
            @Nonnull final PolicyEnforcer policyEnforcer) {
        checkNotNull(feature, "feature");
        checkNotNull(policyEnforcer, "policy enforcer");

        final String resource = PersistenceConstants.FIELD_FEATURES_WITH_PATH + feature.getId();
        final ResourceKey resourceKey = ResourceKey.newInstance(PoliciesResourceType.THING, resource);
        final EffectedSubjectIds effectedSubjectIds =
                policyEnforcer.getSubjectIdsWithPermission(resourceKey, Permission.READ);
        final Set<String> readGrantedSubjectIds = effectedSubjectIds.getGranted();
        final Set<String> readRevokedSubjectIds = effectedSubjectIds.getRevoked();
        final BaseResourcePermissions baseResourcePermissions = new BaseResourcePermissions(resource,
                readGrantedSubjectIds, readRevokedSubjectIds);

        return new FeatureResourcePermissions(baseResourcePermissions, feature.getId());
    }

    @Nonnull
    @Override
    public String getResource() {
        return baseResourcePermissions.getResource();
    }

    @Nonnull
    @Override
    public Set<String> getReadGrantedSubjectIds() {
        return baseResourcePermissions.getReadGrantedSubjectIds();
    }

    @Nonnull
    @Override
    public Set<String> getReadRevokedSubjectIds() {
        return baseResourcePermissions.getReadRevokedSubjectIds();
    }

    @Nonnull
    @Override
    public String createPolicyEntryId(@Nonnull final CharSequence thingId) {
        argumentNotEmpty(thingId, "thing ID");
        return thingId + ":" + featureId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FeatureResourcePermissions that = (FeatureResourcePermissions) o;
        return Objects.equals(baseResourcePermissions, that.baseResourcePermissions) &&
                Objects.equals(featureId, that.featureId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseResourcePermissions, featureId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "baseResourcePermissions=" + baseResourcePermissions +
                ", featureId=" + featureId +
                "]";
    }

}
