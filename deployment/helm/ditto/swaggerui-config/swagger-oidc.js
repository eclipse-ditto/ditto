/* Copyright (c) 2026 Contributors to the Eclipse Foundation
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
(function (global) {
    "use strict";

    // Same query params as Ditto UI (ui/modules/environments/environments.ts) - allows sharing
    // one environments URL for both Ditto UI and Swagger UI.
    const ENVIRONMENTS_URL_PARAM = "environmentsURL";
    const PRIMARY_ENV_PARAM = "primaryEnvironmentName";
    const OIDC_DISCOVERY_PLACEHOLDER = "__OIDC_DISCOVERY_URL__";
    const DITTO_UI_STORAGE_KEY = "ditto-ui-env";

    // Restricts where environments may be loaded from - they contain the OIDC provider configuration, so loading them
    // from an arbitrary URL would let a crafted link hijack the user's login.
    // Same rules as the Ditto UI (ui/modules/environments/environmentsUrl.ts) - keep both in sync.
    const UI_CONFIG_PATH = "ui-config.json";
    const ANY_ORIGIN = "*";
    const BLOCKED_PATH_SEGMENTS = ["api", "ws"];

    async function loadAllowedOrigins() {
        try {
            const response = await fetch(UI_CONFIG_PATH, { mode: "same-origin", cache: "no-cache" });
            if (!response.ok) {
                return [];
            }
            const json = await response.json();
            const allowedOrigins = json && json.environmentsURL && json.environmentsURL.allowedOrigins;
            if (allowedOrigins === undefined) {
                return [];
            }
            if (!Array.isArray(allowedOrigins) || !allowedOrigins.every((o) => typeof o === "string")) {
                throw new Error("\"environmentsURL.allowedOrigins\" must be an array of strings");
            }
            return allowedOrigins;
        } catch (e) {
            console.warn("SwaggerOidc: Invalid or unavailable UI configuration in", UI_CONFIG_PATH, "- using defaults", e);
            return [];
        }
    }

    function normalizeOrigin(origin) {
        try {
            return new URL(origin).origin;
        } catch (e) {
            console.warn("SwaggerOidc: Ignoring invalid origin in \"environmentsURL.allowedOrigins\":", origin);
            return null;
        }
    }

    function containsBlockedPathSegment(pathname) {
        let decoded;
        try {
            decoded = decodeURIComponent(pathname);
        } catch (e) {
            return true;
        }
        return decoded.toLowerCase()
            .split(/[/\\]+/)
            .some((segment) => BLOCKED_PATH_SEGMENTS.includes(segment));
    }

    function resolveEnvironmentsUrl(rawUrl, pageUrl, allowedOrigins) {
        let url;
        try {
            url = new URL(rawUrl, pageUrl);
        } catch (e) {
            throw new Error(`Environments URL "${rawUrl}" is not a valid URL`);
        }
        if (url.protocol !== "https:" && url.protocol !== "http:") {
            throw new Error(`Environments URL "${rawUrl}" must use http or https`);
        }
        const ownOrigin = new URL(pageUrl).origin;
        const originAllowed = url.origin === ownOrigin || (allowedOrigins || []).some((allowed) =>
            allowed === ANY_ORIGIN || normalizeOrigin(allowed) === url.origin);
        if (!originAllowed) {
            throw new Error(`Loading environments from origin "${url.origin}" is not allowed`);
        }
        if (containsBlockedPathSegment(url.pathname)) {
            throw new Error(`Loading environments from path "${url.pathname}" is not allowed`);
        }
        return url;
    }

    async function fetchEnvironments(rawUrl, pageUrl, allowedOrigins, signal) {
        const url = resolveEnvironmentsUrl(rawUrl, pageUrl, allowedOrigins);
        const sameOrigin = url.origin === new URL(pageUrl).origin;
        // 'same-origin' mode additionally rejects redirects to other origins
        const response = await fetch(url.href, { signal: signal, mode: sameOrigin ? "same-origin" : "cors" });
        if (response.redirected) {
            resolveEnvironmentsUrl(response.url, pageUrl, allowedOrigins);
        }
        return response;
    }

    function scopesToObject(scopes) {
        if (Array.isArray(scopes)) {
            return scopes.reduce((acc, scope) => {
                if (scope) {
                    acc[scope] = scope;
                }
                return acc;
            }, {});
        }
        const result = {};
        (scopes || "")
            .split(/\s+/)
            .filter(Boolean)
            .forEach((s) => {
                result[s] = s;
            });
        return result;
    }

    function patchSpecObject(spec, oidcConfig) {
        if (!spec || !oidcConfig || !oidcConfig.openIdConnectUrl) {
            return spec;
        }
        const schemes = spec.components && spec.components.securitySchemes;
        if (!schemes) {
            return spec;
        }
        if (schemes.OpenIDConnect) {
            schemes.OpenIDConnect.openIdConnectUrl = oidcConfig.openIdConnectUrl;
        }
        return spec;
    }

    function extractOidcConfigFromEnv(env) {
        if (!env || !env.authSettings || !env.authSettings.oidc || !env.authSettings.oidc.providers) {
            return null;
        }
        const mainOidc = env.authSettings.main && env.authSettings.main.oidc;
        const devopsOidc = env.authSettings.devops && env.authSettings.devops.oidc;
        const providerKey =
            (mainOidc && (mainOidc.provider || mainOidc.defaultProvider)) ||
            (devopsOidc && (devopsOidc.provider || devopsOidc.defaultProvider));
        if (!providerKey) {
            return null;
        }
        const provider = env.authSettings.oidc.providers[providerKey];
        if (!provider) {
            return null;
        }

        const metadataUrl = provider.metadataUrl;
        const authority = provider.authority ? provider.authority.replace(/\/+$/, "") : null;
        const clientId = provider.client_id;
        const scope = provider.scope || "openid";

        const openIdConnectUrl = metadataUrl
            ? metadataUrl
            : (authority ? `${authority}/.well-known/openid-configuration` : null);

        if (!openIdConnectUrl) {
            console.warn("SwaggerOidc: Cannot determine OpenID Connect discovery URL - neither metadataUrl nor authority provided");
            return null;
        }

        return {
            openIdConnectUrl,
            clientId,
            scopes: scope
        };
    }

    function getEnvFromEnvs(envs, envName) {
        if (!envs || typeof envs !== "object") {
            return null;
        }
        if (envName) {
            return envs[envName] || null;
        }
        // No envName: pick first environment that has an OIDC provider (avoid picking one without OIDC)
        const keys = Object.keys(envs);
        for (let i = 0; i < keys.length; i++) {
            const env = envs[keys[i]];
            if (extractOidcConfigFromEnv(env)) {
                return env;
            }
        }
        return keys[0] ? envs[keys[0]] : null;
    }

    async function resolveOidcConfig() {
        const params = new URLSearchParams(global.location.search);
        const environmentsURL = params.get(ENVIRONMENTS_URL_PARAM);
        const primaryEnvName = params.get(PRIMARY_ENV_PARAM);

        let envs = null;

        if (environmentsURL) {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 5000);
            try {
                const allowedOrigins = await loadAllowedOrigins();
                const response = await fetchEnvironments(environmentsURL, global.location.href, allowedOrigins,
                    controller.signal);
                clearTimeout(timeoutId);
                if (response.ok) {
                    const contentType = response.headers.get("content-type") || "";
                    if (contentType.includes("application/json")) {
                        envs = await response.json();
                    }
                }
            } catch (e) {
                clearTimeout(timeoutId);
                if (e.name === 'AbortError') {
                    console.warn("SwaggerOidc: Fetch timeout loading environments from", environmentsURL);
                } else {
                    console.warn("SwaggerOidc: Failed to load environments from", environmentsURL, e);
                }
            }
        }

        if (!envs && typeof global.localStorage !== "undefined") {
            try {
                const stored = global.localStorage.getItem(DITTO_UI_STORAGE_KEY);
                if (stored) {
                    envs = JSON.parse(stored);
                }
            } catch (e) {
                console.warn("SwaggerOidc: Failed to parse environments from localStorage", e);
            }
        }

        const env = getEnvFromEnvs(envs, primaryEnvName);
        return env ? extractOidcConfigFromEnv(env) : null;
    }

    function createResponseInterceptor(oidcConfig, openApiFileName) {
        return function (res) {
            if (oidcConfig && oidcConfig.openIdConnectUrl && res && res.url && res.url.includes(openApiFileName)) {
                if (res.data && typeof res.data === "object") {
                    try {
                        patchSpecObject(res.data, oidcConfig);
                    } catch (e) {
                        console.warn("SwaggerOidc: Failed to patch spec object", e);
                    }
                } else if (typeof res.text === "string") {
                    res.text = res.text.replaceAll(OIDC_DISCOVERY_PLACEHOLDER, oidcConfig.openIdConnectUrl);
                } else if (typeof res.data === "string") {
                    res.data = res.data.replaceAll(OIDC_DISCOVERY_PLACEHOLDER, oidcConfig.openIdConnectUrl);
                }
            }
            // filter ".well-known/openid-configuration" responses to only keep "authorization_code" as grant type
            if (res.url?.includes('openid-configuration')) {
                const body = JSON.parse(res.text);
                body.grant_types_supported = ['authorization_code'];
                res.text = JSON.stringify(body);
                res.body = body;
            }
            return res;
        };
    }

    async function initSwaggerUiWithOidc(options) {
        const opts = options || {};
        const openApiFileName = opts.openApiFileName || "ditto-api-2.yml";
        const oidcConfig = await resolveOidcConfig();
        let didPatchOnce = false;

        const uiConfig = {
            validatorUrl: null,
            docExpansion: "none",
            dom_id: opts.domId || "#swagger-ui",
            deepLinking: true,
            presets: [
                SwaggerUIBundle.presets.apis,
                SwaggerUIStandalonePreset
            ],
            plugins: [
                SwaggerUIBundle.plugins.DownloadUrl
            ],
            layout: "StandaloneLayout",
            oauth2RedirectUrl: global.location.origin + (opts.oauth2RedirectPath || "/oauth2-redirect.html"),
            responseInterceptor: createResponseInterceptor(oidcConfig, openApiFileName),
            onComplete: function () {
                if (opts.preauthorizeBasic) {
                    const basic = opts.preauthorizeBasic;
                    ui.preauthorizeBasic(basic.name, basic.username, basic.password);
                }
                if (!didPatchOnce && oidcConfig && oidcConfig.openIdConnectUrl) {
                    didPatchOnce = true;
                    try {
                        const spec = ui.specSelectors.specJson().toJS();
                        const patched = patchSpecObject(spec, oidcConfig);
                        ui.specActions.updateSpec(JSON.stringify(patched));
                    } catch (e) {
                        console.warn("SwaggerOidc: Failed to update spec in onComplete", e);
                    }
                }
                if (typeof opts.onComplete === "function") {
                    opts.onComplete(ui, oidcConfig);
                }
            },
            requestInterceptor: (req) => {
                // removing this header so that we have a "simple" CORS request to the IdP - not requiring configured allowed origins
                delete req.headers['X-Requested-With'];
                return req;
            }
        };

        if (Array.isArray(opts.urls)) {
            uiConfig.urls = opts.urls;
            if (opts.primaryName) {
                uiConfig["urls.primaryName"] = opts.primaryName;
            }
        } else if (opts.specUrl) {
            uiConfig.url = opts.specUrl;
        }

        const ui = SwaggerUIBundle(uiConfig);

        if (opts.oauth) {
            const mode = opts.oauth.mode || "always";
            const config = Object.assign({}, opts.oauth.config || {});
            if (opts.oauth.useOidcClientId) {
                config.clientId = (oidcConfig && oidcConfig.clientId) || config.clientId || "";
            }
            if (opts.oauth.useOidcScopes) {
                config.scopes = (oidcConfig && oidcConfig.scopes) || config.scopes || "openid";
            }
            const shouldInit = mode === "always" || (mode === "whenClientId" && config.clientId);
            if (shouldInit) {
                ui.initOAuth(config);
            }
        }

        if (opts.exposeUi !== false) {
            global.ui = ui;
        }

        return ui;
    }

    global.SwaggerOidc = {
        initSwaggerUiWithOidc,
        resolveEnvironmentsUrl
    };
})(window);
