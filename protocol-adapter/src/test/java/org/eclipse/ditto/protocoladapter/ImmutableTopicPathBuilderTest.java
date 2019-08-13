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
package org.eclipse.ditto.protocoladapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.model.things.ThingId;
import org.junit.Test;

/**
 */
public class ImmutableTopicPathBuilderTest {

    /** */
    @Test
    public void buildThingModifyCommandTopicPath() {
        final TopicPath expected = ImmutableTopicPath
                .of("org.eclipse.ditto.test", "myThing", TopicPath.Group.THINGS, TopicPath.Channel.TWIN,
                        TopicPath.Criterion.COMMANDS, TopicPath.Action.MODIFY);
        final TopicPath actual = ProtocolFactory.newTopicPathBuilder(ThingId.of("org.eclipse.ditto.test", "myThing")) //
                .twin() //
                .commands() //
                .modify() //
                .build();

        final String expectedTopicPathString = "org.eclipse.ditto.test/myThing/things/twin/commands/modify";

        assertThat(actual).isEqualTo(expected);
        assertThat(actual.getPath()).isEqualTo(expectedTopicPathString);
    }

    /** */
    @Test
    public void buildThingModifiedEventTopicPath() {
        final TopicPath expected = ImmutableTopicPath
                .of("org.eclipse.ditto.test", "myThing", TopicPath.Group.THINGS, TopicPath.Channel.TWIN,
                        TopicPath.Criterion.EVENTS, TopicPath.Action.MODIFIED);
        final TopicPath actual = ProtocolFactory
                .newTopicPathBuilder(ThingId.of("org.eclipse.ditto.test", "myThing")) //
                .twin() //
                .events() //
                .modified() //
                .build();

        final String expectedTopicPathString = "org.eclipse.ditto.test/myThing/things/twin/events/modified";

        assertThat(actual).isEqualTo(expected);
        assertThat(actual.getPath()).isEqualTo(expectedTopicPathString);
    }

}
