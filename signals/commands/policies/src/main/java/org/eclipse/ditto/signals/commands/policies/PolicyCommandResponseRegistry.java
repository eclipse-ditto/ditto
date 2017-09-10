/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.commands.policies;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicyEntryResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteResourceResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteSubjectResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntriesResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntryResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResourceResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResourcesResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubjectResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubjectsResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntriesResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntryResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResourceResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResourcesResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubjectResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubjectsResponse;

/**
 * Registry which is capable of parsing {@link PolicyCommandResponse}s from JSON.
 */
@Immutable
public final class PolicyCommandResponseRegistry extends AbstractCommandResponseRegistry<PolicyCommandResponse> {

    private PolicyCommandResponseRegistry(final Map<String, JsonParsable<PolicyCommandResponse>> parseStrategies) {
        super(parseStrategies);
    }

    public static PolicyCommandResponseRegistry newInstance() {
        final Map<String, JsonParsable<PolicyCommandResponse>> parseStrategies = new HashMap<>();

        parseStrategies.put(CreatePolicyResponse.TYPE, CreatePolicyResponse::fromJson);
        parseStrategies.put(ModifyPolicyResponse.TYPE, ModifyPolicyResponse::fromJson);
        parseStrategies.put(DeletePolicyResponse.TYPE, DeletePolicyResponse::fromJson);

        parseStrategies.put(ModifyPolicyEntriesResponse.TYPE, ModifyPolicyEntriesResponse::fromJson);
        parseStrategies.put(ModifyPolicyEntryResponse.TYPE, ModifyPolicyEntryResponse::fromJson);
        parseStrategies.put(DeletePolicyEntryResponse.TYPE, DeletePolicyEntryResponse::fromJson);

        parseStrategies.put(ModifySubjectsResponse.TYPE, ModifySubjectsResponse::fromJson);
        parseStrategies.put(ModifySubjectResponse.TYPE, ModifySubjectResponse::fromJson);
        parseStrategies.put(DeleteSubjectResponse.TYPE, DeleteSubjectResponse::fromJson);

        parseStrategies.put(ModifyResourcesResponse.TYPE, ModifyResourcesResponse::fromJson);
        parseStrategies.put(ModifyResourceResponse.TYPE, ModifyResourceResponse::fromJson);
        parseStrategies.put(DeleteResourceResponse.TYPE, DeleteResourceResponse::fromJson);

        parseStrategies.put(RetrievePolicyResponse.TYPE, RetrievePolicyResponse::fromJson);

        parseStrategies.put(RetrievePolicyEntriesResponse.TYPE, RetrievePolicyEntriesResponse::fromJson);
        parseStrategies.put(RetrievePolicyEntryResponse.TYPE, RetrievePolicyEntryResponse::fromJson);

        parseStrategies.put(RetrieveSubjectsResponse.TYPE, RetrieveSubjectsResponse::fromJson);
        parseStrategies.put(RetrieveSubjectResponse.TYPE, RetrieveSubjectResponse::fromJson);

        parseStrategies.put(RetrieveResourcesResponse.TYPE, RetrieveResourcesResponse::fromJson);
        parseStrategies.put(RetrieveResourceResponse.TYPE, RetrieveResourceResponse::fromJson);

        parseStrategies.put(PolicyErrorResponse.TYPE, PolicyErrorResponse::fromJson);

        return new PolicyCommandResponseRegistry(parseStrategies);
    }

}
