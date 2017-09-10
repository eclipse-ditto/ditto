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
package org.eclipse.ditto.signals.commands.policies.exceptions;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.policies.PolicyEntryInvalidException;
import org.eclipse.ditto.model.policies.PolicyIdInvalidException;
import org.eclipse.ditto.model.policies.SubjectIdInvalidException;
import org.eclipse.ditto.signals.base.AbstractErrorRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.CommonErrorRegistry;

/**
 * A {@link org.eclipse.ditto.signals.base.ErrorRegistry} aware of all {@link org.eclipse.ditto.model.policies.PolicyException}s.
 */
@Immutable
public final class PolicyErrorRegistry extends AbstractErrorRegistry<DittoRuntimeException> {

    private PolicyErrorRegistry(final Map<String, JsonParsable<DittoRuntimeException>> parseStrategies) {
        super(parseStrategies, CommonErrorRegistry.newInstance());
    }

    /**
     * Returns a new {@code PolicyErrorRegistry}.
     *
     * @return the error registry.
     */
    public static PolicyErrorRegistry newInstance() {
        final Map<String, JsonParsable<DittoRuntimeException>> parseStrategies = new HashMap<>();

        parseStrategies.put(PolicyConflictException.ERROR_CODE, PolicyConflictException::fromJson);
        parseStrategies.put(PolicyIdInvalidException.ERROR_CODE, PolicyIdInvalidException::fromJson);
        parseStrategies.put(PolicyEntryInvalidException.ERROR_CODE, PolicyEntryInvalidException::fromJson);
        parseStrategies.put(SubjectIdInvalidException.ERROR_CODE, SubjectIdInvalidException::fromJson);
        parseStrategies.put(PolicyNotAccessibleException.ERROR_CODE, PolicyNotAccessibleException::fromJson);
        parseStrategies.put(PolicyNotModifiableException.ERROR_CODE, PolicyNotModifiableException::fromJson);
        parseStrategies.put(PolicyIdNotExplicitlySettableException.ERROR_CODE,
                PolicyIdNotExplicitlySettableException::fromJson);
        parseStrategies.put(PolicyUnavailableException.ERROR_CODE, PolicyUnavailableException::fromJson);
        parseStrategies.put(PolicyTooManyModifyingRequestsException.ERROR_CODE,
                PolicyTooManyModifyingRequestsException::fromJson);
        parseStrategies.put(PolicyEntryNotAccessibleException.ERROR_CODE, PolicyEntryNotAccessibleException::fromJson);
        parseStrategies.put(PolicyEntryNotModifiableException.ERROR_CODE, PolicyEntryNotModifiableException::fromJson);
        parseStrategies.put(PolicyEntryModificationInvalidException.ERROR_CODE,
                PolicyEntryModificationInvalidException::fromJson);
        parseStrategies.put(PolicyModificationInvalidException.ERROR_CODE,
                PolicyModificationInvalidException::fromJson);
        parseStrategies.put(ResourcesNotModifiableException.ERROR_CODE, ResourcesNotModifiableException::fromJson);
        parseStrategies.put(ResourceNotAccessibleException.ERROR_CODE, ResourceNotAccessibleException::fromJson);
        parseStrategies.put(ResourcesNotAccessibleException.ERROR_CODE, ResourcesNotAccessibleException::fromJson);
        parseStrategies.put(ResourceNotModifiableException.ERROR_CODE, ResourceNotModifiableException::fromJson);
        parseStrategies.put(SubjectsNotModifiableException.ERROR_CODE, SubjectsNotModifiableException::fromJson);
        parseStrategies.put(SubjectNotAccessibleException.ERROR_CODE, SubjectNotAccessibleException::fromJson);
        parseStrategies.put(SubjectNotModifiableException.ERROR_CODE, SubjectNotModifiableException::fromJson);
        parseStrategies.put(SubjectsNotModifiableException.ERROR_CODE, SubjectsNotModifiableException::fromJson);

        return new PolicyErrorRegistry(parseStrategies);
    }
}
