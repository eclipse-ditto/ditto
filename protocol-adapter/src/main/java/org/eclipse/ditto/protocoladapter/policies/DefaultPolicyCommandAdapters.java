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
package org.eclipse.ditto.protocoladapter.policies;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.protocoladapter.Adapter;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.PolicyCommandAdapters;
import org.eclipse.ditto.signals.base.ErrorRegistry;
import org.eclipse.ditto.signals.commands.policies.PolicyErrorResponse;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommand;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommandResponse;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommand;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommandResponse;

/**
 * Instantiates {@link Adapter}s used to process policy commands, responses and errors.
 */
public class DefaultPolicyCommandAdapters implements PolicyCommandAdapters {

    private final PolicyErrorResponseAdapter policyErrorResponseAdapter;
    private final PolicyModifyCommandAdapter policyModifyCommandAdapter;
    private final PolicyQueryCommandAdapter policyQueryCommandAdapter;
    private final PolicyModifyCommandResponseAdapter policyModifyCommandResponseAdapter;
    private final PolicyQueryCommandResponseAdapter policyQueryCommandResponseAdapter;

    public DefaultPolicyCommandAdapters(final ErrorRegistry<DittoRuntimeException> errorRegistry,
            final HeaderTranslator headerTranslator) {
        policyErrorResponseAdapter = PolicyErrorResponseAdapter.of(headerTranslator, errorRegistry);
        policyModifyCommandAdapter = PolicyModifyCommandAdapter.of(headerTranslator);
        policyQueryCommandAdapter = PolicyQueryCommandAdapter.of(headerTranslator);
        policyModifyCommandResponseAdapter = PolicyModifyCommandResponseAdapter.of(headerTranslator);
        policyQueryCommandResponseAdapter = PolicyQueryCommandResponseAdapter.of(headerTranslator);
    }

    @Override
    public Adapter<PolicyErrorResponse> getErrorResponseAdapter() {
        return policyErrorResponseAdapter;
    }

    @Override
    public Adapter<PolicyModifyCommand<?>> getModifyCommandAdapter() {
        return policyModifyCommandAdapter;
    }

    @Override
    public Adapter<PolicyModifyCommandResponse<?>> getModifyCommandResponseAdapter() {
        return policyModifyCommandResponseAdapter;
    }

    @Override
    public Adapter<PolicyQueryCommand<?>> getQueryCommandAdapter() {
        return policyQueryCommandAdapter;
    }

    @Override
    public Adapter<PolicyQueryCommandResponse<?>> getQueryCommandResponseAdapter() {
        return policyQueryCommandResponseAdapter;
    }
}