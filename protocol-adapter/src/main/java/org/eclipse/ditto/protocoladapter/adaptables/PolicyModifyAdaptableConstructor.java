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
package org.eclipse.ditto.protocoladapter.adaptables;

import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPathBuilder;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommand;

final class PolicyModifyAdaptableConstructor extends ModifyCommandAdaptableConstructor<PolicyModifyCommand> {

    @Override
    public TopicPathBuilder getTopicPathBuilder(final PolicyModifyCommand command) {
        return ProtocolFactory.newTopicPathBuilder(command.getEntityId()).policies();
    }

}
