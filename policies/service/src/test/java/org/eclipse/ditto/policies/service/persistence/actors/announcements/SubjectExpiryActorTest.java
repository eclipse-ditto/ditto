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
import static org.eclipse.ditto.policies.service.persistence.actors.announcements.SubjectExpiryActor.Message.SUBJECT_DELETED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.common.DittoDuration;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectAnnouncement;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.signals.announcements.PolicyAnnouncement;
import org.eclipse.ditto.policies.model.signals.announcements.SubjectDeletionAnnouncement;
import org.eclipse.ditto.policies.service.common.config.PolicyAnnouncementConfig;
import org.eclipse.ditto.policies.service.persistence.actors.strategies.commands.SudoDeleteExpiredSubject;
import org.junit.After;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.FSM;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link SubjectExpiryActor}.
 * <p>
 * Tests are named test[hasExpiry][hasBeforeExpiry][whenDeleted][hasAcknowledgement][description]
 * where each boolean flag that follows are coded with true=1 and false=0.
 * It is not necessary to test all 16 combinations of the boolean flags because they are correlated, e.g.
 * there can be no beforeExpiry announcement if there is no expiry.
 * <p>
 * [description] is one of:
 * <ul>
 * <li>[empty-string]: happy path</li>
 * <li>purge[0-9]: subject deleted after the i-th message</li>
 * <li>error[0-9]: error after the i-th message</li>
 * <li>grace[0-9]: grace period exceeded after the i-th message</li>
 * </ul>
 * Not all combinations are present because e.g. a test without acknowledgement or persistence requests cannot fail or
 * exceed the grace period. Some cases can be combined, e.g., purge1grace2 for subject deleted after first message
 * and grace period exceeded after second message.
 */
public final class SubjectExpiryActorTest {

    private static final SubjectId SUBJECT_DITTO_DITTO = SubjectId.newInstance("ditto:ditto");
    private static final String ACK_LABEL_CONNECTION_ACK = "connection:ack";

    private final ActorSystem system = ActorSystem.create(getClass().getSimpleName(), ConfigFactory.load("test"));
    private final PolicyId policyId = PolicyId.of("policy:id");
    private final Duration maxTimeout = Duration.ofMinutes(1);

    @SuppressWarnings("unchecked")
    private final DistributedPub<PolicyAnnouncement<?>> policiesPub = mock(DistributedPub.class);

    @SuppressWarnings("unchecked")
    private final ArgumentCaptor<PolicyAnnouncement<?>> announcementCaptor =
            ArgumentCaptor.forClass(PolicyAnnouncement.class);

    private final ArgumentCaptor<ActorRef> senderCaptor = ArgumentCaptor.forClass(ActorRef.class);

    private final PolicyAnnouncementConfig config = PolicyAnnouncementConfig.of(ConfigFactory.empty());

    @After
    public void shutdown() {
        TestKit.shutdownActorSystem(system);
    }

    // S0000: SubjectExpiryActor won't be created
    // S0001: Won't be created; no acknowledgement without announcement

    // S0010

