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
 * This class combines the permissions on a particular attribute.
 */
@Immutable
final class AttributeResourcePermissions implements ResourcePermissions {

    private final ResourcePermissions baseResourcePermissions;

    private AttributeResourcePermissions(final ResourcePermissions theBaseResourcePermissions) {
        baseResourcePermissions = theBaseResourcePermissions;
    }

    /**
     * Returns an instance of {@code AttributeResourcePermissions}.
     *
     * @param attributePointer the absolute path to the attribute value.
     * @param attributeValue the value denoted by {@code attributePointer}.
     * @param policyEnforcer the policy enforcer is used to gain the IDs of subjects with grants on the attribute.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    @Nonnull
    static AttributeResourcePermissions getInstance(@Nonnull final JsonPointer attributePointer,
            @Nonnull final JsonValue attributeValue, @Nonnull final PolicyEnforcer policyEnforcer) {

        checkNotNull(attributePointer, "attribute pointer");
        checkNotNull(attributeValue, "attribute value");
        checkNotNull(policyEnforcer, "policy enforcer");

        final String attributePointerWithoutStartingSlash = toStringWithoutStartingSlash(attributePointer);
        final String resource =
                PersistenceConstants.FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH + attributePointerWithoutStartingSlash;
        final String path = PersistenceConstants.FIELD_ATTRIBUTES_WITH_PATH + attributePointerWithoutStartingSlash;
        final ResourceKey resourceKey = ResourceKey.newInstance(PoliciesResourceType.THING, path);
        final EffectedSubjectIds effectedSubjectIds =
                policyEnforcer.getSubjectIdsWithPermission(resourceKey, Permission.READ);
        final Set<String> readGrantedSubjectIds = effectedSubjectIds.getGranted();
        final Set<String> readRevokedSubjectIds = effectedSubjectIds.getRevoked();
        final BaseResourcePermissions baseResourcePermissions = new BaseResourcePermissions(resource,
                readGrantedSubjectIds, readRevokedSubjectIds);

        return new AttributeResourcePermissions(baseResourcePermissions);
    }

    private static String toStringWithoutStartingSlash(final JsonPointer jsonPointer) {
        final String s = jsonPointer.toString();
        if (s.startsWith(PersistenceConstants.SLASH) && !s.isEmpty()) {
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
        return baseResourcePermissions.createPolicyEntryId(thingId);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AttributeResourcePermissions that = (AttributeResourcePermissions) o;
        return Objects.equals(baseResourcePermissions, that.baseResourcePermissions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseResourcePermissions);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "baseResourcePermissions=" + baseResourcePermissions +
                "]";
    }

}
