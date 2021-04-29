/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.namespaces.signals.commands;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.Command;

/**
 * A command for managing a particular namespace.
 *
 * @param <T> the type of the implementing class.
 */
public interface NamespaceCommand<T extends NamespaceCommand<T>> extends Command<T>, WithNamespace {

    /**
     * This class contains definitions for all specific fields of a {@code NamespaceCommand}'s JSON representation.
     */
    @Immutable
    abstract class JsonFields extends Command.JsonFields {

        /**
         * Namespace at which the command is directed.
         */
        public static final JsonFieldDefinition<String> NAMESPACE = JsonFactory.newStringFieldDefinition("namespace",
                FieldType.REGULAR, JsonSchemaVersion.V_2);

    }

}
