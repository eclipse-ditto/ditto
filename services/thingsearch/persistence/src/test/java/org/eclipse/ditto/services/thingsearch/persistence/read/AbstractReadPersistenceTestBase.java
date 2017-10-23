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
package org.eclipse.ditto.services.thingsearch.persistence.read;

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.THINGS_COLLECTION_NAME;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.bson.Document;
import org.eclipse.ditto.services.thingsearch.persistence.AbstractThingSearchPersistenceTestBase;
import org.eclipse.ditto.services.thingsearch.persistence.read.document.PolicyDocumentBuilder;
import org.eclipse.ditto.services.thingsearch.persistence.util.BlockingSingleResultCallback;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.eclipse.ditto.services.thingsearch.persistence.read.document.ThingDocumentBuilder;
import org.junit.Before;


/**
 * Abstract base class for search persistence tests.
 */
public abstract class AbstractReadPersistenceTestBase extends AbstractThingSearchPersistenceTestBase {

    protected MongoSearchTestDataPersistence testdataPersistence;

    @Before
    public void initTestDataPersistence() {
        testdataPersistence = new MongoSearchTestDataPersistence(mongoClient);
    }

    protected void insertDocs(final Iterable<Document> documents) {
        documents.forEach(this::insertDoc);
    }

    protected void insertDocs(final Document firstDocument, final Document... furtherDocuments) {
        final Collection<Document> allDocuments = new ArrayList<>(1 + furtherDocuments.length);
        allDocuments.add(firstDocument);
        Collections.addAll(allDocuments, furtherDocuments);
        insertDocs(allDocuments);
    }

    protected void insertDoc(final Document doc) {
        final BlockingSingleResultCallback<Void> callback = new BlockingSingleResultCallback<>();
        testdataPersistence.insert(doc, callback, actorMaterializer, THINGS_COLLECTION_NAME);
        callback.get();
    }

    protected void insertPolicyEntry(final String id, final String resource) {
        final PolicyDocumentBuilder policyBuilder = new PolicyDocumentBuilder();
        final Document policyEntry1 = policyBuilder.policyIndexId(id).resource(resource).readSubjects(KNOWN_SUBJECTS)
                .revokedReadSubjects(KNOWN_REVOKED_SUBJECTS)
                .build();
        insertPolicyDoc(policyEntry1);
    }

    protected void insertPolicyEntry(final String id, final String resource, final List<String> subjects) {
        final PolicyDocumentBuilder policyBuilder = new PolicyDocumentBuilder();
        final Document policyEntry1 = policyBuilder.policyIndexId(id)
                .resource(resource)
                .readSubjects(subjects)
                .build();
        insertPolicyDoc(policyEntry1);
    }

    protected void insertPolicyDoc(final Document doc) {
        final BlockingSingleResultCallback<Void> callback = new BlockingSingleResultCallback<>();
        testdataPersistence.insert(doc, callback, actorMaterializer,
                PersistenceConstants.POLICIES_BASED_SEARCH_INDEX_COLLECTION_NAME);
        callback.get();
    }

    protected ThingDocumentBuilder buildDocV1WithAcl(final String thingId) {
        return buildDocV1WithAcl(thingId, KNOWN_SUBJECTS);
    }

    protected ThingDocumentBuilder buildDocV1WithAcl(final String thingId, Collection<String> subjects) {
        final ThingDocumentBuilder builder = ThingDocumentBuilder.create(thingId);
        subjects.forEach(builder::aclReadEntry);
        return builder;
    }

    protected ThingDocumentBuilder buildDocV2WithGlobalReads(final String thingId) {
        final ThingDocumentBuilder thingDocumentBuilder = ThingDocumentBuilder.create(thingId);
        return thingDocumentBuilder.globalReads(KNOWN_SUBJECTS);
    }

    protected ThingDocumentBuilder buildDocV2WithGlobalReads(final String thingId, Collection<String> subjects) {
        final ThingDocumentBuilder thingDocumentBuilder = ThingDocumentBuilder.create(thingId);
        return thingDocumentBuilder.globalReads(subjects);
    }

}
