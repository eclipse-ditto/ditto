/* eslint-disable require-jsdoc */
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

import { EventSourcePolyfill } from 'event-source-polyfill';
import {
  devopsBearerToken,
  devopsOidcBearerToken,
  devopsUsernamePassword,
  mainBearerToken,
  mainOidcBearerToken,
  mainUsernamePassword
} from './environments/authorization';
import * as Environments from './environments/environments.js';
import { AuthMethod } from './environments/environments.js';
import * as Utils from './utils.js';
import { showError } from './utils.js';


const config = {
  things: {
    listConnections: {
      path: '/api/2/solutions/{{solutionId}}/connections',
      method: 'GET',
      body: null,
      unwrapJsonPath: null,
    },
    retrieveConnection: {
      path: '/api/2/solutions/{{solutionId}}/connections/{{connectionId}}',
      method: 'GET',
      body: null,
      unwrapJsonPath: null,
    },
    createConnection: {
      path: '/api/2/solutions/{{solutionId}}/connections',
      method: 'POST',
      body: null,
      unwrapJsonPath: null,
    },
    modifyConnection: {
      path: '/api/2/solutions/{{solutionId}}/connections/{{connectionId}}',
      method: 'PUT',
      body: null,
      unwrapJsonPath: null,
    },
    deleteConnection: {
      path: '/api/2/solutions/{{solutionId}}/connections/{{connectionId}}',
      method: 'DELETE',
      body: null,
      unwrapJsonPath: null,
    },
    retrieveStatus: {
      path: '/api/2/solutions/{{solutionId}}/connections/{{connectionId}}/status',
      method: 'GET',
      body: null,
      unwrapJsonPath: null,
    },
    retrieveConnectionLogs: {
      path: '/api/2/solutions/{{solutionId}}/connections/{{connectionId}}/logs',
      method: 'GET',
      body: null,
      unwrapJsonPath: null,
    },
    retrieveConnectionMetrics: {
      path: '/api/2/solutions/{{solutionId}}/connections/{{connectionId}}/metrics',
      method: 'GET',
      body: null,
      unwrapJsonPath: null,
    },
    connectionCommand: {
      path: '/api/2/solutions/{{solutionId}}/connections/{{connectionId}}/command',
      method: 'POST',
      body: null,
      unwrapJsonPath: null,
    },
  },
  ditto_3: {
    listConnections: {
      path: '/api/2/connections',
      method: 'GET',
      body: null,
      unwrapJsonPath: null,
    },
    retrieveConnection: {
      path: '/api/2/connections/{{connectionId}}',
      method: 'GET',
      body: null,
      unwrapJsonPath: null,
    },
    createConnection: {
      path: '/api/2/connections',
      method: 'POST',
      body: null,
      unwrapJsonPath: null,
    },
    modifyConnection: {
      path: '/api/2/connections/{{connectionId}}',
      method: 'PUT',
      body: null,
      unwrapJsonPath: null,
    },
    deleteConnection: {
      path: '/api/2/connections/{{connectionId}}',
      method: 'DELETE',
      body: null,
      unwrapJsonPath: null,
    },
    retrieveStatus: {
      path: '/api/2/connections/{{connectionId}}/status',
      method: 'GET',
      body: null,
      unwrapJsonPath: null,
    },
    retrieveConnectionLogs: {
      path: '/api/2/connections/{{connectionId}}/logs',
      method: 'GET',
      body: null,
      unwrapJsonPath: null,
    },
    retrieveConnectionMetrics: {
      path: '/api/2/connections/{{connectionId}}/metrics',
      method: 'GET',
      body: null,
      unwrapJsonPath: null,
    },
    connectionCommand: {
      path: '/api/2/connections/{{connectionId}}/command',
      method: 'POST',
      body: null,
      unwrapJsonPath: null,
    },
  },
  ditto_2: {
    listConnections: {
      path: '/devops/piggyback/connectivity',
      method: 'POST',
      body: {
        'targetActorSelection': '/user/connectivityRoot/connectionIdsRetrieval/singleton',
        'headers': {
          'aggregate': false,
        },
        'piggybackCommand': {
          'type': 'connectivity.commands:retrieveAllConnectionIds',
        },
      },
      unwrapJsonPath: '?.?.connectionIds',
    },
    retrieveConnection: {
      path: '/devops/piggyback/connectivity',
      method: 'POST',
      body: {
        'targetActorSelection': '/system/sharding/connection',
        'headers': {
          'aggregate': false,
        },
        'piggybackCommand': {
          'type': 'connectivity.commands:retrieveConnection',
          'connectionId': '{{connectionId}}',
        },
      },
      unwrapJsonPath: '?.?.connection',
    },
    createConnection: {
      path: '/devops/piggyback/connectivity',
      method: 'POST',
      body: {
        'targetActorSelection': '/system/sharding/connection',
        'headers': {
          'aggregate': false,
        },
        'piggybackCommand': {
          'type': 'connectivity.commands:createConnection',
          'connection': '{{connectionJson}}',
        },
      },
      unwrapJsonPath: '?.?.connection',
    },
    modifyConnection: {
      path: '/devops/piggyback/connectivity',
      method: 'POST',
      body: {
        'targetActorSelection': '/system/sharding/connection',
        'headers': {
          'aggregate': false,
        },
        'piggybackCommand': {
          'type': 'connectivity.commands:modifyConnection',
          'connection': '{{connectionJson}}',
        },
      },
      unwrapJsonPath: null,
    },
    deleteConnection: {
      path: '/devops/piggyback/connectivity',
      method: 'POST',
      body: {
        'targetActorSelection': '/system/sharding/connection',
        'headers': {
          'aggregate': false,
        },
        'piggybackCommand': {
          'type': 'connectivity.commands:deleteConnection',
          'connectionId': '{{connectionId}}',
        },
      },
      unwrapJsonPath: null,
    },
    retrieveStatus: {
      path: '/devops/piggyback/connectivity',
      method: 'POST',
      body: {
        'targetActorSelection': '/system/sharding/connection',
        'headers': {
          'aggregate': false,
        },
        'piggybackCommand': {
          'type': 'connectivity.commands:retrieveConnectionStatus',
          'connectionId': '{{connectionId}}',
        },
      },
      unwrapJsonPath: '?.?',
    },
    retrieveConnectionLogs: {
      path: '/devops/piggyback/connectivity',
      method: 'POST',
      body: {
        'targetActorSelection': '/system/sharding/connection',
        'headers': {
          'aggregate': false,
          'is-group-topic': true,
        },
        'piggybackCommand': {
          'type': 'connectivity.commands:retrieveConnectionLogs',
          'connectionId': '{{connectionId}}',
        },
      },
      unwrapJsonPath: '?.?',
    },
    retrieveConnectionMetrics: {
      path: '/devops/piggyback/connectivity',
      method: 'POST',
      body: {
        'targetActorSelection': '/system/sharding/connection',
        'headers': {
          'aggregate': false,
          'is-group-topic': true,
        },
        'piggybackCommand': {
          'type': 'connectivity.commands:retrieveConnectionMetrics',
          'connectionId': '{{connectionId}}',
        },
      },
      unwrapJsonPath: '?.?',
    },
    connectionCommand: {
      path: '/devops/piggyback/connectivity',
      method: 'POST',
      body: {
        'targetActorSelection': '/system/sharding/connection',
        'headers': {
          'aggregate': false,
          'is-group-topic': true,
        },
        'piggybackCommand': {
          'type': '{{command}}',
          'connectionId': '{{connectionId}}',
        },
      },
      unwrapJsonPath: null,
    },
  },
};

