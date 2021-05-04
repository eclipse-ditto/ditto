/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol;

import static org.eclipse.ditto.protocol.TopicPath.Channel.LIVE;
import static org.eclipse.ditto.protocol.TopicPath.Channel.TWIN;

import java.util.Arrays;
import java.util.Collection;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Base class for parameterized tests that support both live and twin channel.
 */
@RunWith(Parameterized.class)
public abstract class LiveTwinTest {

    @Parameterized.Parameters(name = "channel: {0}")
    public static Collection<Object[]> data() {
        final Object[][] data = {{LIVE}, {TWIN}};
        return Arrays.asList(data);
    }

    @Parameterized.Parameter
    public TopicPath.Channel channel;

    protected TopicPath topicPath(final TopicPath.Action action) {
        final TopicPathBuilder topicPathBuilder = TopicPath.newBuilder(TestConstants.THING_ID).things();

        switch (channel) {
            case TWIN:
                topicPathBuilder.twin();
                break;
            case LIVE:
                topicPathBuilder.live();
                break;
            default:
                throw new IllegalStateException("channel is expected to be TWIN or LIVE, but was: " + channel.getName());
        }

        final CommandsTopicPathBuilder commandsTopicPathBuilder = topicPathBuilder.commands();

        switch (action) {
            case CREATE:
                commandsTopicPathBuilder.create();
                break;
            case RETRIEVE:
                commandsTopicPathBuilder.retrieve();
                break;
            case MODIFY:
                commandsTopicPathBuilder.modify();
                break;
            case MERGE:
                commandsTopicPathBuilder.merge();
                break;
            case DELETE:
                commandsTopicPathBuilder.delete();
                break;
        }
        return commandsTopicPathBuilder.build();
    }
}
