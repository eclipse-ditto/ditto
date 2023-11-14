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
import * as Utils from '../utils.js';
import templatesHTML from './templates.html';
import templatesByService from './piggybackTemplates.json';
import * as Piggyback from './piggyback.js';

const dom = {
    templateServiceSelector: null,
    templateSelector: null,
    commandPreview: null,
    insertTemplate: null,
    templatesModal: null
};

let serviceOverall = "Overall";
let selectedService;

let templatesByNameMap = new Map();
let templatesByServiceMap = new Map();

document.getElementById('templatesHTML').innerHTML = templatesHTML;

export async function ready() {
    Utils.getAllElementsById(dom);

    loadTemplatesAndServices();

    dom.templateServiceSelector.onchange = onServiceSelected;
    dom.templateSelector.onchange = onTemplateSelected;

    dom.insertTemplate.onclick = insertTemplate;
}

export function setSelectedService(service) {
    dom.templateServiceSelector.value = service;
    onServiceSelected();
}

export function buildPiggybackCommand(targetActorSelection, headers, command) {
    return {
        targetActorSelection: targetActorSelection,
        headers: headers,
        piggybackCommand: command
    };
}

function loadTemplatesAndServices() {
    let services = [];
    for (const [service, templateEntry] of Object.entries(templatesByService).sort()) {
        services.push(service);
        templatesByServiceMap.set(service, []);
        for (const [templateName, template] of Object.entries(templateEntry)) {
            templatesByServiceMap.get(service).push(templateName);
            templatesByNameMap.set(templateName, template);
            if (template.service !== serviceOverall) {
                // Overall service option contains all templates.
                templatesByServiceMap.get(serviceOverall).push(templateName);
            }
        }
    }

    Utils.setOptions(dom.templateServiceSelector, services);
    Utils.setOptions(dom.templateSelector, templatesByServiceMap.get(serviceOverall));
}

function onServiceSelected() {
    if (dom.templateServiceSelector.value !== selectedService) {
        selectedService = dom.templateServiceSelector.value;
        let templates = templatesByServiceMap.get(selectedService);
        Utils.setOptions(dom.templateSelector, templates);
        onTemplateSelected();
    }
}

function onTemplateSelected() {
    let selectedTemplate = templatesByNameMap.get(dom.templateSelector.value);
    if (selectedTemplate) {
        let templateBody = buildPiggybackCommand(selectedTemplate.targetActorSelection,
            selectedTemplate.headers, selectedTemplate.command);
        dom.commandPreview.innerHTML = Utils.stringifyPretty(templateBody);
    } else {
        dom.commandPreview.innerHTML = "";
    }
}

async function insertTemplate() {
    let selectedTemplate = templatesByNameMap.get(dom.templateSelector.value);
    if (selectedTemplate) {
        Piggyback.onInsertTemplate(selectedTemplate);
    }
}

