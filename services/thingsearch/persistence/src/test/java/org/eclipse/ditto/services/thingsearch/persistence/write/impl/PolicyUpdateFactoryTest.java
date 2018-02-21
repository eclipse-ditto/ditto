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

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ID;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.REGEX_START_THING_ID;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bson.BsonRegularExpression;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.model.policies.EffectedPermissions;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyBuilder;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.Resources;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policies.Subjects;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcers;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.eclipse.ditto.services.thingsearch.persistence.TestConstants;
import org.eclipse.ditto.services.thingsearch.persistence.ThingResourceKey;
import org.eclipse.ditto.services.utils.persistence.mongo.assertions.BsonAssertions;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link PolicyUpdateFactory}.
 */
public final class PolicyUpdateFactoryTest {

    private static final Set<String> READ_PERMISSIONS = Collections.singleton(Permission.READ);
    private static final Set<String> WRITE_PERMISSIONS = Collections.singleton(Permission.WRITE);
    private static final Set<String> NO_PERMISSIONS = Collections.emptySet();
    private static final ResourceKey THING_ATTRIBUTES_LOCATION_RESOURCE_KEY =
            ResourceKey.newInstance(PoliciesResourceType.THING, "/attributes/location");
    private static Policy defaultPolicy;

    private PolicyEnforcer policyEnforcer;

    /** */
    @BeforeClass
    public static void setUpStatic() {
        defaultPolicy = PoliciesModelFactory.newPolicyBuilder(TestConstants.Thing.THING_ID)
                .forLabel("someLabel")
                .setSubject(TestConstants.Policy.SUBJECT)
                .setGrantedPermissions(ThingResourceKey.ROOT, Permission.READ)
                .build();
    }

