/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.expression.FieldExpressionUtil;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.service.common.model.ResultList;
import org.eclipse.ditto.thingsearch.service.persistence.AbstractThingSearchPersistenceITBase;
import org.junit.Before;


/**
 * Abstract base class for search persistence tests.
 */
public abstract class AbstractReadPersistenceITBase extends AbstractThingSearchPersistenceITBase {

    static final PolicyId POLICY_ID = PolicyId.of("global", "policy");

    protected static final Map<String, String> SIMPLE_FIELD_MAPPINGS = new HashMap<>();
    static {
        SIMPLE_FIELD_MAPPINGS.put(FieldExpressionUtil.FIELD_NAME_THING_ID, FieldExpressionUtil.FIELD_ID);
        SIMPLE_FIELD_MAPPINGS.put(FieldExpressionUtil.FIELD_NAME_NAMESPACE, FieldExpressionUtil.FIELD_NAMESPACE);
    }

    private Enforcer policyEnforcer;

    @Before
    @Override
    public void before() {
        super.before();
        policyEnforcer = PolicyEnforcers.defaultEvaluator(createPolicy());
    }

    ResultList<ThingId> findForCriteria(final Criteria criteria) {
        return findAll(qbf.newBuilder(criteria).build());
    }

    Thing persistThing(final Thing thing) {
        return persistThingV2(thing);
    }

    Thing createThing(final String thingId) {
        return createThing(ThingId.of(thingId));
    }

    Thing createThing(final ThingId thingId) {
        return createThingV2(thingId);
    }

    List<Thing> createThings(final Collection<ThingId> thingIds) {
        return thingIds
                .stream()
                .map(this::createThing)
                .collect(Collectors.toList());
    }

    void deleteThing(final Thing thing, final long policyRevision) {
        deleteThing(thing.getEntityId()
                        .orElseThrow(() -> new IllegalArgumentException("Thing should contain an entity id.")),
                thing.getRevision()
                        .orElseThrow(() -> new IllegalArgumentException("Thing should have a revision."))
                        .toLong(),
                thing.getPolicyEntityId().orElse(null),
                policyRevision);
    }

    void deleteThing(final ThingId thingId, final long revision, @Nullable final PolicyId policyId,
            final long policyRevision) {

        runBlockingWithReturn(writePersistence.delete(thingId, revision, policyId, policyRevision));
    }

    Thing createThingV2(final ThingId id) {
        return Thing.newBuilder()
                .setId(id)
                .setPolicyId(POLICY_ID)
                .setRevision(0L)
                .build();
    }

    Thing persistThingV2(final Thing thingV2) {
        final Enforcer enforcer = getPolicyEnforcer(thingV2.getEntityId().orElseThrow(IllegalStateException::new));
        log.info("EXECUTED {}", runBlockingWithReturn(writePersistence.write(thingV2, enforcer, 0L)));
        return thingV2;
    }

    /**
     * Get the Enforcer that is used by the Test. If not overridden by subclass, it will allow the {@link
     * #KNOWN_SUBJECTS} {@code READ} access to {@code thing:/}.
     *
     * @param thingId The thingId for which the policy enforcer should be got
     */
    Enforcer getPolicyEnforcer(final ThingId thingId) {
        return policyEnforcer;
    }

    private static Policy createPolicy() {
        final Collection<Subject> subjects =
                KNOWN_SUBJECTS.stream()
                        .map(subjectId -> Subject.newInstance(subjectId, SubjectType.GENERATED))
                        .collect(Collectors.toList());
        final Collection<Resource> resources = Collections.singletonList(Resource.newInstance(
                ResourceKey.newInstance("thing:/"),
                EffectedPermissions.newInstance(Collections.singletonList("READ"), Collections.emptyList())
        ));
        final PolicyEntry policyEntry = PolicyEntry.newInstance("viewer", subjects, resources);
        return PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(policyEntry)
                .setRevision(1L)
                .build();
    }

}