    @Test
    public void test0010() {
        // aka test0010purge0
        new TestKit(system) {{
            final Subject subject = createSubject(null,
                    null,
                    true,
                    null,
                    Duration.ofSeconds(0)
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            final var announcement = announcementCaptor.getValue();

            assertThat(announcement).isInstanceOf(SubjectDeletionAnnouncement.class);
            final var subjectDeletionAnnouncement = (SubjectDeletionAnnouncement) announcement;
            assertThat(subjectDeletionAnnouncement.getSubjectIds())
                    .containsExactly(SUBJECT_DITTO_DITTO);
            assertThat(subjectDeletionAnnouncement.getDittoHeaders().getAcknowledgementRequests()).isEmpty();
            expectTerminated(underTest);
            expectNoMessage();
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test0010disableWhenDeleted() {
        // aka test0010purge0
        new TestKit(system) {{
            final Subject subject = createSubject(null,
                    null,
                    true,
                    null,
                    Duration.ofSeconds(0)
            );

            final var disabledConfig = PolicyAnnouncementConfig.of(ConfigFactory.parseMap(
                    Map.of("announcement.enable-announcements-when-deleted", false)
            ));

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), disabledConfig);
            final ActorRef underTest = watch(childActorOf(props));

            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            expectTerminated(underTest);
            expectNoMessage();
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    // S0011

    @Test
    public void test0011() {
        // aka test0011purge0
        new TestKit(system) {{
            final Subject subject = createSubject(null,
                    null,
                    true,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0),
                    ACK_LABEL_CONNECTION_ACK
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            final var announcement = announcementCaptor.getValue();
            final var sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId, HttpStatus.OK,
                    announcement.getDittoHeaders()), getTestActor());

            assertThat(announcement).isInstanceOf(SubjectDeletionAnnouncement.class);
            final var subjectDeletionAnnouncement = (SubjectDeletionAnnouncement) announcement;
            assertThat(subjectDeletionAnnouncement.getSubjectIds())
                    .containsExactly(SUBJECT_DITTO_DITTO);
            assertThat(subjectDeletionAnnouncement.getDittoHeaders().getAcknowledgementRequests())
                    .containsExactly(AcknowledgementRequest.parseAcknowledgementRequest(ACK_LABEL_CONNECTION_ACK));

            expectTerminated(underTest);
            expectNoMessage();
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test0011error2() {
        // aka test0011purge0
        new TestKit(system) {{
            final Subject subject = createSubject(null,
                    null,
                    true,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0),
                    ACK_LABEL_CONNECTION_ACK
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            var announcement = announcementCaptor.getValue();
            var sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    announcement.getDittoHeaders()), getTestActor());

            verify(policiesPub, timeout(30_000).times(2))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            announcement = announcementCaptor.getValue();
            sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId, HttpStatus.OK,
                    announcement.getDittoHeaders()), getTestActor());

            assertThat(announcement).isInstanceOf(SubjectDeletionAnnouncement.class);
            final var subjectDeletionAnnouncement = (SubjectDeletionAnnouncement) announcement;
            assertThat(subjectDeletionAnnouncement.getSubjectIds())
                    .containsExactly(SUBJECT_DITTO_DITTO);
            assertThat(subjectDeletionAnnouncement.getDittoHeaders().getAcknowledgementRequests())
                    .containsExactly(AcknowledgementRequest.parseAcknowledgementRequest(ACK_LABEL_CONNECTION_ACK));

            expectTerminated(underTest);
            expectNoMessage();
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test0011grace2() {
        // aka test0011purge0
        new TestKit(system) {{
            final Subject subject = createSubject(null,
                    null,
                    true,
                    Duration.ofMillis(1),
                    Duration.ofSeconds(0),
                    ACK_LABEL_CONNECTION_ACK
            );

            final Props props =
                    SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4).negated(), policiesPub,
                            maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            verify(policiesPub, timeout(30_000)).publishWithAcks(any(), any(), any(), any());

            expectTerminated(Duration.ofSeconds(10), underTest);
            expectNoMessage();
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    // S01xx: Not possible to send beforeExpiry announcement without expiry

    // S1000

    @Test
    public void test1000() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(3)),
                    null,
                    false,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0)
                    );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            final var deleteCommand = expectMsgClass(Duration.ofSeconds(30), SudoDeleteExpiredSubject.class);
            assertThat(deleteCommand.getSubject()).isEqualTo(subject);
            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            expectTerminated(underTest);
            expectNoMessage();
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    // S1001: Not possible to request acknowledgements without announcement

    // S1010

    @Test
    public void test1010() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(3)),
                    null,
                    true,
                    Duration.ofSeconds(30),
                    null
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            final var deleteCommand = expectMsgClass(Duration.ofSeconds(30), SudoDeleteExpiredSubject.class);
            assertThat(deleteCommand.getSubject()).isEqualTo(subject);
            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            final var announcement = announcementCaptor.getValue();

            assertThat(announcement).isInstanceOf(SubjectDeletionAnnouncement.class);
            final var subjectDeletionAnnouncement = (SubjectDeletionAnnouncement) announcement;
            assertThat(subjectDeletionAnnouncement.getSubjectIds())
                    .containsExactly(SUBJECT_DITTO_DITTO);
            assertThat(subjectDeletionAnnouncement.getDittoHeaders().getAcknowledgementRequests())
                    .isEmpty();

            expectTerminated(underTest);
            expectNoMessage();
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    // S1011

    @Test
    public void test1011() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(3)),
                    null,
                    true,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0),
                    ACK_LABEL_CONNECTION_ACK
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            final var deleteCommand = expectMsgClass(Duration.ofSeconds(30), SudoDeleteExpiredSubject.class);
            assertThat(deleteCommand.getSubject()).isEqualTo(subject);
            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            final var announcement = announcementCaptor.getValue();
            final var sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId, HttpStatus.OK,
                    announcement.getDittoHeaders()), getTestActor());

            assertThat(announcement).isInstanceOf(SubjectDeletionAnnouncement.class);
            final var subjectDeletionAnnouncement = (SubjectDeletionAnnouncement) announcement;
            assertThat(subjectDeletionAnnouncement.getSubjectIds())
                    .containsExactly(SUBJECT_DITTO_DITTO);
            assertThat(subjectDeletionAnnouncement.getDittoHeaders().getAcknowledgementRequests())
                    .containsExactly(AcknowledgementRequest.parseAcknowledgementRequest(ACK_LABEL_CONNECTION_ACK));

            expectTerminated(underTest);
            expectNoMessage();
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test1011error2() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(3)),
                    null,
                    true,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0),
                    ACK_LABEL_CONNECTION_ACK
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            expectMsgClass(Duration.ofSeconds(30), SudoDeleteExpiredSubject.class);
            underTest.tell(FSM.StateTimeout$.MODULE$, ActorRef.noSender());

            expectMsgClass(Duration.ofSeconds(30), SudoDeleteExpiredSubject.class);
            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            final var announcement = announcementCaptor.getValue();
            final var sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId, HttpStatus.OK,
                    announcement.getDittoHeaders()), getTestActor());

            expectTerminated(underTest);
            expectNoMessage();
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test1011grace2() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(3)),
                    null,
                    true,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0),
                    ACK_LABEL_CONNECTION_ACK
            );

            final Props props =
                    SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4).negated(), policiesPub,
                            maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            expectMsgClass(Duration.ofSeconds(30), SudoDeleteExpiredSubject.class);
            underTest.tell(FSM.StateTimeout$.MODULE$, ActorRef.noSender());

            expectTerminated(underTest);
            expectNoMessage();
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    // S1100

    @Test
    public void test1100() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(3)),
                    Duration.ofMillis(500),
                    false,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0)
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), any());
            final var announcement = announcementCaptor.getValue();

            assertThat(announcement).isInstanceOf(SubjectDeletionAnnouncement.class);
            final var subjectDeletionAnnouncement = (SubjectDeletionAnnouncement) announcement;
            assertThat(subjectDeletionAnnouncement.getSubjectIds())
                    .containsExactly(SUBJECT_DITTO_DITTO);
            assertThat(subjectDeletionAnnouncement.getDittoHeaders().getAcknowledgementRequests())
                    .isEmpty();

            final var deleteCommand = expectMsgClass(SudoDeleteExpiredSubject.class);
            assertThat(deleteCommand.getSubject()).isEqualTo(subject);
            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());
            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test1100purge0() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(10)),
                    Duration.ofSeconds(7),
                    false,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0)
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            verify(policiesPub, timeout(30_000)).publishWithAcks(any(), any(), any(), any());

            // no need for expectNoMsg because extraneous SudoDeleteExpiredSubject will cause expectTerminated to fail
            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test1100error3() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(3)),
                    Duration.ofMillis(500),
                    false,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0)
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            verify(policiesPub, timeout(30_000)).publishWithAcks(any(), any(), any(), any());

            var deleteCommand = expectMsgClass(SudoDeleteExpiredSubject.class);
            assertThat(deleteCommand.getSubject()).isEqualTo(subject);
            underTest.tell(FSM.StateTimeout$.MODULE$, ActorRef.noSender());

            // no retry because no announcement: wait for restart to try again
            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test1100grace3() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(3)),
                    Duration.ofMillis(500),
                    false,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0)
            );

            final Props props =
                    SubjectExpiryActor.props(policyId, subject, Duration.ofSeconds(1).negated(), policiesPub,
                            maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), any());
            final var announcement = announcementCaptor.getValue();

            assertThat(announcement).isInstanceOf(SubjectDeletionAnnouncement.class);
            final var subjectDeletionAnnouncement = (SubjectDeletionAnnouncement) announcement;
            assertThat(subjectDeletionAnnouncement.getSubjectIds())
                    .containsExactly(SUBJECT_DITTO_DITTO);
            assertThat(subjectDeletionAnnouncement.getDittoHeaders().getAcknowledgementRequests())
                    .isEmpty();

            var deleteCommand = expectMsgClass(SudoDeleteExpiredSubject.class);
            assertThat(deleteCommand.getSubject()).isEqualTo(subject);
            underTest.tell(FSM.StateTimeout$.MODULE$, ActorRef.noSender());

            // no retry because no announcement: wait for restart to try again
            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    // S1101

    @Test
    public void test1101() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(3)),
                    Duration.ofMillis(500),
                    false,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0),
                    ACK_LABEL_CONNECTION_ACK
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            final var announcement = announcementCaptor.getValue();
            final var sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId, HttpStatus.OK,
                    announcement.getDittoHeaders()), getTestActor());

            assertThat(announcement).isInstanceOf(SubjectDeletionAnnouncement.class);
            final var subjectDeletionAnnouncement = (SubjectDeletionAnnouncement) announcement;
            assertThat(subjectDeletionAnnouncement.getSubjectIds())
                    .containsExactly(SUBJECT_DITTO_DITTO);
            assertThat(subjectDeletionAnnouncement.getDittoHeaders().getAcknowledgementRequests())
                    .containsExactly(AcknowledgementRequest.parseAcknowledgementRequest(ACK_LABEL_CONNECTION_ACK));

            final var deleteCommand = expectMsgClass(SudoDeleteExpiredSubject.class);
            assertThat(deleteCommand.getSubject()).isEqualTo(subject);
            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());
            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test1101purge0() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofMillis(3500)),
                    Duration.ofMillis(500),
                    false,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0),
                    ACK_LABEL_CONNECTION_ACK
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            final var announcement = announcementCaptor.getValue();
            final var sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId, HttpStatus.OK,
                    announcement.getDittoHeaders()), getTestActor());

            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test1101purge1() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofMillis(3500)),
                    Duration.ofMillis(500),
                    false,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0),
                    ACK_LABEL_CONNECTION_ACK
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            final var announcement = announcementCaptor.getValue();
            final var sender = senderCaptor.getValue();

            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId, HttpStatus.OK,
                    announcement.getDittoHeaders()), getTestActor());

            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test1101purge2() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofMillis(10_000)),
                    Duration.ofMillis(9000),
                    false,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0),
                    ACK_LABEL_CONNECTION_ACK
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            final var announcement = announcementCaptor.getValue();
            final var sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId, HttpStatus.OK,
                    announcement.getDittoHeaders()), getTestActor());

            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test1101error1() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(3)),
                    Duration.ofMillis(1500),
                    false,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0),
                    ACK_LABEL_CONNECTION_ACK
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            var announcement = announcementCaptor.getValue();
            var sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId,
                    HttpStatus.INTERNAL_SERVER_ERROR, announcement.getDittoHeaders()), getTestActor());

            verify(policiesPub, timeout(30_000).times(2))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            announcement = announcementCaptor.getValue();
            sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId,
                    HttpStatus.OK, announcement.getDittoHeaders()), getTestActor());

            expectMsgClass(SudoDeleteExpiredSubject.class);
            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());
            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test1101grace1() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(3)),
                    Duration.ofMillis(1500),
                    false,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0),
                    ACK_LABEL_CONNECTION_ACK
            );

            final Props props =
                    SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4).negated(), policiesPub,
                            maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            var announcement = announcementCaptor.getValue();
            var sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId,
                    HttpStatus.INTERNAL_SERVER_ERROR, announcement.getDittoHeaders()), getTestActor());

            expectMsgClass(SudoDeleteExpiredSubject.class);
            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());
            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    // S1110

    @Test
    public void test1110() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(3)),
                    Duration.ofMillis(500),
                    true,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0)
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            final var announcement = announcementCaptor.getValue();

            assertThat(announcement).isInstanceOf(SubjectDeletionAnnouncement.class);
            final var subjectDeletionAnnouncement = (SubjectDeletionAnnouncement) announcement;
            assertThat(subjectDeletionAnnouncement.getSubjectIds())
                    .containsExactly(SUBJECT_DITTO_DITTO);
            assertThat(subjectDeletionAnnouncement.getDittoHeaders().getAcknowledgementRequests())
                    .isEmpty();

            final var deleteCommand = expectMsgClass(SudoDeleteExpiredSubject.class);
            assertThat(deleteCommand.getSubject()).isEqualTo(subject);
            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());
            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test1110purge0() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(160)),
                    Duration.ofMillis(500),
                    true,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0)
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());
            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());

            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test1110purge1() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(60)),
                    Duration.ofSeconds(57),
                    true,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0)
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            verify(policiesPub, timeout(30_000)).publishWithAcks(any(), any(), any(), any());
            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test1110grace2() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(3)),
                    Duration.ofMillis(500),
                    true,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0)
            );

            final Props props =
                    SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4).negated(), policiesPub,
                            maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            verify(policiesPub, timeout(30_000)).publishWithAcks(any(), any(), any(), any());

            expectMsgClass(SudoDeleteExpiredSubject.class);
            underTest.tell(FSM.StateTimeout$.MODULE$, ActorRef.noSender());
            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    // S1111

    @Test
    public void test1111() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(3)),
                    Duration.ofMillis(500),
                    true,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0),
                    ACK_LABEL_CONNECTION_ACK
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            final var announcement = announcementCaptor.getValue();
            final var sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId, HttpStatus.OK,
                    announcement.getDittoHeaders()), getTestActor());

            assertThat(announcement).isInstanceOf(SubjectDeletionAnnouncement.class);
            final var subjectDeletionAnnouncement = (SubjectDeletionAnnouncement) announcement;
            assertThat(subjectDeletionAnnouncement.getSubjectIds())
                    .containsExactly(SUBJECT_DITTO_DITTO);
            assertThat(subjectDeletionAnnouncement.getDittoHeaders().getAcknowledgementRequests())
                    .containsExactly(AcknowledgementRequest.parseAcknowledgementRequest(ACK_LABEL_CONNECTION_ACK));

            final var deleteCommand = expectMsgClass(SudoDeleteExpiredSubject.class);
            assertThat(deleteCommand.getSubject()).isEqualTo(subject);
            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());
            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test1111purge0() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(3)),
                    Duration.ofMillis(500),
                    true,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0),
                    ACK_LABEL_CONNECTION_ACK
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            final var announcement = announcementCaptor.getValue();
            final var sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId, HttpStatus.OK,
                    announcement.getDittoHeaders()), getTestActor());

            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test1111purge1() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(3)),
                    Duration.ofMillis(500),
                    true,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0),
                    ACK_LABEL_CONNECTION_ACK
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            final var announcement = announcementCaptor.getValue();
            final var sender = senderCaptor.getValue();

            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId, HttpStatus.OK,
                    announcement.getDittoHeaders()), getTestActor());

            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test1111purge2() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(10)),
                    Duration.ofMillis(9500),
                    true,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0),
                    ACK_LABEL_CONNECTION_ACK
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            final var announcement = announcementCaptor.getValue();
            final var sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId, HttpStatus.OK,
                    announcement.getDittoHeaders()), getTestActor());

            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test1111purge0error2() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(30)),
                    Duration.ofMillis(500),
                    true,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0),
                    ACK_LABEL_CONNECTION_ACK
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            var announcement = announcementCaptor.getValue();
            var sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId,
                    HttpStatus.REQUEST_TIMEOUT, announcement.getDittoHeaders()), getTestActor());

            verify(policiesPub, timeout(30_000).times(2))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            announcement = announcementCaptor.getValue();
            sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId, HttpStatus.OK,
                    announcement.getDittoHeaders()), getTestActor());

            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test1111purge0grace2() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(30)),
                    Duration.ofMillis(500),
                    true,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0),
                    ACK_LABEL_CONNECTION_ACK
            );

            final Props props =
                    SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4).negated(), policiesPub,
                            maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            final var announcement = announcementCaptor.getValue();
            final var sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId,
                    HttpStatus.REQUEST_TIMEOUT, announcement.getDittoHeaders()), getTestActor());

            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test1111purge1error2() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(3)),
                    Duration.ofMillis(500),
                    true,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0),
                    ACK_LABEL_CONNECTION_ACK
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            var announcement = announcementCaptor.getValue();
            var sender = senderCaptor.getValue();

            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId,
                    HttpStatus.INTERNAL_SERVER_ERROR, announcement.getDittoHeaders()), getTestActor());

            verify(policiesPub, timeout(30_000).times(2))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            announcement = announcementCaptor.getValue();
            sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId,
                    HttpStatus.OK, announcement.getDittoHeaders()), getTestActor());

            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test1111purge1grace2() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(3)),
                    Duration.ofMillis(500),
                    true,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0),
                    ACK_LABEL_CONNECTION_ACK
            );

            final Props props =
                    SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4).negated(), policiesPub,
                            maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            var announcement = announcementCaptor.getValue();
            var sender = senderCaptor.getValue();

            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId,
                    HttpStatus.INTERNAL_SERVER_ERROR, announcement.getDittoHeaders()), getTestActor());

            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test1111grace1() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(10)),
                    Duration.ofMillis(9500),
                    true,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0),
                    ACK_LABEL_CONNECTION_ACK
            );

            final Props props =
                    SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4).negated(), policiesPub,
                            maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            var announcement = announcementCaptor.getValue();
            var sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId,
                    HttpStatus.INTERNAL_SERVER_ERROR, announcement.getDittoHeaders()), getTestActor());

            expectMsgClass(SudoDeleteExpiredSubject.class);
            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            verify(policiesPub, timeout(30_000).times(2))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            announcement = announcementCaptor.getValue();
            sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId,
                    HttpStatus.OK, announcement.getDittoHeaders()), getTestActor());

            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test1111grace1grace5() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(10)),
                    Duration.ofMillis(9500),
                    true,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0),
                    ACK_LABEL_CONNECTION_ACK
            );

            final Props props =
                    SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4).negated(), policiesPub,
                            maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            var announcement = announcementCaptor.getValue();
            var sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId,
                    HttpStatus.INTERNAL_SERVER_ERROR, announcement.getDittoHeaders()), getTestActor());

            expectMsgClass(SudoDeleteExpiredSubject.class);
            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            verify(policiesPub, timeout(30_000).times(2))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            announcement = announcementCaptor.getValue();
            sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId,
                    HttpStatus.REQUEST_TIMEOUT, announcement.getDittoHeaders()), getTestActor());

            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test1111error1() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(3)),
                    Duration.ofMillis(500),
                    true,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0),
                    ACK_LABEL_CONNECTION_ACK
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            var announcement = announcementCaptor.getValue();
            var sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId,
                    HttpStatus.REQUEST_TIMEOUT, announcement.getDittoHeaders()), getTestActor());

            verify(policiesPub, timeout(30_000).times(2))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            announcement = announcementCaptor.getValue();
            sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId,
                    HttpStatus.OK, announcement.getDittoHeaders()), getTestActor());

            expectMsgClass(SudoDeleteExpiredSubject.class);
            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());
            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void test1111error3() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(3)),
                    Duration.ofMillis(500),
                    true,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(0),
                    ACK_LABEL_CONNECTION_ACK
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            verify(policiesPub, timeout(30_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            var announcement = announcementCaptor.getValue();
            var sender = senderCaptor.getValue();

            sender.tell(Acknowledgement.of(AcknowledgementLabel.of(ACK_LABEL_CONNECTION_ACK), policyId,
                    HttpStatus.OK, announcement.getDittoHeaders()), getTestActor());

            expectMsgClass(SudoDeleteExpiredSubject.class);
            underTest.tell(FSM.StateTimeout$.MODULE$, ActorRef.noSender());

            expectTerminated(underTest);
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void testRandomization() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(5)),
                    Duration.ofSeconds(0),
                    false,
                    null,
                    Duration.ofDays(20)
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            verify(policiesPub, timeout(4_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            final var announcement = announcementCaptor.getValue();

            assertThat(announcement).isInstanceOf(SubjectDeletionAnnouncement.class);
            final var subjectDeletionAnnouncement = (SubjectDeletionAnnouncement) announcement;
            assertThat(subjectDeletionAnnouncement.getSubjectIds())
                    .containsExactly(SUBJECT_DITTO_DITTO);
            assertThat(subjectDeletionAnnouncement.getDittoHeaders().getAcknowledgementRequests()).isEmpty();
            expectTerminated(underTest);
            expectNoMessage();
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    @Test
    public void testNoRandomization() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(5)),
                    Duration.ofSeconds(0),
                    false,
                    null,
                    Duration.ZERO
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            expectTerminated(underTest);
            expectNoMessage();
        }};
    }

    @Test
    public void testDefaultRandomization() {
        new TestKit(system) {{
            final Subject subject = createSubject(Instant.now().plus(Duration.ofSeconds(5)),
                    Duration.ofSeconds(0),
                    false,
                    null,
                    null
            );

            final Props props = SubjectExpiryActor.props(policyId, subject, Duration.ofHours(4), policiesPub,
                    maxTimeout, getTestActor(), config);
            final ActorRef underTest = watch(childActorOf(props));

            underTest.tell(SUBJECT_DELETED, ActorRef.noSender());

            verify(policiesPub, timeout(4_000))
                    .publishWithAcks(announcementCaptor.capture(), any(), any(), senderCaptor.capture());
            final var announcement = announcementCaptor.getValue();

            assertThat(announcement).isInstanceOf(SubjectDeletionAnnouncement.class);
            final var subjectDeletionAnnouncement = (SubjectDeletionAnnouncement) announcement;
            assertThat(subjectDeletionAnnouncement.getSubjectIds())
                    .containsExactly(SUBJECT_DITTO_DITTO);
            assertThat(subjectDeletionAnnouncement.getDittoHeaders().getAcknowledgementRequests()).isEmpty();
            expectTerminated(underTest);
            expectNoMessage();
            verifyNoMoreInteractions(policiesPub);
        }};
    }

    private static Subject createSubject(@Nullable final Instant expiry,
            @Nullable final Duration beforeExpiry,
            final boolean whenDeleted,
            @Nullable final Duration ackTimeout,
            @Nullable final Duration randomizationInterval,
            final String... ackLabels) {

        return Subject.newInstance(SUBJECT_DITTO_DITTO,
                SubjectType.UNKNOWN,
                expiry != null ? PoliciesModelFactory.newSubjectExpiry(expiry) : null,
                SubjectAnnouncement.of(
                        beforeExpiry != null ? DittoDuration.of(beforeExpiry) : null,
                        whenDeleted,
                        Arrays.stream(ackLabels)
                                .map(AcknowledgementRequest::parseAcknowledgementRequest)
                                .collect(Collectors.toList()),
                        ackTimeout != null ? DittoDuration.of(ackTimeout) : null,
                        randomizationInterval != null ?DittoDuration.of(randomizationInterval) : null
                )
        );
    }
}
