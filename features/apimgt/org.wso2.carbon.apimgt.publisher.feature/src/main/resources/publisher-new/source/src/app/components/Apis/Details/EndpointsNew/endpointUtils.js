/**
 * Copyright (c)  WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Utility method to get the endpoint property name based on the given endpoint type and category.
 *
 * @param {string} type The type of the endpoint (load_balance/ failover)
 * @param {string} category The endpoint category (production/ sandbox)
 * @return {string} The property name of the endpoints.
 */
function getEndpointTypeProperty(type, category) {
    if (type === 'load_balance' || type === 'http') {
        return category;
    } else {
        return category === 'sandbox_endpoints' ? 'sandbox_failovers' : 'production_failovers';
    }
}

/**
 * Merge the loadbalance/ failover endpoints to single object.
 *
 * @param {object} endpointConfig The endpoint config object
 * @return {object} {production: [], sandbox: []}
 * */
function mergeEndpoints(endpointConfig) {
    const type = endpointConfig.endpoint_type;
    if (type === 'load_balance') {
        return { production: endpointConfig.production_endpoints, sandbox: endpointConfig.sandbox_endpoints };
    } else if (type === 'failover') {
        const prodEps = [endpointConfig.production_endpoints].concat(endpointConfig.production_failovers);
        const sandboxEps = [endpointConfig.sandbox_endpoints].concat(endpointConfig.sandbox_failovers);
        return { production: prodEps, sandbox: sandboxEps };
    }
    return { production: [endpointConfig.production_endpoints], sandbox: [endpointConfig.sandbox_endpoints] };
}

export { getEndpointTypeProperty, mergeEndpoints };
