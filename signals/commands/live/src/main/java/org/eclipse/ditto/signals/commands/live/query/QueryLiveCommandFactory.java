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

import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.live.base.LiveCommand;

/**
 * A factory for getting immutable instances of query {@link LiveCommand LiveCommand}s.
 */
@ParametersAreNonnullByDefault
@Immutable
public final class QueryLiveCommandFactory {

    private QueryLiveCommandFactory() {
        throw new AssertionError();
    }

    /**
     * Returns a new immutable instance of {@code RetrieveAttributeLiveCommand}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link org.eclipse.ditto.signals.commands.things.query.RetrieveAttribute
     * RetrieveAttribute}.
     */
    @Nonnull
    public static RetrieveAttributeLiveCommand retrieveAttribute(final Command<?> command) {
        return RetrieveAttributeLiveCommandImpl.of(command);
    }

    /**
     * Returns a new immutable instance of {@code RetrieveAttributesLiveCommand}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link org.eclipse.ditto.signals.commands.things.query.RetrieveAttributes
     * RetrieveAttributes}.
     */
    @Nonnull
    public static RetrieveAttributesLiveCommand retrieveAttributes(final Command<?> command) {
        return RetrieveAttributesLiveCommandImpl.of(command);
    }

    /**
     * Returns a new immutable instance of {@code RetrieveFeatureLiveCommand}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link org.eclipse.ditto.signals.commands.things.query.RetrieveFeature
     * RetrieveFeature}.
     */
    @Nonnull
    public static RetrieveFeatureLiveCommand retrieveFeature(final Command<?> command) {
        return RetrieveFeatureLiveCommandImpl.of(command);
    }

    /**
     * Returns a new immutable instance of {@code RetrieveFeaturePropertiesLiveCommand}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperties
     * RetrieveFeatureProperties}.
     */
    @Nonnull
    public static RetrieveFeaturePropertiesLiveCommand retrieveFeatureProperties(final Command<?> command) {
        return RetrieveFeaturePropertiesLiveCommandImpl.of(command);
    }

    /**
     * Returns a new immutable instance of {@code RetrieveFeaturePropertyLiveCommand}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperty
     * RetrieveFeatureProperty}.
     */
    @Nonnull
    public static RetrieveFeaturePropertyLiveCommand retrieveFeatureProperty(final Command<?> command) {
        return RetrieveFeaturePropertyLiveCommandImpl.of(command);
    }

    /**
     * Returns a new immutable instance of {@code RetrieveFeaturesLiveCommand}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link org.eclipse.ditto.signals.commands.things.query.RetrieveFeatures
     * RetrieveFeatures}.
     */
    @Nonnull
    public static RetrieveFeaturesLiveCommand retrieveFeatures(final Command<?> command) {
        return RetrieveFeaturesLiveCommandImpl.of(command);
    }

    /**
     * Returns a new immutable instance of {@code RetrieveThingLiveCommand}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link org.eclipse.ditto.signals.commands.things.query.RetrieveThing
     * RetrieveThing}.
     */
    @Nonnull
    public static RetrieveThingLiveCommand retrieveThing(final Command<?> command) {
        return RetrieveThingLiveCommandImpl.of(command);
    }

    /**
     * Returns a new immutable instance of {@code RetrieveThingsLiveCommand}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link org.eclipse.ditto.signals.commands.things.query.RetrieveThings
     * RetrieveThings}.
     */
    @Nonnull
    public static RetrieveThingsLiveCommand retrieveThings(final Command<?> command) {
        return RetrieveThingsLiveCommandImpl.of(command);
    }

}
