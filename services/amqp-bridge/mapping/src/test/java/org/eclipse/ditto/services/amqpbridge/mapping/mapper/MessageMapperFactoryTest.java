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
package org.eclipse.ditto.services.amqpbridge.mapping.mapper;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.eclipse.ditto.services.amqpbridge.mapping.mapper.test.Mappers;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.test.MappingContexts;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.testkit.javadsl.TestKit;

public class MessageMapperFactoryTest {

    private static ActorSystem system;

    private MessageMapperFactory factory;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @Before
    public void setUp() {
        factory = new MessageMapperFactory((ExtendedActorSystem) system, Mappers.class);
    }

    @After
    public void tearDown() {
        factory = null;
    }


    @Test
    public void createMapperInstanceFromFactoryMethod() throws Exception {
        assertThat(factory.findAndCreateInstanceFor(MappingContexts.NOOP_FUNCTION)).isPresent();
    }

    @Test
    public void createMapperInstanceFromClass() throws Exception {
        assertThat(factory.findAndCreateInstanceFor(MappingContexts.NOOP_CLASS)).isPresent();
    }

    @Test
    public void createMissingMapperInstance() throws Exception {
        assertThat(factory.findAndCreateInstanceFor(MappingContexts.MISSING_MAPPER)).isEmpty();
    }

    @Test
    public void createIllegalMapperInstanceFails() {
        assertThatExceptionOfType(ClassCastException.class).isThrownBy(
                () -> factory.findAndCreateInstanceFor(MappingContexts.ILLEGAL_MAPPER));
    }

    @Test
    public void createWithFactoryMethod() throws Exception {
        assertThat(factory.findFactoryMethodAndCreateInstanceFor(MappingContexts.NOOP_FUNCTION)).isPresent();
    }

    @Test
    public void createWithFactoryMethodFindsNoMethod() throws Exception {
        assertThat(factory.findFactoryMethodAndCreateInstanceFor(MappingContexts.NOOP_CLASS)).isEmpty();
    }

    @Test
    public void createWithClassName() throws Exception {
        assertThat(factory.findClassAndCreateInstanceFor(MappingContexts.NOOP_CLASS)).isPresent();
    }

    @Test
    public void createWithClassNameFindsNoClass() throws Exception {
        assertThat(factory.findClassAndCreateInstanceFor(MappingContexts.NOOP_FUNCTION)).isEmpty();
    }

    @Test
    public void createWithClassNameFailsForNonMapperClass() {
        assertThatExceptionOfType(ClassCastException.class).isThrownBy(
                () -> factory.findClassAndCreateInstanceFor(MappingContexts.ILLEGAL_MAPPER));
    }
}