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

import { UserManagerSettings } from 'oidc-client-ts';
import * as Utils from '../utils.js';
/* eslint-disable arrow-parens */
/* eslint-disable prefer-const */
/* eslint-disable require-jsdoc */
import * as Authorization from './authorization.js';
import { fillDevopsUsernamePassword, fillMainUsernamePassword } from './authorization.js';
import environmentsHTML from './environments.html';
import defaultTemplates from './environmentTemplates.json';

const OIDC_CALLBACK_STATE = 'state';
export const URL_PRIMARY_ENVIRONMENT_NAME = 'primaryEnvironmentName';
export const URL_OIDC_PROVIDER = 'oidcProvider';
const URL_ENVIRONMENTS = 'environmentsURL';
const STORAGE_KEY = 'ditto-ui-env';

export enum AuthMethod {
  oidc='oidc',
  basic='basic',
  bearer='bearer',
  pre='pre'
}

export type OidcAuthSettings = {
  /** Whether the SSO (via OIDC) section should be enabled in the Authorize popup */
  enabled: boolean,
  /** The default OIDC provider to pre-configure - must match a key in "AuthSettings.oidc" */
  defaultProvider: string | null,
  /** Whether to automatically start SSO when the Authorize popup model loads */
  autoSso: boolean,
  /** The actually chosen OIDC provider (which can be changed by the user in the frontend) - must match a key in "AuthSettings.oidc" */
  provider?: string,
  /** The cached bearer token obtained via SSO */
  bearerToken?: string
}

type BasicAuthSettings = {
  /** Whether the Basic Auth section should be enabled in the Authorize popup */
  enabled: boolean,
  /** The default username and password to pre-configure */
  defaultUsernamePassword: string | null
}

type BearerAuthSettings = {
  /** Whether the Bearer Auth section should be enabled in the Authorize popup */
  enabled: boolean
}

type PreAuthSettings = {
  /** Whether the Pre-Authenticated section should be enabled in the Authorize popup */
  enabled: boolean,
  /** The pre-authenticated username to pre-configure */
  defaultDittoPreAuthenticatedUsername: string | null,
  /** The cached pre-authenticated username */
  dittoPreAuthenticatedUsername?: string
}

type CommonAuthSettings = {
  /** The authentication method to apply */
  method: AuthMethod,
  /** Authentication settings for SSO (OIDC) based authentication */
  oidc: OidcAuthSettings,
  /** Authentication settings for Bearer authentication (manually providing a Bearer token to the UI) */
  bearer: BearerAuthSettings
  /** Authentication settings for Basic authentication */
  basic: BasicAuthSettings,
}

type MainAuthSettings = CommonAuthSettings & {
  /** Authentication settings for Pre-Authenticated authentication */
  pre: PreAuthSettings
}

export type OidcProviderConfiguration = UserManagerSettings /* from 'oidc-client-ts' */ & {
  /** The name used in the drop-down list of available OIDC providers */
  displayName: string,
  /** Configures the field to use as 'Bearer' token from the response of the OIDC provider's /token endpoint, e.g. either "access_token" or "id_token" */
  extractBearerTokenFrom: string
}

type AuthSettings = {
  /** Contains the settings for the 'main' user authentication, accessing things+policies */
  main: MainAuthSettings,
  /** Contains the settings for the 'devops' user authentication, accessing connections+operations */
  devops: CommonAuthSettings,
  /** The shared OpenID Connect (OIDC) provider configuration with the provider as key and the configuration as value */
  oidc: Record<string, OidcProviderConfiguration>
}

type FieldListItem = {
  active: boolean,
  path: string,
  label: string
}

type Environment = {
  /** The Ditto API URI to use, without the `/api/2` part */
  api_uri: string,
  /** The Ditto main version, either `2` or `3` */
  ditto_version: number,
  /** Whether to hide the "Policies" tab */
  disablePolicies?: boolean,
  /** Whether to hide the "Connections" tab */
  disableConnections?: boolean,
  /** Whether to hide the "Operations" tab */
  disableOperations?: boolean,
  /** The authorization settings for the UI */
  authSettings?: AuthSettings,
  /** A comma separated list of namespaces to perform Thing searches in */
  searchNamespaces?: string,
  /** Holds templates for well known (feature) messages */
  messageTemplates?: any,
  /** Contains a list of fields to be shown as additional columns in the Things search result table */
  fieldList?: FieldListItem[],
  /** Contains well known filters which should be suggested when typing in the Things search field */
  filterList?: string[],
  /** Holds a list of "pinned" things */
  pinnedThings?: string[],
  /** Holds a list of recently opened Policies in the "Policy" tab */
  recentPolicyIds?: string[],
}

