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
package org.eclipse.ditto.signals.commands.live.modify;

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.signals.base.WithFeatureId;
import org.eclipse.ditto.signals.commands.live.base.LiveCommand;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;

/**
 * {@link ModifyFeatureProperty} live command giving access to the command and all of its special accessors. Also the
 * entry point for creating a {@link ModifyFeaturePropertyLiveCommandAnswerBuilder} as answer for an incoming command.
 */
public interface ModifyFeaturePropertyLiveCommand
        extends LiveCommand<ModifyFeaturePropertyLiveCommand, ModifyFeaturePropertyLiveCommandAnswerBuilder>,
        ThingModifyCommand<ModifyFeaturePropertyLiveCommand>, WithFeatureId {

    /**
     * Returns the JSON pointer of the Property to modify.
     *
     * @return the JSON pointer.
     * @see ModifyFeatureProperty#getPropertyPointer()
     */
    @Nonnull
    JsonPointer getPropertyPointer();

    /**
     * Returns the value of the Property to modify.
     *
     * @return the value.
     * @see ModifyFeatureProperty#getPropertyValue()
     */
    @Nonnull
    JsonValue getPropertyValue();

}
