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
package org.eclipse.ditto.protocol.adapter.policies;

import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.ErrorRegistry;
import org.eclipse.ditto.policies.model.signals.announcements.PolicyAnnouncement;
import org.eclipse.ditto.policies.model.signals.commands.PolicyErrorResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.PolicyModifyCommand;
import org.eclipse.ditto.policies.model.signals.commands.modify.PolicyModifyCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.PolicyQueryCommand;
import org.eclipse.ditto.policies.model.signals.commands.query.PolicyQueryCommandResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.protocol.adapter.Adapter;
import org.eclipse.ditto.protocol.adapter.provider.PolicyCommandAdapterProvider;

/**
 * Instantiates and provides {@link Adapter}s used to process Policy commands, responses and errors and announcements.
 *
 * @since 1.1.0
 */
public final class DefaultPolicyCommandAdapterProvider implements PolicyCommandAdapterProvider {

    private final PolicyErrorResponseAdapter policyErrorResponseAdapter;
    private final PolicyModifyCommandAdapter policyModifyCommandAdapter;
    private final PolicyQueryCommandAdapter policyQueryCommandAdapter;
    private final PolicyModifyCommandResponseAdapter policyModifyCommandResponseAdapter;
    private final PolicyQueryCommandResponseAdapter policyQueryCommandResponseAdapter;
    private final PolicyAnnouncementAdapter policyAnnouncementAdapter;
    private final PolicyEventAdapter policyEventAdapter;

    public DefaultPolicyCommandAdapterProvider(final ErrorRegistry<DittoRuntimeException> errorRegistry,
            final HeaderTranslator headerTranslator) {
        policyErrorResponseAdapter = PolicyErrorResponseAdapter.of(headerTranslator, errorRegistry);
        policyModifyCommandAdapter = PolicyModifyCommandAdapter.of(headerTranslator);
        policyQueryCommandAdapter = PolicyQueryCommandAdapter.of(headerTranslator);
        policyModifyCommandResponseAdapter = PolicyModifyCommandResponseAdapter.of(headerTranslator);
        policyQueryCommandResponseAdapter = PolicyQueryCommandResponseAdapter.of(headerTranslator);
        policyAnnouncementAdapter = PolicyAnnouncementAdapter.of(headerTranslator);
        policyEventAdapter = PolicyEventAdapter.of(headerTranslator);
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

    public Adapter<PolicyAnnouncement<?>> getAnnouncementAdapter() {
        return policyAnnouncementAdapter;
    }

    @Override
    public Adapter<PolicyEvent<?>> getEventAdapter() {
        return policyEventAdapter;
    }

    @Override
    public List<Adapter<?>> getAdapters() {
        return Arrays.asList(
                policyErrorResponseAdapter,
                policyModifyCommandAdapter,
                policyQueryCommandAdapter,
                policyModifyCommandResponseAdapter,
                policyQueryCommandResponseAdapter,
                policyAnnouncementAdapter,
                policyEventAdapter
        );
    }
}