const DEFAULT_AUTH_SETTINGS: AuthSettings = {
  main: {
    method: AuthMethod.basic,
    oidc: {
      enabled: false,
      defaultProvider: null,
      autoSso: false
    },
    basic: {
      enabled: true,
      defaultUsernamePassword: "ditto:ditto"
    },
    bearer: {
      enabled: true
    },
    pre: {
      enabled: false,
      defaultDittoPreAuthenticatedUsername: null
    }
  },
  devops: {
    method: AuthMethod.basic,
    oidc: {
      enabled: false,
      defaultProvider: null,
      autoSso: false
    },
    basic: {
      enabled: true,
      defaultUsernamePassword: "devops:foobar"
    },
    bearer: {
      enabled: false
    }
  },
  "oidc": {
  }
}

let environments: Record<string, Environment>;
let selectedEnvName: string;

let settingsEditor;

let dom = {
  environmentSelector: null,
  tbodyEnvironments: null,
  tableValidationEnvironments: null,
  crudEnvironmentFields: null,
  crudEnvironmentJson: null,
  inputApiUri: null,
  inputSearchNamespaces: null,
  selectDittoVersion: null,
  inputTabPolicies: null,
  inputTabConnections: null,
  inputTabOperations: null,
};

let observers = [];

document.getElementById('environmentsHTML').innerHTML = environmentsHTML;

function Environment(env: Environment): void {
  Object.assign(this, env);
  this.authSettings || (this.authSettings = DEFAULT_AUTH_SETTINGS); 
  this.authSettings.main.oidc.provider = env.authSettings?.main?.oidc?.defaultProvider;
  fillMainUsernamePassword(env.authSettings?.main?.basic?.defaultUsernamePassword);
  this.authSettings.main.pre.dittoPreAuthenticatedUsername = env.authSettings?.main?.pre?.defaultDittoPreAuthenticatedUsername;
  this.authSettings.devops.oidc.provider = env.authSettings?.devops?.oidc?.defaultProvider;
  fillDevopsUsernamePassword(env.authSettings?.devops?.basic?.defaultUsernamePassword);
}

export function currentEnvironmentSelector() {
  return dom.environmentSelector.value;
}

export function current() {
  return environments[currentEnvironmentSelector()];
}

export function addChangeListener(observer) {
  observers.push(observer);
}

async function notifyAll(initialPageLoad: boolean, modifiedField = null) {
  // Notify Authorization first to set right auth header
  await Authorization.onEnvironmentChanged(initialPageLoad);
  // Notify others
  observers.forEach(observer => observer.call(null, modifiedField));
}

export async function ready() {
  Utils.getAllElementsById(dom);

  Utils.addValidatorToTable(dom.tbodyEnvironments, dom.tableValidationEnvironments);

  environments = await loadEnvironmentTemplates();

  settingsEditor = Utils.createAceEditor('settingsEditor', 'ace/mode/json', true);

  dom.tbodyEnvironments.addEventListener('click', onEnvironmentsTableClick);

  dom.crudEnvironmentJson.addEventListener('onCreateClick', onCreateEnvironmentClick);
  dom.crudEnvironmentJson.addEventListener('onUpdateClick', onUpdateEnvironmentClick);
  dom.crudEnvironmentJson.addEventListener('onDeleteClick', onDeleteEnvironmentClick);
  dom.crudEnvironmentJson.addEventListener('onEditToggle', onEditToggle);

  dom.crudEnvironmentFields.addEventListener('onCreateClick', onCreateEnvironmentClick);
  dom.crudEnvironmentFields.addEventListener('onUpdateClick', onUpdateEnvironmentClick);
  dom.crudEnvironmentFields.addEventListener('onDeleteClick', onDeleteEnvironmentClick);
  dom.crudEnvironmentFields.addEventListener('onEditToggle', onEditToggle);

  dom.environmentSelector.onchange = onEnvironmentSelectorChange;

  // Ensure that ace editor to refresh when updated while hidden
  document.querySelector('a[data-bs-target="#tabEnvJson"]').addEventListener('shown.bs.tab', () => {
    settingsEditor.renderer.updateFull();
  });

  await environmentsJsonChanged(true);
}

