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

import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.bson.Document;
import org.bson.conversions.Bson;

/**
 * Wraps information needed to perform update operations on the policies index collection as wel as updates on the
 * things search collection related to policies.
 */
@NotThreadSafe
final class PolicyUpdate {

    private final Bson policyIndexRemoveFilter;
    private final Set<Document> policyIndexInsertEntries;
    private final Bson pullGlobalReads;
    private final Bson pushGlobalReads;
    private final Bson pullAclEntries;

    /**
     * Constructs a new {@code PolicyUpdate} object.
     *
     * @param policyIndexRemoveFilter BSON containing the filter to remove entries in the policy index.
     * @param policyIndexInsertEntries list of BSON objects containing the inserts for the policy index.
     */
    PolicyUpdate(final Bson policyIndexRemoveFilter, final Set<Document> policyIndexInsertEntries) {
        this(policyIndexRemoveFilter, policyIndexInsertEntries, null, null, null);
    }

    /**
     * Constructs a new {@code PolicyUpdate} object.
     *
     * @param policyIndexRemoveFilter BSON containing the filter to remove entries in the policy index.
     * @param policyIndexInsertEntries list of BSON objects containing the inserts for the policy index.
     * @param pullGlobalReads pulls all global reads.
     * @param pushGlobalReads pushes new global reads.
     * @param pullAclEntries pulls all ACL entries.
     */
    PolicyUpdate(final Bson policyIndexRemoveFilter, final Set<Document> policyIndexInsertEntries,
            @Nullable final Bson pullGlobalReads, @Nullable final Bson pushGlobalReads,
            @Nullable final Bson pullAclEntries) {
        this.policyIndexRemoveFilter = policyIndexRemoveFilter;
        this.policyIndexInsertEntries = policyIndexInsertEntries;
        this.pullGlobalReads = pullGlobalReads;
        this.pushGlobalReads = pushGlobalReads;
        this.pullAclEntries = pullAclEntries;
    }

    Bson getPolicyIndexRemoveFilter() {
        return policyIndexRemoveFilter;
    }

    Set<Document> getPolicyIndexInsertEntries() {
        return policyIndexInsertEntries;
    }

    @Nullable
    Bson getPullGlobalReads() {
        return pullGlobalReads;
    }

    @Nullable
    Bson getPushGlobalReads() {
        return pushGlobalReads;
    }

    @Nullable
    Bson getPullAclEntries() {
        return pullAclEntries;
    }

    @SuppressWarnings("squid:S1067")
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PolicyUpdate that = (PolicyUpdate) o;
        return Objects.equals(policyIndexRemoveFilter, that.policyIndexRemoveFilter) &&
                Objects.equals(policyIndexInsertEntries, that.policyIndexInsertEntries) &&
                Objects.equals(pullGlobalReads, that.pullGlobalReads) &&
                Objects.equals(pushGlobalReads, that.pushGlobalReads) &&
                Objects.equals(pullAclEntries, that.pullAclEntries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(policyIndexRemoveFilter, policyIndexInsertEntries, pullGlobalReads, pushGlobalReads,
                pullAclEntries);
    }

}
