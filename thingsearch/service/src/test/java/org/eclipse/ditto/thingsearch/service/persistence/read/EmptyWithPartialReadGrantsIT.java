/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.thingsearch.service.persistence.read;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;

import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.thingsearch.service.persistence.TestConstants;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests {@code empty()} for subjects which only have partial READ grants on paths that are absent in some things.
 */
public final class EmptyWithPartialReadGrantsIT extends AbstractReadPersistenceITBase {

    private static final String FEATURE_ID = "lamp";

    /**
     * Thing without attributes, with a feature without property {@code x}.
     */
    private static final ThingId THING_ABSENT_ID =
            TestConstants.thingId(TestConstants.Thing.NAMESPACE, "absent");

    /**
     * Thing with empty attributes {@code {}}, with a feature with property {@code x} set to {@code 0}.
     */
    private static final ThingId THING_EMPTY_ID =
            TestConstants.thingId(TestConstants.Thing.NAMESPACE, "empty");

    /**
     * Thing with attribute {@code foo}, with a feature with property {@code x} set to {@code "on"}.
     */
    private static final ThingId THING_SET_ID =
            TestConstants.thingId(TestConstants.Thing.NAMESPACE, "set");

    private static final String SUBJECT_ATTRIBUTES = "test:attributes";
    private static final String SUBJECT_FOO = "test:foo";
    private static final String SUBJECT_PROPERTY = "test:property";
    private static final String SUBJECT_REVOKED = "test:revoked";
    private static final String SUBJECT_ABSENT_ONLY = "test:absent-only";
    private static final String SUBJECT_FEATURE_ID = "test:feature-id";

    private static final Policy POLICY = PoliciesModelFactory.newPolicy("""
            {
              "policyId": "%s",
              "revision": 1,
              "entries": {
                "ATTRIBUTES": {
                  "subjects": { "%s": { "type": "generated" } },
                  "resources": {
                    "thing:/thingId": { "grant": ["READ"], "revoke": [] },
                    "thing:/attributes": { "grant": ["READ"], "revoke": [] }
                  }
                },
                "FOO": {
                  "subjects": { "%s": { "type": "generated" } },
                  "resources": {
                    "thing:/thingId": { "grant": ["READ"], "revoke": [] },
                    "thing:/attributes/foo": { "grant": ["READ"], "revoke": [] }
                  }
                },
                "PROPERTY": {
                  "subjects": { "%s": { "type": "generated" } },
                  "resources": {
                    "thing:/thingId": { "grant": ["READ"], "revoke": [] },
                    "thing:/features/%s/properties/x": { "grant": ["READ"], "revoke": [] }
                  }
                },
                "REVOKED": {
                  "subjects": { "%s": { "type": "generated" } },
                  "resources": {
                    "thing:/": { "grant": ["READ"], "revoke": [] },
                    "thing:/attributes": { "grant": [], "revoke": ["READ"] }
                  }
                },
                "ABSENT_ONLY": {
                  "subjects": { "%s": { "type": "generated" } },
                  "resources": {
                    "thing:/attributes/foo": { "grant": ["READ"], "revoke": [] }
                  }
                },
                "FEATURE_ID": {
                  "subjects": { "%s": { "type": "generated" } },
                  "resources": {
                    "thing:/thingId": { "grant": ["READ"], "revoke": [] },
                    "thing:/features/%s/id": { "grant": ["READ"], "revoke": [] }
                  }
                }
              }
            }
            """.formatted(POLICY_ID, SUBJECT_ATTRIBUTES, SUBJECT_FOO, SUBJECT_PROPERTY, FEATURE_ID, SUBJECT_REVOKED,
            SUBJECT_ABSENT_ONLY, SUBJECT_FEATURE_ID, FEATURE_ID));

    private final ThingsFieldExpressionFactory ef = ThingsFieldExpressionFactory.of(SIMPLE_FIELD_MAPPINGS);

