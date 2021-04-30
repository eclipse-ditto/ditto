/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.junit.Test;

/**
 * Unit test for {@link UnknownTopicPathException}.
 */
public final class UnknownTopicPathExceptionTest {

    @Test
    public void fromTopicAndPath() {
        final TopicPath topicPath =
                TopicPath.newBuilder(ThingId.of("ns", "id")).things().twin().commands().modify().build();
        final MessagePath messagePath = ImmutableMessagePath.of(JsonPointer.of("/policyId"));
        final DittoHeaders dittoHeaders = DittoHeaders.empty();

        final UnknownTopicPathException exception =
                UnknownTopicPathException.fromTopicAndPath(topicPath, messagePath, dittoHeaders);

        Assertions.assertThat(exception)
                .hasMessageContaining(topicPath.getPath())
                .hasMessageContaining(messagePath.toString());
    }

    @Test
    public void testImmutability() {
        assertInstancesOf(UnknownTopicPathException.class, areImmutable());
    }

}
