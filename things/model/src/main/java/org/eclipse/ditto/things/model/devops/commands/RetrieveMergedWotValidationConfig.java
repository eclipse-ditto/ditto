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

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;

/**
 * Command to retrieve a merged WoT (Web of Things) validation configuration.
 * <p>
 * This command retrieves a WoT validation configuration that has been merged with static configuration.
 * The merged configuration represents the effective validation settings that will be applied, combining
 * both dynamic and static configuration settings. The merging process follows these rules:
 * </p>
 * <ul>
 *     <li>Dynamic configuration takes precedence over static configuration in case of conflicts</li>
 *     <li>Top-level settings (enabled, logWarning) are merged with dynamic settings taking precedence</li>
 *     <li>Thing validation settings are merged with dynamic settings taking precedence</li>
 *     <li>Feature validation settings are merged with dynamic settings taking precedence</li>
 *     <li>Dynamic configuration sections are combined from both sources</li>
 * </ul>
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableCommand(typePrefix = WotValidationConfigCommand.TYPE_PREFIX, name = RetrieveMergedWotValidationConfig.NAME)
public final class RetrieveMergedWotValidationConfig
        extends AbstractWotValidationConfigCommand<RetrieveMergedWotValidationConfig>
        implements WotValidationConfigCommand<RetrieveMergedWotValidationConfig> {

    /**
     * Name of this command.
     * This is used to identify the command type in the command journal and for deserialization.
     */
    public static final String NAME = "retrieveMergedWotValidationConfig";

    private static final String TYPE = TYPE_PREFIX + NAME;

    /**
     * Constructs a new {@code RetrieveMergedWotValidationConfig} command.
     *
     * @param configId the ID of the WoT validation config to retrieve and merge with static configuration.
     * @param dittoHeaders the headers of the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    private RetrieveMergedWotValidationConfig(final WotValidationConfigId configId, final DittoHeaders dittoHeaders) {
        super(TYPE, configId, dittoHeaders);
    }

    /**
     * Creates a new instance of {@code RetrieveMergedWotValidationConfig}.
     *
     * @param configId the ID of the WoT validation config to retrieve and merge with static configuration.
     * @param dittoHeaders the headers of the command.
     * @return the new instance.
     */
    public static RetrieveMergedWotValidationConfig of(final WotValidationConfigId configId,
            final DittoHeaders dittoHeaders) {
        Objects.requireNonNull(configId, "configId");
        Objects.requireNonNull(dittoHeaders, "dittoHeaders");
        return new RetrieveMergedWotValidationConfig(configId, dittoHeaders);
    }

    /**
     * Creates a new instance of {@code RetrieveMergedWotValidationConfig} from a JSON object.
     *
     * @param jsonObject the JSON object.
     * @param dittoHeaders the headers of the command.
     * @return the new instance.
     */
    public static RetrieveMergedWotValidationConfig fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        final String configIdString = jsonObject.getValueOrThrow(WotValidationConfigCommand.JsonFields.CONFIG_ID);
        final WotValidationConfigId configId = WotValidationConfigId.of(configIdString);
        return of(configId, dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public Command.Category getCategory() {
        return Command.Category.QUERY;
    }

    @Override
    public RetrieveMergedWotValidationConfig setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), dittoHeaders);
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
        return other instanceof RetrieveMergedWotValidationConfig;
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
