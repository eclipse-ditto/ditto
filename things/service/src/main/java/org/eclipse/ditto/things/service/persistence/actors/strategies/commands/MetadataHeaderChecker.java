/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.things.model.signals.commands.exceptions.MetadataHeadersConflictException;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;

@Immutable
final class MetadataHeaderChecker {

    private static final Set<String> METADATA_HEADERS = Set.of(DittoHeaderDefinition.PUT_METADATA.getKey(),
            DittoHeaderDefinition.GET_METADATA.getKey(), DittoHeaderDefinition.DELETE_METADATA.getKey());

    private MetadataHeaderChecker() {
        throw new AssertionError();
    }

    /**
     * Checks if multiple metadata header are present.
     *
     * @param command the command used for checking if validation should be applied.
     * @param dittoHeaders the dittoHeader to check for metadata headers.
     * @throws MetadataHeadersConflictException in case there are multiple metadata header.
     */
    public static void check(final Command<?> command, final DittoHeaders dittoHeaders) {
        checkNotNull(command, "Command");
        checkNotNull(dittoHeaders, "DittoHeaders");

        if (checkIfMultipleMetadataHeadersArePresent(dittoHeaders)) {
            if (command instanceof ThingQueryCommand<?>) {
                throw MetadataHeadersConflictException.newBuilder()
                        .message("Metadata of the Thing could not be retrieved.")
                        .description("Multiple metadata headers were specified." +
                                " For GET requests only the 'get-metadata' header can be specified.")
                        .build();
            } else if (command instanceof ThingModifyCommand<?>) {
                throw MetadataHeadersConflictException.newBuilder()
                        .message("Metadata of the Thing could not be created, modified or deleted.")
                        .description("Multiple metadata headers were specified." +
                                " For PUT/PATCH requests either the 'put-metadata' or 'delete-metadata' header can be specified not both at the same time.")
                        .build();
            }
        }
    }

    private static boolean checkIfMultipleMetadataHeadersArePresent(final DittoHeaders dittoHeaders) {
        final Set<String> metadataHeaders = dittoHeaders.keySet()
                .stream()
                .filter(METADATA_HEADERS::contains)
                .collect(Collectors.toSet());

        return metadataHeaders.size() > 1;
    }

}
