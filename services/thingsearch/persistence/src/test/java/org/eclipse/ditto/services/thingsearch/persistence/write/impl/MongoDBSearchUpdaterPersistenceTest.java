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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.json.JsonFactory.newValue;
import static org.eclipse.ditto.model.base.auth.AuthorizationModelFactory.newAuthSubject;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.model.policies.Subjects;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcers;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.services.thingsearch.persistence.AbstractThingSearchPersistenceTestBase;
import org.eclipse.ditto.signals.events.things.AclEntryDeleted;
import org.eclipse.ditto.signals.events.things.AclEntryModified;
import org.eclipse.ditto.signals.events.things.AclModified;
import org.eclipse.ditto.signals.events.things.AttributeCreated;
import org.eclipse.ditto.signals.events.things.AttributeDeleted;
import org.eclipse.ditto.signals.events.things.AttributeModified;
import org.eclipse.ditto.signals.events.things.AttributesCreated;
import org.eclipse.ditto.signals.events.things.AttributesDeleted;
import org.eclipse.ditto.signals.events.things.FeatureCreated;
import org.eclipse.ditto.signals.events.things.FeatureDeleted;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesDeleted;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesModified;
import org.eclipse.ditto.signals.events.things.FeaturePropertyDeleted;
import org.eclipse.ditto.signals.events.things.FeaturePropertyModified;
import org.eclipse.ditto.signals.events.things.FeaturesDeleted;
import org.eclipse.ditto.signals.events.things.FeaturesModified;
import org.eclipse.ditto.signals.events.things.ThingCreated;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactoryImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.query.PolicyRestrictedSearchAggregation;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for search updater persistence.
 */
public final class MongoDBSearchUpdaterPersistenceTest extends AbstractThingSearchPersistenceTestBase {

    private static final String KNOWN_THING_ID = ":myThing1";
    private static final String KEY1 = "key1";
    private static final String KEY2 = "key2";
    private static final String KEY3 = "key3";
    private static final String KEY4 = "key4";
    private static final String KEY5 = "key5";
    private static final String NULL_ATTRIBUTE = "nullAttribute";
    private static final String PROP1 = "prop1";
    private static final String PROP1_EXTENDED = "prop1xyz";
    private static final String PROP2 = "prop2";
    private static final String PROP3 = "prop3";
    private static final String PROP4 = "prop4";
    private static final String PROP5 = "prop5";
    private static final String PROP6 = "prop6";
    private static final String PROP7 = "prop7";
    private static final String PROP8 = "prop8";
    private static final String PROP_VALUE1 = "propval1";
    private static final String PROP_VALUE2 = "propval2";
    private static final Integer PROP_VALUE3 = 42;
    private static final Boolean PROP_VALUE4 = false;
    private static final String PROP_VALUE5 = "propval5";
    private static final String PROP_VALUE6 = "propval6";
    private static final String PROP_VALUE8 = "propval8";
    private static final String DOTTED_PROP = "dotted.property";
    private static final String VALUE1 = "value1";
    private static final Integer VALUE2 = 5;
    private static final Integer NEW_VALUE_2 = 10;
    private static final Boolean VALUE3 = true;
    private static final String VALUE4 = null;
    private static final Double VALUE5 = 123.45;
    private static final String FEATURE_ID1 = "feature1";
    private static final String FEATURE_ID2 = "feature2";
    private static final String FEATURE_WITH_DOTS = "feature.with.dots";
    private static final List<String> DEFAULT_POLICY_SUBJECTS = Collections.singletonList("iot-things:mySid");

    private final ThingsFieldExpressionFactory fef = new ThingsFieldExpressionFactoryImpl();

    private PolicyEnforcer policyEnforcer;

    /** */
    @Before
    public void setUp() {
        policyEnforcer = PolicyEnforcers.defaultEvaluator(createPolicy1());
    }

