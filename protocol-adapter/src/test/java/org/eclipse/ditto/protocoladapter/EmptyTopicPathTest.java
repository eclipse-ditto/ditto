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
package org.eclipse.ditto.protocoladapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link EmptyTopicPathTest}.
 */
public final class EmptyTopicPathTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    private EmptyTopicPath underTest;

    @Before
    public void setUp() {
        underTest = EmptyTopicPath.getInstance();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(EmptyTopicPath.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(EmptyTopicPath.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void getInstanceReturnsNotNull() {
        assertThat(EmptyTopicPath.getInstance()).isNotNull();
    }

    @Test
    public void getNamespaceReturnsNull() {
        assertThat(underTest.getNamespace()).isNull();
    }

    @Test
    public void getEntityNameReturnsNull() {
        assertThat(underTest.getEntityName()).isNull();
    }

    @Test
    public void getGroupReturnsNull() {
        assertThat(underTest.getGroup()).isNull();
    }

    @Test
    public void getCriterionReturnsNull() {
        assertThat(underTest.getCriterion()).isNull();
    }

    @Test
    public void getChannelReturnsNull() {
        assertThat(underTest.getChannel()).isNull();
    }

    @Test
    public void getActionReturnsEmptyOptional() {
        assertThat(underTest.getAction()).isEmpty();
    }

    @Test
    public void getSearchActionReturnsEmptyOptional() {
        assertThat(underTest.getSearchAction()).isEmpty();
    }

    @Test
    public void getSubjectReturnsEmptyOptional() {
        assertThat(underTest.getSubject()).isEmpty();
    }

    @Test
    public void getPathReturnsEmptyString() {
        assertThat(underTest.getPath()).isEmpty();
    }

    @Test
    public void isGroupReturnsExpected() {
        softly.assertThat(underTest.isGroup(null)).as("null group").isFalse();
        for (final TopicPath.Group group : TopicPath.Group.values()) {
            softly.assertThat(underTest.isGroup(group)).as(group.getName()).isFalse();
        }
    }

    @Test
    public void isChannelReturnsExpected() {
        softly.assertThat(underTest.isChannel(null)).as("null channel").isFalse();
        for (final TopicPath.Channel channel : TopicPath.Channel.values()) {
            softly.assertThat(underTest.isChannel(channel)).as(channel.getName()).isFalse();
        }
    }

    @Test
    public void isCriterionReturnsExpected() {
        softly.assertThat(underTest.isCriterion(null)).as("null criterion").isFalse();
        for (final TopicPath.Criterion criterion : TopicPath.Criterion.values()) {
            softly.assertThat(underTest.isCriterion(criterion)).as(criterion.getName()).isFalse();
        }
    }

    @Test
    public void isActionReturnsExpected() {
        softly.assertThat(underTest.isAction(null)).as("null action").isFalse();
        for (final TopicPath.Action group : TopicPath.Action.values()) {
            softly.assertThat(underTest.isAction(group)).as(group.getName()).isFalse();
        }
    }

}