    /** */
    @Before
    public void setUp() {
        policyEnforcer = PolicyEnforcers.defaultEvaluator(defaultPolicy);
    }

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(PolicyUpdateFactory.class, areImmutable());
    }

    /** */
    @Test
    public void createPolicyIndexUpdateForThingWithAttributesOnly() {
        final Thing thingWithAttributesOnly = TestConstants.Thing.THING.toBuilder()
                .removeAllFeatures()
                .build();


        final PolicyUpdate policyUpdate =
                PolicyUpdateFactory.createPolicyIndexUpdate(thingWithAttributesOnly, policyEnforcer);

        final Set<Document> expectedPolicyDocs = toSet(
                createPolicyIndexDoc("attribute/location/latitude", toSubjectIdsSet(TestConstants.Policy.SUBJECT),
                        Collections.emptySet()),
                createPolicyIndexDoc("attribute/location/longitude", toSubjectIdsSet(TestConstants.Policy.SUBJECT),
                        Collections.emptySet()),
                createPolicyIndexDoc("attribute/manufacturer", toSubjectIdsSet(TestConstants.Policy.SUBJECT),
                        Collections.emptySet())
        );
        assertPolicyUpdate(policyUpdate, expectedPolicyDocs, createPushGlobalReadsBson(TestConstants.Policy.SUBJECT));
    }

    @Test
    public void createPolicyIndexUpdateForThingWithFeaturesOnly() {
        final Thing thingWithFeaturesOnly = TestConstants.Thing.THING.toBuilder()
                .removeAllAttributes()
                .build();


        final PolicyUpdate policyUpdate =
                PolicyUpdateFactory.createPolicyIndexUpdate(thingWithFeaturesOnly, policyEnforcer);

        final Set<Document> expectedPolicyDocs = toSet(
                createPolicyIndexDoc("FluxCapacitor", "features/FluxCapacitor",
                        toSubjectIdsSet(TestConstants.Policy.SUBJECT), Collections.emptySet()),
                createPolicyIndexDoc("FluxCapacitorfeatures/properties/target_year_1",
                        "features/properties/target_year_1", toSubjectIdsSet(TestConstants.Policy.SUBJECT),
                        Collections.emptySet()),
                createPolicyIndexDoc("FluxCapacitorfeatures/properties/target_year_2",
                        "features/properties/target_year_2", toSubjectIdsSet(TestConstants.Policy.SUBJECT),
                        Collections.emptySet()),
                createPolicyIndexDoc("FluxCapacitorfeatures/properties/target_year_3",
                        "features/properties/target_year_3", toSubjectIdsSet(TestConstants.Policy.SUBJECT),
                        Collections.emptySet())
        );
        assertPolicyUpdate(policyUpdate, expectedPolicyDocs, createPushGlobalReadsBson(TestConstants.Policy.SUBJECT));
    }

    @Test
    public void createPolicyIndexUpdateForThingWithComplexPolicyCreatesExpectedUpdate() {
        final Thing thingWithAttributesOnly = TestConstants.Thing.THING.toBuilder()
                .removeAllFeatures()
                .build();

        final PolicyBuilder complexPolicyBuilder = Policy.newBuilder(TestConstants.Thing.POLICY_ID);

        final Subject subjectWithRootGrantAndRevoke = createSubject("withRootGrantAndRevoke");
        complexPolicyBuilder.set(createPolicyEntry(ThingResourceKey.ROOT, subjectWithRootGrantAndRevoke,
                READ_PERMISSIONS, READ_PERMISSIONS));

        final Subject subjectWithRootGrantAndAttributesRevoke = createSubject("withRootGrantAndAttributesRevoke");
        complexPolicyBuilder.set(createPolicyEntry(ThingResourceKey.ROOT, subjectWithRootGrantAndAttributesRevoke,
                READ_PERMISSIONS, NO_PERMISSIONS));
        complexPolicyBuilder.set(createPolicyEntry(ThingResourceKey.ATTRIBUTES, subjectWithRootGrantAndAttributesRevoke,
                NO_PERMISSIONS, READ_PERMISSIONS));

        final Subject subjectWithRootRevokeAndAttributesGrant = createSubject("withRootRevokeAndAttributesGrant");
        complexPolicyBuilder.set(createPolicyEntry(ThingResourceKey.ROOT, subjectWithRootRevokeAndAttributesGrant,
                NO_PERMISSIONS, READ_PERMISSIONS));
        complexPolicyBuilder.set(createPolicyEntry(ThingResourceKey.ATTRIBUTES, subjectWithRootRevokeAndAttributesGrant,
                READ_PERMISSIONS, NO_PERMISSIONS));

        final Subject subjectWithAttributesGrantAndLocationRevoke = createSubject
                ("withAttributesGrantAndLocationRevoke");
        complexPolicyBuilder.set(
                createPolicyEntry(ThingResourceKey.ATTRIBUTES, subjectWithAttributesGrantAndLocationRevoke,
                        READ_PERMISSIONS, NO_PERMISSIONS));
        complexPolicyBuilder.set(createPolicyEntry(THING_ATTRIBUTES_LOCATION_RESOURCE_KEY,
                subjectWithAttributesGrantAndLocationRevoke, NO_PERMISSIONS, READ_PERMISSIONS));

        final Subject subjectWithAttributesRevokeAndLocationGrant = createSubject
                ("withAttributesRevokeAndLocationGrant");
        complexPolicyBuilder.set(
                createPolicyEntry(ThingResourceKey.ATTRIBUTES, subjectWithAttributesRevokeAndLocationGrant,
                        NO_PERMISSIONS, READ_PERMISSIONS));
        complexPolicyBuilder.set(createPolicyEntry(THING_ATTRIBUTES_LOCATION_RESOURCE_KEY,
                subjectWithAttributesRevokeAndLocationGrant, READ_PERMISSIONS, NO_PERMISSIONS));

        final Subject subjectWithGrantOnThingId = createSubject("withGrantOnThingId");
        complexPolicyBuilder.set(createPolicyEntry(ThingResourceKey.THING_ID, subjectWithGrantOnThingId,
                READ_PERMISSIONS, NO_PERMISSIONS));

        final Subject subjectWithGrantOnPolicyId = createSubject("withGrantOnPolicyId");
        complexPolicyBuilder.set(createPolicyEntry(ThingResourceKey.POLICY_ID, subjectWithGrantOnPolicyId,
                READ_PERMISSIONS, NO_PERMISSIONS));

        final Subject subjectWithGrantOnFeatures = createSubject("withGrantOnFeatures");
        complexPolicyBuilder.set(createPolicyEntry(ThingResourceKey.FEATURES, subjectWithGrantOnFeatures,
                READ_PERMISSIONS, NO_PERMISSIONS));

        final ResourceKey resourceKeyForUndefinedField = ResourceKey.newInstance(PoliciesResourceType.THING,
                "/undefined");
        final Subject subjectWithGrantOnUndefinedField = createSubject("withGrantOnUndefinedField");
        complexPolicyBuilder.set(createPolicyEntry(resourceKeyForUndefinedField, subjectWithGrantOnUndefinedField,
                READ_PERMISSIONS, NO_PERMISSIONS));

        final Subject subjectWithWriteGrantOnAttributes = createSubject("withWriteGrantsOnAttributes");
        complexPolicyBuilder.set(createPolicyEntry(ThingResourceKey.ATTRIBUTES, subjectWithWriteGrantOnAttributes,
                WRITE_PERMISSIONS, NO_PERMISSIONS));

        final Policy complexPolicy = complexPolicyBuilder.build();
        final PolicyEnforcer complexPolicyEnforcer = PolicyEnforcers.defaultEvaluator(complexPolicy);
        final PolicyUpdate policyUpdate =
                PolicyUpdateFactory.createPolicyIndexUpdate(thingWithAttributesOnly, complexPolicyEnforcer);

        final Set<Document> expectedPolicyDocs = toSet(
                createPolicyIndexDoc("attribute/location/latitude", toSubjectIdsSet
                        (subjectWithRootRevokeAndAttributesGrant, subjectWithAttributesRevokeAndLocationGrant),
                        toSubjectIdsSet(subjectWithRootGrantAndRevoke, subjectWithRootGrantAndAttributesRevoke,
                                subjectWithAttributesGrantAndLocationRevoke)),
                createPolicyIndexDoc("attribute/location/longitude", toSubjectIdsSet
                        (subjectWithRootRevokeAndAttributesGrant, subjectWithAttributesRevokeAndLocationGrant),
                        toSubjectIdsSet(subjectWithRootGrantAndRevoke, subjectWithRootGrantAndAttributesRevoke,
                                subjectWithAttributesGrantAndLocationRevoke)),
                createPolicyIndexDoc("attribute/manufacturer", toSubjectIdsSet
                        (subjectWithRootRevokeAndAttributesGrant, subjectWithAttributesGrantAndLocationRevoke),
                        toSubjectIdsSet(subjectWithRootGrantAndRevoke, subjectWithRootGrantAndAttributesRevoke,
                                subjectWithAttributesRevokeAndLocationGrant))
        );
        final Subject[] expectedGlobalReadsSubjects = {subjectWithRootGrantAndAttributesRevoke,
                subjectWithRootRevokeAndAttributesGrant, subjectWithAttributesGrantAndLocationRevoke,
                subjectWithAttributesRevokeAndLocationGrant, subjectWithGrantOnThingId,
                subjectWithGrantOnPolicyId, subjectWithGrantOnFeatures,
                subjectWithGrantOnUndefinedField};
        assertPolicyUpdate(policyUpdate, expectedPolicyDocs, createPushGlobalReadsBson(expectedGlobalReadsSubjects));
    }

    private static PolicyEntry createPolicyEntry(final ResourceKey rootResourceKey,
            final Subject subject,
            final Iterable<String> granted,
            final Iterable<String> revoked) {

        return PolicyEntry.newInstance(UUID.randomUUID().toString(), Subjects.newInstance(subject),
                Resources.newInstance(Resource.newInstance(rootResourceKey,
                        EffectedPermissions.newInstance(granted, revoked))));
    }

    private static Subject createSubject(final CharSequence subjectIdWithoutIssuer) {
        return Subject.newInstance(SubjectId.newInstance(SubjectIssuer.GOOGLE, subjectIdWithoutIssuer));
    }

    private static void assertPolicyUpdate(final PolicyUpdate policyUpdate,
            final Collection<Document> expectedPolicyDocs, final Bson expectedPushGlobalReads) {

        BsonAssertions.assertThat(policyUpdate.getPolicyIndexInsertEntries()).isEqualTo(expectedPolicyDocs);

        final BsonRegularExpression startsWithThingIdRegex =
                new BsonRegularExpression(REGEX_START_THING_ID + Pattern.quote(TestConstants.Thing.THING_ID + ":"));
        final Document expectedIndexRemoveFilter = new Document().append(FIELD_ID, startsWithThingIdRegex);
        BsonAssertions.assertThat(policyUpdate.getPolicyIndexRemoveFilter()).isEqualTo(expectedIndexRemoveFilter);
        assertPushGlobalReads(expectedPushGlobalReads, policyUpdate.getPushGlobalReads());
        BsonAssertions.assertThat(policyUpdate.getPullGlobalReads()).isEqualTo(PolicyUpdateFactory.PULL_GLOBAL_READS);
        BsonAssertions.assertThat(policyUpdate.getPullAclEntries()).isEqualTo(PolicyUpdateFactory.PULL_ACL);
    }

    private static void assertPushGlobalReads(final Bson expected, final Bson actual) {
        // order does not matter for the global-reads documents, so it is a bit complicated to test this
        final String grDocsPath = "$push.__internal.$each";
        final Collection<Document> expectedGrDocs =
                org.eclipse.ditto.services.utils.persistence.mongo.BsonUtil.getValueByPath(expected, grDocsPath);
        final Collection<Document> actualGrDocs =
                org.eclipse.ditto.services.utils.persistence.mongo.BsonUtil.getValueByPath(actual, grDocsPath);

        BsonAssertions.assertThat(actualGrDocs).isEqualToInAnyOrder(expectedGrDocs);
    }

    private static Set<String> toSubjectIdsSet(final Subject... subjects) {
        return Stream.of(subjects)
                .map(Subject::getId)
                .map(SubjectId::toString)
                .collect(Collectors.toSet());
    }

    @SafeVarargs
    private static <T> Set<T> toSet(final T... objects) {
        final Set<T> result = new HashSet<>(objects.length);
        Collections.addAll(result, objects);
        return result;
    }

    private static Document createPolicyIndexDoc(final String resource, final Set<String> grants,
            final Set<String> revokes) {

        return createPolicyIndexDoc(resource, resource, grants, revokes);
    }

    private static Bson createPushGlobalReadsBson(final Subject... subjects) {
        final Set<String> subjectIds = toSubjectIdsSet(subjects);

        final Set<Document> globalReadsDocs = subjectIds.stream()
                .map(subjectId -> new Document(PersistenceConstants.FIELD_GLOBAL_READS, subjectId))
                .collect(Collectors.toSet());
        return new Document(PersistenceConstants.PUSH,
                new Document(PersistenceConstants.FIELD_INTERNAL,
                        new Document(PersistenceConstants.EACH,
                                globalReadsDocs)));
    }

    private static Document createPolicyIndexDoc(final String idSuffix,
            final String resource,
            final Set<String> grants,
            final Set<String> revokes) {

        return new Document()
                .append(PersistenceConstants.FIELD_ID, TestConstants.Thing.THING_ID + ":" + idSuffix)
                .append(PersistenceConstants.FIELD_RESOURCE, resource)
                .append(PersistenceConstants.FIELD_GRANTED, grants)
                .append(PersistenceConstants.FIELD_REVOKED, revokes);
    }

}
