/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter.adaptables;

/**
 * Factory class to get instances of all {@link MappingStrategies}.
 */
public final class MappingStrategiesFactory {

    public static PolicyModifyCommandMappingStrategies getPolicyModifyCommandMappingStrategies() {
        return PolicyModifyCommandMappingStrategies.getInstance();
    }

    public static PolicyQueryCommandMappingStrategies getPolicyQueryCommandMappingStrategies() {
        return PolicyQueryCommandMappingStrategies.getInstance();
    }

    public static PolicyModifyCommandResponseMappingStrategies getPolicyModifyCommandResponseMappingStrategies() {
        return PolicyModifyCommandResponseMappingStrategies.getInstance();
    }

    public static PolicyQueryCommandResponseMappingStrategies getPolicyQueryCommandResponseMappingStrategies() {
        return PolicyQueryCommandResponseMappingStrategies.getInstance();
    }

    public static ThingModifyCommandMappingStrategies getThingModifyCommandMappingStrategies() {
        return ThingModifyCommandMappingStrategies.getInstance();
    }

    public static ThingQueryCommandMappingStrategies getThingQueryCommandMappingStrategies() {
        return ThingQueryCommandMappingStrategies.getInstance();
    }

    public static ThingModifyCommandResponseMappingStrategies getThingModifyCommandResponseMappingStrategies() {
        return ThingModifyCommandResponseMappingStrategies.getInstance();
    }

    public static ThingQueryCommandResponseMappingStrategies getThingQueryCommandResponseMappingStrategies() {
        return ThingQueryCommandResponseMappingStrategies.getInstance();
    }

    public static ThingSearchCommandMappingStrategies getThingSearchCommandMappingStrategies(){
        return ThingSearchCommandMappingStrategies.getInstance();
    }

    public static ThingEventMappingStrategies getThingEventMappingStrategies() {
        return ThingEventMappingStrategies.getInstance();
    }
    public static SubscriptionEventMappingStrategies getSubscriptionEventMappingStrategies(){
        return SubscriptionEventMappingStrategies.getInstance();
    }

    public static MessageCommandMappingStrategies getMessageCommandMappingStrategies() {
        return MessageCommandMappingStrategies.getInstance();
    }

    public static MessageCommandResponseMappingStrategies getMessageCommandResponseMappingStrategies() {
        return MessageCommandResponseMappingStrategies.getInstance();
    }
}