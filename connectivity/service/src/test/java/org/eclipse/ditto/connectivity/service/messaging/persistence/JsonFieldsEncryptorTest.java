/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.connectivity.service.messaging.persistence;

import static org.junit.Assert.*;

import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.connectivity.service.config.DefaultFieldsEncryptionConfig;
import org.eclipse.ditto.connectivity.service.config.FieldsEncryptionConfig;
import org.eclipse.ditto.connectivity.service.util.EncryptorAesGcm;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class JsonFieldsEncryptorTest {

    public static String SYMMETRICAL_KEY;
    private static FieldsEncryptionConfig TEST_CONFIG;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() throws NoSuchAlgorithmException {
        final Config config = ConfigFactory.load("connection-fields-encryption-test");
        TEST_CONFIG = DefaultFieldsEncryptionConfig.of(config.getConfig("connection"));
        SYMMETRICAL_KEY = TEST_CONFIG.getSymmetricalKey();
    }

    @Test
    public void encryptConnectionFieldsPreservesData() {
        final List<String> jsonPointers = TEST_CONFIG.getJsonPointers();
        final List<String> willNotBeEncrypted = List.of("/uriWithoutUserInfo", "/uriWithoutPassword");
        final JsonObject plainConnection = JsonObject.of(connJson);
        final JsonObject encrypted = JsonFieldsEncryptor.encrypt(plainConnection, "", jsonPointers, SYMMETRICAL_KEY);
        jsonPointers.stream().filter(p -> !willNotBeEncrypted.contains(p)).forEach(pointer -> {
            Optional<JsonValue> value = encrypted.getValue(pointer);
            assertTrue("missing " + pointer + " after encryption", value.isPresent());
            softly.assertThat(value.get()).isNotEqualTo(plainConnection.getValue(pointer).get());
        });

        final JsonObject decryptedConnection = JsonFieldsEncryptor.decrypt(encrypted, "", jsonPointers, SYMMETRICAL_KEY);
        jsonPointers.forEach(pointer -> {
            Optional<JsonValue> value = decryptedConnection.getValue(pointer);
            assertTrue("missing " + pointer + " after encryption", value.isPresent());
            softly.assertThat(value.get()).isEqualTo(plainConnection.getValue(pointer).get());
        });

    }

    @Test
    public void replacePasswordInUriUserInfo() {
        final String uri = "amqps://user:passwordValue@hono.eclipseprojects.io:5671";
        final String patchedPwd = "passwordValuePatched";
        final String expectedUri = "amqps://user:" + patchedPwd + "@hono.eclipseprojects.io:5671";
        final String patchedUri = JsonFieldsEncryptor.replaceUriPassword(uri, patchedPwd);
        assertEquals(expectedUri, patchedUri);
    }
    @Test
    public void replacePasswordInUriUserInfoPasswordIsTheSameAsUser() {
        final String uri = "amqps://user:user@hono.eclipseprojects.io:5671";
        final String patchedPwd = "passwordValuePatched";
        final String expectedUri = "amqps://user:"+ patchedPwd +"@hono.eclipseprojects.io:5671";
        final String patchedUri = JsonFieldsEncryptor.replaceUriPassword(uri, patchedPwd);
        assertEquals(expectedUri, patchedUri);
    }
    @Test
    public void replacePasswordInUriUserInfoWithQueryParamSameAaPwd() {
        final String uri = "amqps://user:passwordValue@hono.eclipseprojects.io:5671?queryParam=passwordValue";
        final String patchedPwd = "passwordValuePatched";
        final String expectedUri = "amqps://user:" + patchedPwd + "@hono.eclipseprojects.io:5671?queryParam=passwordValue";
        final String patchedUri = JsonFieldsEncryptor.replaceUriPassword(uri, patchedPwd);
        assertEquals(expectedUri , patchedUri);
    }

    /**
     * Connection is not valid as it includes conflicting entries. Only for test purposes
     */
    private static final String connJson = """
            {
                    "__lifecycle": "ACTIVE",
                    "id": "73d34426-1e67-4a37-82aa-b9064b3b9efa",
                    "name": "hono-example-sshTunnel-connection-123",
                    "connectionType": "amqp-10",
                    "connectionStatus": "closed",
                    "uri": "amqps://user:passwordValue@hono.eclipseprojects.io:5671",
                    "sources": [
                        {
                            "addresses": [
                                "telemetry/FOO"
                            ],
                            "consumerCount": 1,
                            "authorizationContext": [
                                "ditto:inbound-auth-subject"
                            ],
                            "enforcement": {
                                "input": "{{ header:device_id }}",
                                "filters": [
                                    "{{ thing:id }}"
                                ]
                            },
                            "headerMapping": {
                                "content-type": "{{header:content-type}}",
                                "reply-to": "{{header:reply-to}}",
                                "correlation-id": "{{header:correlation-id}}"
                            },
                            "payloadMapping": [
                                "Ditto"
                            ],
                            "replyTarget": {
                                "address": "{{header:reply-to}}",
                                "headerMapping": {
                                    "content-type": "{{header:content-type}}",
                                    "correlation-id": "{{header:correlation-id}}"
                                },
                                "expectedResponseTypes": [
                                    "response",
                                    "error"
                                ],
                                "enabled": true
                            }
                        }
                    ],
                    "targets": [
                        {
                            "address": "events/twin",
                            "topics": [
                                "_/_/things/twin/events"
                            ],
                            "authorizationContext": [
                                "ditto:outbound-auth-subject"
                            ],
                            "headerMapping": {
                                "message-id": "{{ header:correlation-id }}",
                                "content-type": "application/vnd.eclipse.ditto+json"
                            }
                        }
                    ],
                    "clientCount": 1,
                    "failoverEnabled": true,
                    "validateCertificates": true,
                    "processorPoolSize": 1,
                    "uriWithoutPassword": "amqps://user@hono.eclipseprojects.io:5671",
                    "uriWithoutUserInfo": "amqps://hono.eclipseprojects.io:5671",
                    "credentials": {
                        "key": "someKey",
                        "clientSecret": "someClientSecret",
                        "parameters": {
                            "accessKey": "accessKeyValue",
                            "secretKey": "secretKeyValue",
                            "sharedKey": "sharedKeyValue"
                        }
                    },
                    "sshTunnel": {
                        "enabled": true,
                        "credentials": {
                            "type": "plain",
                            "username": "usernameValue",
                            "password": "passwordValue",
                            "privateKey": "privateKeyValue"
                        },
                        "validateHost": true,
                        "knownHosts": [
                            "MD5:e0:3a:34:1c:68:ed:c6:bc:7c:ca:a8:67:c7:45:2b:19"
                        ],
                        "uri": "ssh://ssh-host:2222"
                    },
                    "tags": []
                }
            """;
}