/*
 *  Copyright WSO2 Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.wso2.carbon.apimgt.gateway.handlers.security;

public class APISecurityConstants {
    public static final String API_AUTH_FAILURE_HANDLER = "_auth_failure_handler_";
    public static final int API_AUTH_GENERAL_ERROR       = 900900;
    public static final String API_AUTH_GENERAL_ERROR_MESSAGE = "Unclassified Authentication Failure";

    public static final int API_AUTH_INVALID_CREDENTIALS = 900901;
    public static final String API_AUTH_INVALID_CREDENTIALS_MESSAGE = "Invalid OAuth Credentials";
    public static final String API_AUTH_INVALID_CREDENTIALS_DESCRIPTION =
            "Make sure you have given the correct access token";

    public static final int API_AUTH_MISSING_CREDENTIALS = 900902;
    public static final String API_AUTH_MISSING_CREDENTIALS_MESSAGE = "Missing OAuth Credentials";
    public static final String API_AUTH_MISSING_CREDENTIALS_DESCRIPTION =
            "Make sure your API invocation call has a header: ";

    public static final int API_AUTH_ACCESS_TOKEN_EXPIRED = 900903;
    public static final String API_AUTH_ACCESS_TOKEN_EXPIRED_MESSAGE = "Access Token Expired";
    public static final String API_AUTH_ACCESS_TOKEN_EXPIRED_DESCRIPTION =
            "Renew the access token and try again";

    public static final int API_AUTH_ACCESS_TOKEN_INACTIVE = 900904;
    public static final String API_AUTH_ACCESS_TOKEN_INACTIVE_MESSAGE = "Access Token Inactive";
    public static final String API_AUTH_ACCESS_TOKEN_INACTIVE_DESCRIPTION =
            "Generate a new access token and try again";

    public static final int API_AUTH_INCORRECT_ACCESS_TOKEN_TYPE = 900905;
    public static final String API_AUTH_INCORRECT_ACCESS_TOKEN_TYPE_MESSAGE = "Incorrect Access Token Type is provided";

    public static final int API_AUTH_INCORRECT_API_RESOURCE = 900906;
    public static final String API_AUTH_INCORRECT_API_RESOURCE_MESSAGE = "No matching resource found in the API for the given request";
    public static final String API_AUTH_INCORRECT_API_RESOURCE_DESCRIPTION =
            "Check the API documentation and add a proper REST resource path to the invocation URL";

    public static final int API_BLOCKED = 900907;
    public static final String API_BLOCKED_MESSAGE = "The requested API is temporarily blocked";

    public static final int API_AUTH_FORBIDDEN = 900908;
    public static final String API_AUTH_FORBIDDEN_MESSAGE = "Resource forbidden ";

    public static final int SUBSCRIPTION_INACTIVE = 900909;
    public static final String SUBSCRIPTION_INACTIVE_MESSAGE = "The subscription to the API is inactive";

    public static final int INVALID_SCOPE = 900910;
    public static final String INVALID_SCOPE_MESSAGE = "The access token does not allow you to access the requested resource";

    public static final int MUTUAL_SSL_VALIDATION_FAILURE = 900911;
    public static final String MUTUAL_SSL_VALIDATION_FAILURE_MESSAGE = "The mutual SSL authentication has failed due "
            + "to invalid/missing client certificate";

    public static final int MULTI_AUTHENTICATION_FAILURE = 900912;
    public static final String MULTI_AUTHENTICATION_FAILURE_MESSAGE = "Authentication has failed after trying with "
            + "multiple authenticators";

    public static final int API_AUTH_MISSING_BASIC_AUTH_CREDENTIALS = 900913;
    public static final String API_AUTH_MISSING_BASIC_AUTH_CREDENTIALS_MESSAGE = "Missing Basic Auth Credentials";
    public static final int API_AUTH_INVALID_BASIC_AUTH_CREDENTIALS = 900914;
    public static final String API_AUTH_INVALID_BASIC_AUTH_CREDENTIALS_MESSAGE = "Invalid Basic Auth Credentials";

    public static final int MULTI_AUTHENTICATION_FAILURE_AND_MISSING_OAUTH_CREDENTIALS = 900915;
    public static final int MULTI_AUTHENTICATION_FAILURE_AND_MISSING_BASIC_AUTH_CREDENTIALS = 900916;
    public static final int MULTI_AUTHENTICATION_FAILURE_AND_MISSING_OAUTH_AND_BASIC_AUTH_CREDENTIALS = 900917;


    // We have added this because we need to add an additional description to the original one and we need to
    // separate the 2 messages
    public static final String DESCRIPTION_SEPARATOR = ". ";

    /**
     * returns an String that corresponds to errorCode passed in
     * @param errorCode
     * @return String
     */
    public static final String getAuthenticationFailureMessage(int errorCode) {
        String errorMessage;
        switch (errorCode) {
            case API_AUTH_ACCESS_TOKEN_EXPIRED:
                errorMessage = API_AUTH_ACCESS_TOKEN_EXPIRED_MESSAGE;
                break;
            case API_AUTH_ACCESS_TOKEN_INACTIVE:
                errorMessage = API_AUTH_ACCESS_TOKEN_INACTIVE_MESSAGE;
                break;
            case API_AUTH_GENERAL_ERROR:
                errorMessage = API_AUTH_GENERAL_ERROR_MESSAGE;
                break;
            case API_AUTH_INVALID_CREDENTIALS:
                errorMessage = API_AUTH_INVALID_CREDENTIALS_MESSAGE;
                break;
            case API_AUTH_INVALID_BASIC_AUTH_CREDENTIALS:
                errorMessage = API_AUTH_INVALID_BASIC_AUTH_CREDENTIALS_MESSAGE;
                break;
            case API_AUTH_MISSING_CREDENTIALS:
                errorMessage = API_AUTH_MISSING_CREDENTIALS_MESSAGE;
                break;
            case API_AUTH_MISSING_BASIC_AUTH_CREDENTIALS:
                errorMessage = API_AUTH_MISSING_BASIC_AUTH_CREDENTIALS_MESSAGE;
                break;
            case API_AUTH_INCORRECT_API_RESOURCE:
                errorMessage = API_AUTH_INCORRECT_API_RESOURCE_MESSAGE;
                break;
            case API_AUTH_INCORRECT_ACCESS_TOKEN_TYPE:
                errorMessage = API_AUTH_INCORRECT_ACCESS_TOKEN_TYPE_MESSAGE;
                break;
            case API_BLOCKED:
                errorMessage = API_BLOCKED_MESSAGE;
                break;
            case API_AUTH_FORBIDDEN:
                errorMessage = API_AUTH_FORBIDDEN_MESSAGE;
                break;
            case SUBSCRIPTION_INACTIVE:
                errorMessage = SUBSCRIPTION_INACTIVE_MESSAGE;
                break;
            case INVALID_SCOPE:
                errorMessage = INVALID_SCOPE_MESSAGE;
                break;
            case MUTUAL_SSL_VALIDATION_FAILURE:
                errorMessage = MUTUAL_SSL_VALIDATION_FAILURE_MESSAGE;
                break;
            case MULTI_AUTHENTICATION_FAILURE:
                errorMessage = MULTI_AUTHENTICATION_FAILURE_MESSAGE;
                break;
            case MULTI_AUTHENTICATION_FAILURE_AND_MISSING_OAUTH_CREDENTIALS:
                errorMessage = MULTI_AUTHENTICATION_FAILURE_MESSAGE;
                break;
            case MULTI_AUTHENTICATION_FAILURE_AND_MISSING_BASIC_AUTH_CREDENTIALS:
                errorMessage = MULTI_AUTHENTICATION_FAILURE_MESSAGE;
                break;
            case MULTI_AUTHENTICATION_FAILURE_AND_MISSING_OAUTH_AND_BASIC_AUTH_CREDENTIALS:
                errorMessage = MULTI_AUTHENTICATION_FAILURE_MESSAGE;
                break;
            default:
                errorMessage = API_AUTH_GENERAL_ERROR_MESSAGE;
                break;
        }
        return errorMessage;
    }

    /**
     * This method is used to get an additional description for error message details.
     *
     * @param errorCode    The error code that is embedded in the exception
     * @param errorMessage The default error message of the exception
     * @return The error description including the original error message and some additional information
     */
    public static String getFailureMessageDetailDescription(int errorCode, String errorMessage){
        String errorDescription = errorMessage;
        switch (errorCode){
            case API_AUTH_INCORRECT_API_RESOURCE:
                errorDescription += DESCRIPTION_SEPARATOR + API_AUTH_INCORRECT_API_RESOURCE_DESCRIPTION;
                break;
            case API_AUTH_ACCESS_TOKEN_INACTIVE:
                errorDescription += DESCRIPTION_SEPARATOR + API_AUTH_ACCESS_TOKEN_INACTIVE_DESCRIPTION;
                break;
            case API_AUTH_MISSING_CREDENTIALS:
                errorDescription += DESCRIPTION_SEPARATOR + API_AUTH_MISSING_CREDENTIALS_DESCRIPTION;
                break;
            case API_AUTH_MISSING_BASIC_AUTH_CREDENTIALS:
                errorDescription += DESCRIPTION_SEPARATOR + API_AUTH_MISSING_CREDENTIALS_DESCRIPTION;
                break;
            case MULTI_AUTHENTICATION_FAILURE_AND_MISSING_BASIC_AUTH_CREDENTIALS:
                errorDescription += DESCRIPTION_SEPARATOR + API_AUTH_MISSING_CREDENTIALS_DESCRIPTION;
                break;
            case MULTI_AUTHENTICATION_FAILURE_AND_MISSING_OAUTH_CREDENTIALS:
                errorDescription += DESCRIPTION_SEPARATOR + API_AUTH_MISSING_CREDENTIALS_DESCRIPTION;
                break;
            case MULTI_AUTHENTICATION_FAILURE_AND_MISSING_OAUTH_AND_BASIC_AUTH_CREDENTIALS:
                errorDescription += DESCRIPTION_SEPARATOR + API_AUTH_MISSING_CREDENTIALS_DESCRIPTION;
                break;
            case API_AUTH_ACCESS_TOKEN_EXPIRED:
                errorDescription += DESCRIPTION_SEPARATOR + API_AUTH_ACCESS_TOKEN_EXPIRED_DESCRIPTION;
                break;
            case API_AUTH_INVALID_CREDENTIALS:
                errorDescription += DESCRIPTION_SEPARATOR + API_AUTH_INVALID_CREDENTIALS_DESCRIPTION;
                break;
            case API_AUTH_INVALID_BASIC_AUTH_CREDENTIALS:
                errorDescription += DESCRIPTION_SEPARATOR + API_AUTH_INVALID_CREDENTIALS_DESCRIPTION;
                break;
            default:
                // Do nothing since we are anyhow returning the original error description.
        }
        return errorDescription;
    }

    public static final String API_SECURITY_NS = "http://wso2.org/apimanager/security";
    public static final String API_SECURITY_NS_PREFIX = "ams";
    
    public static final int DEFAULT_MAX_VALID_KEYS = 250;
    public static final int DEFAULT_MAX_INVALID_KEYS = 100;
}
