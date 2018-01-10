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

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.EffectedPermissions;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcers;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Permission;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.thingsearch.persistence.AbstractThingSearchPersistenceITBase;
import org.eclipse.ditto.services.thingsearch.persistence.write.impl.MongoEventToPersistenceStrategyFactory;
import org.eclipse.ditto.services.thingsearch.persistence.write.impl.MongoThingsSearchUpdaterPersistence;
import org.junit.Before;


/**
 * Abstract base class for search persistence tests.
 */
public abstract class AbstractReadPersistenceITBase extends AbstractThingSearchPersistenceITBase {

    static final String POLICY_ID = "global:policy";

    private PolicyEnforcer policyEnforcer;

    @Before
    public void initTestDataPersistence() {
        policyEnforcer = PolicyEnforcers.defaultEvaluator(createPolicy());
        writePersistence = new MongoThingsSearchUpdaterPersistence(getClient(),
                log, MongoEventToPersistenceStrategyFactory.getInstance());
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
        if (isV1()) {
            return createThingV1(thingId);
        } else {
            return createThingV2(thingId);
        }
    }

    List<Thing> createThings(final Collection<String> thingIds) {
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
    Thing createThingV1(final String id) {
        return createThingV1(id, KNOWN_SUBJECTS);
    }

    Thing createThingV1(final String id, final Collection<String> subjects) {
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
        final long revision = thingV1.getRevision()
                .orElseThrow(() ->
                        new RuntimeException(MessageFormat.format("Thing <{}> does not contain revision", thingV1)))
                .toLong();
        assertThat(runBlockingWithReturn(writePersistence.insertOrUpdate(thingV1, revision, -1L)))
                .isTrue();
        return thingV1;
    }

    Thing createThingV2(final String id) {
        return createThingV2(id, POLICY_ID);
    }

    Thing createThingV2(final String id, final String policyId) {
        return Thing.newBuilder()
                .setId(id)
                .setPolicyId(policyId)
                .setRevision(0L)
                .build();
    }

    Thing persistThingV2(final Thing thingV2) {
        final long revision = thingV2.getRevision()
                .orElseThrow(() ->
                        new RuntimeException(MessageFormat.format("Thing <{}> does not contain revision", thingV2)))
                .toLong();
        assertThat(runBlockingWithReturn(writePersistence.insertOrUpdate(thingV2, revision, 0L)))
                .isTrue();
        assertThat(runBlockingWithReturn(writePersistence.updatePolicy(thingV2, getPolicyEnforcer(thingV2.getId()
                .orElseThrow(() -> new IllegalStateException("not possible"))))))
                .isTrue();
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
     * Get the PolicyEnforcer that is used by the Test. If not overridden by subclass, it will allow the {@link
     * #KNOWN_SUBJECTS} {@code READ} access to {@code thing:/}.
     *
     * @param thingId The thingId for which the policy enforcer should be got
     */
    PolicyEnforcer getPolicyEnforcer(final String thingId) {
        return policyEnforcer;
    }

    private static Policy createPolicy() {
        final Collection<Subject> subjects =
                KNOWN_SUBJECTS.stream()
                        .map(subjectId -> Subject.newInstance(subjectId, SubjectType.UNKNOWN))
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