let authHeaderKey;
let authHeaderValue;

/**
 * Activates authorization header for api calls
 * @param {boolean} forDevOps if true, the credentials for the dev ops api will be used.
 */
export function setAuthHeader(forDevOps: boolean) {
  authHeaderValue = undefined;
  let environment = Environments.current();
  if (forDevOps) {
    let devopsAuthMethod = environment.authSettings?.devops?.method;
    if (devopsAuthMethod === AuthMethod.basic) {
      let devopsUserPass = devopsUsernamePassword();
      if (devopsUserPass) {
        authHeaderKey = 'Authorization';
        authHeaderValue = 'Basic ' + window.btoa(devopsUserPass);
      } else {
        showError('DevOps Username/password missing');
        document.getElementById('authorize').click();
      }
    } else if (devopsAuthMethod === AuthMethod.bearer) {
      let devopsToken = devopsBearerToken();
      if (devopsToken) {
        authHeaderKey = 'Authorization';
        authHeaderValue = 'Bearer ' + devopsToken;
      } else {
        showError('DevOps Bearer token missing');
        document.getElementById('authorize').click();
      }
    } else if (devopsAuthMethod === AuthMethod.oidc) {
      let devopsOidcToken = devopsOidcBearerToken();
      if (devopsOidcToken) {
        authHeaderKey = 'Authorization';
        authHeaderValue = 'Bearer ' + devopsOidcToken;
      } else {
        showError('DevOps SSO (Bearer) token missing');
        document.getElementById('authorize').click();
      }
    } else {
      authHeaderKey = 'Authorization';
      authHeaderValue = 'Basic';
    }
  } else {
    let mainAuthMethod = environment.authSettings?.main?.method;
    if (mainAuthMethod === AuthMethod.basic) {
      let mainUserPass = mainUsernamePassword();
      if (mainUserPass) {
        authHeaderKey = 'Authorization';
        authHeaderValue = 'Basic ' + window.btoa(mainUserPass);
      } else {
        showError('Username/password missing');
        document.getElementById('authorize').click();
      }
    } else if (mainAuthMethod === AuthMethod.pre) {
      if (environment.authSettings.main.pre.dittoPreAuthenticatedUsername) {
        authHeaderKey = 'x-ditto-pre-authenticated';
        authHeaderValue = environment.authSettings.main.pre.dittoPreAuthenticatedUsername;
      } else {
        showError('Pre-Authenticated username missing');
        document.getElementById('authorize').click();
      }
    } else if (mainAuthMethod === AuthMethod.bearer) {
      let mainToken = mainBearerToken();
      if (mainToken) {
        authHeaderKey = 'Authorization';
        authHeaderValue = 'Bearer ' + mainToken;
      } else {
        showError('Bearer token missing');
        document.getElementById('authorize').click();
      }
    } else if (mainAuthMethod === AuthMethod.oidc) {
      let mainOidcToken = mainOidcBearerToken();
      if (mainOidcToken) {
        authHeaderKey = 'Authorization';
        authHeaderValue = 'Bearer ' + mainOidcToken;
      } else {
        showError('SSO (Bearer) token missing');
        document.getElementById('authorize').click();
      }
    } else {
      authHeaderKey = 'Authorization';
      authHeaderValue = 'Basic';
    }
  }
}

