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
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;

public class AdaptableConstructorFactory {

    public static RetrieveThingsAdaptableConstructor newRetrieveThingsAdaptableConstructor() {
        return new RetrieveThingsAdaptableConstructor();
    }

    public static ThingQueryAdaptableConstructor newThingQueryAdaptableConstructor() {
        return new ThingQueryAdaptableConstructor();
    }

    public static AdaptableConstructor<PolicyQueryCommand> newPolicyQueryAdaptableConstructor() {
        return new PolicyQueryAdaptableConstructor();
    }

    public static AdaptableConstructor<ThingModifyCommand> newThingModifyAdaptableConstructor() {
        return new ThingModifyAdaptableConstructor();
    }

    public static AdaptableConstructor<PolicyModifyCommand> newPolicyModifyAdaptableConstructor() {
        return new PolicyModifyAdaptableConstructor();
    }

    public static AdaptableConstructor<PolicyModifyCommandResponse> newPolicyModifyResponseAdaptableConstructor() {
        return new PolicyModifyResponseAdaptableConstructor();
    }
}
