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
package org.eclipse.ditto.services.policies.persistence.actors.strategies.commands;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.services.utils.headers.conditional.ConditionalHeadersValidator;
import org.eclipse.ditto.services.utils.persistentactors.etags.AbstractConditionHeaderCheckingCommandStrategy;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyEntryModificationInvalidException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyEntryNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyModificationInvalidException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.ResourceNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.SubjectNotAccessibleException;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;

/**
 * Abstract base class for {@link org.eclipse.ditto.signals.commands.policies.PolicyCommand} strategies.
 *
 * @param <C> the type of the handled command - of type {@code Command} as also
 * {@link org.eclipse.ditto.services.models.policies.commands.sudo.SudoCommand} are handled which are no PolicyCommands.
 */
abstract class AbstractPolicyCommandStrategy<C extends Command<C>>
        extends AbstractConditionHeaderCheckingCommandStrategy<C, Policy, PolicyId, PolicyEvent> {

    AbstractPolicyCommandStrategy(final Class<C> theMatchingClass) {
        super(theMatchingClass);
    }

    @Override
    public ConditionalHeadersValidator getValidator() {
        return PoliciesConditionalHeadersValidatorProvider.getInstance();
    }

    @Override
    public boolean isDefined(final C command) {
        return true;
    }

    static DittoRuntimeException policyEntryNotFound(final PolicyId policyId, final Label label,
            final DittoHeaders dittoHeaders) {
        return PolicyEntryNotAccessibleException.newBuilder(policyId, label).dittoHeaders(dittoHeaders).build();
    }

    static DittoRuntimeException subjectNotFound(final PolicyId policyId, final Label label,
            final CharSequence subjectId, final DittoHeaders dittoHeaders) {
        return SubjectNotAccessibleException.newBuilder(policyId, label.toString(), subjectId)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    static DittoRuntimeException resourceNotFound(final PolicyId policyId, final Label label,
            final ResourceKey resourceKey, final DittoHeaders dittoHeaders) {
        return ResourceNotAccessibleException.newBuilder(policyId, label, resourceKey.toString())
                .dittoHeaders(dittoHeaders)
                .build();
    }

    static DittoRuntimeException policyNotFound(final PolicyId policyId, final DittoHeaders dittoHeaders) {
        return PolicyNotAccessibleException.newBuilder(policyId).dittoHeaders(dittoHeaders).build();
    }

    static DittoRuntimeException policyInvalid(final PolicyId policyId, @Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return PolicyModificationInvalidException.newBuilder(policyId)
                .description(message)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    static DittoRuntimeException policyEntryInvalid(final PolicyId policyId, final Label label,
            @Nullable final String message, final DittoHeaders dittoHeaders) {
        return PolicyEntryModificationInvalidException.newBuilder(policyId, label)
                .description(message)
                .dittoHeaders(dittoHeaders)
                .build();
    }
}
