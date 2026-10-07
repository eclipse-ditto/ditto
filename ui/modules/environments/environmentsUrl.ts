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

/*
 * Restricts where the `environmentsURL` query parameter may load environments from.
 *
 * Environments carry the Ditto API URI and the OIDC provider configuration, so whoever controls the loaded file
 * controls where the UI sends the user's credentials and tokens. Loading it from an arbitrary URL lets a crafted link
 * hijack the user's login. Hence by default only files served from the UI's own origin are accepted.
 *
 * The same rules are implemented in deployment/helm/ditto/swaggerui-config/swagger-oidc.js - keep both in sync.
 */

/** Path of the optional, operator-provided UI configuration file, relative to the UI's index.html */
export const UI_CONFIG_PATH = 'ui-config.json';

/** Allowed origins entry which accepts environments from any origin - insecure, see documentation */
export const ANY_ORIGIN = '*';

/**
 * Path segments which are never accepted, as below them a Ditto deployment serves JSON written by its users
 * (e.g. Thing attributes), which would let any user with write access craft environments for other users.
 */
const BLOCKED_PATH_SEGMENTS = ['api', 'ws'];

export type UiConfig = {
  environmentsURL: {
    /**
     * Origins (e.g. "https://config.example.com") besides the UI's own origin from which environments may be loaded.
     * "*" allows any origin.
     */
    allowedOrigins: string[]
  }
}

export const DEFAULT_UI_CONFIG: UiConfig = {
  environmentsURL: {
    allowedOrigins: [],
  },
};

/**
 * Loads the optional UI configuration file served next to the UI.
 * Falls back to the secure defaults if the file does not exist or is invalid.
 * @param {string} configUrl URL of the configuration file
 * @return {Promise<UiConfig>} the UI configuration
 */
export async function loadUiConfig(configUrl: string = UI_CONFIG_PATH): Promise<UiConfig> {
  let response: Response;
  try {
    response = await fetch(configUrl, {mode: 'same-origin', cache: 'no-cache'});
  } catch (err) {
    console.warn(`Could not load UI configuration from ${configUrl}, using defaults`, err);
    return DEFAULT_UI_CONFIG;
  }
  if (!response.ok) {
    // no configuration provided - the default case
    return DEFAULT_UI_CONFIG;
  }
  try {
    const json = await response.json();
    const allowedOrigins = json?.environmentsURL?.allowedOrigins;
    if (allowedOrigins === undefined) {
      return DEFAULT_UI_CONFIG;
    }
    if (!Array.isArray(allowedOrigins) || !allowedOrigins.every((o) => typeof o === 'string')) {
      throw new Error('"environmentsURL.allowedOrigins" must be an array of strings');
    }
    return {environmentsURL: {allowedOrigins}};
  } catch (err) {
    console.warn(`Invalid UI configuration in ${configUrl}, using defaults`, err);
    return DEFAULT_UI_CONFIG;
  }
}

/**
 * Resolves and validates a URL to load environments from.
 * @param {string} rawUrl the URL as passed in the `environmentsURL` query parameter, may be relative
 * @param {string} pageUrl the URL of the UI page, used to resolve relative URLs and determine the UI's origin
 * @param {string[]} allowedOrigins additional origins to accept besides the UI's own origin
 * @return {URL} the resolved URL
 * @throws {Error} if the URL must not be used to load environments from
 */
export function resolveEnvironmentsUrl(rawUrl: string, pageUrl: string, allowedOrigins: string[] = []): URL {
  let url: URL;
  try {
    url = new URL(rawUrl, pageUrl);
  } catch (err) {
    throw new Error(`Environments URL "${rawUrl}" is not a valid URL`);
  }
  if (url.protocol !== 'https:' && url.protocol !== 'http:') {
    throw new Error(`Environments URL "${rawUrl}" must use http or https`);
  }
  const ownOrigin = new URL(pageUrl).origin;
  if (url.origin !== ownOrigin && !isAllowedOrigin(url.origin, allowedOrigins)) {
    throw new Error(`Loading environments from origin "${url.origin}" is not allowed. ` +
        'Only environments served from the UI\'s own origin are accepted, ' +
        'unless the origin is configured in "environmentsURL.allowedOrigins" of the UI configuration.');
  }
  if (containsBlockedPathSegment(url.pathname)) {
    throw new Error(`Loading environments from path "${url.pathname}" is not allowed, ` +
        `as it may point to the Ditto API (paths containing "/${BLOCKED_PATH_SEGMENTS.join('/" or "/')}/")`);
  }
  return url;
}

/**
 * Loads environments from the passed URL after validating it with {@link resolveEnvironmentsUrl}.
 * @param {string} rawUrl the URL as passed in the `environmentsURL` query parameter, may be relative
 * @param {string} pageUrl the URL of the UI page
 * @param {string[]} allowedOrigins additional origins to accept besides the UI's own origin
 * @return {Promise<any>} the parsed environments JSON
 */
export async function fetchEnvironments(rawUrl: string, pageUrl: string, allowedOrigins: string[] = []): Promise<any> {
  const url = resolveEnvironmentsUrl(rawUrl, pageUrl, allowedOrigins);
  const sameOrigin = url.origin === new URL(pageUrl).origin;
  // 'same-origin' mode additionally rejects redirects to other origins
  const response = await fetch(url.href, {mode: sameOrigin ? 'same-origin' : 'cors'});
  if (!response.ok) {
    throw new Error(`URL ${url.href} can not be loaded`);
  }
  if (response.redirected) {
    // the target of a redirect has to pass the same checks
    resolveEnvironmentsUrl(response.url, pageUrl, allowedOrigins);
  }
  return response.json();
}

function isAllowedOrigin(origin: string, allowedOrigins: string[]): boolean {
  return allowedOrigins.some((allowed) => allowed === ANY_ORIGIN || normalizeOrigin(allowed) === origin);
}

function normalizeOrigin(origin: string): string | null {
  try {
    return new URL(origin).origin;
  } catch (err) {
    console.warn(`Ignoring invalid origin "${origin}" in "environmentsURL.allowedOrigins"`);
    return null;
  }
}

function containsBlockedPathSegment(pathname: string): boolean {
  let decoded: string;
  try {
    decoded = decodeURIComponent(pathname);
  } catch (err) {
    // malformed percent-encoding - don't try to reason about it
    return true;
  }
  return decoded.toLowerCase()
      .split(/[/\\]+/)
      .some((segment) => BLOCKED_PATH_SEGMENTS.includes(segment));
}
