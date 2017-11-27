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
package org.eclipse.ditto.services.thingsearch.persistence.mapping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bson.Document;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Permission;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;


/**
 * Builder to create Thing {@link Document}s which are saved into the mongoDB search collection.
 */
final class ThingDocumentBuilder {

    private final Document tDocument;
    private final AttributesDocumentBuilder attributesBuilder;
    private final FeaturesDocumentBuilder featuresdocumentBuilder;
    private final List<Document> aclEntries;

    private ThingDocumentBuilder(final String thingId, final String namespace, final String policyId) {
        tDocument = new Document();
        tDocument.append(PersistenceConstants.FIELD_ID, thingId);
        tDocument.append(PersistenceConstants.FIELD_NAMESPACE, namespace);
        tDocument.append(PersistenceConstants.FIELD_POLICY_ID, policyId);
        aclEntries = new ArrayList<>();
        attributesBuilder = AttributesDocumentBuilder.create();
        featuresdocumentBuilder = FeaturesDocumentBuilder.create();
    }

    /**
     * Create a new ThingDocumentBuilder.
     *
     * @param thingId the ID of the thing.
     * @return the same instance of this builder to allow method chaining.
     */
    public static ThingDocumentBuilder create(final String thingId) {
        String namespace = "";
        if (thingId.contains(":")) {
            namespace = thingId.substring(0, thingId.indexOf(':'));
        }
        return new ThingDocumentBuilder(thingId, namespace, null);
    }

    /**
     * Create a new ThingDocumentBuilder.
     *
     * @param thingId the ID of the thing.
     * @param policyId the policy ID of the thing.
     * @return the same instance of this builder to allow method chaining.
     */
    public static ThingDocumentBuilder create(final String thingId, final String policyId) {
        String namespace = "";
        if (thingId.contains(":")) {
            namespace = thingId.substring(0, thingId.indexOf(':'));
        }
        return new ThingDocumentBuilder(thingId, namespace, policyId);
    }

    /**
     * Adds a map of attributes.
     *
     * @param attributes the attributes to add.
     * @return the same instance of this builder to allow method chaining.
     * @throws NullPointerException if {@code attributes} is {@code null}.
     */
    public ThingDocumentBuilder attributes(final Attributes attributes) {
        attributesBuilder.attributes(attributes);
        return this;
    }

    /**
     * Adds the given features.
     *
     * @param features the features to add.
     * @return the same instance of this builder to allow method chaining.
     * @throws NullPointerException if {@code features} is {@code null}.
     */
    public ThingDocumentBuilder features(final Features features) {
        featuresdocumentBuilder.features(features);
        return this;
    }

    /**
     * Adds an attribute.
     *
     * @param key the key of the attribute to add.
     * @param value the value of the attribute to add.
     * @return the same instance of this builder to allow method chaining.
     */
    public ThingDocumentBuilder attribute(final String key, final String value) {
        attributesBuilder.attribute(key, value);
        return this;
    }

    /**
     * Adds an attribute.
     *
     * @param key the key of the attribute to add.
     * @param value the value of the attribute to add.
     * @return the same instance of this builder to allow method chaining.
     */
    public ThingDocumentBuilder attribute(final String key, final Number value) {
        attributesBuilder.attribute(key, value);
        return this;
    }

    /**
     * Adds an attribute.
     *
     * @param key the key of the attribute to add.
     * @param value the value of the attribute to add.
     * @return the same instance of this builder to allow method chaining.
     */
    public ThingDocumentBuilder attribute(final String key, final Boolean value) {
        attributesBuilder.attribute(key, value);
        return this;
    }

    /**
     * Adds an ACL entry.
     *
     * @param sid the sid for the entry.
     * @return the same instance of this builder to allow method chaining.
     */
    public ThingDocumentBuilder aclReadEntry(final String sid) {
        aclEntries.add(new Document(PersistenceConstants.FIELD_ACL, sid));
        return this;
    }

    /**
     * Adds all sids which do have READ permission.
     *
     * @param acl the whole access control list.
     * @return the same instance of this builder to allow method chaining.
     */
    public ThingDocumentBuilder acl(final AccessControlList acl) {
        acl.getEntriesSet()
                .stream()
                .filter(entry -> entry.contains(Permission.READ))
                .map(AclEntry::getAuthorizationSubject)
                .map(AuthorizationSubject::getId)
                .forEach(this::aclReadEntry);
        return this;
    }

    /**
     * Returns the built document.
     *
     * @return the document.
     */
    @SuppressWarnings("unchecked")
    public Document build() {
        final Document attributes = attributesBuilder.build();
        final Document features = featuresdocumentBuilder.build();
        final Collection<Document> internal = (Collection<Document>) attributes.get(PersistenceConstants.FIELD_INTERNAL);
        addAclEntries(internal);
        addFeatureEntries(internal, (Collection<Document>) features.get(PersistenceConstants.FIELD_INTERNAL));
        tDocument.append(PersistenceConstants.FIELD_INTERNAL, internal);
        tDocument.append(PersistenceConstants.FIELD_ATTRIBUTES, attributes.get(PersistenceConstants.FIELD_ATTRIBUTES));
        tDocument.append(PersistenceConstants.FIELD_FEATURES, features.get(PersistenceConstants.FIELD_FEATURES));
        return tDocument;
    }

    private static void addFeatureEntries(final Collection<Document> internalList,
            final Collection<Document> featureList) {
        internalList.addAll(featureList);
    }

    private void addAclEntries(final Collection<Document> internal) {
        if (!aclEntries.isEmpty()) {
            internal.addAll(aclEntries);
        }
    }

}
