/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
/// <reference types="node" />

import * as fs from 'fs';
import * as path from 'path';
import * as vm from 'vm';
import {
  DEFAULT_UI_CONFIG,
  fetchEnvironments,
  loadUiConfig,
  resolveEnvironmentsUrl,
} from '../../modules/environments/environmentsUrl';

type Resolver = (rawUrl: string, pageUrl: string, allowedOrigins: string[]) => URL;

const PAGE_URL = 'https://ditto.example.com/ui/?primaryEnvironmentName=dev';
const ANY = '*';

/**
 * Loads the copy of the validation in the Swagger UI integration, which can't share code with the UI bundle.
 * @return {Resolver} the Swagger UI's resolveEnvironmentsUrl
 */
function loadSwaggerResolver(): Resolver {
  const source = fs.readFileSync(
      path.join(__dirname, '../../../deployment/helm/ditto/swaggerui-config/swagger-oidc.js'), 'utf8');
  const fakeWindow: any = {};
  vm.runInNewContext(source, {window: fakeWindow, URL, console});
  return fakeWindow.SwaggerOidc.resolveEnvironmentsUrl;
}

const implementations: [string, Resolver][] = [
  ['Ditto UI', resolveEnvironmentsUrl],
  ['Swagger UI', loadSwaggerResolver()],
];

