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
package org.eclipse.ditto.model.base.entity.validation;

import static org.eclipse.ditto.model.base.entity.id.RegexPatterns.ENTITY_NAME_GROUP_NAME;
import static org.eclipse.ditto.model.base.entity.id.RegexPatterns.ENTITY_NAME_PATTERN;
import static org.eclipse.ditto.model.base.entity.id.RegexPatterns.ID_PATTERN;
import static org.eclipse.ditto.model.base.entity.id.RegexPatterns.NAMESPACE_DELIMITER;
import static org.eclipse.ditto.model.base.entity.id.RegexPatterns.NAMESPACE_GROUP_NAME;
import static org.eclipse.ditto.model.base.entity.id.RegexPatterns.NAMESPACE_PATTERN;

import java.util.regex.Matcher;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.entity.id.NamespacedEntityIdInvalidException;

@Immutable
public final class EntityIdPatternValidator extends AbstractPatternValidator {

    private RepresentationTuple tuple;

    public static EntityIdPatternValidator getInstance() {
        return new EntityIdPatternValidator();
    }

    @Override
    public boolean isValid(final CharSequence toBeValidated) {
        if (toBeValidated.length() > MAX_LENGTH) {
            return false;
        }
        final Matcher matcher = ID_PATTERN.matcher(toBeValidated);
        if (matcher.matches()) {
            this.tuple =
                    new RepresentationTuple(matcher.group(NAMESPACE_GROUP_NAME), matcher.group(ENTITY_NAME_GROUP_NAME));
            return true;
        }
        return false;
    }

    public String validate(final @Nullable String namespace, final @Nullable String name) {
        final String sp = namespace + NAMESPACE_DELIMITER + name;

        if (namespace == null || name == null) {
            throw NamespacedEntityIdInvalidException.newBuilder(sp).build();
        }

        if (name.length() > MAX_LENGTH) {
            throw NamespacedEntityIdInvalidException.newBuilder(sp).build();
        }

        if (!NAMESPACE_PATTERN.matcher(namespace).matches() || !ENTITY_NAME_PATTERN.matcher(name).matches()) {
            throw NamespacedEntityIdInvalidException.newBuilder(sp).build();
        }

        return sp;
    }

    public String getNamespace() {
        return this.tuple.namespace;
    }

    public String getName() {
        return this.tuple.name;
    }

    public String getString() {
        return this.tuple.stringRepresentation;
    }

    private static final class RepresentationTuple {
        public final String namespace;
        public final String name;
        public final String stringRepresentation;

        public RepresentationTuple (final String ns, final String n) {
            this.namespace = ns;
            this.name = n;
            this.stringRepresentation = namespace + NAMESPACE_DELIMITER + name;
        }
    }
}
