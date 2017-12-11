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
 * Action checking for whole number literals.
 */
public final class IntegerLiteralAction implements Action {

    @Override
    @SuppressWarnings({"squid:S2201", "ResultOfMethodCallIgnored"})
    public boolean run(final Context context) {
        try {
            Long.parseLong(context.getMatch());
        } catch (final NumberFormatException ignored) {
            throw new ActionException("Invalid number literal (the value is too big).");
        }
        return true;
    }
}
