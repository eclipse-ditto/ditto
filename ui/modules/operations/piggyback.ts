/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

/* eslint-disable arrow-parens */
/* eslint-disable prefer-const */
/* eslint-disable require-jsdoc */
import * as API from '../api.js';
import * as Utils from '../utils.js';
import piggybackHTML from './piggyback.html';
import piggybackPlaceholders from './piggybackPlaceholders.json';
import * as Templates from './templates.js';
import {TabHandler} from '../utils/tabHandler.js';

const EDITOR_INVALID_JSON_MNSSAGE = 'Invalid json!'
const HEADER_IS_REQUIRED_MESSAGE = 'Headers field is required!';
const COMMAND_IS_REQUIRED = 'Command field is required!';
const TIMEOUT_IS_REQUIRED_MESSAGE = 'Timeout field is required!';
const ACTOR_PATH_IS_REQUIRED_MESSAGE = 'Actor path field is required!';
const NO_SERVICES_MESSAGE = 'No services available!';
const SERVICES_LOADING = 'Loading...';
const REQUEST_IN_PROGRESS_MESSAGE = 'request in progress';
const REQUEST_ERROR_MESSAGE = 'error';

let chosenTemplate;

let serviceOverall = 'Overall';
let servicesAvailable = {};
let chosenService;

let instancesAll = 'All';
let chosenInstance;

let aceHeadersEditor;
let aceCommandEditor;
let aceResponse;
let editorValidatorMap = new Map();

let dom = {
    showTemplates: null,
    serviceSelector: null,
    instanceContainer: null,
    instanceSelector: null,
    timeout: null,
    buttonLoadServiceInstances: null,
    targetActorSelection: null,
    buttonSubmit: null,
    buttonCancel: null,
    responseStatus: null,
    commandValidationElement: null,
    headerValidationElement: null,
    tabOperations: null,
    collapseOperations: null,
}

document.getElementById('piggybackHTML').innerHTML = piggybackHTML;

export async function ready() {
    Utils.getAllElementsById(dom);

    dom.showTemplates.onclick = function () {
        let service = dom.serviceSelector.value
        if (service === SERVICES_LOADING) {
            service = serviceOverall;
        }
        Templates.setSelectedService(service);
    }

    TabHandler(dom.tabOperations, dom.collapseOperations, loadServicesAndInstances, 'disableOperations');

    initAceEditors();

    dom.targetActorSelection.placeholder = piggybackPlaceholders.targetActorSelection;

    dom.buttonLoadServiceInstances.onclick = loadServicesAndInstances;
    dom.buttonSubmit.onclick = submitPiggybackCommand;

    dom.serviceSelector.onchange = onServiceSelected;
    dom.instanceSelector.onchange = onInstanceSelected;
    dom.timeout.onchange = onTimeoutChange;
    dom.targetActorSelection.onchange = onActorPathChange;
}

export function onInsertTemplate(template) {
    chosenTemplate = template;
    dom.serviceSelector.value = chosenTemplate.service;
    chosenInstance = undefined;
    onServiceSelected();
    dom.timeout.value = chosenTemplate.timeout;
    dom.targetActorSelection.value = chosenTemplate.targetActorSelection;
    aceHeadersEditor.setValue(Utils.stringifyPretty(chosenTemplate.headers), -1);
    aceCommandEditor.setValue(Utils.stringifyPretty(chosenTemplate.command), -1);
}

