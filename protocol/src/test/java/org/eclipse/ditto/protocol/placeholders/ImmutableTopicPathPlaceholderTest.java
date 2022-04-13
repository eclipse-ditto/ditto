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
package org.eclipse.ditto.protocol.placeholders;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Tests {@link org.eclipse.ditto.protocol.placeholders.ImmutableTopicPathPlaceholder}.
 */
public final class ImmutableTopicPathPlaceholderTest {

    private static final String KNOWN_NAMESPACE = "org.eclipse.ditto.test";
    private static final String KNOWN_NAME = "myThing";
    private static final TopicPath.Channel KNOWN_CHANNEL = TopicPath.Channel.TWIN;
    private static final TopicPath.Group KNOWN_GROUP = TopicPath.Group.THINGS;
    private static final TopicPath.Criterion KNOWN_CRITERION = TopicPath.Criterion.COMMANDS;
    private static final TopicPath.Action KNOWN_ACTION = TopicPath.Action.MODIFY;
    private static final String KNOWN_SUBJECT = "mySubject";
    private static final String KNOWN_SUBJECT2 = "$set.configuration/steps";

    private static final TopicPath KNOWN_TOPIC_PATH = TopicPath.newBuilder(ThingId.of(KNOWN_NAMESPACE, KNOWN_NAME))
            .twin().things().commands().modify().build();

    private static final TopicPath KNOWN_TOPIC_PATH_SUBJECT1 =
            TopicPath.newBuilder(ThingId.of(KNOWN_NAMESPACE, KNOWN_NAME))
                    .live().things().messages().subject(KNOWN_SUBJECT).build();

    private static final TopicPath KNOWN_TOPIC_PATH_SUBJECT2 =
            TopicPath.newBuilder(ThingId.of(KNOWN_NAMESPACE, KNOWN_NAME))
                    .live().things().messages().subject(KNOWN_SUBJECT2).build();

    private static final ImmutableTopicPathPlaceholder UNDER_TEST = ImmutableTopicPathPlaceholder.INSTANCE;

    /**
     * Assert immutability.
     */
    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(ImmutableTopicPathPlaceholder.class, MutabilityMatchers.areImmutable());
    }

    /**
     * Test hash code and equals.
     */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableTopicPathPlaceholder.class)
                .suppress(Warning.INHERITED_DIRECTLY_FROM_OBJECT)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testReplaceFull() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_TOPIC_PATH, "full")).contains(KNOWN_TOPIC_PATH.getPath());
    }

    @Test
    public void testReplaceNamespace() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_TOPIC_PATH, "namespace")).contains(KNOWN_NAMESPACE);
    }

    @Test
    public void testReplaceEntityName() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_TOPIC_PATH, "entityName")).contains(KNOWN_NAME);
    }

    @Test
    public void testReplaceChannel() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_TOPIC_PATH, "channel")).contains(KNOWN_CHANNEL.getName());
    }

    @Test
    public void testReplaceGroup() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_TOPIC_PATH, "group")).contains(KNOWN_GROUP.getName());
    }

    @Test
    public void testReplaceCriterion() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_TOPIC_PATH, "criterion")).contains(KNOWN_CRITERION.getName());
    }

    @Test
    public void testReplaceAction() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_TOPIC_PATH, "action")).contains(KNOWN_ACTION.getName());
    }

    @Test
    public void testReplaceSubjectEmpty() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_TOPIC_PATH, "subject")).isEmpty();
    }

    @Test
    public void testReplaceSubject() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_TOPIC_PATH_SUBJECT1, "subject")).contains(KNOWN_SUBJECT);
    }

    @Test
    public void testReplaceWeirdSubject() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_TOPIC_PATH_SUBJECT2, "subject")).contains(KNOWN_SUBJECT2);
    }

    @Test
    public void testReplaceActionOrSubject() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_TOPIC_PATH, "action-subject")).contains(KNOWN_ACTION.getName());
    }

    @Test
    public void testReplaceActionOrSubjectWithSubject() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_TOPIC_PATH_SUBJECT1, "action-subject")).contains(KNOWN_SUBJECT);
    }

    @Test
    public void testReplaceActionOrSubjectWithWeirdSubject() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_TOPIC_PATH_SUBJECT2, "action-subject")).contains(KNOWN_SUBJECT2);
    }

    @Test
    public void testUnknownPlaceholderReturnsEmpty() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_TOPIC_PATH, "foo")).isEmpty();
    }

}
