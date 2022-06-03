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
package org.eclipse.ditto.protocol.mapper;

import org.eclipse.ditto.policies.model.signals.commands.query.PolicyQueryCommand;
import org.eclipse.ditto.protocol.PayloadBuilder;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPathBuilder;

final class PolicyQuerySignalMapper extends AbstractQuerySignalMapper<PolicyQueryCommand<?>> {

    @Override
    TopicPathBuilder getTopicPathBuilder(final PolicyQueryCommand<?> command) {
        return ProtocolFactory.newTopicPathBuilder(command.getEntityId()).policies();
    }

    @Override
    void enhancePayloadBuilder(final PolicyQueryCommand<?> command, final PayloadBuilder payloadBuilder) {
        command.getSelectedFields().ifPresent(payloadBuilder::withFields);
    }

}
