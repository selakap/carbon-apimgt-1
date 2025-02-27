/**
 * Copyright (c) 2017, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict';

import APIClientFactory from './APIClientFactory';
import Resource from './Resource';
import Utils from './Utils';

/**
 * Class to expose Application {Resource} related operations i:e: Get all Application , Delete, Generate Keys ect..
 * @param {string} name Application name
 * @param {string} description Application description
 * @param {string} throttlingTier Application throttling tier
 * @param {string} kwargs Arguments
 */
export default class Application extends Resource {
    constructor(name, description, throttlingTier, kwargs) {
        super();
        this.id = kwargs ? kwargs.applicationId : null;
        this.client = new APIClientFactory().getAPIClient(Utils.getEnvironment().label).client;
        this.keys = new Map();
        this.tokens = new Map();
        for (const key in kwargs) {
            if (kwargs.hasOwnProperty(key)) {
                if (key === 'keys') {
                    this._setKeys(kwargs[key]);
                    continue;
                }
                this[key] = kwargs[key];
            }
        }
    }

    /** *
     * Set this.keys object by iterating the keys array received from REST API
     * @param {Array} keys  An array of keys object containing either PRODUCTION or/and SANDBOX key information
     * @private
     */
    _setKeys(keys) {
        for (const keyObj of keys) {
            this.keys.set(keyObj.keyType, keyObj);
        }
    }

    /** *
     * Set this.tokens object by iterating the keys array received from REST API
     * @param {Array} keys  An array of keys object containing either PRODUCTION or/and SANDBOX key information
     * @private
     */
    _setTokens(keys) {
        for (const keyObj of keys) {
            this.tokens.set(keyObj.keyType, keyObj.token);
        }
    }

    /** *
     * Get keys of the current instance of an application
     * @param  {string} keyType Key type either `Production` or `SandBox`
     * @returns {promise} Set the fetched CS/CK into current instance and return keys array as Promise object
     */
    getKeys(keyType) {
        return this.client.then(client => client.apis['Application Keys']
            .get_applications__applicationId__keys({ applicationId: this.applicationId }))
            .then((keysResponse) => {
                let keys = keysResponse.obj.list;
                this._setKeys(keys);
                this._setTokens(keys);
                return this.keys;
            });
    }

    /** *
     * Generate token for this application instance
     * @param {string} type token type
     * @param {string} validityPeriod token validityPeriod
     * @param {string} selectedScopes token scopes
     * @returns {promise} Set the generated token into current
     * instance and return tokenObject received as Promise object
     */
    generateToken(type, validityPeriod, selectedScopes) {
        const promiseToken = this.client.then((client) => {
            const keys = this.keys.get(type);
            const accessToken = this.tokens.get(type);
            const requestContent = {
                consumerSecret: keys.consumerSecret,
                validityPeriod: validityPeriod,
                revokeToken: accessToken.accessToken,
                scopes: selectedScopes,
                additionalProperties: ''
            };
            const payload = { applicationId: this.id, keyType: type, body: requestContent };
            return client.apis['Application Tokens']
                .post_applications__applicationId__keys__keyType__generate_token(payload);
        });
        return promiseToken.then((tokenResponse) => {
            const token = tokenResponse.obj;
            this.tokens.set(type, token);
            return token;
        });
    }

    /** *
     * Generate Consumer Secret and Consumer Key for this application instance
     * @param {string} keyType Key type either `Production` or `SandBox`
     * @param {string[]} supportedGrantTypes Grant types supported
     * @param  {string} callbackUrl callback url
     * @param  {string} tokenType Token type either `OAUTH` or `JWT`
     * @returns {promise} Set the generated token into current instance and return tokenObject
     * received as Promise object
     */
    generateKeys(keyType, supportedGrantTypes, callbackUrl) {
        const promisedKeys = this.client.then((client) => {
            const requestContent = {
                keyType, /* TODO: need to support dynamic key types ~tmkb */
                grantTypesToBeSupported: supportedGrantTypes,
                callbackUrl,
            };
            const payload = { applicationId: this.id, body: requestContent };
            return client.apis['Application Keys'].post_applications__applicationId__generate_keys(payload);
        });
        return promisedKeys.then((keysResponse) => {
            this.keys.set(keyType, keysResponse.obj);
            return this.keys.get(keyType);
        });
    }

    /** *
     * Generate Consumer Secret and Consumer Key for this application instance
     * @param  {string} tokenType Token Type either `OAUTH` or `JWT`
     * @param  {string} keyType Key type either `Production` or `SandBox`
     * @param {string[]} supportedGrantTypes Grant types supported
     * @param  {string} callbackUrl callback url
     * @param  {String} consumerKey Consumer key of application
     * @param  {String} consumerSecret Consumer secret of application
     * @returns {promise} Update the callbackURL and/or supportedGrantTypes
     */
    updateKeys(tokenType, keyType, supportedGrantTypes, callbackUrl, consumerKey, consumerSecret) {
        const promisedPut = this.client.then((client) => {
            const requestContent = {
                consumerKey,
                consumerSecret,
                supportedGrantTypes,
                callbackUrl,
                keyType,
                tokenType,
            };
            const payload = { applicationId: this.id, keyType, body: requestContent };
            return client.apis['Application Keys'].put_applications__applicationId__keys__keyType_(payload);
        });
        return promisedPut.then((keysResponse) => {
            this.keys.set(keyType, keysResponse.obj);
            return this;
        });
    }

    static get(id) {
        const apiClient = new APIClientFactory().getAPIClient(Utils.getEnvironment());
        const promised_get = apiClient.client.then(
            (client) => {
                return client.apis.Applications.get_applications__applicationId_({ applicationId: id },
                    this._requestMetaData());
            }
        );
        return promised_get.then((response) => {
            const app_json = response.obj;
            return new Application(app_json.name, app_json.description, app_json.throttlingTier, app_json);
        });
    }

    static all() {
        const apiClient = new APIClientFactory().getAPIClient(Utils.getEnvironment());
        const promised_all = apiClient.client.then(
            (client) => {
                return client.apis.Applications.get_applications({}, this._requestMetaData());
            }
        );
        return promised_all.then(response => response.obj);
    }

    static deleteApp(id) {
        const apiClient = new APIClientFactory().getAPIClient(Utils.getEnvironment());
        const promised_delete = apiClient.client.then(
            (client) => {
                return client.apis.Applications.delete_applications__applicationId_({ applicationId: id },
                    this._requestMetaData());
            }
        );
        return promised_delete.then(response => response.ok);
    }
}

Application.KEY_TYPES = {
    PRODUCTION: 'PRODUCTION',
    SANDBOX: 'SANDBOX',
};
