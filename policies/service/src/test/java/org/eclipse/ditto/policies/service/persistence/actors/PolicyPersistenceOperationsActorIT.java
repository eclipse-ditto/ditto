/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.service.persistence.actors;

import java.util.Arrays;
import java.util.Collections;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.service.persistence.serializer.PolicyMongoSnapshotAdapter;
import org.eclipse.ditto.internal.utils.persistence.mongo.ops.eventsource.MongoEventSourceITAssertions;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyResponse;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * Tests {@link org.eclipse.ditto.policies.service.persistence.actors.PolicyPersistenceOperationsActor}.
 */
@AllValuesAreNonnullByDefault
public final class PolicyPersistenceOperationsActorIT extends MongoEventSourceITAssertions<PolicyId> {

    @Test
    public void purgeNamespaceWithoutSuffix() {
        assertPurgeNamespace();
    }

    @Test
    public void purgeEntitiesWithNamespace() {
        assertPurgeEntitiesWithNamespace();
    }

    @Override
    protected String getServiceName() {
        // this loads the policies.conf as ActorSystem conf
        return "policies";
    }

    @Override
    protected String getResourceType() {
        return PolicyCommand.RESOURCE_TYPE;
    }

    @Override
    protected PolicyId toEntityId(final CharSequence entityId) {
        return PolicyId.of(entityId);
    }

    @Override
    protected Object getCreateEntityCommand(final PolicyId id) {
        final Policy policy = Policy.newBuilder(id)
                .forLabel("DUMMY")
                .setSubject("ditto:random-subject", SubjectType.GENERATED)
                .setResource(Resource.newInstance(PolicyCommand.RESOURCE_TYPE, "/",
                        EffectedPermissions.newInstance(Arrays.asList("READ", "WRITE"), Collections.emptyList())))
                .build();
        return CreatePolicy.of(policy, DittoHeaders.empty());
    }

    @Override
    protected Class<?> getCreateEntityResponseClass() {
        return CreatePolicyResponse.class;
    }

    @Override
    protected Object getRetrieveEntityCommand(final PolicyId id) {
        return RetrievePolicy.of(id, DittoHeaders.empty());
    }

    @Override
    protected Class<?> getRetrieveEntityResponseClass() {
        return RetrievePolicyResponse.class;
    }

    @Override
    protected Class<?> getEntityNotAccessibleClass() {
        return PolicyNotAccessibleException.class;
    }

    @Override
    protected ActorRef startActorUnderTest(final ActorSystem actorSystem, final ActorRef pubSubMediator,
            final Config config) {

        final Props opsActorProps = PolicyPersistenceOperationsActor.props(pubSubMediator, mongoDbConfig, config,
                persistenceOperationsConfig);
        return actorSystem.actorOf(opsActorProps, PolicyPersistenceOperationsActor.ACTOR_NAME);
    }

    @Override
    protected ActorRef startEntityActor(final ActorSystem system, final ActorRef pubSubMediator, final PolicyId id) {
        final Props props = PolicySupervisorActor.props(pubSubMediator, new PolicyMongoSnapshotAdapter(),
                Mockito.mock(DistributedPub.class));

        return system.actorOf(props, id.toString());
    }

}