async function onEnvironmentSelectorChange() {
  let urlSearchParams = new URLSearchParams(window.location.search);
  urlSearchParams.set(URL_PRIMARY_ENVIRONMENT_NAME, currentEnvironmentSelector());
  window.history.replaceState(null, null, `${window.location.pathname}?${urlSearchParams}`);
  await notifyAll(false);
}

async function onDeleteEnvironmentClick() {
  Utils.assert(selectedEnvName, 'No environment selected', dom.tableValidationEnvironments);
  Utils.assert(Object.keys(environments).length >= 2, 'At least one environment is required',
      dom.tableValidationEnvironments);
  delete environments[selectedEnvName];
  selectedEnvName = null;
  await environmentsJsonChanged(false);
}

async function onCreateEnvironmentClick(event) {
  Utils.assert(event.target.idValue,
      'Environment name must not be empty',
      event.target.validationElement);
  Utils.assert(!environments[event.target.idValue],
      'Environment name already used',
      event.target.validationElement);

  if (event.target === dom.crudEnvironmentFields) {
    environments[event.target.idValue] = new Environment({
      api_uri: dom.inputApiUri.value ?? '',
      searchNamespaces: dom.inputSearchNamespaces.value ?? '',
      ditto_version: dom.selectDittoVersion.value ? dom.selectDittoVersion.value : '3',
    });
  } else {
    environments[event.target.idValue] = new Environment(JSON.parse(settingsEditor.getValue()));
  }

  selectedEnvName = event.target.idValue;
  event.target.toggleEdit();
  await environmentsJsonChanged(false);
}

function onEnvironmentsTableClick(event) {
  if (event.target && event.target.tagName === 'TD') {
    if (selectedEnvName && selectedEnvName === event.target.parentNode.id) {
      selectedEnvName = null;
    } else {
      selectedEnvName = event.target.parentNode.id;
    }
    updateEnvEditors();
  }
}

async function onUpdateEnvironmentClick(event) {
  if (selectedEnvName !== event.target.idValue) {
    changeEnvironmentName();
  }
  if (event.target === dom.crudEnvironmentFields) {
    environments[selectedEnvName].api_uri = dom.inputApiUri.value;
    environments[selectedEnvName].ditto_version = dom.selectDittoVersion.value;
    environments[selectedEnvName].searchNamespaces = dom.inputSearchNamespaces.value;
    environments[selectedEnvName].disablePolicies = !dom.inputTabPolicies.checked;
    environments[selectedEnvName].disableConnections = !dom.inputTabConnections.checked;
    environments[selectedEnvName].disableOperations = !dom.inputTabOperations.checked;
  } else {
    environments[selectedEnvName] = JSON.parse(settingsEditor.getValue());
  }
  event.target.toggleEdit();
  await environmentsJsonChanged(false);

  function changeEnvironmentName() {
    environments[event.target.idValue] = environments[selectedEnvName];
    delete environments[selectedEnvName];
    selectedEnvName = event.target.idValue;
  }
}

export function saveEnvironmentsToLocalStorage() {
  environments && localStorage.setItem(STORAGE_KEY, JSON.stringify(environments));
}

export async function environmentsJsonChanged(initialPageLoad: boolean, modifiedField = null) {
  updateEnvSelector();

  saveEnvironmentsToLocalStorage();

  updateEnvEditors();
  updateEnvTable();

  await notifyAll(initialPageLoad, modifiedField);

  function updateEnvSelector() {
    let activeEnvironment = dom.environmentSelector.value;
    let urlSearchParams = new URLSearchParams(window.location.search);
    if (!activeEnvironment) {
      activeEnvironment = urlSearchParams.get(URL_PRIMARY_ENVIRONMENT_NAME);
    }
    let oidcState = urlSearchParams.get(OIDC_CALLBACK_STATE);
    if (!activeEnvironment && oidcState !== null) {
      let stateAndUrlState = oidcState.split(";");
      if (stateAndUrlState.length > 1) {
        const urlState = atob(stateAndUrlState[1]); // base64 decode to also support e.g. "&"
        const preservedQueryParams = new URLSearchParams(urlState)
        const primaryEnvironmentName = preservedQueryParams.get(URL_PRIMARY_ENVIRONMENT_NAME);
        const oidcProvider = preservedQueryParams.get(URL_OIDC_PROVIDER);
        activeEnvironment = primaryEnvironmentName;
        if (oidcProvider) {
          environments[activeEnvironment].authSettings.main.oidc.provider = oidcProvider
        }
      }
    }
    if (!activeEnvironment || !environments[activeEnvironment]) {
      activeEnvironment = Object.keys(environments)[0];
    }

    Utils.setOptions(dom.environmentSelector, Object.keys(environments));

    dom.environmentSelector.value = activeEnvironment;
  }

  function updateEnvTable() {
    dom.tbodyEnvironments.textContent = '';
    Object.keys(environments).forEach((key) => {
      Utils.addTableRow(dom.tbodyEnvironments, key, key === selectedEnvName);
    });
  }
}

