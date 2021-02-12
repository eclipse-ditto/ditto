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
package org.eclipse.ditto.protocoladapter.policies;

import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.protocoladapter.Adapter;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.provider.PolicyCommandAdapterProvider;
import org.eclipse.ditto.signals.base.ErrorRegistry;
import org.eclipse.ditto.signals.commands.policies.PolicyErrorResponse;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommand;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommandResponse;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommand;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommandResponse;
import org.eclipse.ditto.signals.notifications.policies.PolicyNotification;

/**
 * Instantiates and provides {@link Adapter}s used to process Policy commands, responses and errors and notifications.
 *
 * @since 1.1.0
 */
public final class DefaultPolicyCommandAdapterProvider implements PolicyCommandAdapterProvider {

    private final PolicyErrorResponseAdapter policyErrorResponseAdapter;
    private final PolicyModifyCommandAdapter policyModifyCommandAdapter;
    private final PolicyQueryCommandAdapter policyQueryCommandAdapter;
    private final PolicyModifyCommandResponseAdapter policyModifyCommandResponseAdapter;
    private final PolicyQueryCommandResponseAdapter policyQueryCommandResponseAdapter;
    private final PolicyNotificationAdapter policyNotificationAdapter;

    public DefaultPolicyCommandAdapterProvider(final ErrorRegistry<DittoRuntimeException> errorRegistry,
            final HeaderTranslator headerTranslator) {
        policyErrorResponseAdapter = PolicyErrorResponseAdapter.of(headerTranslator, errorRegistry);
        policyModifyCommandAdapter = PolicyModifyCommandAdapter.of(headerTranslator);
        policyQueryCommandAdapter = PolicyQueryCommandAdapter.of(headerTranslator);
        policyModifyCommandResponseAdapter = PolicyModifyCommandResponseAdapter.of(headerTranslator);
        policyQueryCommandResponseAdapter = PolicyQueryCommandResponseAdapter.of(headerTranslator);
        policyNotificationAdapter = PolicyNotificationAdapter.of(headerTranslator);
    }

    public Adapter<PolicyErrorResponse> getErrorResponseAdapter() {
        return policyErrorResponseAdapter;
    }

    public Adapter<PolicyModifyCommand<?>> getModifyCommandAdapter() {
        return policyModifyCommandAdapter;
    }

    public Adapter<PolicyModifyCommandResponse<?>> getModifyCommandResponseAdapter() {
        return policyModifyCommandResponseAdapter;
    }

    public Adapter<PolicyQueryCommand<?>> getQueryCommandAdapter() {
        return policyQueryCommandAdapter;
    }

    public Adapter<PolicyQueryCommandResponse<?>> getQueryCommandResponseAdapter() {
        return policyQueryCommandResponseAdapter;
    }

    public Adapter<PolicyNotification<?>> getNotificationAdapter() {
        return policyNotificationAdapter;
    }

    @Override
    public List<Adapter<?>> getAdapters() {
        return Arrays.asList(
                policyErrorResponseAdapter,
                policyModifyCommandAdapter,
                policyQueryCommandAdapter,
                policyModifyCommandResponseAdapter,
                policyQueryCommandResponseAdapter,
                policyNotificationAdapter
        );
    }
}
