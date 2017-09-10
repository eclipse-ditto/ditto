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
package org.eclipse.ditto.protocoladapter;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableTopicPath}.
 */
public final class ImmutableTopicPathTest {

    private static final String KNOWN_NAMESPACE = "org.eclipse.ditto.test";
    private static final TopicPath.Group KNOWN_GROUP = TopicPath.Group.THINGS;
    private static final TopicPath.Channel KNOWN_CHANNEL = TopicPath.Channel.TWIN;
    private static final TopicPath.Criterion KNOWN_CRITERION = TopicPath.Criterion.COMMANDS;
    private static final TopicPath.Action KNOWN_ACTION = TopicPath.Action.MODIFY;
    private static final String KNOWN_ID = "myThing";

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableTopicPath.class)
                .usingGetClass()
                .verify();
    }

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableTopicPath.class, areImmutable());
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullNamespace() {
        ImmutableTopicPath.of(null, KNOWN_ID, KNOWN_GROUP, KNOWN_CHANNEL, KNOWN_CRITERION, KNOWN_ACTION);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullId() {
        ImmutableTopicPath.of(KNOWN_NAMESPACE, null, KNOWN_GROUP, KNOWN_CHANNEL, KNOWN_CRITERION, KNOWN_ACTION);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullGroup() {
        ImmutableTopicPath.of(KNOWN_NAMESPACE, KNOWN_ID, null, KNOWN_CHANNEL, KNOWN_CRITERION, KNOWN_ACTION);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullCriterion() {
        ImmutableTopicPath.of(KNOWN_NAMESPACE, KNOWN_ID, KNOWN_GROUP, KNOWN_CHANNEL, null, KNOWN_ACTION);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullChannel() {
        ImmutableTopicPath.of(KNOWN_NAMESPACE, KNOWN_ID, KNOWN_GROUP, null, KNOWN_CRITERION, KNOWN_ACTION);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullAction() {
        ImmutableTopicPath.of(KNOWN_NAMESPACE, KNOWN_ID, KNOWN_GROUP, KNOWN_CHANNEL, KNOWN_CRITERION,
                (TopicPath.Action) null);
    }

}
