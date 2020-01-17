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

import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommand;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommandResponse;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommand;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommandResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;

public class AdaptableConstructorFactory {

    public static AdaptableConstructor<ThingModifyCommand<?>> newThingModifyAdaptableConstructor() {
        return new ThingModifyAdaptableConstructor();
    }

    public static AdaptableConstructor<ThingModifyCommandResponse<?>> newThingModifyResponseAdaptableConstructor() {
        return new ThingModifyResponseAdaptableConstructor();
    }

    public static AdaptableConstructor<ThingQueryCommand<?>> newThingQueryAdaptableConstructor() {
        return new ThingQueryAdaptableConstructor();
    }

    public static AdaptableConstructor<ThingQueryCommandResponse<?>> newThingQueryResponseAdaptableConstructor() {
        return new ThingQueryResponseAdaptableConstructor();
    }

    public static AdaptableConstructor<RetrieveThings> newRetrieveThingsAdaptableConstructor() {
        return new RetrieveThingsAdaptableConstructor();
    }

    public static AdaptableConstructor<RetrieveThingsResponse> newRetrieveThingsResponseAdaptableConstructor() {
        return new RetrieveThingsResponseAdaptableConstructor();
    }

    // -------------------------------------------------------------------------------------------------------------- //


    public static AdaptableConstructor<PolicyModifyCommand<?>> newPolicyModifyAdaptableConstructor() {
        return new PolicyModifyAdaptableConstructor();
    }

    public static AdaptableConstructor<PolicyModifyCommandResponse<?>> newPolicyModifyResponseAdaptableConstructor() {
        return new PolicyModifyResponseAdaptableConstructor();
    }

    public static AdaptableConstructor<PolicyQueryCommand<?>> newPolicyQueryAdaptableConstructor() {
        return new PolicyQueryAdaptableConstructor();
    }

    public static AdaptableConstructor<PolicyQueryCommandResponse<?>> newPolicyQueryResponseAdaptableConstructor() {
        return new PolicyQueryResponseAdaptableConstructor();
    }

}
