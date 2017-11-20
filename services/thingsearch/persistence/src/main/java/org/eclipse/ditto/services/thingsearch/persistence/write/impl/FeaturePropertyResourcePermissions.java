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

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policiesenforcers.EffectedSubjectIds;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;

/**
 * This class combines the permissions on a particular feature attribute.
 */
@Immutable
final class FeaturePropertyResourcePermissions implements ResourcePermissions {

    private final ResourcePermissions baseResourcePermissions;
    private final String featureId;

    private FeaturePropertyResourcePermissions(final ResourcePermissions theBaseResourcePermissions,
            final String theFeatureId) {
        baseResourcePermissions = theBaseResourcePermissions;
        featureId = theFeatureId;
    }

    /**
     * Returns an instance of {@code FeaturePropertyResourcePermissions} based on the specified parameters.
     *
     * @param featureId the ID of the parent feature of the property.
     * @param propertyPointer the full path to this property within its parent feature.
     * @param propertyValue the value of the property.
     * @param policyEnforcer the policy enforcer is used to gain the IDs of subjects with grants on the property.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code featureId} is empty.
     */
    @Nonnull
    static FeaturePropertyResourcePermissions getInstance(@Nonnull final CharSequence featureId,
            @Nonnull final JsonPointer propertyPointer, @Nonnull final JsonValue propertyValue,
            @Nonnull final PolicyEnforcer policyEnforcer) {
        argumentNotEmpty(featureId, "feature ID");
        checkNotNull(propertyPointer, "property pointer");
        checkNotNull(propertyValue, "property value");
        checkNotNull(policyEnforcer, "policy enforcer");

        final String propertyPointerWithoutStartingSlash = toStringWithoutStartingSlash(propertyPointer);
        final String resource =
                String.join(
                        PersistenceConstants.SLASH, PersistenceConstants.FIELD_FEATURES,
                        PersistenceConstants.FIELD_PROPERTIES, propertyPointerWithoutStartingSlash);
        final String path =
                String.join(PersistenceConstants.SLASH, PersistenceConstants.FIELD_FEATURES, featureId,
                        PersistenceConstants.FIELD_PROPERTIES, propertyPointerWithoutStartingSlash);
        final ResourceKey resourceKey = ResourceKey.newInstance(PoliciesResourceType.THING, path);
        final EffectedSubjectIds effectedSubjectIds =
                policyEnforcer.getSubjectIdsWithPermission(resourceKey, Permission.READ);
        final Set<String> readGrantedSubjectIds = effectedSubjectIds.getGranted();
        final Set<String> readRevokedSubjectIds = effectedSubjectIds.getRevoked();
        final BaseResourcePermissions baseResourcePermissions = new BaseResourcePermissions(resource,
                readGrantedSubjectIds, readRevokedSubjectIds);

        return new FeaturePropertyResourcePermissions(baseResourcePermissions, featureId.toString());
    }

    private static String toStringWithoutStartingSlash(final JsonPointer jsonPointer) {
        final String s = jsonPointer.toString();
        if (s.startsWith(PersistenceConstants.SLASH)) {
            return s.substring(1);
        }
        return s;
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
        return thingId + ":" + featureId + getResource();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FeaturePropertyResourcePermissions that = (FeaturePropertyResourcePermissions) o;
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
