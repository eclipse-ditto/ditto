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
package org.eclipse.ditto.protocol.mapper;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.protocol.PayloadBuilder;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.TopicPathBuilder;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;

final class ThingMergeSignalMapper extends AbstractModifySignalMapper<MergeThing> {

    @Override
    void enhancePayloadBuilder(final MergeThing command, final PayloadBuilder payloadBuilder) {
        payloadBuilder.withValue(command.getValue());
    }

    @Override
    TopicPathBuilder getTopicPathBuilder(final MergeThing command) {
        return ProtocolFactory.newTopicPathBuilder(command.getEntityId()).things();
    }

    private static final TopicPath.Action[] SUPPORTED_ACTIONS = {TopicPath.Action.MERGE};

    @Override
    TopicPath.Action[] getSupportedActions() {
        return SUPPORTED_ACTIONS;
    }

    @Override
    DittoHeaders enhanceHeaders(final MergeThing signal) {
        return ProtocolFactory.newHeadersWithJsonMergePatchContentType(signal.getDittoHeaders());
    }
}
