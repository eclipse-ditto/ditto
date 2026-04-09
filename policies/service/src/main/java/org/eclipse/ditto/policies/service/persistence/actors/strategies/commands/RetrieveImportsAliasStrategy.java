/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.service.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ImportsAlias;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.ImportsAliasNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveImportsAlias;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveImportsAliasResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link RetrieveImportsAlias} command.
 */
@Immutable
final class RetrieveImportsAliasStrategy
        extends AbstractPolicyQueryCommandStrategy<RetrieveImportsAlias> {

    RetrieveImportsAliasStrategy(final PolicyConfig policyConfig) {
        super(RetrieveImportsAlias.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final RetrieveImportsAlias command,
            @Nullable final Metadata metadata) {

        final PolicyId policyId = context.getState();
        final Label label = command.getLabel();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        if (policy != null) {
            final Optional<ImportsAlias> optionalAlias = policy.getImportsAliases().getAlias(label);
            if (optionalAlias.isPresent()) {
                final ImportsAlias importsAlias = optionalAlias.get();
                final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                        RetrieveImportsAliasResponse.of(policyId, label,
                                importsAlias.toJson(dittoHeaders.getSchemaVersion()
                                        .orElse(importsAlias.getLatestSchemaVersion()),
                                        FieldType.regularOrSpecial()),
                                createCommandResponseDittoHeaders(dittoHeaders, nextRevision - 1)),
                        policy);
                return ResultFactory.newQueryResult(command, response);
            }
            return ResultFactory.newErrorResult(
                    ImportsAliasNotAccessibleException.newBuilder(policyId, label)
                            .dittoHeaders(dittoHeaders)
                            .build(),
                    command);
        }
        return ResultFactory.newErrorResult(
                policyNotFound(policyId, dittoHeaders), command);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrieveImportsAlias command,
            @Nullable final Policy newEntity) {
        return Optional.empty();
    }
}