    @Before
    public void createTestData() {
        persistThing(createThing(THING_ABSENT_ID)
                .setFeatures(features(Feature.newBuilder().withId(FEATURE_ID).build().setProperty("y", 1))));
        persistThing(createThing(THING_EMPTY_ID)
                .setAttributes(ThingsModelFactory.emptyAttributes())
                .setFeatures(features(Feature.newBuilder().withId(FEATURE_ID).build().setProperty("x", 0))));
        persistThing(createThing(THING_SET_ID)
                .setAttribute("foo", "bar")
                .setFeatures(features(Feature.newBuilder().withId(FEATURE_ID).build().setProperty("x", "on"))));
    }

    @Override
    Policy getPolicy(final ThingId thingId) {
        return POLICY;
    }

    @Test
    public void emptyAttributesWithReadOnAttributes() {
        final Criteria crit = cf.emptyCriteria(ef.existsBy("attributes"));

        assertThat(findFor(crit, SUBJECT_ATTRIBUTES)).containsOnly(THING_ABSENT_ID, THING_EMPTY_ID);
    }

    @Test
    public void notEmptyAttributesWithReadOnAttributes() {
        final Criteria crit = cf.nor(cf.emptyCriteria(ef.existsBy("attributes")));

        assertThat(findFor(crit, SUBJECT_ATTRIBUTES)).containsOnly(THING_SET_ID);
    }

    @Test
    public void emptyAbsentAttributeWithReadOnAttribute() {
        final Criteria crit = cf.emptyCriteria(ef.existsByAttribute("foo"));

        assertThat(findFor(crit, SUBJECT_FOO)).containsOnly(THING_ABSENT_ID, THING_EMPTY_ID);
    }

    @Test
    public void notEmptyAbsentAttributeWithReadOnAttribute() {
        final Criteria crit = cf.nor(cf.emptyCriteria(ef.existsByAttribute("foo")));

        assertThat(findFor(crit, SUBJECT_FOO)).containsOnly(THING_SET_ID);
    }

    @Test
    public void emptyAbsentPropertyOfWildcardFeatureWithReadOnProperty() {
        final Criteria crit = cf.emptyCriteria(ef.existsBy("features/*/properties/x"));

        assertThat(findFor(crit, SUBJECT_PROPERTY)).containsOnly(THING_ABSENT_ID);
    }

    @Test
    public void emptyAbsentPropertyOfFeatureWithReadOnProperty() {
        final Criteria crit = cf.emptyCriteria(ef.existsByFeatureProperty(FEATURE_ID, "x"));

        assertThat(findFor(crit, SUBJECT_PROPERTY)).containsOnly(THING_ABSENT_ID);
    }

    @Test
    public void emptyAttributesWithRevokedReadOnAttributes() {
        // the revoke on attributes applies whether attributes exist or not
        final Criteria crit = cf.emptyCriteria(ef.existsBy("attributes"));

        assertThat(findFor(crit, SUBJECT_REVOKED)).isEmpty();
    }

    @Test
    public void subjectWithGrantOnlyOnAbsentPathDoesNotFindThing() {
        // the subject may read attributes/foo of THING_SET_ID, but has no grant on a path which exists in the other
        // things, so it must not find those
        assertThat(findFor(cf.any(), SUBJECT_ABSENT_ONLY)).containsOnly(THING_SET_ID);
        assertThat(findFor(cf.emptyCriteria(ef.existsByAttribute("foo")), SUBJECT_ABSENT_ONLY)).isEmpty();
    }

    @Test
    public void grantOnFeatureIdPathIsNoGrantOnFeature() {
        // "features/lamp/id" is not a field of the feature, a grant on it must not count as READ on the feature
        final Criteria crit = cf.emptyCriteria(ef.existsBy("features/*/properties/x"));

        assertThat(findFor(crit, SUBJECT_FEATURE_ID)).isEmpty();
    }

    private Collection<ThingId> findFor(final Criteria criteria, final String subject) {
        return findAll(qbf.newBuilder(criteria).build(), List.of(subject));
    }

    private static Features features(final Feature feature) {
        return Features.newBuilder().set(feature).build();
    }

}
