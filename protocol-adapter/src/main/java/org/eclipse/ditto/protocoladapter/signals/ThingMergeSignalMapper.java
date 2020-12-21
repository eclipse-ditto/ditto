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
package org.eclipse.ditto.protocoladapter.signals;

import org.eclipse.ditto.protocoladapter.PayloadBuilder;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.TopicPathBuilder;
import org.eclipse.ditto.signals.commands.things.modify.MergeThing;

final class ThingMergeSignalMapper extends AbstractModifySignalMapper<MergeThing> {

    @Override
    void enhancePayloadBuilder(final MergeThing command, final PayloadBuilder payloadBuilder) {
        payloadBuilder.withValue(command.getValue());
    }

    @Override
    TopicPathBuilder getTopicPathBuilder(final MergeThing command) {
        return ProtocolFactory.newTopicPathBuilder(command.getEntityId()).things();
    }

    private static final TopicPath.Action[] SUPPORTED_ACTIONS =
            {TopicPath.Action.MERGE};

    @Override
    TopicPath.Action[] getSupportedActions() {
        return SUPPORTED_ACTIONS;
    }

}
