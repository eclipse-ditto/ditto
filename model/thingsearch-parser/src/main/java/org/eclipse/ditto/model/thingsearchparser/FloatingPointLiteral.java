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
package org.eclipse.ditto.model.thingsearchparser;

import org.parboiled.Action;
import org.parboiled.Context;
import org.parboiled.errors.ActionException;

/**
 * Action checking for Floating point literals.
 */
public final class FloatingPointLiteral implements Action {

    @Override
    @SuppressWarnings({"squid:S2201", "ResultOfMethodCallIgnored"})
    public boolean run(final Context context) {
        try {
            Double.parseDouble(context.getMatch());
        } catch (final NumberFormatException ignored) {
            throw new ActionException("Invalid IEEE 754-2008 binary64 (double precision) number.");
        }
        return true;
    }
}
