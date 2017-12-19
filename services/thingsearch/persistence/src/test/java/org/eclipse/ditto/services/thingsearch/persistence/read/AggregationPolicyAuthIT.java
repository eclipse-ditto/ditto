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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.thingsearch.persistence.TestConstants.Thing.NAMESPACE;
import static org.eclipse.ditto.services.thingsearch.persistence.TestConstants.thingId;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.EffectedPermissions;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcers;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Permission;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.query.PolicyRestrictedSearchAggregation;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the correct authorization checking on searches by policies.
 */
public final class AggregationPolicyAuthIT extends AbstractReadPersistenceITBase {

    private static final String KNOWN_NUMBER_ATTR = "numberAttr";
    private static final String KNOWN_STRING_ATTR = "stringAttr";
    private static final String KNOWN_BOOL_ATTR = "boolAttr";
    private static final String KNOWN_REGEX_ATTR = "stringRegex";

    private static final String KNOWN_NUMBER_ATTR2 = "numberAttr2";
    private static final String KNOWN_BOOL_ATTR2 = "boolAttr2";
    private static final String KNOWN_REGEX_ATTR2 = "stringRegex2";

    private static final String THING1_ID = thingId(NAMESPACE, "thing1");
    private static final String THING1_KNOWN_STR_ATTR_VALUE = "a";
    private static final int THING1_KNOWN_NUM_ATTR_VALUE = 1;
    private static final boolean THING1_KNOWN_BOOL_ATTR_VALUE = true;
    private static final String THING2_ID = thingId(NAMESPACE, "thing2");
    private static final String THING2_KNOWN_STR_ATTR_VALUE = "b";
    private static final int THING2_KNOWN_NUM_ATTR_VALUE = 1;
    private static final boolean THING2_KNOWN_BOOL_ATTR_VALUE = true;
    private static final String THING3_ID = thingId(NAMESPACE, "thing3");
    private static final String THING3_KNOWN_STR_ATTR_VALUE = "c";
    private static final double THING3_KNOWN_NUM_ATTR_VALUE = 1;
    private static final boolean THING3_KNOWN_BOOL_ATTR_VALUE = true;
    private static final String THING4_ID = thingId(NAMESPACE, "thing4");
    private static final String THING5_ID = thingId(NAMESPACE, "thing5");
    private static final String THING4_KNOWN_STR_ATTR_VALUE = "d";
    private static final int THING4_KNOWN_NUM_ATTR_VALUE = 1;
    private static final boolean THING4_KNOWN_BOOL_ATTR_VALUE = false;

    private static final String THING1_KNOWN_STR_REGEX_VALUE = "Das ist ein X belibiger String"; // starts with
    private static final String THING2_KNOWN_STR_REGEX_VALUE = "Der zweite Das String"; // contains
    private static final String THING3_KNOWN_STR_REGEX_VALUE = "Teststring nummer drei"; // wildcard
    private static final String THING4_KNOWN_STR_REGEX_VALUE = "Der vierte und letzte Teststring"; // ends with

    private static final List<String> SUBJECTS_USER_1 = Collections.singletonList("eclipse:user1");
    private static final List<String> SUBJECTS_USER_2 = Collections.singletonList("eclipse:user2");

    private final PolicyEnforcer policyEnforcerThing1_5 = PolicyEnforcers.defaultEvaluator(createPolicy1_5());
    private final PolicyEnforcer policyEnforcerThing2_3_4 = PolicyEnforcers.defaultEvaluator(createPolicy2_3_4());

    @Before
    @Override
    public void before() {
        super.before();
        insertThings();
    }

    // this is a V2 Test with policies
    @Override
    protected JsonSchemaVersion getVersion() {
        return JsonSchemaVersion.V_2;
    }

