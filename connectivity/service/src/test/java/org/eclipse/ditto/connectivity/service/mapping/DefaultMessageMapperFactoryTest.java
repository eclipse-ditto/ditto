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
package org.eclipse.ditto.connectivity.service.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.MappingContext;
import org.eclipse.ditto.connectivity.service.mapping.test.MappingContexts;
import org.eclipse.ditto.connectivity.service.mapping.test.MockMapper;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.event.DiagnosticLoggingAdapter;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link DefaultMessageMapperFactory}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class DefaultMessageMapperFactoryTest {

    private static ActorSystem system;

    @Mock
    private DiagnosticLoggingAdapter log;

    private DefaultMessageMapperFactory underTest;

    @BeforeClass
    public static void initTestFixture() {
        final Config testConfig = ConfigFactory.parseMap(
                Collections.singletonMap("ditto.connectivity.mapping.dummy", ""))
                .withFallback(ConfigFactory.load("test"));
        system = ActorSystem.create("test", testConfig);
    }

    @AfterClass
    public static void shutDownActorSystem() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @Before
    public void setUp() {
        underTest = DefaultMessageMapperFactory.of(TestConstants.createConnection(), TestConstants.CONNECTIVITY_CONFIG,
                system, log);
    }

    @After
    public void tearDown() {
        underTest = null;
    }

    @Test
    public void loadMapperFromFactoryMethod() {
        final Map<String, String> options = new HashMap<>();
        // add this to pass configuration checks
        options.put(MockMapper.OPT_IS_VALID, String.valueOf(true));

        // looking for a method containing 'test' and returning a MessageMapper in Mappers.class
        final MappingContext ctx = MappingContexts.mock(MockMapper.ALIAS, options);

        assertThat(underTest.mapperOf("test", ctx)).isPresent();
    }

    @Test
    public void loadMapperFromClass() {
        final MappingContext ctx = MappingContexts.mock(true);
        assertThat(underTest.mapperOf(MockMapper.class.getName(), ctx)).isPresent();
    }

    @Test
    public void loadMapperWitInvalidOptions() {
        final MappingContext ctx = MappingContexts.mock(false);
        assertThat(underTest.mapperOf("test", ctx)).isEmpty();
    }

    @Test
    public void loadMissingMapper() {
        final MappingContext ctx = MappingContexts.mock("missing-mapper", Collections.emptyMap());
        assertThat(underTest.mapperOf("missing-mapper-id", ctx)).isEmpty();
    }

    @Test
    public void loadNonMessageMapperClass() {
        final MappingContext ctx = MappingContexts.mock(String.class.getCanonicalName(),
                Collections.emptyMap());

        assertThat(underTest.mapperOf("some-id", ctx)).isEmpty();
    }

    @Test
    public void createWithAlias() {
        // looking for a mapper alias 'test' and returning a MessageMapper in Mappers.class
        assertThat(underTest.createMessageMapperInstance(MockMapper.ALIAS)).isPresent();
    }

    @Test
    public void createWithUnknownAliasFindsNoMapper() {
        assertThat(underTest.createMessageMapperInstance("strong-smell-wasabi")).isEmpty();
    }

    @Test
    public void createWithClassNameFailsForNonMapperClass() {
        assertThat(underTest.createMessageMapperInstance(String.class.getCanonicalName())).isEmpty();
    }

    @Test
    public void loadMapperWithInvalidConfig() {
        final MappingContext ctx = MappingContexts.mock(false);
        assertThat(underTest.mapperOf("test", ctx)).isEmpty();
    }

    @Test
    public void loadMapperWithoutContentType() {
        final Map<String, String> opts = new HashMap<>();

        final MappingContext ctx =
                ConnectivityModelFactory.newMappingContext(MockMapper.class.getCanonicalName(), opts);
        assertThat(underTest.mapperOf("test", ctx)).isEmpty();
    }

    @Test
    public void loadFallbackMappersByAlias() {

        final MessageMapperRegistry registry = underTest.registryOf(DittoMessageMapper.CONTEXT,
                ConnectivityModelFactory.emptyPayloadMappingDefinition());

        final List<MessageMapper> dittoMappers =
                registry.getMappers(ConnectivityModelFactory.newPayloadMapping("Ditto"));
        assertThat(dittoMappers).hasSize(1);
        final MessageMapper dittoMapper = dittoMappers.get(0);
        assertThat(dittoMapper).isInstanceOf(WrappingMessageMapper.class);
        assertThat(dittoMapper.getId()).isEqualTo("Ditto");
        assertThat(((WrappingMessageMapper) dittoMapper).getDelegate()).isInstanceOf(DittoMessageMapper.class);
    }

    @Test
    public void loadRegistry() {
        final MappingContext fooCtx = MappingContexts.mock(true);
        final MessageMapperRegistry underTest = this.underTest.registryOf(DittoMessageMapper.CONTEXT,
                ConnectivityModelFactory.newPayloadMappingDefinition("foo", fooCtx));
        final MessageMapper fooMapper = underTest.getMappers(ConnectivityModelFactory.newPayloadMapping("foo")).get(0);
        final MessageMapper defaultMapper = underTest.getDefaultMapper();
        assertThat(fooMapper).isInstanceOf(WrappingMessageMapper.class);
        assertThat(((WrappingMessageMapper) fooMapper).getDelegate()).isInstanceOf(MockMapper.class);
        assertThat(defaultMapper).isInstanceOf(WrappingMessageMapper.class);
        assertThat(((WrappingMessageMapper) defaultMapper).getDelegate()).isInstanceOf(DittoMessageMapper.class);
    }

}