describe.each(implementations)('%s: resolve environments URL', (_name, resolve) => {
  beforeEach(() => {
    // invalid configured origins are expected to be logged
    jest.spyOn(console, 'warn').mockImplementation(() => {});
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  test.each([
    ['/ui-environments.json', 'https://ditto.example.com/ui-environments.json'],
    ['envs.json', 'https://ditto.example.com/ui/envs.json'],
    ['./config/envs.json', 'https://ditto.example.com/ui/config/envs.json'],
    ['https://ditto.example.com/ui-environments.json', 'https://ditto.example.com/ui-environments.json'],
    ['/apidoc/envs.json', 'https://ditto.example.com/apidoc/envs.json'],
    ['/wot/envs.json', 'https://ditto.example.com/wot/envs.json'],
    ['/api-environments.json', 'https://ditto.example.com/api-environments.json'],
    // same scheme as the page without "//" is parsed as a relative path, not as a host:
    ['https:attacker.example/env.json', 'https://ditto.example.com/ui/attacker.example/env.json'],
  ])('accepts same-origin URL %s', (rawUrl, expected) => {
    expect(resolve(rawUrl, PAGE_URL, []).href).toBe(expected);
  });

  test.each([
    'https://attacker.example/env.json',
    'http://ditto.example.com/env.json', // different scheme = different origin
    'https://ditto.example.com:8443/env.json', // different port = different origin
    'https://ditto.example.com.attacker.example/env.json',
    'https://ditto.example.com@attacker.example/env.json',
    '//attacker.example/env.json',
    '/\\attacker.example/env.json',
    '\\\\attacker.example/env.json',
    'http://127.0.0.1:9902/env.json',
    ' https://attacker.example/env.json',
  ])('rejects cross-origin URL %s', (rawUrl) => {
    expect(() => resolve(rawUrl, PAGE_URL, [])).toThrow(/origin/);
  });

  test.each([
    'data:application/json,{"x":{"api_uri":"https://attacker.example"}}',
    'blob:https://ditto.example.com/0d2c8e7b-3a3b-4b1e-9f3e-1c1d2e3f4a5b',
    'javascript:alert(1)',
    'file:///etc/passwd',
    'ftp://ditto.example.com/env.json',
  ])('rejects non-http(s) URL %s', (rawUrl) => {
    expect(() => resolve(rawUrl, PAGE_URL, [ANY])).toThrow(/http or https/);
  });

  test.each([
    '/api/2/things/org.example:thing/attributes/env',
    '/API/2/things/org.example:thing/attributes/env',
    '/api',
    '/ws/2',
    'https://ditto.example.com//api/2/things/org.example:thing/attributes/env',
    '/ui/../api/2/things/org.example:thing/attributes/env',
    '/ui/%2e%2e/api/2/things/org.example:thing/attributes/env',
    '/api%2F2/things/org.example:thing/attributes/env',
    '/%61pi/2/things/org.example:thing/attributes/env',
    '/ditto/api/2/things/org.example:thing/attributes/env',
    '/%E0%A4%A/env.json', // malformed encoding
    'https://ditto.example.com/api/2/things/org.example:thing/attributes/env',
  ])('rejects Ditto API path %s', (rawUrl) => {
    expect(() => resolve(rawUrl, PAGE_URL, [])).toThrow(/path/);
  });

  test('rejects Ditto API path on an allowed origin', () => {
    expect(() => resolve('https://config.example/api/2/things/x:y/attributes/env', PAGE_URL,
        ['https://config.example'])).toThrow(/path/);
  });

  test.each([
    [['https://config.example'], 'https://config.example/envs.json'],
    [['https://config.example/'], 'https://config.example/envs.json'],
    [['https://config.example/some/path'], 'https://config.example/envs.json'],
    [['https://CONFIG.example'], 'https://config.example/envs.json'],
    [['https://other.example', 'https://config.example'], 'https://config.example/envs.json'],
    [['https://config.example:8443'], 'https://config.example:8443/envs.json'],
    [['*'], 'https://anything.example/envs.json'],
    [['*'], 'http://anything.example/envs.json'],
  ])('accepts allowed origins %j for %s', (allowedOrigins, rawUrl) => {
    expect(resolve(rawUrl, PAGE_URL, allowedOrigins).href).toBe(rawUrl);
  });

  test.each([
    [['https://config.example'], 'http://config.example/envs.json'],
    [['https://config.example'], 'https://config.example:8443/envs.json'],
    [['https://config.example'], 'https://sub.config.example/envs.json'],
    [['https://config.example'], 'https://config.example.attacker.example/envs.json'],
    [['config.example'], 'https://config.example/envs.json'], // not an origin - ignored
    [['not a url', ''], 'https://config.example/envs.json'],
    [['null'], 'https://config.example/envs.json'],
    [[], 'https://config.example/envs.json'],
  ])('rejects with allowed origins %j the URL %s', (allowedOrigins, rawUrl) => {
    expect(() => resolve(rawUrl, PAGE_URL, allowedOrigins)).toThrow(/origin/);
  });

  test('rejects invalid URL', () => {
    expect(() => resolve('https://', PAGE_URL, [])).toThrow(/not a valid URL/);
  });
});

describe('load UI configuration', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  function mockFetch(response: Partial<Response> | Error) {
    return jest.spyOn(global, 'fetch').mockImplementation(() =>
      response instanceof Error ? Promise.reject(response) : Promise.resolve(response as Response));
  }

  test('loads only from the own origin', async () => {
    const fetchMock = mockFetch({ok: false, status: 404});
    await loadUiConfig();
    expect(fetchMock).toHaveBeenCalledWith('ui-config.json', expect.objectContaining({mode: 'same-origin'}));
  });

  test('uses defaults when no configuration file exists', async () => {
    mockFetch({ok: false, status: 404});
    await expect(loadUiConfig()).resolves.toEqual(DEFAULT_UI_CONFIG);
  });

  test('uses defaults when loading fails', async () => {
    jest.spyOn(console, 'warn').mockImplementation(() => {});
    mockFetch(new TypeError('Failed to fetch'));
    await expect(loadUiConfig()).resolves.toEqual(DEFAULT_UI_CONFIG);
  });

  test('uses configured allowed origins', async () => {
    mockFetch({ok: true, json: () => Promise.resolve({environmentsURL: {allowedOrigins: ['https://config.example']}})});
    await expect(loadUiConfig()).resolves.toEqual({environmentsURL: {allowedOrigins: ['https://config.example']}});
  });

  test.each([
    ['not JSON', () => Promise.reject(new SyntaxError('Unexpected token'))],
    ['a string instead of an array', () => Promise.resolve({environmentsURL: {allowedOrigins: '*'}})],
    ['non-string entries', () => Promise.resolve({environmentsURL: {allowedOrigins: [42]}})],
  ])('uses defaults for configuration with %s', async (_desc, json) => {
    jest.spyOn(console, 'warn').mockImplementation(() => {});
    mockFetch({ok: true, json} as Partial<Response>);
    await expect(loadUiConfig()).resolves.toEqual(DEFAULT_UI_CONFIG);
  });

  test('uses defaults for configuration without environmentsURL section', async () => {
    mockFetch({ok: true, json: () => Promise.resolve({})});
    await expect(loadUiConfig()).resolves.toEqual(DEFAULT_UI_CONFIG);
  });
});

describe('fetch environments', () => {
  const ENVIRONMENTS = {dev: {api_uri: 'https://ditto.example.com'}};

  afterEach(() => {
    jest.restoreAllMocks();
  });

  test('fetches same-origin environments in same-origin mode', async () => {
    const fetchMock = jest.spyOn(global, 'fetch').mockResolvedValue(
        {ok: true, redirected: false, json: () => Promise.resolve(ENVIRONMENTS)} as Response);
    await expect(fetchEnvironments('/ui-environments.json', PAGE_URL, [])).resolves.toEqual(ENVIRONMENTS);
    expect(fetchMock).toHaveBeenCalledWith('https://ditto.example.com/ui-environments.json', {mode: 'same-origin'});
  });

  test('fetches allowed cross-origin environments in cors mode', async () => {
    const fetchMock = jest.spyOn(global, 'fetch').mockResolvedValue(
        {ok: true, redirected: false, json: () => Promise.resolve(ENVIRONMENTS)} as Response);
    await fetchEnvironments('https://config.example/envs.json', PAGE_URL, ['https://config.example']);
    expect(fetchMock).toHaveBeenCalledWith('https://config.example/envs.json', {mode: 'cors'});
  });

  test('does not fetch rejected URLs', async () => {
    const fetchMock = jest.spyOn(global, 'fetch');
    await expect(fetchEnvironments('https://attacker.example/env.json', PAGE_URL, [])).rejects.toThrow(/origin/);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  test('rejects redirect to a disallowed origin', async () => {
    jest.spyOn(global, 'fetch').mockResolvedValue({
      ok: true, redirected: true, url: 'https://attacker.example/env.json',
      json: () => Promise.resolve(ENVIRONMENTS),
    } as Response);
    await expect(fetchEnvironments('https://config.example/envs.json', PAGE_URL, ['https://config.example']))
        .rejects.toThrow(/origin/);
  });

  test('rejects redirect to the Ditto API', async () => {
    jest.spyOn(global, 'fetch').mockResolvedValue({
      ok: true, redirected: true, url: 'https://ditto.example.com/api/2/things/x:y/attributes/env',
      json: () => Promise.resolve(ENVIRONMENTS),
    } as Response);
    await expect(fetchEnvironments('/ui-environments.json', PAGE_URL, [])).rejects.toThrow(/path/);
  });

  test('accepts redirect within the own origin', async () => {
    jest.spyOn(global, 'fetch').mockResolvedValue({
      ok: true, redirected: true, url: 'https://ditto.example.com/ui/ui-environments.json',
      json: () => Promise.resolve(ENVIRONMENTS),
    } as Response);
    await expect(fetchEnvironments('/ui-environments.json', PAGE_URL, [])).resolves.toEqual(ENVIRONMENTS);
  });

  test('fails for unsuccessful response', async () => {
    jest.spyOn(global, 'fetch').mockResolvedValue({ok: false, status: 404} as Response);
    await expect(fetchEnvironments('/ui-environments.json', PAGE_URL, [])).rejects.toThrow(/can not be loaded/);
  });
});
