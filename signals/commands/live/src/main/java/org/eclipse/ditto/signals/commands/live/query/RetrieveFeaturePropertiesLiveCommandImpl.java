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
package org.eclipse.ditto.signals.commands.live.query;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperties;

/**
 * An immutable implementation of {@link RetrieveFeaturePropertiesLiveCommand}.
 */
@ParametersAreNonnullByDefault
@Immutable
final class RetrieveFeaturePropertiesLiveCommandImpl
        extends AbstractQueryLiveCommand<RetrieveFeaturePropertiesLiveCommand,
        RetrieveFeaturePropertiesLiveCommandAnswerBuilder> implements RetrieveFeaturePropertiesLiveCommand {

    private final String featureId;

    private RetrieveFeaturePropertiesLiveCommandImpl(final RetrieveFeatureProperties command) {
        super(command);
        featureId = command.getFeatureId();
    }

    /**
     * Returns an instance of {@code RetrieveFeaturePropertiesLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link RetrieveFeatureProperties}.
     */
    @Nonnull
    public static RetrieveFeaturePropertiesLiveCommandImpl of(final Command<?> command) {
        return new RetrieveFeaturePropertiesLiveCommandImpl((RetrieveFeatureProperties) command);
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Override
    public RetrieveFeaturePropertiesLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(RetrieveFeatureProperties.of(getThingId(), getFeatureId(), getSelectedFields().orElse(null),
                dittoHeaders));
    }

    @Nonnull
    @Override
    public RetrieveFeaturePropertiesLiveCommandAnswerBuilder answer() {
        return RetrieveFeaturePropertiesLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