    @Test
    public void grantedAccessWithAnd() {
        final PolicyRestrictedSearchAggregation aggregation = aggregationForUser1(cf.and(Arrays.asList(
                cf.fieldCriteria(fef.filterByAttribute(KNOWN_STRING_ATTR), cf.eq(THING1_KNOWN_STR_ATTR_VALUE)),
                cf.fieldCriteria(fef.filterByAttribute(KNOWN_NUMBER_ATTR), cf.lt(200)))));

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING1_ID);
    }

    @Test
    public void notGrantedAccessWithAnd() {
        final PolicyRestrictedSearchAggregation aggregation = aggregationForUser1(cf.and(Arrays.asList(
                cf.fieldCriteria(fef.filterByAttribute(KNOWN_STRING_ATTR), cf.eq(THING1_KNOWN_STR_ATTR_VALUE)),
                cf.fieldCriteria(fef.filterByAttribute(KNOWN_BOOL_ATTR), cf.eq(THING1_KNOWN_BOOL_ATTR_VALUE)))));

        final Collection<String> result = findAll(aggregation);
        assertThat(result).isEmpty();
    }

    @Test
    public void grantedAccessWithOr() {
        final PolicyRestrictedSearchAggregation aggregation = aggregationForUser1(cf.or(Arrays.asList(
                cf.fieldCriteria(fef.filterByAttribute(KNOWN_STRING_ATTR), cf.eq(THING1_KNOWN_STR_ATTR_VALUE)),
                cf.fieldCriteria(fef.filterByAttribute(KNOWN_BOOL_ATTR), cf.eq(THING1_KNOWN_BOOL_ATTR_VALUE)))));

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING1_ID);
    }

    @Test
    public void notGrantedAccessWithOr() {
        // user1 has access to KNOWN_STRING_ATTR but no access to KNOWN_BOOL_ATTR.
        // KNOWN_STRING_ATTR does not match the search criteria but KNOWN_BOOL_ATTR does.
        // user1 should get an empty result because the matching attribute isn't visible.
        final PolicyRestrictedSearchAggregation aggregation = aggregationForUser1(cf.or(Arrays.asList(
                cf.fieldCriteria(fef.filterByAttribute(KNOWN_STRING_ATTR), cf.eq("does_not_match")),
                cf.fieldCriteria(fef.filterByAttribute(KNOWN_BOOL_ATTR), cf.eq(THING1_KNOWN_BOOL_ATTR_VALUE)))));

        final Collection<String> result = findAll(aggregation);
        assertThat(result).isEmpty();
    }

    @Test
    public void grantedAccessWithNotOr() {
        final PolicyRestrictedSearchAggregation aggregation = aggregationForUser1(cf.nor(cf.or(Arrays.asList(
                cf.fieldCriteria(fef.filterByAttribute(KNOWN_STRING_ATTR), cf.eq("does_not_match")),
                cf.fieldCriteria(fef.filterByAttribute(KNOWN_BOOL_ATTR), cf.ne(THING1_KNOWN_BOOL_ATTR_VALUE))))
        ));

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING1_ID, THING5_ID);
    }

    @Test
    public void grantedAccessWithExists() {
        final PolicyRestrictedSearchAggregation aggregation = aggregationForUser1(
                cf.existsCriteria(fef.existsByAttribute(KNOWN_STRING_ATTR)));

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING1_ID);
    }

    @Test
    public void notGrantedAccessWithExists() {
        final PolicyRestrictedSearchAggregation aggregation = aggregationForUser1(
                cf.existsCriteria(fef.existsByAttribute(KNOWN_BOOL_ATTR)));

        final Collection<String> result = findAll(aggregation);
        assertThat(result).isEmpty();
    }

    @Test
    public void grantedAccessWithNotExists() {
        final PolicyRestrictedSearchAggregation aggregation = aggregationForUser1(cf.nor(
                cf.existsCriteria(fef.existsByAttribute(KNOWN_STRING_ATTR))));

        final Collection<String> result = findAll(aggregation);

        assertThat(result).containsOnly(THING5_ID);
    }

    @Test
    public void notGrantedAccessWithNotExists() {
        final PolicyRestrictedSearchAggregation aggregation = aggregationForUser1(
                cf.nor(Collections.singletonList(cf.existsCriteria(fef.existsByAttribute(KNOWN_BOOL_ATTR)))));

        final Collection<String> result = findAll(aggregation);

        assertThat(result).containsOnly(THING5_ID);
    }

    private PolicyRestrictedSearchAggregation aggregationForUser1(final Criteria criteria) {
        return abf
                .newBuilder(criteria)
                .authorizationSubjects(SUBJECTS_USER_1)
                .build();
    }


    private void insertThings() {
        final Attributes attributes1 = Attributes.newBuilder()
                .set(KNOWN_STRING_ATTR, THING1_KNOWN_STR_ATTR_VALUE)
                .set(KNOWN_NUMBER_ATTR, THING1_KNOWN_NUM_ATTR_VALUE)
                .set(KNOWN_BOOL_ATTR, THING1_KNOWN_BOOL_ATTR_VALUE)
                .set(KNOWN_REGEX_ATTR, THING1_KNOWN_STR_REGEX_VALUE)
                .build();
        final Attributes attributes2 = Attributes.newBuilder()
                .set(KNOWN_STRING_ATTR, THING2_KNOWN_STR_ATTR_VALUE)
                .set(KNOWN_NUMBER_ATTR, THING2_KNOWN_NUM_ATTR_VALUE)
                .set(KNOWN_BOOL_ATTR, THING2_KNOWN_BOOL_ATTR_VALUE)
                .set(KNOWN_REGEX_ATTR, THING2_KNOWN_STR_REGEX_VALUE)
                .build();
        final Attributes attributes3 = Attributes.newBuilder()
                .set(KNOWN_STRING_ATTR, THING3_KNOWN_STR_ATTR_VALUE)
                .set(KNOWN_NUMBER_ATTR, THING3_KNOWN_NUM_ATTR_VALUE)
                .set(KNOWN_BOOL_ATTR, THING3_KNOWN_BOOL_ATTR_VALUE)
                .set(KNOWN_REGEX_ATTR, THING3_KNOWN_STR_REGEX_VALUE)
                .build();
        final Attributes attributes4 = Attributes.newBuilder()
                .set(KNOWN_STRING_ATTR, THING4_KNOWN_STR_ATTR_VALUE)
                .set(KNOWN_NUMBER_ATTR, THING4_KNOWN_NUM_ATTR_VALUE)
                .set(KNOWN_BOOL_ATTR, THING4_KNOWN_BOOL_ATTR_VALUE)
                .set(KNOWN_REGEX_ATTR, THING4_KNOWN_STR_REGEX_VALUE)
                .build();
        final Attributes attributes5 = Attributes.newBuilder()
                .set(KNOWN_NUMBER_ATTR2, THING4_KNOWN_NUM_ATTR_VALUE)
                .set(KNOWN_BOOL_ATTR2, THING4_KNOWN_BOOL_ATTR_VALUE)
                .set(KNOWN_REGEX_ATTR2, THING4_KNOWN_STR_REGEX_VALUE)
                .build();

        // create the things
        persistThing(createThingV2(THING1_ID).setAttributes(attributes1));
        persistThing(createThingV2(THING2_ID, "other:policy").setAttributes(attributes2));
        persistThing(createThingV2(THING3_ID, "other:policy").setAttributes(attributes3));
        persistThing(createThingV2(THING4_ID, "other:policy").setAttributes(attributes4));
        persistThing(createThingV2(THING5_ID).setAttributes(attributes5));
    }

    /**
     * Overriden to get different policyEnforcer for the different Things.
     */
    @Override
    protected PolicyEnforcer getPolicyEnforcer(final String thingId) {
        if (thingId.equals(THING1_ID) || thingId.equals(THING5_ID)) {
            return policyEnforcerThing1_5;
        }
        return policyEnforcerThing2_3_4;
    }

    private static Policy createPolicy1_5() {
        final List<PolicyEntry> entries = Arrays.asList(
                // allow some attributes for user 1
                createPolicyEntry("user1_stuff",
                        Arrays.asList("attributes/" + KNOWN_STRING_ATTR,
                                "attributes/" + KNOWN_NUMBER_ATTR,
                                "attributes/" + KNOWN_BOOL_ATTR2),
                        SUBJECTS_USER_1
                ),
                // allow some attributes for user 2
                createPolicyEntry("user2_stuff",
                        Arrays.asList("attributes/" + KNOWN_BOOL_ATTR,
                                "attributes/" + KNOWN_REGEX_ATTR),
                        SUBJECTS_USER_2
                )
        );

        return PoliciesModelFactory
                .newPolicyBuilder(POLICY_ID)
                .setAll(entries)
                .setRevision(1L)
                .build();
    }

    private static Policy createPolicy2_3_4() {
        return PoliciesModelFactory
                .newPolicyBuilder("other:policy")
                .setRevision(1L)
                .build();
    }


    private static PolicyEntry createPolicyEntry(final String label, final Collection<String> resourceKeys,
            final Collection<String> subjects) {
        return createPolicyEntry(label, resourceKeys, subjects, Collections
                        .singletonList(Permission.READ.name()),
                Collections.emptyList());
    }


    private static PolicyEntry createPolicyEntry(final String label,
            final Collection<String> resourceKeys,
            final Collection<String> subjects,
            final Collection<String> grantedPermissions,
            final Collection<String> revokedPermissions) {
        final Collection<Subject> s = subjects.stream()
                .map(subjectId -> Subject.newInstance(subjectId, SubjectType.UNKNOWN))
                .collect(Collectors.toList());
        final Collection<Resource> r = resourceKeys.stream()
                .map(key -> Resource.newInstance(
                        PoliciesResourceType.thingResource(key), EffectedPermissions.newInstance
                                (grantedPermissions, revokedPermissions)))
                .collect(Collectors.toList());
        return PolicyEntry.newInstance(label, s, r);
    }

}