function shouldShowAuthDialog(dittoErr) {
  return (dittoErr.status === 400 && dittoErr.error === "jwt:invalid") ||
    dittoErr.status === 401;
}

function showDittoError(dittoErr, response) {
  if (dittoErr.status && dittoErr.message) {
    Utils.showError(dittoErr.description + `\n(${dittoErr.error})`, dittoErr.message, dittoErr.status);
    if (shouldShowAuthDialog(dittoErr)) {
      document.getElementById('authorize').click();
    }
  } else {
    Utils.showError(JSON.stringify(dittoErr), 'Error', response.status);
  }
}

/**
 * Calls the Ditto api
 * @param {string} method 'POST', 'GET', 'DELETE', etc.
 * @param {string} path of the Ditto call (e.g. '/things')
 * @param {Object} body payload for the api call
 * @param {Object} additionalHeaders object with additional header fields
 * @param {boolean} returnHeaders request full response instead of json content
 * @param {boolean} devOps default: false. Set true to avoid /api/2 path
 * @param {boolean} returnErrorJson default: false. Set true to return the response of a failed HTTP call as JSON
 * @return {Object} result as json object
 */
export async function callDittoREST(method: string,
                                    path: string,
                                    body = null,
                                    additionalHeaders = null,
                                    returnHeaders = false,
                                    devOps = false,
                                    returnErrorJson = false): Promise<any> {
  if (authHeaderValue) {
    let response;
    const contentType = method === 'PATCH' ? 'application/merge-patch+json' : 'application/json';
    try {
      response = await fetch(Environments.current().api_uri + (devOps ? '' : '/api/2') + path, {
        method: method,
        headers: {
          'Content-Type': contentType,
          [authHeaderKey]: authHeaderValue,
          ...additionalHeaders,
        },
        ...(method !== 'GET' && method !== 'DELETE' && body !== undefined) && {body: JSON.stringify(body)},
      });
    } catch (err) {
      Utils.showError(err);
      throw err;
    }
    if (!response.ok) {
      if (returnErrorJson) {
        if (returnHeaders) {
          return response;
        } else {
          return response.json().then((dittoErr) => {
            showDittoError(dittoErr, response);
            return dittoErr;
          });
        }
      } else {
        response.json()
          .then((dittoErr) => {
            showDittoError(dittoErr, response);
          })
          .catch((err) => {
            Utils.showError('No error details from Ditto', response.statusText, response.status);
          });
        throw new Error('An error occurred: ' + response.status);
      }
    }
    if (response.status !== 204) {
      if (returnHeaders) {
        return response;
      } else {
        return response.json();
      }
    } else {
      return null;
    }
  } else {
    throw new Error("Authentication missing");
  }
}

