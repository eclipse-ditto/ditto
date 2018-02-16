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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.amqpbridge.AmqpBridgeModelFactory;
import org.eclipse.ditto.model.amqpbridge.InternalMessage;
import org.eclipse.ditto.model.amqpbridge.MappingContext;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.test.Mappers;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.test.MappingContexts;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.test.MockMapper;
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
        factory = new MessageMapperFactory((ExtendedActorSystem) system, Mappers.class, null);
    }

    @After
    public void tearDown() {
        factory = null;
    }


    @Test
    public void loadMapperFromFactoryMethod() throws Exception {
        final String contentType = "test";
        final boolean isContentTypeRequired = true;
        final Map<String, String> options = new HashMap<>();
        // add this to pass configuration checks
        options.put(MockMapper.OPT_IS_VALID, String.valueOf(true));

        // looking for a method containing 'test' and returning a MessageMapper in Mappers.class
        final MappingContext ctx = MappingContexts.mock(contentType, isContentTypeRequired, "test", options);

        final Optional<MessageMapper> underTest = factory.loadMapper(ctx);
        assertThat(underTest).isPresent();
        assertThat(underTest.get().getContentType()).isEqualTo(contentType);
        assertThat(underTest.get().isContentTypeRequired()).isEqualTo(isContentTypeRequired);
    }

    @Test
    public void loadMapperFromClass() throws Exception {
        final String contentType = "test";
        final boolean isContentTypeRequired = true;

        final MappingContext ctx = MappingContexts.mock(contentType, isContentTypeRequired, true);

        final Optional<MessageMapper> underTest = factory.loadMapper(ctx);
        assertThat(underTest).isPresent();
        assertThat(underTest.get().getContentType()).isEqualTo(contentType);
        assertThat(underTest.get().isContentTypeRequired()).isEqualTo(isContentTypeRequired);
    }

    @Test
    public void loadMissingMapper() throws Exception {
        final MappingContext ctx = MappingContexts.mock("test", true, "not-a-class",
                Collections.emptyMap());

        assertThat(factory.loadMapper(ctx)).isEmpty();
    }

    @Test
    public void loadNonMessageMapperClass() {
        final MappingContext ctx = MappingContexts.mock("test", true, String.class.getCanonicalName(),
                Collections.emptyMap());

        assertThat(factory.loadMapper(ctx)).isEmpty();
    }

    @Test
    public void createWithFactoryMethod() throws Exception {
        // looking for a method containing 'test' and returning a MessageMapper in Mappers.class
        final MappingContext ctx = MappingContexts.mock("test", true, "test", Collections.emptyMap());
        assertThat(factory.findFactoryMethodAndCreateInstance(ctx)).isPresent();
    }

    @Test
    public void createWithFactoryMethodFindsNoMethod() throws Exception {
        // looking for a message containing 'strong-smell-wasabi' which does not exist in Mappers.class and therefore
        // expect an empty optional
        final MappingContext ctx = MappingContexts.mock("test", true, "strong-smell-wasabi",
                Collections.emptyMap());

        assertThat(factory.findFactoryMethodAndCreateInstance(ctx)).isEmpty();
    }

    @Test
    public void createWithClassName() throws Exception {
        // MockMapper extends MessageMapper and can be loaded
        final MappingContext ctx = MappingContexts.mock("test", true, MockMapper.class,
                Collections.emptyMap());

        assertThat(factory.findClassAndCreateInstance(ctx)).isPresent();
    }

    @Test
    public void createWithClassNameFindsNoClass() throws Exception {
        final MappingContext ctx = MappingContexts.mock("test", true, "not-a-class",
                Collections.emptyMap());

        assertThat(factory.findClassAndCreateInstance(ctx)).isEmpty();
    }

    @Test
    public void createWithClassNameFailsForNonMapperClass() {
        // load string as a MessageMapper -> should fail
        final MappingContext ctx = MappingContexts.mock("test", true, String.class.getCanonicalName(),
                Collections.emptyMap());

        assertThatExceptionOfType(ClassCastException.class).isThrownBy(
                () -> factory.findClassAndCreateInstance(ctx));
    }

    @Test
    public void loadMapperWithInvalidConfig() {
        final String contentType = "test";
        final boolean isContentTypeRequired = true;

        final MappingContext ctx = MappingContexts.mock(contentType, isContentTypeRequired, false);
        assertThat(factory.loadMapper(ctx)).isEmpty();
    }

    @Test
    public void loadMapperWithoutContentType() {
        final String contentType = "test";
        Map<String, String> opts = new HashMap<>();

        final MappingContext ctx = AmqpBridgeModelFactory.newMappingContext(contentType, MockMapper.class.getCanonicalName(), opts);
        assertThat(factory.loadMapper(ctx)).isEmpty();
    }

    @Test
    public void loadMapperWithoutRequiredContentTypeOptionSetsRequireContentTypeToTrue() {
        final String contentType = "test";
        Map<String, String> opts = new HashMap<>();
        opts.put(MessageMapper.OPT_CONTENT_TYPE, contentType);
        opts.put(MockMapper.OPT_IS_VALID, String.valueOf(true));

        final MappingContext ctx = AmqpBridgeModelFactory.newMappingContext(contentType, MockMapper.class.getCanonicalName(), opts);
        final Optional<MessageMapper> underTest = factory.loadMapper(ctx);
        assertThat(underTest).isPresent();
        assertThat(underTest.get().getContentType()).isEqualTo(contentType);
        assertThat(underTest.get().isContentTypeRequired()).isEqualTo(true);
    }


    @Test
    public void loadMappers() {
        final List<MappingContext> contexts = Arrays.asList(
                MappingContexts.mock("foo", true,true),
                MappingContexts.mock("bar", false, true)
        );
        final List<MessageMapper> mappers = factory.loadMappers(contexts);
        assertThat(mappers).isNotEmpty().hasSize(2);
        assertThat(mappers.stream().map(MessageMapper::getContentType).collect(Collectors.toList())).contains("foo",
                "bar");
    }

    @Test
    public void loadMappersWithFails() {
        final List<MappingContext> contexts = Arrays.asList(
                MappingContexts.mock("foo", true,false),
                MappingContexts.mock("bar", false, true)
        );
        final List<MessageMapper> mappers = factory.loadMappers(contexts);
        assertThat(mappers).isNotEmpty().hasSize(1);
        assertThat(mappers.stream().map(MessageMapper::getContentType).collect(Collectors.toList())).contains("bar");
    }

    @Test
    public void loadRegistry() {
        final MappingContext fooCtx = MappingContexts.mock("foo", true,true);
        final MappingContext barCtx = MappingContexts.mock("bar", true,true);

        final InternalMessage fooMessage = new InternalMessage.Builder(Collections.singletonMap(MessageMapper
                .CONTENT_TYPE_KEY, "foo")).build();
        final InternalMessage barMessage = new InternalMessage.Builder(Collections.singletonMap(MessageMapper
                .CONTENT_TYPE_KEY, "foo")).build();
        final InternalMessage otherMessage = new InternalMessage.Builder(Collections.singletonMap(MessageMapper
                .CONTENT_TYPE_KEY, "other")).build();

        final MessageMapper defaultMapper = new MockMapper(); // default mapper with null content type

        final List<MappingContext> contexts = Arrays.asList(fooCtx, barCtx);
        MessageMapperRegistry underTest = factory.loadRegistry(defaultMapper, contexts);
        assertThat(underTest.getDefaultMapper().equals(defaultMapper)).isTrue();
        assertThat(underTest.findMapper(fooMessage)).isPresent().map(MessageMapper::getContentType)
                .isEqualTo(Optional.of("foo"));
        assertThat(underTest.findMapper(barMessage)).isPresent().map(MessageMapper::getContentType)
                .isEqualTo(Optional.of("foo"));
        assertThat(underTest.findMapper(otherMessage)).isEmpty();

        //select uses default mapper
        assertThat(underTest.selectMapper(otherMessage)).isPresent().map(MessageMapper::getContentType).isEqualTo
                (Optional.empty());
    }
}