async function loadServicesAndInstances() {
    servicesAvailable[serviceOverall] = [];
    Utils.setOptions(dom.serviceSelector, [SERVICES_LOADING]);
    await API.callDittoREST('GET', '/devops/logging', null, null, false, true)
        .then((result) => {
            let servicesJsonArray = Object.entries(result).sort();
            if (servicesJsonArray.length > 0) {
                servicesJsonArray.forEach(service => {
                    let jsonServiceInstances = Object.values(service[1]);
                    let instances = jsonServiceInstances.map((jsonServiceInstance) => {
                        return jsonServiceInstance.instance;
                    }).sort();
                    instances.unshift(instancesAll);
                    servicesAvailable[service[0]] = instances;
                });
            } else {
                servicesAvailable = [];
            }
        })
        .catch((_err) => {
            servicesAvailable = [];
        });

    setServices(servicesAvailable);

    function setServices(services) {
        let serviceNames = Object.keys(services);
        if (!chosenService) {
            if (chosenTemplate && serviceNames.indexOf(chosenTemplate.service) !== -1) {
                chosenService = chosenTemplate.service;
            } else if (serviceNames.length > 0) {
                chosenService = serviceNames[0];
            }
        } else {
            if (serviceNames.indexOf(chosenService) == -1) {
                chosenService = undefined;
            }
        }

        Utils.setOptions(dom.serviceSelector, serviceNames);
        dom.serviceSelector.value = chosenService;
        onServiceSelected();
    }
}

function onServiceSelected() {
    if (!dom.serviceSelector.value !== chosenService) {
        validateAndReturn(dom.serviceSelector.value !== '', NO_SERVICES_MESSAGE, dom.serviceSelector);
        chosenService = dom.serviceSelector.value;
        if (chosenService && chosenService !== serviceOverall) {
            dom.instanceContainer.removeAttribute('hidden', '');
            setInstances(servicesAvailable[chosenService]);
        } else {
            dom.instanceContainer.setAttribute('hidden', '');
        }
    }

    function setInstances(serviceInstances) {
        if (!chosenInstance) {
            if (serviceInstances.length > 0) {
                chosenInstance = serviceInstances[0];
            }
        } else {
            if (serviceInstances.indexOf(chosenInstance) == -1) {
                chosenInstance = undefined;
            }
        }

        Utils.setOptions(dom.instanceSelector, serviceInstances);
        dom.instanceSelector.value = chosenInstance;
    }
}

function initAceEditors() {
    aceHeadersEditor = Utils.createAceEditor('aceHeaders', 'ace/mode/json');
    aceHeadersEditor.setOption('wrap', true);
    editorValidatorMap.set(aceHeadersEditor.getSession(), dom.headerValidationElement);

    aceCommandEditor = Utils.createAceEditor('aceCommand', 'ace/mode/json');
    aceCommandEditor.setOption('wrap', true);
    editorValidatorMap.set(aceCommandEditor.getSession(), dom.commandValidationElement);

    aceResponse = Utils.createAceEditor('acePiggybackResponse', 'ace/mode/json');
    aceResponse.setReadOnly(true);
    aceResponse.setOption('wrap', true);


    aceHeadersEditor.setOption('placeholder', Utils.stringifyPretty(piggybackPlaceholders.headers));
    aceCommandEditor.setOption('placeholder', Utils.stringifyPretty(piggybackPlaceholders.command));

    aceHeadersEditor.getSession().on('changeAnnotation', onEditorChangeAnnotation);
    aceCommandEditor.getSession().on('changeAnnotation', onEditorChangeAnnotation);
}

function onEditorChangeAnnotation(_event, editorSession) {
    validateAndReturn(!hasEditorError(editorSession), EDITOR_INVALID_JSON_MNSSAGE, editorValidatorMap.get(editorSession));
}

function hasEditorError(editorSession) {
    return editorSession.getAnnotations().filter((a) => a.type === 'error').length > 0;
}