export function getEventSource(thingIds, urlParams) {
  if (authHeaderValue) {
    return new EventSourcePolyfill(
      `${Environments.current().api_uri}/api/2/things?ids=${thingIds}${urlParams ? '&' + urlParams : ''}`, {
        headers: {
          [authHeaderKey]: authHeaderValue,
        },
      },
    );
  } else {
    throw new Error("Authentication missing");
  }
}

/**
 * Calls connections api. Uses devops api in case of Ditto and the solutions api in case of Things
 * @param {*} operation connections api operation
 * @param {*} successCallback callback on success
 * @param {*} connectionId connectionId
 * @param {*} connectionJson optional json of connection configuration
 * @param {*} command optional command
 * @return {*} promise to the result
 */
export async function callConnectionsAPI(operation, successCallback, connectionId = '', connectionJson = null, command = null) {
  const params = config[env()][operation];
  let response;
  let body;
  if (params.body) {
    body = JSON.stringify(params.body)
      .replace('{{connectionId}}', connectionId)
      .replace('"{{connectionJson}}"', JSON.stringify(connectionJson))
      .replace('{{command}}', command);
  } else if (connectionJson) {
    body = JSON.stringify(connectionJson);
  } else {
    body = command;
  }

  if (authHeaderValue) {
    try {
      response = await fetch(Environments.current().api_uri + params.path
        .replace('{{connectionId}}', connectionId), {
        method: params.method,
        headers: {
          'Content-Type': operation === 'connectionCommand' ? 'text/plain' : 'application/json',
          [authHeaderKey]: authHeaderValue,
        },
        ...(body) && {body: body},
      });
    } catch (err) {
      Utils.showError(err);
      throw err;
    }
  } else {
    throw new Error("Authentication missing");
  }

  if (!response.ok) {
    response.json()
        .then((dittoErr) => {
          showDittoError(dittoErr, response);
        })
        .catch((err) => {
          Utils.showError('No error details from Ditto', response.statusText, response.status);
        });
    throw new Error('An error occured: ' + response.status);
  }
  if (operation !== 'connectionCommand' && response.status !== 204) {
    document.body.style.cursor = 'progress';
    response.json()
        .then((data) => {
          if (data && data['?'] && data['?']['?'].status >= 400) {
            const dittoErr = data['?']['?'].payload;
            showDittoError(dittoErr, response);
          } else {
            if (params.unwrapJsonPath) {
              params.unwrapJsonPath.split('.').forEach(function(node) {
                if (node === '?') {
                  node = Object.keys(data)[0];
                }
                data = data[node];
              });
            }
            successCallback(data);
          }
        }).catch((error) => {
          Utils.showError('Error calling connections API', error);
          throw error;
        }).finally(() => {
          document.body.style.cursor = 'default';
        });
  } else {
    successCallback && successCallback();
  }
}

export function env() {
  if (Environments.current().ditto_version === 2) {
    return 'ditto_2';
  } else {
    return 'ditto_3';
  }
}

