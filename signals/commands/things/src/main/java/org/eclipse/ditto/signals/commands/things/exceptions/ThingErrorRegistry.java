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
package org.eclipse.ditto.signals.commands.things.exceptions;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.things.AclEntryInvalidException;
import org.eclipse.ditto.model.things.AclInvalidException;
import org.eclipse.ditto.model.things.AclNotAllowedException;
import org.eclipse.ditto.model.things.PolicyIdMissingException;
import org.eclipse.ditto.model.things.FeatureDefinitionEmptyException;
import org.eclipse.ditto.model.things.FeatureDefinitionIdentifierInvalidException;
import org.eclipse.ditto.model.things.ThingIdInvalidException;
import org.eclipse.ditto.signals.base.AbstractErrorRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.CommonErrorRegistry;


/**
 * A {@link org.eclipse.ditto.signals.base.ErrorRegistry} aware of all {@link org.eclipse.ditto.model.things.ThingException}s.
 */
@Immutable
public final class ThingErrorRegistry extends AbstractErrorRegistry<DittoRuntimeException> {

    private ThingErrorRegistry(final Map<String, JsonParsable<DittoRuntimeException>> parseStrategies) {
        super(parseStrategies, CommonErrorRegistry.newInstance());
    }

    /**
     * Returns a new {@code ThingErrorRegistry}.
     *
     * @return the error registry.
     */
    public static ThingErrorRegistry newInstance() {
        return newInstance(Collections.emptyMap());
    }

    /**
     * Returns a new {@code ThingErrorRegistry} providing {@code additionalParseStrategies} as argument - that way the
     * user of this ThingErrorRegistry can register additional parsers for his own extensions of
     * {@link DittoRuntimeException}.
     *
     * @param additionalParseStrategies a map containing of DittoRuntimeException ERROR_CODE as keys and JsonParsable of
     * DittoRuntimeException as values.
     * @return the error registry.
     * @throws NullPointerException if {@code additionalParseStrategies} is {@code null}.
     */
    public static ThingErrorRegistry newInstance(
            final Map<String, JsonParsable<DittoRuntimeException>> additionalParseStrategies) {
        ConditionChecker.checkNotNull(additionalParseStrategies, "additional parse strategies");
        final Map<String, JsonParsable<DittoRuntimeException>> parseStrategies =
                new HashMap<>(additionalParseStrategies);

        parseStrategies.put(AclInvalidException.ERROR_CODE, AclInvalidException::fromJson);
        parseStrategies.put(AclEntryInvalidException.ERROR_CODE, AclEntryInvalidException::fromJson);
        parseStrategies.put(AclNotAllowedException.ERROR_CODE, AclNotAllowedException::fromJson);
        parseStrategies.put(AclModificationInvalidException.ERROR_CODE, AclModificationInvalidException::fromJson);
        parseStrategies.put(AclNotAccessibleException.ERROR_CODE, AclNotAccessibleException::fromJson);
        parseStrategies.put(AclNotModifiableException.ERROR_CODE, AclNotModifiableException::fromJson);
        parseStrategies.put(ThingConflictException.ERROR_CODE, ThingConflictException::fromJson);
        parseStrategies.put(ThingIdInvalidException.ERROR_CODE, ThingIdInvalidException::fromJson);
        parseStrategies.put(ThingIdNotExplicitlySettableException.ERROR_CODE,
                ThingIdNotExplicitlySettableException::fromJson);
        parseStrategies.put(ThingNotAccessibleException.ERROR_CODE, ThingNotAccessibleException::fromJson);
        parseStrategies.put(ThingNotDeletableException.ERROR_CODE, ThingNotDeletableException::fromJson);
        parseStrategies.put(ThingNotCreatableException.ERROR_CODE, ThingNotCreatableException::fromJson);
        parseStrategies.put(ThingNotModifiableException.ERROR_CODE, ThingNotModifiableException::fromJson);
        parseStrategies.put(PolicyIdNotAccessibleException.ERROR_CODE, PolicyIdNotAccessibleException::fromJson);
        parseStrategies.put(PolicyIdNotModifiableException.ERROR_CODE, PolicyIdNotModifiableException::fromJson);
        parseStrategies.put(PolicyIdNotAllowedException.ERROR_CODE, PolicyIdNotAllowedException::fromJson);
        parseStrategies.put(PolicyNotAllowedException.ERROR_CODE, PolicyNotAllowedException::fromJson);
        parseStrategies.put(PolicyIdMissingException.ERROR_CODE, PolicyIdMissingException::fromJson);
        parseStrategies.put(ThingUnavailableException.ERROR_CODE, ThingUnavailableException::fromJson);
        parseStrategies.put(ThingTooManyModifyingRequestsException.ERROR_CODE,
                ThingTooManyModifyingRequestsException::fromJson);
        parseStrategies.put(AttributesNotAccessibleException.ERROR_CODE, AttributesNotAccessibleException::fromJson);
        parseStrategies.put(AttributeNotAccessibleException.ERROR_CODE, AttributeNotAccessibleException::fromJson);
        parseStrategies.put(AttributesNotModifiableException.ERROR_CODE, AttributesNotModifiableException::fromJson);
        parseStrategies.put(AttributeNotModifiableException.ERROR_CODE, AttributeNotModifiableException::fromJson);
        parseStrategies.put(FeaturesNotAccessibleException.ERROR_CODE, FeaturesNotAccessibleException::fromJson);
        parseStrategies.put(FeaturesNotModifiableException.ERROR_CODE, FeaturesNotModifiableException::fromJson);
        parseStrategies.put(FeatureNotAccessibleException.ERROR_CODE, FeatureNotAccessibleException::fromJson);
        parseStrategies.put(FeatureNotModifiableException.ERROR_CODE, FeatureNotModifiableException::fromJson);
        parseStrategies.put(FeatureDefinitionNotAccessibleException.ERROR_CODE,
                FeatureDefinitionNotAccessibleException::fromJson);
        parseStrategies.put(FeatureDefinitionNotModifiableException.ERROR_CODE,
                FeatureDefinitionNotModifiableException::fromJson);
        parseStrategies.put(FeaturePropertiesNotAccessibleException.ERROR_CODE,
                FeaturePropertiesNotAccessibleException::fromJson);
        parseStrategies.put(FeaturePropertiesNotModifiableException.ERROR_CODE,
                FeaturePropertiesNotModifiableException::fromJson);
        parseStrategies.put(FeaturePropertyNotAccessibleException.ERROR_CODE,
                FeaturePropertyNotAccessibleException::fromJson);
        parseStrategies.put(FeaturePropertyNotModifiableException.ERROR_CODE,
                FeaturePropertyNotModifiableException::fromJson);
        parseStrategies.put(FeatureDefinitionEmptyException.ERROR_CODE, FeatureDefinitionEmptyException::fromJson);
        parseStrategies.put(FeatureDefinitionIdentifierInvalidException.ERROR_CODE,
                FeatureDefinitionIdentifierInvalidException::fromJson);

        return new ThingErrorRegistry(parseStrategies);
    }

}
