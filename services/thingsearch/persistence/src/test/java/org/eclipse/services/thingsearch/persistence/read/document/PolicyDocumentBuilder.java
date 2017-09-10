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
package org.eclipse.services.thingsearch.persistence.read.document;

import java.util.List;

import org.bson.Document;

import org.eclipse.services.thingsearch.persistence.PersistenceConstants;

/**
 * Builder of policy documents in the policies search-index collection. Each document corresponds to only ONE resource.
 * A policy document is generated in the index for each attribute/feature of a Thing with policy.
 */
public final class PolicyDocumentBuilder {

    private String policyIndexId;
    private String resource;
    private List<String> readSubjects;
    private List<String> revokedReadSubjects;

    /**
     * Sets the policy ID.
     *
     * @param id the policy ID.
     * @return the builder.
     */
    public PolicyDocumentBuilder policyIndexId(final String id) {
        this.policyIndexId = id;
        return this;
    }

    /**
     * Sets the policy resource of this policy document.
     *
     * @param resource the resource.
     * @return the builder.
     */
    public PolicyDocumentBuilder resource(final String resource) {
        this.resource = resource;
        return this;
    }

    /**
     * Sets the granted read subjects.
     *
     * @param readSubjects the subjects with READ granted.
     * @return the builder.
     */
    public PolicyDocumentBuilder readSubjects(final List<String> readSubjects) {
        this.readSubjects = readSubjects;
        return this;
    }

    /**
     * Sets the subjects with READ revoked.
     *
     * @param revokedReadSubjects the subjects with READ revoked.
     * @return the builder.
     */
    public PolicyDocumentBuilder revokedReadSubjects(final List<String> revokedReadSubjects) {
        this.revokedReadSubjects = revokedReadSubjects;
        return this;
    }

    /**
     * Returns the built document.
     *
     * @return the document.
     */
    public Document build() {
        final Document document = new Document();
        document.append(PersistenceConstants.FIELD_ID, policyIndexId);
        document.append(PersistenceConstants.FIELD_RESOURCE, resource);
        document.append(PersistenceConstants.FIELD_GRANTED, readSubjects);
        document.append(PersistenceConstants.FIELD_REVOKED, revokedReadSubjects);
        return document;
    }
}
