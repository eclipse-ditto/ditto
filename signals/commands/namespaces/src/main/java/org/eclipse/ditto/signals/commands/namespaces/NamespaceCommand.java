/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.namespaces;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * A command for managing a particular namespace.
 *
 * @param <T> the type of the implementing class.
 */
public interface NamespaceCommand<T extends NamespaceCommand> extends Command<T>, WithNamespace {

    /**
     * This class contains definitions for all specific fields of a {@code NamespaceCommand}'s JSON representation.
     */
    @Immutable
    abstract class JsonFields extends Command.JsonFields {

        /**
         * Namespace at which the command is directed.
         */
        public static final JsonFieldDefinition<String> NAMESPACE = JsonFactory.newStringFieldDefinition("namespace",
                FieldType.REGULAR, JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

    }

}
