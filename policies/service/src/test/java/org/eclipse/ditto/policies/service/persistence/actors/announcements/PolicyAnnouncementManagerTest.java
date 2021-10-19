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
package org.eclipse.ditto.policies.service.persistence.actors.announcements;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyLifecycle;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectAnnouncement;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectType;
import org.junit.After;
import org.junit.Test;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.japi.pf.ReceiveBuilder;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link PolicyAnnouncementManager}.
 */
public final class PolicyAnnouncementManagerTest {

    private final ActorSystem system = ActorSystem.create();

    @After
    public void shutdown() {
        TestKit.shutdownActorSystem(system);
    }

    @Test
    public void deleting2SubjectsPublishesSubjectDeletionAnnouncement() {
        new TestKit(system) {{
            final Props forwarderProps = Props.create(Forwarder.class, () -> new Forwarder(getRef()));
            final Props propsUnderTest = Props.create(PolicyAnnouncementManager.class,
                    () -> new PolicyAnnouncementManager(s -> forwarderProps));

            final var subjectId = SubjectId.newInstance("issue:subject");
            final var announcement = SubjectAnnouncement.of(null, true);
            final var subject1 = Subject.newInstance(subjectId, SubjectType.newInstance("type1"), null, announcement);
            final var subject2 = Subject.newInstance(subjectId, SubjectType.newInstance("type2"), null, announcement);
            final var subject3 = Subject.newInstance("issuer:subject3", SubjectType.newInstance("type3"));
            final Policy policy1 = PoliciesModelFactory.newPolicyBuilder(PolicyId.of("policy:id"))
                    .setLifecycle(PolicyLifecycle.ACTIVE)
                    .forLabel("label1")
                    .setSubject(subject1)
                    .setSubject(subject3)
                    .setGrantedPermissions("thing", JsonPointer.empty(), "READ", "WRITE")
                    .setGrantedPermissions("policy", JsonPointer.empty(), "READ", "WRITE")
                    .forLabel("label2")
                    .setSubject(subject2)
                    .setGrantedPermissions("thing", JsonPointer.empty(), "READ", "WRITE")
                    .build();
            final Policy policy2 = policy1.toBuilder()
                    .forLabel("label1")
                    .removeSubject(subject1)
                    .forLabel("label2")
                    .removeSubject(subject2)
                    .build();

            final ActorRef underTest = childActorOf(propsUnderTest, "underTest");
            underTest.tell(policy1, getRef());
            final var child1 = watch(expectMsgClass(ActorRef.class));
            final var child2 = watch(expectMsgClass(ActorRef.class));
            underTest.tell(policy2, getRef());

            final var terminatedActor = new AtomicReference<ActorRef>();
            final var subjectDeletedActor = new AtomicReference<ActorRef>();
            final Consumer<Object> assertMessage = message -> {
                if (message instanceof Terminated) {
                    terminatedActor.set(((Terminated) message).getActor());
                } else {
                    assertThat(message).isEqualTo(SubjectExpiryActor.Message.SUBJECT_DELETED);
                    subjectDeletedActor.set(getLastSender());
                }
            };

            final var message1 = expectMsgClass(Object.class);
            assertMessage.accept(message1);
            final var message2 = expectMsgClass(Object.class);
            assertMessage.accept(message2);
            final var childActors = Set.of(child1, child2);
            assertThat(terminatedActor.get()).isIn(childActors);
            assertThat(subjectDeletedActor.get()).isIn(childActors);
            assertThat(terminatedActor.get()).isNotEqualTo(subjectDeletedActor.get());
        }};
    }

    private static final class Forwarder extends AbstractActor {

        private final ActorRef actorRef;

        private Forwarder(final ActorRef actorRef) {
            this.actorRef = actorRef;
        }

        @Override
        public void preStart() {
            actorRef.tell(getSelf(), getSelf());
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .matchAny(message -> actorRef.tell(message, getSelf()))
                    .build();
        }
    }
}