function updateEnvEditors() {
  if (selectedEnvName) {
    const selectedEnvironment = environments[selectedEnvName];
    dom.crudEnvironmentFields.idValue = selectedEnvName;
    dom.crudEnvironmentJson.idValue = selectedEnvName;
    settingsEditor.setValue(Utils.stringifyPretty(selectedEnvironment), -1);
    dom.inputApiUri.value = selectedEnvironment.api_uri;
    dom.inputSearchNamespaces.value = selectedEnvironment.searchNamespaces ?? '';
    dom.selectDittoVersion.value = selectedEnvironment.ditto_version ? selectedEnvironment.ditto_version : '3';
    dom.inputTabPolicies.checked = !selectedEnvironment.disablePolicies;
    dom.inputTabConnections.checked = !selectedEnvironment.disableConnections;
    dom.inputTabOperations.checked = !selectedEnvironment.disableOperations;
  } else {
    dom.crudEnvironmentFields.idValue = null;
    dom.crudEnvironmentJson.idValue = null;
    settingsEditor.setValue('');
    dom.inputApiUri.value = null;
    dom.inputSearchNamespaces.value = null;
    dom.selectDittoVersion.value = 3;
    dom.inputTabPolicies.checked = true;
    dom.inputTabConnections.checked = true;
    dom.inputTabOperations.checked = true;
  }
}

async function loadEnvironmentTemplates() {
  let fromURL;
  let fromLocalStorage;

  let urlSearchParams = new URLSearchParams(window.location.search);
  let environmentsURL = urlSearchParams.get(URL_ENVIRONMENTS);
  if (environmentsURL) {
    try {
      let response = await fetch(environmentsURL);
      if (!response.ok) {
        throw new Error(`URL ${environmentsURL} can not be loaded`);
      }
      fromURL = await response.json();
      validateEnvironments(fromURL);
    } catch (err) {
      fromURL = null;
      Utils.showError(
          err.message,
          'Error loading environments from URL');
    }
  }

  const restoredEnv = localStorage.getItem(STORAGE_KEY);
  if (restoredEnv) {
    fromLocalStorage = JSON.parse(restoredEnv);
  }

  let result;

  if (fromURL) {
    if (fromLocalStorage) {
      result = merge(fromURL, fromLocalStorage);
    } else {
      result = fromURL;
    }
  } else if (fromLocalStorage) {
    result = fromLocalStorage;
  } else {
    result = defaultTemplates;
  }

  Object.keys(result).forEach((env) => {
    result[env] = new Environment(result[env]);
  });

  return result;

  function validateEnvironments(envs) {
    Object.keys(envs).forEach((key) => {
      if (!envs[key].hasOwnProperty('api_uri')) {
        throw new SyntaxError('Environments json invalid');
      }
    });
  }

  function merge(remote, local) {
    let merged = {};
    Object.keys(remote).forEach((env) => {
      merged[env] = {
        ...local[env],
        ...remote[env],
      };
    });
    return {
      ...local,
      ...merged,
    };
  }
}

function onEditToggle(event) {
  dom.inputApiUri.disabled = !event.detail.isEditing;
  dom.inputSearchNamespaces.disabled = !event.detail.isEditing;
  dom.selectDittoVersion.disabled = !event.detail.isEditing;
  dom.inputTabPolicies.disabled = !event.detail.isEditing;
  dom.inputTabConnections.disabled = !event.detail.isEditing;
  dom.inputTabOperations.disabled = !event.detail.isEditing;
  settingsEditor.setReadOnly(!event.detail.isEditing);
  settingsEditor.renderer.setShowGutter(event.detail.isEditing);
  if (!event.detail.isEditing) {
    updateEnvEditors();
  }
}