    /** */
    @Test
    public void insertAndExistsV1() throws ExecutionException, InterruptedException {
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 0, 0));

        exists(KNOWN_THING_ID);
    }

    /** */
    @Test
    public void insertAndExistsWithDotsV1() throws ExecutionException, InterruptedException {

        final Thing dottedThing = addDottedAttributesFeaturesAndProperties(createThing(KNOWN_THING_ID, VALUE1));
        runBlocking(writePersistence.insertOrUpdate(dottedThing, 0, 0));

        exists(KNOWN_THING_ID);
    }

    /** */
    @Test
    public void insertAndExistsV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 0, 0, policyEnforcer));

        exists(KNOWN_THING_ID);
    }

    /** */
    @Test
    public void insertAndExistsWithDotsV2() throws ExecutionException, InterruptedException {
        final Thing thing = addDottedAttributesFeaturesAndProperties(createThingV2(KNOWN_THING_ID, VALUE1));
        runBlocking(writePersistence.insertOrUpdate(thing, 0, 0, policyEnforcer));

        exists(KNOWN_THING_ID);
    }

    private static Thing addDottedAttributesFeaturesAndProperties(final Thing thing) {
        return thing
                .setAttribute("attribute.with.dots", "some.value")
                .setFeatureProperty("feature.with.dots", "prop.with.dots", "some.more.dots");
    }

    private void exists(final String knownThingId) {
        final PolicyRestrictedSearchAggregation aggregation =
                abf.newBuilder(cf.any()).authorizationSubjects(KNOWN_SUBJECTS_2).build();
        final List<String> foundAll = findAll(aggregation);
        assertThat(foundAll).contains(knownThingId);
    }

    /** */
    @Test
    public void insertWithSameRevision() throws ExecutionException, InterruptedException {
        Assertions.assertThat(runBlockingWithReturn(writePersistence.insertOrUpdate(createThing(KNOWN_THING_ID, VALUE1), 2, 0)))
                .isTrue();
        Assertions.assertThat(runBlockingWithReturn(
                writePersistence.insertOrUpdate(createThing(KNOWN_THING_ID, "anotherValue"), 2, 0))).isFalse();
    }

    /** */
    @Test
    public void insertWithHigherRevisionV1() throws ExecutionException, InterruptedException {
        insertOrUpdateThing(createThing(KNOWN_THING_ID, VALUE1), 2, 0);
        insertOrUpdateThing(createThing(KNOWN_THING_ID, "anotherValue"), 3, 0);

        insertWithHigherRevision();
    }

    /** */
    @Test
    public void insertWithHigherRevisionV2() throws ExecutionException, InterruptedException {
        runBlocking(writePersistence.insertOrUpdate(createThingV2(KNOWN_THING_ID, VALUE1), 2, 0, policyEnforcer));
        runBlocking(
                writePersistence.insertOrUpdate(createThingV2(KNOWN_THING_ID, "anotherValue"), 3, 0, policyEnforcer));

        insertWithHigherRevision();
    }

    private void insertWithHigherRevision() throws ExecutionException, InterruptedException {
        final PolicyRestrictedSearchAggregation aggregation1 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq("anotherValue")))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        final PolicyRestrictedSearchAggregation aggregation2 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        assertThat(findAll(aggregation1)).contains(KNOWN_THING_ID);
        assertThat(findAll(aggregation2)).isEmpty();
    }

    /** */
    @Test
    public void deleteNotExisting() throws ExecutionException, InterruptedException {
        delete(KNOWN_THING_ID, 0);
    }

    /** */
    @Test
    public void deleteWithSameRevisionV1() throws ExecutionException, InterruptedException {
        // prepare
        insertOrUpdateThing(createThing(KNOWN_THING_ID, VALUE1), 2, 0);
        deleteWithSameRevision();
    }

    /** */
    @Test
    public void deleteWithSameRevisionV2() throws ExecutionException, InterruptedException {
        // prepare
        runBlocking(writePersistence.insertOrUpdate(createThingV2(KNOWN_THING_ID, VALUE1), 2, 0, policyEnforcer));
        deleteWithSameRevision();
    }

    private void deleteWithSameRevision() throws ExecutionException, InterruptedException {
        // test
        delete(KNOWN_THING_ID, 2);

        final PolicyRestrictedSearchAggregation aggregation =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        // verify
        assertThat(findAll(aggregation)).contains(KNOWN_THING_ID);
    }

    /** */
    @Test
    public void deleteWithHigherRevision() throws ExecutionException, InterruptedException {
        // prepare
        insertOrUpdateThing(createThing(KNOWN_THING_ID, VALUE1), 2, 0);

        // test
        delete(KNOWN_THING_ID, 3);

        final PolicyRestrictedSearchAggregation aggregation =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        // verify
        assertThat(findAll(aggregation)).isEmpty();
    }

    /** */
    @Test
    public void deleteWithHigherRevisionV2() throws ExecutionException, InterruptedException {
        // prepare
        runBlocking(writePersistence.insertOrUpdate(createThingV2(KNOWN_THING_ID, VALUE1), 2, 0, policyEnforcer));

        // test
        delete(KNOWN_THING_ID, 3);

        final PolicyRestrictedSearchAggregation aggregation =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        // verify
        assertThat(findAll(aggregation)).isEmpty();
    }

    /** */
    @Test
    public void insertDeleteAndInsertAgain() throws ExecutionException, InterruptedException {
        insertOrUpdateThing(createThing(KNOWN_THING_ID, VALUE1), 2, 0);
        delete(KNOWN_THING_ID, 3);
        insertOrUpdateThing(createThing(KNOWN_THING_ID, "anotherValue"), 4, 0);

        insertWithHigherRevision();
    }

    /** */
    @Test
    public void insertDeleteAndInsertAgainV2() throws ExecutionException, InterruptedException {
        // prepare
        runBlocking(writePersistence.insertOrUpdate(createThingV2(KNOWN_THING_ID, VALUE1), 2, 0, policyEnforcer));

        delete(KNOWN_THING_ID, 3);
        runBlocking(
                writePersistence.insertOrUpdate(createThingV2(KNOWN_THING_ID, "anotherValue"), 4, 0, policyEnforcer));

        insertWithHigherRevision();
    }

    /** */
    @Test
    public void updateWholeACL() throws ExecutionException, InterruptedException {
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);
        insertOrUpdateThing(thing, 0, 0);

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(0, policyEnforcer)
                .addEvent(createAclModified("newSid", 1L, org.eclipse.ditto.model.things.Permission.READ,
                        org.eclipse.ditto.model.things.Permission.WRITE,
                        org.eclipse.ditto.model.things.Permission.ADMINISTRATE), JsonSchemaVersion.V_1)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        final PolicyRestrictedSearchAggregation aggregation2 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                        .authorizationSubjects(Collections.singletonList("newSid")).build();

        assertThat(findAll(aggregation1)).isEmpty();
        assertThat(findAll(aggregation2)).contains(KNOWN_THING_ID);
    }

    /** */
    @Test(expected = IllegalArgumentException.class)
    public void updateWholeACLWithUnexpectedSequenceNumber() throws ExecutionException, InterruptedException {
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);
        insertOrUpdateThing(thing, 0, 0);

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(0, policyEnforcer)
                .addEvent(createAclModified("newSid", 2L, org.eclipse.ditto.model.things.Permission.READ,
                        org.eclipse.ditto.model.things.Permission.WRITE,
                        org.eclipse.ditto.model.things.Permission.ADMINISTRATE), JsonSchemaVersion.V_1)
                .build();

        runBlocking(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes));
    }

    @Test
    public void createNewAclEntry() throws ExecutionException, InterruptedException {
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);
        insertOrUpdateThing(thing, 0, 0);

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(0, policyEnforcer)
                .addEvent(createAclModified("anotherSid", 1L, org.eclipse.ditto.model.things.Permission.READ),
                        JsonSchemaVersion.V_1)
                .build();

        final PolicyRestrictedSearchAggregation aggregation =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                        .authorizationSubjects(Collections.singletonList("anotherSid")).build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes))).isTrue();
        assertThat(findAll(aggregation)).contains(KNOWN_THING_ID);
    }

    private static ThingEvent createAclModified(final CharSequence authSubjectId,
            final long revision,
            final org.eclipse.ditto.model.things.Permission permission,
            final org.eclipse.ditto.model.things.Permission... furtherPermissions) {

        final AclEntry aclEntry = ThingsModelFactory.newAclEntry(AuthorizationSubject.newInstance(authSubjectId),
                permission, furtherPermissions);
        final AccessControlList accessControlList = ThingsModelFactory.newAcl(aclEntry);
        return AclModified.of(KNOWN_THING_ID, accessControlList, revision, DittoHeaders.empty());
    }

    @Test
    public void updateExistingAclEntryAddRead() throws ExecutionException, InterruptedException {
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);
        insertOrUpdateThing(thing, 0, 0);

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(0, policyEnforcer)
                .addEvent(createAclModified("iot-things:mySid2", 1L, org.eclipse.ditto.model.things.Permission.READ,
                        org.eclipse.ditto.model.things.Permission.WRITE), JsonSchemaVersion.V_1)
                .build();

        final PolicyRestrictedSearchAggregation aggregation =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                        .authorizationSubjects(Collections.singletonList("iot-things:mySid2"))
                        .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();
        assertThat(findAll(aggregation)).isNotNull()
                .contains(KNOWN_THING_ID);
    }

    @Test
    public void updateExistingAclEntryRemoveRead() throws ExecutionException, InterruptedException {
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);
        insertOrUpdateThing(thing, 0, 0);

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(0, policyEnforcer)
                .addEvent(createAclModified("iot-things:mySid3", 1L, org.eclipse.ditto.model.things.Permission.WRITE),
                        JsonSchemaVersion.V_1)
                .build();

        final PolicyRestrictedSearchAggregation aggregation =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                        .authorizationSubjects(Collections.singletonList("iot-things:mySid3"))
                        .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes))).isTrue();
        assertThat(findAll(aggregation)).isEmpty();
    }

    @Test
    public void deleteAclEntry() throws ExecutionException, InterruptedException {
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);
        insertOrUpdateThing(thing, 0, 0);

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(0, policyEnforcer)
                .addEvent(AclEntryDeleted.of(KNOWN_THING_ID, AuthorizationSubject.newInstance("iot-things:mySid3"), 1L,
                        DittoHeaders.empty()), JsonSchemaVersion.V_1)
                .build();

        final PolicyRestrictedSearchAggregation aggregation =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                        .authorizationSubjects(Collections.singletonList("iot-things:mySid3"))
                        .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes))).isTrue();
        assertThat(findAll(aggregation)).isEmpty();
    }

    /** */
    @Test
    public void updateAllAttributesV1() throws ExecutionException, InterruptedException {
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);
        insertOrUpdateThing(thing, 1L, -1L);
        updateAllAttributes(JsonSchemaVersion.V_1, KNOWN_ATTRIBUTE_1);
    }

    /** */
    @Test
    public void updateAllAttributesWithDotsV1() throws ExecutionException, InterruptedException {
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);
        insertOrUpdateThing(thing, 1L, -1L);
        updateAllAttributes(JsonSchemaVersion.V_1, "some.attribute.with.dot");
    }

    /** */
    @Test
    public void updateAllAttributesV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));
        updateAllAttributes(JsonSchemaVersion.LATEST, KNOWN_ATTRIBUTE_1);
    }

    /** */
    @Test
    public void updateAllAttributesWithDotsV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));
        updateAllAttributes(JsonSchemaVersion.LATEST, "some.attribute.with.dot");
    }

    private void updateAllAttributes(final JsonSchemaVersion schemaVersion, final String attributeName)
            throws ExecutionException, InterruptedException {

        final Attributes newAttributes = Attributes.newBuilder().set(attributeName, KNOWN_NEW_VALUE).build();
        final AttributesCreated attributesCreated =
                AttributesCreated.of(KNOWN_THING_ID, newAttributes, 2L, DittoHeaders.empty());

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(attributesCreated, schemaVersion)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes))).isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(attributeName), cf.eq(KNOWN_NEW_VALUE)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        final PolicyRestrictedSearchAggregation aggregation2 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).contains(KNOWN_THING_ID);

        final Collection<String> result2 = findAll(aggregation2);
        assertThat(result2).isEmpty();
    }

    @Test
    public void addNewSingleSimpleAttributeV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));

        final JsonPointer pointer = JsonFactory.newPointer(KNOWN_ATTRIBUTE_1);
        final JsonValue value = newValue(KNOWN_NEW_VALUE);

        final AttributeModified attributeModified =
                AttributeModified.of(KNOWN_THING_ID, pointer, value, 2L, DittoHeaders.empty());

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(attributeModified, JsonSchemaVersion.LATEST)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KNOWN_ATTRIBUTE_1), cf.eq(KNOWN_NEW_VALUE)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).contains(KNOWN_THING_ID);
    }

    @Test
    public void addNewSingleSimpleAttributeV1() throws ExecutionException, InterruptedException {
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);
        insertOrUpdateThing(thing, 1L, -1L);

        updateSingleExistingSimpleAttribute(KNOWN_ATTRIBUTE_1, KNOWN_NEW_VALUE);
    }

    @Test
    public void addNewSingleSimpleAttributeWithDotsV1() throws ExecutionException, InterruptedException {
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);
        insertOrUpdateThing(thing, 1L, -1L);

        updateSingleExistingSimpleAttribute("new.attribute", KNOWN_NEW_VALUE);
    }

    @Test
    public void updateSingleExistingSimpleAttributeV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));

        updateSingleExistingSimpleAttribute(KEY2, KNOWN_NEW_VALUE);
    }

    @Test
    public void addNewSingleSimpleAttributeWithDotsV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));

        updateSingleExistingSimpleAttribute("new.attribute", KNOWN_NEW_VALUE);
    }

    private void updateSingleExistingSimpleAttribute(final String key, final String value)
            throws ExecutionException, InterruptedException {
        final JsonPointer pointer = JsonFactory.newPointer(key);
        final AttributeModified attributeModified =
                AttributeModified.of(KNOWN_THING_ID, pointer, JsonValue.of(value), 2L, DittoHeaders.empty());

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(attributeModified, JsonSchemaVersion.LATEST)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes))).isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(key), cf.eq(value)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();
        final Collection<String> result = findAll(aggregation1);
        assertThat(result).contains(KNOWN_THING_ID);
    }

    @Test
    public void updateSingleExistingSimpleAttributeV1() throws ExecutionException, InterruptedException {
        insertOrUpdateThing(createThing(KNOWN_THING_ID, VALUE1), 1L, -1L);

        final JsonPointer pointer = JsonFactory.newPointer(KEY2);
        final JsonValue value = newValue(NEW_VALUE_2);
        final AttributeCreated attributeCreated =
                AttributeCreated.of(KNOWN_THING_ID, pointer, value, 2L, DittoHeaders.empty());

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(attributeCreated, JsonSchemaVersion.V_1)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY2), cf.eq(NEW_VALUE_2)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();
        final Collection<String> result = findAll(aggregation1);
        assertThat(result).contains(KNOWN_THING_ID);
    }

    @Test
    public void updateSimpleAttributeByNonExistingPrefixNameV1() throws ExecutionException, InterruptedException {
        insertOrUpdateThing(createThing(KNOWN_THING_ID, VALUE1), 1L, -1L);

        final JsonPointer pointer = JsonFactory.newPointer("key");
        final JsonValue value = newValue(NEW_VALUE_2);
        final AttributeCreated attributeCreated =
                AttributeCreated.of(KNOWN_THING_ID, pointer, value, 2L, DittoHeaders.empty());

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(attributeCreated, JsonSchemaVersion.V_1)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY2), cf.eq(NEW_VALUE_2)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).isEmpty();
    }

    @Test
    public void updateSimpleAttributeByNonExistingPrefixNameV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));

        final JsonPointer pointer = JsonFactory.newPointer("key");
        final JsonValue value = newValue(NEW_VALUE_2);
        final AttributeModified attributeModified =
                AttributeModified.of(KNOWN_THING_ID, pointer, value, 2L, DittoHeaders.empty());

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(attributeModified, JsonSchemaVersion.LATEST)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY2), cf.eq(NEW_VALUE_2)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).isEmpty();
    }

    @Test
    public void addNewSingleComplexAttributeV1() throws ExecutionException, InterruptedException {
        insertOrUpdateThing(createThing(KNOWN_THING_ID, VALUE1), 1L, -1L);

        final JsonPointer pointer = JsonFactory.newPointer("new1/new2/new3");
        final JsonObject value =
                JsonFactory.newObject().setValue("bool1", true).setValue(KNOWN_ATTRIBUTE_1, KNOWN_NEW_VALUE);
        final AttributeModified attributeModified =
                AttributeModified.of(KNOWN_THING_ID, pointer, value, 2L, DittoHeaders.empty());

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(attributeModified, JsonSchemaVersion.V_1)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        addNewSingleComplexAttribute();
    }

    private void addNewSingleComplexAttribute() throws ExecutionException, InterruptedException {
        final PolicyRestrictedSearchAggregation aggregation1 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute("new1/new2/new3/bool1"), cf.eq(Boolean.TRUE)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).contains(KNOWN_THING_ID);

        final PolicyRestrictedSearchAggregation aggregation2 = abf.newBuilder(
                cf.fieldCriteria(fef.filterByAttribute("new1/new2/new3/" + KNOWN_ATTRIBUTE_1), cf.eq(KNOWN_NEW_VALUE)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result2 = findAll(aggregation2);
        assertThat(result2).contains(KNOWN_THING_ID);

        final PolicyRestrictedSearchAggregation aggregation3 =
                abf.newBuilder(cf.existsCriteria(fef.existsByAttribute("new1/new2")))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        final Collection<String> result3 = findAll(aggregation3);
        assertThat(result3).contains(KNOWN_THING_ID);
    }

    @Test
    public void addNewSingleComplexAttributeV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));

        final JsonObject value = JsonFactory.newObjectBuilder()
                .set("bool1", true)
                .set(KNOWN_ATTRIBUTE_1, KNOWN_NEW_VALUE)
                .build();
        final AttributeModified attributeModified = createAttributeModified("new1/new2/new3", value, 2L);

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(attributeModified, JsonSchemaVersion.LATEST)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        addNewSingleComplexAttribute();
    }

    private static AttributeModified createAttributeModified(final CharSequence attributePointer,
            final JsonValue attributeValue, final long revision) {
        return AttributeModified.of(KNOWN_THING_ID, JsonFactory.newPointer(attributePointer), attributeValue, revision,
                DittoHeaders.empty());
    }

    @Test
    public void replaceSimpleWithComplexAttributeV1() throws ExecutionException, InterruptedException {
        insertOrUpdateThing(createThing(KNOWN_THING_ID, VALUE1), 1L, -1L);

        final JsonObject value = JsonFactory.newObjectBuilder()
                .set("bool1", true)
                .set(KNOWN_ATTRIBUTE_1, KNOWN_NEW_VALUE)
                .build();
        final AttributeModified attributeModified = createAttributeModified(KEY1, value, 2L);

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(attributeModified, JsonSchemaVersion.V_1)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute("key1/bool1"), cf.eq(Boolean.TRUE)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).contains(KNOWN_THING_ID);

        final PolicyRestrictedSearchAggregation aggregation2 =
                abf.newBuilder(cf.fieldCriteria(ef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();
        final Collection<String> result2 = findAll(aggregation2);
        assertThat(result2).isEmpty();

        final CombinedThingWrites writes2 = CombinedThingWrites.newBuilder(2L, policyEnforcer)
                .addEvent(createAttributeModified(KEY1, newValue(NEW_VALUE_2), 3L), JsonSchemaVersion.V_1)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes2)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation3 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(NEW_VALUE_2)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        final Collection<String> result3 = findAll(aggregation3);
        assertThat(result3).contains(KNOWN_THING_ID);
    }

    @Test
    public void replaceSimpleWithComplexAttributeV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));

        final JsonObject value = JsonFactory.newObjectBuilder()
                .set("bool1", true)
                .set(KNOWN_ATTRIBUTE_1, KNOWN_NEW_VALUE)
                .build();
        final AttributeModified attributeModified = createAttributeModified(KEY1, value, 2L);

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(attributeModified, JsonSchemaVersion.LATEST)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute("key1/bool1"), cf.eq(Boolean.TRUE)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).contains(KNOWN_THING_ID);

        final PolicyRestrictedSearchAggregation aggregation2 =
                abf.newBuilder(cf.fieldCriteria(ef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();
        final Collection<String> result2 = findAll(aggregation2);
        assertThat(result2).isEmpty();

        final JsonValue simpleValue = newValue(NEW_VALUE_2);
        final AttributeModified attributeModified2 = createAttributeModified(KEY1, simpleValue, 3L);

        final CombinedThingWrites writes2 = CombinedThingWrites.newBuilder(2L, policyEnforcer)
                .addEvent(attributeModified2, JsonSchemaVersion.LATEST)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes2)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation3 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(NEW_VALUE_2)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        final Collection<String> result3 = findAll(aggregation3);
        assertThat(result3).contains(KNOWN_THING_ID);
    }

    @Test
    public void deleteSingleAttributeV1() throws ExecutionException, InterruptedException {
        createThing(KNOWN_THING_ID, VALUE1);

        final JsonPointer attributePointer = JsonFactory.newPointer(KEY1);
        final AttributeDeleted attributeDeleted =
                AttributeDeleted.of(KNOWN_THING_ID, attributePointer, 2L, DittoHeaders.empty());

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(attributeDeleted, JsonSchemaVersion.V_1)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        final Collection<String> result = findAll(aggregation1);

        assertThat(result).isEmpty();
    }

    @Test
    public void deleteSingleAttributeV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));

        final JsonPointer attributePointer = JsonFactory.newPointer(KEY1);
        final AttributeDeleted attributeDeleted =
                AttributeDeleted.of(KNOWN_THING_ID, attributePointer, 2L, DittoHeaders.empty());

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(attributeDeleted, JsonSchemaVersion.LATEST)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();


        final PolicyRestrictedSearchAggregation aggregation1 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).isEmpty();
    }

    @Test
    public void deleteAllAttributesV1() throws ExecutionException, InterruptedException {
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);
        insertOrUpdateThing(thing, 1L, -1L);

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(AttributesDeleted.of(KNOWN_THING_ID, 2L, DittoHeaders.empty()),
                        JsonSchemaVersion.V_1)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).isEmpty();

        final PolicyRestrictedSearchAggregation aggregation2 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(PROP_VALUE1)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        final Collection<String> result2 = findAll(aggregation2);
        assertThat(result2).contains(KNOWN_THING_ID);
    }

    @Test
    public void deleteAllAttributesV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));


        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(AttributesDeleted.of(KNOWN_THING_ID, 2L, DittoHeaders.empty()),
                        JsonSchemaVersion.LATEST)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).isEmpty();

        final PolicyRestrictedSearchAggregation aggregation2 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(PROP_VALUE1)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        final Collection<String> result2 = findAll(aggregation2);
        assertThat(result2).contains(KNOWN_THING_ID);
    }

    @Test
    public void deleteExistingFeatureV1() throws ExecutionException, InterruptedException {
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);
        insertOrUpdateThing(thing, 1L, -1L);

        final PolicyRestrictedSearchAggregation aggregation1 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(PROP_VALUE1)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        final Collection<String> result = findAll(aggregation1);

        assertThat(result).contains(KNOWN_THING_ID);

        final FeatureDeleted featureDeleted =
                FeatureDeleted.of(KNOWN_THING_ID, FEATURE_ID1, 2L, DittoHeaders.empty());

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(featureDeleted, JsonSchemaVersion.LATEST)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();
        assertThat(findAll(aggregation1)).isEmpty();

        final PolicyRestrictedSearchAggregation aggregation2 =
                abf.newBuilder(cf.existsCriteria(ef.existsByFeatureId(FEATURE_ID1)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        final Collection<String> result2 = findAll(aggregation2);
        assertThat(result2).isEmpty();
    }

    @Test
    public void addNewFeatureV1() throws ExecutionException, InterruptedException {
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);
        insertOrUpdateThing(thing, 1L, -1L);

        final PolicyRestrictedSearchAggregation aggregation1 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP5), cf.eq(PROP_VALUE5)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();
        final Collection<String> result = findAll(aggregation1);
        assertThat(result).isEmpty();

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(createFeatureCreated(FEATURE_ID2), JsonSchemaVersion.V_1)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();
        assertThat(findAll(aggregation1)).contains(KNOWN_THING_ID);

        final PolicyRestrictedSearchAggregation aggregation2 =
                abf.newBuilder(cf.existsCriteria(fef.existsByFeatureId(FEATURE_ID2)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        assertThat(findAll(aggregation2)).contains(KNOWN_THING_ID);
    }

    private static ThingEvent createFeatureCreated(final CharSequence featureId) {
        final Feature feature = createFeature(featureId.toString());
        return FeatureCreated.of(KNOWN_THING_ID, feature, 2L, DittoHeaders.empty());
    }

    @Test
    public void addNewFeatureV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));

        final PolicyRestrictedSearchAggregation aggregation1 =
                abf.newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP5), cf.eq(PROP_VALUE5)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();
        final Collection<String> result = findAll(aggregation1);
        assertThat(result).isEmpty();

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(createFeatureCreated(FEATURE_ID2), JsonSchemaVersion.LATEST)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();


        assertThat(findAll(aggregation1)).contains(KNOWN_THING_ID);

        final PolicyRestrictedSearchAggregation aggregation2 = abf
                .newBuilder(cf.existsCriteria(fef.existsByFeatureId(FEATURE_ID2)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        assertThat(findAll(aggregation2)).contains(KNOWN_THING_ID);
    }

    @Test
    public void updateExistingFeatureV1() throws ExecutionException, InterruptedException {
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);
        insertOrUpdateThing(thing, 1L, -1L);

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(createFeatureCreated(FEATURE_ID1), JsonSchemaVersion.V_1)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP5), cf.eq(PROP_VALUE5)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).contains(KNOWN_THING_ID);

        final PolicyRestrictedSearchAggregation aggregation2 = abf
                .newBuilder(cf.existsCriteria(fef.existsByFeatureProperty(PROP5)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        assertThat(findAll(aggregation2)).contains(KNOWN_THING_ID);
    }

    @Test
    public void updateExistingDottedFeatureV1() throws ExecutionException, InterruptedException {
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);
        insertOrUpdateThing(thing, 1L, -1L);

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(createFeatureCreated("feauture.with.dots"), JsonSchemaVersion.V_1)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP5), cf.eq(PROP_VALUE5)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).contains(KNOWN_THING_ID);

        final PolicyRestrictedSearchAggregation aggregation2 = abf
                .newBuilder(cf.existsCriteria(fef.existsByFeatureProperty(PROP5)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        assertThat(findAll(aggregation2)).contains(KNOWN_THING_ID);
    }

    @Test
    public void updateExistingFeatureV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(createFeatureCreated(FEATURE_ID1), JsonSchemaVersion.LATEST)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP5), cf.eq(PROP_VALUE5)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).contains(KNOWN_THING_ID);

        final PolicyRestrictedSearchAggregation aggregation2 = abf
                .newBuilder(cf.existsCriteria(fef.existsByFeatureProperty(PROP5)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        assertThat(findAll(aggregation2)).contains(KNOWN_THING_ID);
    }

    @Test
    public void deletePropertiesOfExistingFeatureV1() throws ExecutionException, InterruptedException {
        insertOrUpdateThing(createThing(KNOWN_THING_ID, VALUE1), 1L, -1L);

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(createFeaturePropertiesDeleted(FEATURE_ID1), JsonSchemaVersion.V_1)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(PROP_VALUE1)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).isEmpty();

        final PolicyRestrictedSearchAggregation aggregation2 = abf
                .newBuilder(cf.existsCriteria(ef.existsByFeatureId(FEATURE_ID1)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result2 = findAll(aggregation2);
        assertThat(result2).contains(KNOWN_THING_ID);
    }

    private static ThingEvent createFeaturePropertiesDeleted(final CharSequence featureId) {
        return FeaturePropertiesDeleted.of(KNOWN_THING_ID, featureId.toString(), 2L,
                DittoHeaders.empty());
    }

    @Test
    public void deletePropertiesOfExistingFeatureV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(createFeaturePropertiesDeleted(FEATURE_ID1), JsonSchemaVersion.LATEST)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(PROP_VALUE1)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).isEmpty();

        final PolicyRestrictedSearchAggregation aggregation2 = abf
                .newBuilder(cf.existsCriteria(ef.existsByFeatureId(FEATURE_ID1)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result2 = findAll(aggregation2);
        assertThat(result2).contains(KNOWN_THING_ID);
    }

    @Test
    public void updatePropertiesOfExistingFeatureV1() throws ExecutionException, InterruptedException {
        insertOrUpdateThing(createThing(KNOWN_THING_ID, VALUE1), 1L, -1L);

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(createFeaturePropertiesModified(FEATURE_ID1), JsonSchemaVersion.V_1)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP6), cf.eq(PROP_VALUE6)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).contains(KNOWN_THING_ID);
    }

    private static ThingEvent createFeaturePropertiesModified(final CharSequence featureId) {
        final Feature feature = createFeature(featureId.toString());
        return FeaturePropertiesModified.of(KNOWN_THING_ID, featureId.toString(), feature.getProperties().orElse
                (null), 2L, DittoHeaders.empty());
    }

    @Test
    public void updatePropertiesOfExistingFeatureV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(createFeaturePropertiesModified(FEATURE_ID1), JsonSchemaVersion.LATEST)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP6), cf.eq(PROP_VALUE6)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).contains(KNOWN_THING_ID);
    }

    @Test
    public void updatePropertiesOfNotExistingFeatureV1() throws ExecutionException, InterruptedException {
        insertOrUpdateThing(createThing(KNOWN_THING_ID, VALUE1), 1L, -1L);

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(createFeaturePropertiesModified(FEATURE_ID2), JsonSchemaVersion.V_1)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP6), cf.eq(PROP_VALUE6)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).contains(KNOWN_THING_ID);

        final PolicyRestrictedSearchAggregation aggregation2 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(PROP_VALUE1)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result2 = findAll(aggregation2);
        assertThat(result2).contains(KNOWN_THING_ID);
    }

    @Test
    public void updatePropertiesOfNotExistingFeatureV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(createFeaturePropertiesModified(FEATURE_ID2), JsonSchemaVersion.LATEST)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP6), cf.eq(PROP_VALUE6)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).contains(KNOWN_THING_ID);

        final PolicyRestrictedSearchAggregation aggregation2 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(PROP_VALUE1)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result2 = findAll(aggregation2);
        assertThat(result2).contains(KNOWN_THING_ID);
    }

    @Test
    public void deleteFeaturePropertyV1() throws ExecutionException, InterruptedException {
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);
        insertOrUpdateThing(thing, 1L, -1L);

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(createFeaturePropertyDeleted(FEATURE_ID1, PROP1, 2L), JsonSchemaVersion.V_1)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(PROP_VALUE1)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).isEmpty();

        final PolicyRestrictedSearchAggregation aggregation2 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1_EXTENDED), cf.eq(PROP_VALUE1)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result2 = findAll(aggregation2);
        assertThat(result2).contains(KNOWN_THING_ID);
    }

    private static ThingEvent createFeaturePropertyDeleted(final CharSequence featureId,
            final CharSequence featurePropertyPointer, final long revision) {
        return FeaturePropertyDeleted.of(KNOWN_THING_ID, featureId.toString(), JsonFactory
                .newPointer(featurePropertyPointer), revision, DittoHeaders.empty());
    }

    @Test
    public void deleteFeaturePropertyV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(createFeaturePropertyDeleted(FEATURE_ID1, PROP1, 2L), JsonSchemaVersion.LATEST)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(PROP_VALUE1)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).isEmpty();

        final PolicyRestrictedSearchAggregation aggregation2 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1_EXTENDED), cf.eq(PROP_VALUE1)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result2 = findAll(aggregation2);
        assertThat(result2).contains(KNOWN_THING_ID);
    }

    @Test
    public void deleteFeaturePropertyWithWrongSourceSequenceNumberV1() throws ExecutionException, InterruptedException {
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);
        insertOrUpdateThing(thing, 1L, -1L);

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(0L, policyEnforcer)
                .addEvent(createFeaturePropertyDeleted(FEATURE_ID1, PROP1, 1L), JsonSchemaVersion.V_1)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isFalse();
    }

    @Test
    public void deleteFeaturePropertyWithWrongSourceSequenceNumberV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(0L, policyEnforcer)
                .addEvent(createFeaturePropertyDeleted(FEATURE_ID1, PROP1, 1L), JsonSchemaVersion.LATEST)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isFalse();
    }

    @Test
    public void updateFeaturePropertyV1() throws ExecutionException, InterruptedException {
        insertOrUpdateThing(createThing(KNOWN_THING_ID, VALUE1), 1L, -1L);

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(createFeaturePropertyModified(FEATURE_ID1, PROP1, newValue(true), 2L), JsonSchemaVersion.V_1)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(Boolean.TRUE)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).contains(KNOWN_THING_ID);

        final PolicyRestrictedSearchAggregation aggregation2 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(PROP_VALUE1)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result2 = findAll(aggregation2);
        assertThat(result2).isEmpty();
    }

    private static ThingEvent createFeaturePropertyModified(final CharSequence featureId,
            final CharSequence propertyPointer, final JsonValue propertyValue, final long revision) {
        return FeaturePropertyModified.of(KNOWN_THING_ID, featureId.toString(), JsonFactory.newPointer
                (propertyPointer), propertyValue, revision, DittoHeaders.empty());
    }

    @Test
    public void updateFeaturePropertyV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(createFeaturePropertyModified(FEATURE_ID1, PROP1, newValue(true), 2L),
                        JsonSchemaVersion.LATEST)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(Boolean.TRUE)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).contains(KNOWN_THING_ID);

        final PolicyRestrictedSearchAggregation aggregation2 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(PROP_VALUE1)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result2 = findAll(aggregation2);
        assertThat(result2).isEmpty();
    }

    @Test
    public void updateFeaturePropertyByOverridingComplexPropertyV1() throws ExecutionException, InterruptedException {
        insertOrUpdateThing(createThing(KNOWN_THING_ID, VALUE1), 1L, -1L);
        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(createFeaturePropertyModified(FEATURE_ID1, PROP7, newValue("simple"), 2L),
                        JsonSchemaVersion.V_1)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP7), cf.eq("simple")))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).contains(KNOWN_THING_ID);

        final PolicyRestrictedSearchAggregation aggregation2 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP7 + "/" + PROP8), cf.eq(PROP_VALUE8)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result2 = findAll(aggregation2);
        assertThat(result2).isEmpty();
    }

    @Test
    public void updateFeaturePropertyByOverridingComplexPropertyV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(createFeaturePropertyModified(FEATURE_ID1, PROP7, newValue("simple"), 2L),
                        JsonSchemaVersion.LATEST)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP7), cf.eq("simple")))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).contains(KNOWN_THING_ID);

        final PolicyRestrictedSearchAggregation aggregation2 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP7 + "/" + PROP8), cf.eq(PROP_VALUE8)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result2 = findAll(aggregation2);
        assertThat(result2).isEmpty();
    }

    @Test
    public void deleteFeaturesV1() throws ExecutionException, InterruptedException {
        insertOrUpdateThing(createThing(KNOWN_THING_ID, VALUE1), 1L, -1L);
        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(FeaturesDeleted.of(KNOWN_THING_ID, 2L, DittoHeaders.empty()), JsonSchemaVersion.V_1)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(PROP_VALUE1)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).isEmpty();

        final PolicyRestrictedSearchAggregation aggregation2 = abf
                .newBuilder(cf.existsCriteria(ef.existsByFeatureId(FEATURE_ID1)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result2 = findAll(aggregation2);
        assertThat(result2).isEmpty();
    }

    @Test
    public void deleteFeaturesV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(FeaturesDeleted.of(KNOWN_THING_ID, 2L, DittoHeaders.empty()),
                        JsonSchemaVersion.LATEST)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(PROP_VALUE1)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).isEmpty();

        final PolicyRestrictedSearchAggregation aggregation2 = abf
                .newBuilder(cf.existsCriteria(ef.existsByFeatureId(FEATURE_ID1)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result2 = findAll(aggregation2);
        assertThat(result2).isEmpty();
    }

    @Test
    public void updateFeaturesV1() throws ExecutionException, InterruptedException {
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);
        insertOrUpdateThing(thing, 1L, -1L);
        updateFeatures("f3", createFeatures(), JsonSchemaVersion.V_1);
    }

    @Test
    public void updateFeaturesV1WithDottedFeatureId() throws ExecutionException, InterruptedException {
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);
        insertOrUpdateThing(thing, 1L, -1L);
        updateFeatures(FEATURE_WITH_DOTS, createFeaturesWithDottedFeatureId(FEATURE_WITH_DOTS), JsonSchemaVersion.V_1);
    }

    @Test
    public void updateFeaturesV1WithDottedPropertyNames() throws ExecutionException, InterruptedException {
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);
        insertOrUpdateThing(thing, 1L, -1L);
        updateFeatures(FEATURE_WITH_DOTS, createFeaturesWithDottedPropertyNames(FEATURE_WITH_DOTS),
                JsonSchemaVersion.V_1);
    }

    @Test
    public void updateFeaturesV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));
        updateFeatures("f3", createFeatures(), JsonSchemaVersion.V_2);
    }

    @Test
    public void updateFeaturesV2WithDottedFeatureId() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));
        updateFeatures(FEATURE_WITH_DOTS, createFeaturesWithDottedFeatureId(FEATURE_WITH_DOTS), JsonSchemaVersion.V_2);
    }

    @Test
    public void updateFeaturesV2WithDottedPropertyNames() throws ExecutionException, InterruptedException {
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));
        updateFeatures(FEATURE_WITH_DOTS, createFeaturesWithDottedPropertyNames(FEATURE_WITH_DOTS),
                JsonSchemaVersion.V_2);
    }

    private void updateFeatures(final String expectedFeatureId, final Features features,
            final JsonSchemaVersion schemaVersion) throws ExecutionException, InterruptedException {

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(createFeaturesModified(features), schemaVersion)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(PROP_VALUE1)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).isEmpty();

        final PolicyRestrictedSearchAggregation aggregation2 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP5), cf.eq(PROP_VALUE5)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result2 = findAll(aggregation2);
        assertThat(result2).contains(KNOWN_THING_ID);

        final PolicyRestrictedSearchAggregation aggregation3 = abf
                .newBuilder(cf.existsCriteria(ef.existsByFeatureId(expectedFeatureId)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result3 = findAll(aggregation3);
        assertThat(result3).contains(KNOWN_THING_ID);
    }

    private static ThingEvent createFeaturesModified(final Features features) {
        return FeaturesModified.of(KNOWN_THING_ID, features, 2L, DittoHeaders.empty());
    }

    @Test
    public void updateFeaturePropertyAndAclV1() throws ExecutionException, InterruptedException {
        insertOrUpdateThing(createThing(KNOWN_THING_ID, VALUE1), 1L, -1L);

        final AclEntry entry = ThingsModelFactory.newAclEntry(newAuthSubject("anotherSid"),
                org.eclipse.ditto.model.things.Permission.READ);

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(AclEntryModified.of(KNOWN_THING_ID, entry, 2L, DittoHeaders.empty()),
                        JsonSchemaVersion.V_1)
                .addEvent(createFeaturePropertyModified(FEATURE_ID1, PROP1, newValue("somethingNew"), 3L),
                        JsonSchemaVersion.V_1)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        assertThat(findAll(aggregation1)).contains(KNOWN_THING_ID);

        final PolicyRestrictedSearchAggregation aggregation2 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq("somethingNew")))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result2 = findAll(aggregation2);
        assertThat(result2).contains(KNOWN_THING_ID);
    }

    @Test
    public void updateFeaturePropertiesAndOneAttributeV1() throws ExecutionException, InterruptedException {
        insertOrUpdateThing(createThing(KNOWN_THING_ID, VALUE1), 1L, -1L);

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(createFeaturePropertiesModified(FEATURE_ID2), JsonSchemaVersion.V_1)
                .addEvent(createAttributeModified(PROP1, newValue("s0meattr12"), 3L), JsonSchemaVersion.V_1)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByAttribute(PROP1), cf.eq("s0meattr12")))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result1 = findAll(aggregation1);
        assertThat(result1).contains(KNOWN_THING_ID);

        final PolicyRestrictedSearchAggregation aggregation2 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP6), cf.eq(PROP_VALUE6)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result2 = findAll(aggregation2);
        assertThat(result2).contains(KNOWN_THING_ID);
    }

    @Test
    public void updateFeaturePropertiesAndOneAttributeV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(createFeaturePropertiesModified(FEATURE_ID1), JsonSchemaVersion.LATEST)
                .addEvent(createAttributeModified(PROP1, newValue("s0meattr12"), 3L), JsonSchemaVersion.LATEST)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByAttribute(PROP1), cf.eq("s0meattr12")))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result1 = findAll(aggregation1);
        assertThat(result1).contains(KNOWN_THING_ID);

        final PolicyRestrictedSearchAggregation aggregation2 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP6), cf.eq(PROP_VALUE6)))
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        final Collection<String> result2 = findAll(aggregation2);

        assertThat(result2).contains(KNOWN_THING_ID);
    }

    @Test
    public void updateSeveralFeaturePropertiesAndDeleteThingV1() throws ExecutionException, InterruptedException {
        insertOrUpdateThing(createThing(KNOWN_THING_ID, VALUE1), 1L, -1L);

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(createAclModified("anotherSid", 2L, org.eclipse.ditto.model.things.Permission.READ),
                        JsonSchemaVersion.V_1)
                .addEvent(createFeaturePropertyModified(FEATURE_ID1, PROP1, newValue("somethingNew"), 3L),
                        JsonSchemaVersion.V_1)
                .addEvent(createFeaturePropertyModified(FEATURE_ID1, PROP1, newValue("somethingNew2"), 4L),
                        JsonSchemaVersion.V_1)
                .addEvent(createFeaturePropertyModified(FEATURE_ID1, PROP1, newValue("somethingNew3"), 5L),
                        JsonSchemaVersion.V_1)
                .addEvent(createFeaturePropertyModified(FEATURE_ID1, PROP1, newValue("somethingNew4"), 6L),
                        JsonSchemaVersion.V_1)
                .addEvent(ThingDeleted.of(KNOWN_THING_ID, 7L, DittoHeaders.empty()), JsonSchemaVersion.V_1)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 = abf
                .newBuilder(cf.any())
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        assertThat(findAll(aggregation1)).isEmpty();
    }

    @Test
    public void updateSeveralFeaturePropertiesAndDeleteThingV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        runBlocking(writePersistence.insertOrUpdate(thing, 1L, -1L, policyEnforcer));

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(1L, policyEnforcer)
                .addEvent(createAclModified("anotherSid", 2L, org.eclipse.ditto.model.things.Permission.READ),
                        JsonSchemaVersion.V_1)
                .addEvent(createFeaturePropertyModified(FEATURE_ID1, PROP1, newValue("somethingNew"), 3L),
                        JsonSchemaVersion.LATEST)
                .addEvent(createFeaturePropertyModified(FEATURE_ID1, PROP1, newValue("somethingNew2"), 4L),
                        JsonSchemaVersion.LATEST)
                .addEvent(createFeaturePropertyModified(FEATURE_ID1, PROP1, newValue("somethingNew3"), 5L),
                        JsonSchemaVersion.LATEST)
                .addEvent(createFeaturePropertyModified(FEATURE_ID1, PROP1, newValue("somethingNew4"), 6L),
                        JsonSchemaVersion.LATEST)
                .addEvent(ThingDeleted.of(KNOWN_THING_ID, 7L, DittoHeaders.empty()), JsonSchemaVersion.LATEST)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 = abf
                .newBuilder(cf.any())
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();

        assertThat(findAll(aggregation1)).isEmpty();
    }

    @Test
    public void createThingAndUpdateAcl() throws ExecutionException, InterruptedException {
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);
        final ThingCreated thingCreated = ThingCreated.of(thing, 1L, DittoHeaders.empty());

        final AclEntry aclEntry =
                ThingsModelFactory.newAclEntry(newAuthSubject("newSid"), org.eclipse.ditto.model.things.Permission.READ,
                        org.eclipse.ditto.model.things.Permission.WRITE,
                        org.eclipse.ditto.model.things.Permission.ADMINISTRATE);
        final AccessControlList acl = ThingsModelFactory.newAcl(aclEntry);
        final AclModified aclModified = AclModified.of(KNOWN_THING_ID, acl, 2L, DittoHeaders.empty());

        final CombinedThingWrites writes = CombinedThingWrites.newBuilder(0L, policyEnforcer)
                .addEvent(thingCreated, JsonSchemaVersion.V_1)
                .addEvent(aclModified, JsonSchemaVersion.V_1)
                .build();

        Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes)))
                .isTrue();

        final PolicyRestrictedSearchAggregation aggregation1 = abf
                .newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                .authorizationSubjects(Collections.singletonList("newSid"))
                .build();

        assertThat(findAll(aggregation1)).isNotNull()
                .contains(KNOWN_THING_ID);
    }

    @Test
    public void updatePolicyForThing() throws ExecutionException, InterruptedException {
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);

        insertOrUpdateThing(thing, 1L, -1L);
        final Policy policy = createPolicy1();

        Assertions.assertThat(runBlockingWithReturn(
                writePersistence.updatePolicy(thing, PolicyEnforcers.defaultEvaluator(policy)))).isTrue();
        Assertions.assertThat(runBlockingWithReturn(
                writePersistence.updatePolicy(thing, PolicyEnforcers.defaultEvaluator(createPolicy2()))))
                .isTrue();
    }

    @Test
    public void createThingWithNullAttributeV2() throws ExecutionException, InterruptedException {
        final Thing thing = createThingV2(KNOWN_THING_ID, VALUE1);
        final Thing thingWithNulLAttribute = thing.setAttribute(NULL_ATTRIBUTE, JsonFactory.nullLiteral());
        runBlocking(writePersistence.insertOrUpdate(thingWithNulLAttribute, 0, 0,
                policyEnforcer));
        final PolicyRestrictedSearchAggregation aggregation =
                abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(NULL_ATTRIBUTE), cf.eq(null)))
                        .authorizationSubjects(KNOWN_SUBJECTS_2)
                        .build();

        assertThat(findAll(aggregation)).contains(KNOWN_THING_ID);
    }

    @Test
    public void migrateExistingThingToV2() throws ExecutionException, InterruptedException {

        // create a thing with an ACL
        final Thing thing = createThing(KNOWN_THING_ID, VALUE1);

        insertOrUpdateThing(thing, 1L, -1L);

        final PolicyRestrictedSearchAggregation aggregation1 = abf.newBuilder(cf.any())
                .authorizationSubjects(KNOWN_SUBJECTS_2)
                .build();
        final List<String> foundAll = findAll(aggregation1);
        assertThat(foundAll).containsOnly(KNOWN_THING_ID);

        final String newUser = "iot-things:someNewUser";
        final Policy newPolicy = createPolicyFor(newUser);

        runBlocking(writePersistence.insertOrUpdate(thing, 2L, 0, PolicyEnforcers.defaultEvaluator(newPolicy)));

        final List<String> foundAll2 = findAll(aggregation1);

        // none of KNOWN_SUBJECTS_2 has permissions in "newPolicy"
        assertThat(foundAll2).isEmpty();

        final PolicyRestrictedSearchAggregation aggregation2 = abf.newBuilder(cf.any())
                .authorizationSubjects(Collections.singletonList(newUser))
                .build();
        final List<String> foundAll3 = findAll(aggregation2);

        // newUser is allowed to read the thing
        assertThat(foundAll3).containsOnly(KNOWN_THING_ID);
    }

    private static Policy createPolicy1() {
        return PoliciesModelFactory.newPolicyBuilder(KNOWN_THING_ID)
                .forLabel("someLabel")
                .setSubject(
                        Subject.newInstance(DEFAULT_POLICY_SUBJECTS.iterator().next(), SubjectType.JWT))
                .setGrantedPermissions("thing", "/", Permission.READ)
                .setRevision(1L)
                .build();
    }

    private static Policy createPolicyFor(final CharSequence user) {
        return PoliciesModelFactory.newPolicyBuilder(KNOWN_THING_ID)
                .forLabel("someLabel")
                .setSubject(Subject.newInstance(user, SubjectType.JWT))
                .setGrantedPermissions("thing", "/", Permission.READ)
                .setRevision(1L)
                .build();
    }

    private static Policy createPolicy2() {
        return PoliciesModelFactory.newPolicyBuilder(KNOWN_THING_ID)
                .forLabel("someLabel")
                .setSubjects(
                        Subjects.newInstance(Subject.newInstance("iot-things:user88", SubjectType.JWT),
                                Subject.newInstance("iot-things:user2", SubjectType.JWT)))
                .setGrantedPermissions("thing", "/", Permission.READ)
                .setRevision(2L)
                .build();
    }

    private static Features createFeatures() {
        final Feature f1 = createFeature("f1");
        final Feature f2 = createFeature("f2");
        final Feature f3 = createFeature("f3");

        return ThingsModelFactory.newFeatures(f1, f2, f3);
    }

    private static Features createFeaturesWithDottedFeatureId(final String knownFeatureIdWithDots) {
        final Feature f1 = createFeature("f.1");
        final Feature f2 = createFeature("f.2");
        final Feature withDots = createFeature(knownFeatureIdWithDots);

        return ThingsModelFactory.newFeatures(f1, f2, withDots);
    }

    private static Features createFeaturesWithDottedPropertyNames(final String knownFeatureIdWithDots) {
        final Feature baselineFeature = createFeature(knownFeatureIdWithDots);
        final FeatureProperties featureProperties = baselineFeature.getProperties().get().toBuilder()
                .set(DOTTED_PROP, JsonFactory.newObjectBuilder().set(DOTTED_PROP, VALUE1).build())
                .build();
        final Feature feature = ThingsModelFactory.newFeature(knownFeatureIdWithDots, featureProperties);

        return ThingsModelFactory.newFeatures(feature);
    }

    private static Feature createFeature(final String featureId) {
        final FeatureProperties featureProperties = ThingsModelFactory.newFeaturePropertiesBuilder()
                .set(PROP5, PROP_VALUE5)
                .set(PROP6, PROP_VALUE6)
                .set(PROP7, JsonFactory.newObjectBuilder()
                        .set(JsonFactory.newKey(PROP8), PROP_VALUE8)
                        .build()
                )
                .build();

        return ThingsModelFactory.newFeature(featureId, featureProperties);
    }

    private static Thing createThingV2(final String thingId, final String attributeValue) {
        final Attributes attributes = ThingsModelFactory.newAttributesBuilder()
                .set(KEY1, attributeValue)
                .set(KEY2, VALUE2)
                .set(KEY3, VALUE3)
                .set(KEY4, VALUE4)
                .set(KEY5, VALUE5)
                .build();

        final FeatureProperties featureProperties = ThingsModelFactory.newFeaturePropertiesBuilder()
                .set(PROP1, PROP_VALUE1)
                .set(PROP1_EXTENDED, PROP_VALUE1)
                .set(PROP2, PROP_VALUE2)
                .set(PROP3, PROP_VALUE3)
                .set(PROP4, PROP_VALUE4)
                .set(PROP7, JsonFactory.newObjectBuilder()
                        .set(JsonFactory.newKey(PROP8), PROP_VALUE8)
                        .build()
                )
                .build();
        final Feature feature = ThingsModelFactory.newFeature(FEATURE_ID1, featureProperties);

        return ThingsModelFactory.newThingBuilder()
                .setId(thingId)
                .setPolicyId(createPolicy1().getId().get())
                .setAttributes(attributes)
                .setFeature(feature)
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setRevision(0L)
                .build();
    }

    private static Thing createThing(final String thingId, final String attributeValue) {
        final AccessControlList acl = ThingsModelFactory.newAclBuilder()
                .set(ThingsModelFactory.newAclEntry(newAuthSubject("iot-things:mySid"),
                        ThingsModelFactory.allPermissions()))
                .set(ThingsModelFactory.newAclEntry(newAuthSubject("iot-things:mySid2"),
                        org.eclipse.ditto.model.things.Permission.WRITE))
                .set(ThingsModelFactory.newAclEntry(newAuthSubject("iot-things:mySid3"),
                        org.eclipse.ditto.model.things.Permission.READ))
                .build();

        final Attributes attributes = ThingsModelFactory.newAttributesBuilder()
                .set(KEY1, attributeValue)
                .set(KEY2, VALUE2)
                .set(KEY3, VALUE3)
                .set(KEY4, VALUE4)
                .set(KEY5, VALUE5)
                .build();

        final FeatureProperties featureProperties = ThingsModelFactory.newFeaturePropertiesBuilder()
                .set(PROP1, PROP_VALUE1)
                .set(PROP1_EXTENDED, PROP_VALUE1)
                .set(PROP2, PROP_VALUE2)
                .set(PROP3, PROP_VALUE3)
                .set(PROP4, PROP_VALUE4)
                .set(PROP7, JsonFactory.newObjectBuilder()
                        .set(JsonFactory.newKey(PROP8), PROP_VALUE8)
                        .build()
                )
                .build();
        final Feature feature = ThingsModelFactory.newFeature(FEATURE_ID1, featureProperties);

        return ThingsModelFactory.newThingBuilder()
                .setId(thingId)
                .setPermissions(acl)
                .setAttributes(attributes)
                .setFeature(feature)
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setRevision(0L)
                .build();
    }

    private static Thing createThingWithDots(final String thingId, final String attributeValue) {
        final AccessControlList acl = ThingsModelFactory.newAclBuilder()
                .set(ThingsModelFactory.newAclEntry(newAuthSubject("iot-things:mySid"),
                        ThingsModelFactory.allPermissions()))
                .set(ThingsModelFactory.newAclEntry(newAuthSubject("iot-things:mySid2"),
                        org.eclipse.ditto.model.things.Permission.WRITE))
                .set(ThingsModelFactory.newAclEntry(newAuthSubject("iot-things:mySid3"),
                        org.eclipse.ditto.model.things.Permission.READ))
                .build();

        final Attributes attributes = ThingsModelFactory.newAttributesBuilder()
                .set(KEY1, attributeValue)
                .set(KEY2, VALUE2)
                .set(KEY3, VALUE3)
                .set(KEY4, VALUE4)
                .set(KEY5, VALUE5)
                .build();

        final FeatureProperties featureProperties = ThingsModelFactory.newFeaturePropertiesBuilder()
                .set(PROP1, PROP_VALUE1)
                .set(PROP1_EXTENDED, PROP_VALUE1)
                .set(PROP2, PROP_VALUE2)
                .set(PROP3, PROP_VALUE3)
                .set(PROP4, PROP_VALUE4)
                .set(PROP7, JsonFactory.newObjectBuilder()
                        .set(JsonFactory.newKey(PROP8), PROP_VALUE8)
                        .build()
                )
                .build();
        final Feature feature = ThingsModelFactory.newFeature(FEATURE_ID1, featureProperties);

        return ThingsModelFactory.newThingBuilder()
                .setId(thingId)
                .setPermissions(acl)
                .setAttributes(attributes)
                .setFeature(feature)
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setRevision(0L)
                .build();
    }

}

