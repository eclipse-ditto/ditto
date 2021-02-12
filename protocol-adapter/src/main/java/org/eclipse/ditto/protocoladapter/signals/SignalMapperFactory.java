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

import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommand;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommandResponse;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommand;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommandResponse;
import org.eclipse.ditto.signals.commands.things.modify.MergeThing;
import org.eclipse.ditto.signals.commands.things.modify.MergeThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.eclipse.ditto.signals.notifications.policies.PolicyNotification;

/**
 * Factory class that instantiates all available {@link SignalMapper}s.
 *
 * @since 1.1.0
 */
public final class SignalMapperFactory {

    public static SignalMapper<ThingModifyCommand<?>> newThingModifySignalMapper() {
        return new ThingModifySignalMapper();
    }

    public static SignalMapper<MergeThing> newThingMergeSignalMapper() {
        return new ThingMergeSignalMapper();
    }

    public static SignalMapper<ThingModifyCommandResponse<?>> newThingModifyResponseSignalMapper() {
        return new ThingModifyResponseSignalMapper();
    }

    public static SignalMapper<MergeThingResponse> newThingMergeResponseSignalMapper() {
        return new ThingMergeResponseSignalMapper();
    }

    public static SignalMapper<ThingQueryCommand<?>> newThingQuerySignalMapper() {
        return new ThingQuerySignalMapper();
    }

    public static SignalMapper<ThingQueryCommandResponse<?>> newThingQueryResponseSignalMapper() {
        return new ThingQueryResponseSignalMapper();
    }

    public static SignalMapper<RetrieveThings> newRetrieveThingsSignalMapper() {
        return new RetrieveThingsSignalMapper();
    }

    public static SignalMapper<RetrieveThingsResponse> newRetrieveThingsResponseSignalMapper() {
        return new RetrieveThingsResponseSignalMapper();
    }

    public static SignalMapper<ThingSearchCommand<?>> newThingSearchSignalMapper() {
        return new ThingSearchSignalMapper<>();
    }

    public static SignalMapper<PolicyModifyCommand<?>> newPolicyModifySignalMapper() {
        return new PolicyModifySignalMapper();
    }

    public static SignalMapper<PolicyModifyCommandResponse<?>> newPolicyModifyResponseSignalMapper() {
        return new PolicyModifyResponseSignalMapper();
    }

    public static SignalMapper<PolicyQueryCommand<?>> newPolicyQuerySignalMapper() {
        return new PolicyQuerySignalMapper();
    }

    public static SignalMapper<PolicyQueryCommandResponse<?>> newPolicyQueryResponseSignalMapper() {
        return new PolicyQueryResponseSignalMapper();
    }

    public static SignalMapper<PolicyNotification<?>> newPolicyNotificationSignalMapper() {
        return new PolicyNotificationSignalMapper();
    }

    public static SignalMapper<MessageCommand<?, ?>> newMessageCommandSignalMapper() {
        return MessageSignalMapper.getInstance();
    }

    public static SignalMapper<MessageCommandResponse<?, ?>> newMessageCommandResponseSignalMapper() {
        return MessageSignalMapper.getInstance();
    }

}