async function submitPiggybackCommand() {
    if (isCommandValid()) {
        dom.responseStatus.innerHTML = REQUEST_IN_PROGRESS_MESSAGE;
        aceResponse.setValue('', -1);
        let path = buildPath(
            dom.serviceSelector.value,
            dom.instanceSelector.value,
            dom.timeout.value
        );

        let piggybackCommandBody = Templates.buildPiggybackCommand(
            dom.targetActorSelection.value,
            JSON.parse(aceHeadersEditor.getValue()),
            JSON.parse(aceCommandEditor.getValue())
        );

        let promise = new Promise((resolve, reject) => {
            onRequestInProgress(reject);
            try {
                API.callDittoREST('POST', path, piggybackCommandBody, null, true, true)
                    .then(result => resolve(result))
                    .catch(err => reject(err));
            } catch (err) {
                onRequestDone();
                aceResponse.setValue(err.message, -1);
                dom.responseStatus.innerHTML = REQUEST_ERROR_MESSAGE;
            }
        });
        promise.then((result: any) => {
            onRequestDone();
            result.json().then(resultJson => {
                aceResponse.setValue(Utils.stringifyPretty(resultJson), -1);
                dom.responseStatus.innerHTML = result.status;
            });
        }).catch(err => {
            onRequestDone();
            aceResponse.setValue(err.message, -1);
            dom.responseStatus.innerHTML = REQUEST_ERROR_MESSAGE;
        });

    }

    function isCommandValid() {
        let isTimeoutValid = validateAndReturn(dom.timeout.value.trim() !== '', TIMEOUT_IS_REQUIRED_MESSAGE, dom.timeout);
        let isServiceValid = validateAndReturn(dom.serviceSelector.value !== '' && dom.serviceSelector.value !== SERVICES_LOADING, NO_SERVICES_MESSAGE, dom.serviceSelector);
        let isActorPathValid = validateAndReturn(dom.targetActorSelection.value.trim() !== '', ACTOR_PATH_IS_REQUIRED_MESSAGE, dom.targetActorSelection);

        let areHeadersValid = validateAndReturn(aceHeadersEditor.getValue().trim() !== '', HEADER_IS_REQUIRED_MESSAGE, dom.headerValidationElement);
        areHeadersValid &&= validateAndReturn(!hasEditorError(aceHeadersEditor.getSession()), EDITOR_INVALID_JSON_MNSSAGE, dom.headerValidationElement);

        let isCommandValid = validateAndReturn(aceCommandEditor.getValue().trim() !== '', COMMAND_IS_REQUIRED, dom.commandValidationElement);
        isCommandValid &&= validateAndReturn(!hasEditorError(aceCommandEditor.getSession()), EDITOR_INVALID_JSON_MNSSAGE, dom.commandValidationElement);

        return isTimeoutValid && isServiceValid && isActorPathValid && areHeadersValid && isCommandValid;

    }

    function buildPath(service, instance, timeout) {
        let path = '';
        if (service && service !== serviceOverall) {
            path = `/${service}`;
            if (instance && instance != instancesAll) {
                path += `/${instance}`;
            }
        }
        return `/devops/piggyback${path}?timeout=${timeout}`;
    }

    function onRequestInProgress(rejectCallback) {
        dom.buttonCancel.onclick = function () {
            rejectCallback({ 'message': 'Cancelled' });
        }
        dom.buttonCancel.removeAttribute('hidden', '');
        dom.buttonSubmit.setAttribute('hidden', '')
    }

    function onRequestDone() {
        dom.buttonCancel.setAttribute('hidden', '');
        dom.buttonSubmit.removeAttribute('hidden', '')
        dom.buttonCancel.onclick = undefined;
    }
}

function onInstanceSelected() {
    chosenInstance = dom.instanceSelector.value;
}

function onTimeoutChange() {
    validateAndReturn(dom.timeout.value.trim() !== '', TIMEOUT_IS_REQUIRED_MESSAGE, dom.timeout);
}

function onActorPathChange() {
    validateAndReturn(dom.targetActorSelection.value.trim() !== '', ACTOR_PATH_IS_REQUIRED_MESSAGE, dom.targetActorSelection);
}

function validateAndReturn(condition, message, validatedElement) {
    try {
        Utils.assert(condition, message, validatedElement);
    } catch (err) {
        return false;
    }

    return true;
}
