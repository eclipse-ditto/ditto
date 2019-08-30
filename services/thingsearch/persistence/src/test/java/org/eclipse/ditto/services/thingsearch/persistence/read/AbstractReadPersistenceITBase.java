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
package org.eclipse.ditto.services.thingsearch.persistence.read;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.model.policies.EffectedPermissions;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Permission;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.thingsearch.common.model.ResultList;
import org.eclipse.ditto.services.thingsearch.persistence.AbstractThingSearchPersistenceITBase;
import org.junit.Before;


/**
 * Abstract base class for search persistence tests.
 */
public abstract class AbstractReadPersistenceITBase extends AbstractThingSearchPersistenceITBase {

    static final PolicyId POLICY_ID = PolicyId.of("global", "policy");

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

    boolean isV1() {
        return getVersion() == JsonSchemaVersion.V_1;
    }

    Thing persistThing(final Thing thing) {
        if (isV1()) {
            return persistThingV1(thing);
        } else {
            return persistThingV2(thing);
        }
    }

    Thing createThing(final String thingId) {
        return createThing(ThingId.of(thingId));
    }

    Thing createThing(final ThingId thingId) {
        if (isV1()) {
            return createThingV1(thingId);
        } else {
            return createThingV2(thingId);
        }
    }

    List<Thing> createThings(final Collection<ThingId> thingIds) {
        return thingIds
                .stream()
                .map(this::createThing)
                .collect(Collectors.toList());
    }


    /**
     * Create a thing v1 with {@link Permission#READ} for {@link #KNOWN_SUBJECTS}.
     *
     * @param id The id of the thing.
     * @return The created (not persisted) Thing object.
     */
    Thing createThingV1(final ThingId id) {
        return createThingV1(id, KNOWN_SUBJECTS);
    }

    Thing createThingV1(final ThingId id, final Collection<String> subjects) {
        final List<AclEntry> aclEntries = subjects.stream()
                .map(subject -> AclEntry.newInstance(AuthorizationSubject.newInstance(subject),
                        Collections.singletonList(Permission.READ)))
                .collect(Collectors.toList());
        return Thing.newBuilder()
                .setId(id)
                .setRevision(0L)
                .setPermissions(aclEntries)
                .build();
    }

    Thing persistThingV1(final Thing thingV1) {
        log.info("EXECUTED {}", runBlockingWithReturn(writePersistence.writeThingWithAcl(thingV1)));
        return thingV1;
    }

    Thing createThingV2(final ThingId id) {
        return createThingV2(id, POLICY_ID.toString());
    }

    Thing createThingV2(final ThingId id, final String policyId) {
        return Thing.newBuilder()
                .setId(id)
                .setPolicyId(policyId)
                .setRevision(0L)
                .build();
    }

    Thing persistThingV2(final Thing thingV2) {
        final Enforcer enforcer = getPolicyEnforcer(thingV2.getEntityId().orElseThrow(IllegalStateException::new));
        log.info("EXECUTED {}", runBlockingWithReturn(writePersistence.write(thingV2, enforcer, 0L)));
        return thingV2;
    }

    /**
     * Get the {@link JsonSchemaVersion} with which the Test is executed. If not overridden by subclass, it will return
     * {@link JsonSchemaVersion#LATEST}.
     *
     * @return {@link JsonSchemaVersion#LATEST}
     */
    JsonSchemaVersion getVersion() {
        return JsonSchemaVersion.LATEST;
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
