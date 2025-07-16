/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.devops.commands;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;

/**
 * Command to retrieve all dynamic config sections in the WoT (Web of Things) validation config.
 * <p>
 * This command is used to retrieve all dynamic configuration sections that have been defined.
 * Each dynamic config section contains validation settings that can be overridden for a specific scope.
 * </p>
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableCommand(typePrefix = WotValidationConfigCommand.TYPE_PREFIX, name = RetrieveAllDynamicConfigSections.NAME)
public final class RetrieveAllDynamicConfigSections
        extends AbstractWotValidationConfigCommand<RetrieveAllDynamicConfigSections>
        implements WotValidationConfigCommand<RetrieveAllDynamicConfigSections> {

    /**
     * Name of this command.
     * This is used to identify the command type in the command journal and for deserialization.
     */
    public static final String NAME = "retrieveAllDynamicConfigSections";

    private static final String TYPE = TYPE_PREFIX + NAME;

    /**
     * Constructs a new {@code RetrieveAllDynamicConfigSections} command.
     *
     * @param configId the ID of the WoT validation config.
     * @param dittoHeaders the headers of the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    private RetrieveAllDynamicConfigSections(final WotValidationConfigId configId, final DittoHeaders dittoHeaders) {
        super(TYPE, configId, dittoHeaders);
    }

    /**
     * Creates a new instance of {@code RetrieveAllDynamicConfigSections}.
     *
     * @param configId the ID of the WoT validation config.
     * @param dittoHeaders the headers of the command.
     * @return the new instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveAllDynamicConfigSections of(final WotValidationConfigId configId,
            final DittoHeaders dittoHeaders) {
        return new RetrieveAllDynamicConfigSections(configId, dittoHeaders);
    }

    /**
     * Creates a new instance of {@code RetrieveAllDynamicConfigSections} from a JSON object.
     * The JSON object should contain the following field:
     * <ul>
     *     <li>{@code configId} (required): The ID of the WoT validation config</li>
     * </ul>
     *
     * @param jsonObject the JSON object.
     * @param dittoHeaders the headers of the command.
     * @return the new instance.
     */
    public static RetrieveAllDynamicConfigSections fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        final String configIdString = jsonObject.getValueOrThrow(WotValidationConfigCommand.JsonFields.CONFIG_ID);
        return of(WotValidationConfigId.of(configIdString), dittoHeaders);
    }

    @Override
    public RetrieveAllDynamicConfigSections setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/dynamicConfigs");
    }

    @Override
    public Command.Category getCategory() {
        return Command.Category.QUERY;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return super.equals(o);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveAllDynamicConfigSections;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                "]";
    }
} 