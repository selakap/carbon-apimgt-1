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

package org.wso2.carbon.apimgt.impl;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMException;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axis2.util.JavaUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.model.APIPublisher;
import org.wso2.carbon.apimgt.api.model.APIStore;
import org.wso2.carbon.apimgt.impl.dto.Environment;
import org.wso2.carbon.apimgt.impl.dto.ThrottleProperties;
import org.wso2.carbon.apimgt.impl.dto.WorkflowProperties;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.securevault.SecretResolver;
import org.wso2.securevault.SecretResolverFactory;
import org.wso2.securevault.commons.MiscellaneousUtil;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global API Manager configuration. This is generally populated from a special XML descriptor
 * file at system startup. Once successfully populated, this class does not allow more parameters
 * to be added to the configuration. The design of this class has been greatly inspired by
 * the ServerConfiguration class in Carbon core. This class uses a similar '.' separated
 * approach to keep track of XML parameters.
 */
public class APIManagerConfiguration {

    private Map<String, List<String>> configuration = new ConcurrentHashMap<String, List<String>>();

    private static Log log = LogFactory.getLog(APIManagerConfiguration.class);

    private static final String USERID_LOGIN = "UserIdLogin";
    private static final String EMAIL_LOGIN = "EmailLogin";
    private static final String PRIMARY_LOGIN = "primary";
    private static final String CLAIM_URI = "ClaimUri";
    public static final  String RECEIVER_URL_PORT = "receiver.url.port";
    public static final String  AUTH_URL_PORT = "auth.url.port";
    public static final String  JMS_PORT = "jms.port";
    public static final String CARBON_CONFIG_PORT_OFFSET_NODE = "Ports.Offset";
    public static final String WEBSOCKET_DEFAULT_GATEWAY_URL = "ws://localhost:9099";
    private Map<String, Map<String, String>> loginConfiguration = new ConcurrentHashMap<String, Map<String, String>>();
    private JSONArray applicationAttributes = new JSONArray();

    private SecretResolver secretResolver;

    private boolean initialized;
    private ThrottleProperties throttleProperties = new ThrottleProperties();
    private WorkflowProperties workflowProperties = new WorkflowProperties();
    private Map<String, Environment> apiGatewayEnvironments = new LinkedHashMap<String, Environment>();
    private static Properties realtimeNotifierProperties;
    private static Properties persistentNotifierProperties;
    private static String tokenRevocationClassName;

    public static Properties getRealtimeTokenRevocationNotifierProperties() {
        return realtimeNotifierProperties;
    }

    public static Properties getPersistentTokenRevocationNotifiersProperties() {
        return persistentNotifierProperties;
    }

    public static String getTokenRevocationClassName() {
        return tokenRevocationClassName;
    }

    public static boolean isTokenRevocationEnabled() {
        return !tokenRevocationClassName.isEmpty();
    }

    private Set<APIStore> externalAPIStores = new HashSet<APIStore>();

    public Map<String, Map<String, String>> getLoginConfiguration() {
        return loginConfiguration;
    }

    /**
     * Populate this configuration by reading an XML file at the given location. This method
     * can be executed only once on a given APIManagerConfiguration instance. Once invoked and
     * successfully populated, it will ignore all subsequent invocations.
     *
     * @param filePath Path of the XML descriptor file
     * @throws APIManagementException If an error occurs while reading the XML descriptor
     */
    public void load(String filePath) throws APIManagementException {
        if (initialized) {
            return;
        }
        InputStream in = null;
        int offset = APIUtil.getPortOffset();
        int receiverPort = 9611 + offset;
        int authUrlPort = 9711 + offset;
        int jmsPort = 5672 + offset;
        System.setProperty(RECEIVER_URL_PORT, "" + receiverPort);
        System.setProperty(AUTH_URL_PORT, "" + authUrlPort);
        System.setProperty(JMS_PORT, "" + jmsPort);
        try {
            in = FileUtils.openInputStream(new File(filePath));
            StAXOMBuilder builder = new StAXOMBuilder(in);
            secretResolver = SecretResolverFactory.create(builder.getDocumentElement(), true);
            readChildElements(builder.getDocumentElement(), new Stack<String>());
            initialized = true;
            addKeyManagerConfigsAsSystemProperties();
            String url = getFirstProperty(APIConstants.API_KEY_VALIDATOR_URL);
            if (url == null) {
                log.error("API_KEY_VALIDATOR_URL is null");
            }
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new APIManagementException("I/O error while reading the API manager " +
                                             "configuration: " + filePath, e);
        } catch (XMLStreamException e) {
            log.error(e.getMessage());
            throw new APIManagementException("Error while parsing the API manager " +
                                             "configuration: " + filePath, e);
        } catch (OMException e) {
            log.error(e.getMessage());
            throw new APIManagementException("Error while parsing API Manager configuration: " + filePath, e);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new APIManagementException("Unexpected error occurred while parsing configuration: " + filePath, e);
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    public Set<String> getConfigKeySet() {
        if (configuration != null) {
            return configuration.keySet();
        }
        return null;
    }

    public String getFirstProperty(String key) {
        List<String> value = configuration.get(key);
        if (value == null) {
            return null;
        }
        return value.get(0);
    }

    public List<String> getProperty(String key) {
        return configuration.get(key);
    }

    public void reloadSystemProperties() {
        for (Map.Entry<String, List<String>> entry : configuration.entrySet()) {
            List<String> list = entry.getValue();
            for (int i = 0; i < list.size(); i++) {
                String text = list.remove(i);
                list.add(i, APIUtil.replaceSystemProperty(text));
            }
        }
    }

    private void readChildElements(OMElement serverConfig,
                                   Stack<String> nameStack) throws APIManagementException{
        for (Iterator childElements = serverConfig.getChildElements(); childElements
                .hasNext(); ) {
            OMElement element = (OMElement) childElements.next();
            String localName = element.getLocalName();
            nameStack.push(localName);
            if ("TokenRevocationNotifiers".equals(localName)) {
                tokenRevocationClassName = element.getAttributeValue(new QName("class"));
            } else if ("RealtimeNotifier".equals(localName)) {
                Iterator revocationPropertiesIterator = element.getChildrenWithLocalName("Property");
                Properties properties = new Properties();
                while (revocationPropertiesIterator.hasNext()) {
                    OMElement propertyElem = (OMElement) revocationPropertiesIterator.next();
                    properties.setProperty(propertyElem.getAttributeValue(new QName("name")),
                            propertyElem.getText());
                }
                realtimeNotifierProperties = properties;
            } else if ("PersistentNotifier".equals(localName)) {
                Iterator revocationPropertiesIterator = element.getChildrenWithLocalName("Property");
                Properties properties = new Properties();
                while (revocationPropertiesIterator.hasNext()) {
                    OMElement propertyElem = (OMElement) revocationPropertiesIterator.next();
                    if (propertyElem.getAttributeValue(new QName("name")).
                            equalsIgnoreCase("password")) {
                        if (secretResolver.isInitialized() && secretResolver
                                .isTokenProtected("TokenRevocationNotifiers.Notifier.Password")) {
                            properties.setProperty(propertyElem.getAttributeValue(new QName("name")),
                                    secretResolver.
                                            resolve("TokenRevocationNotifiers.Notifier.Password"));
                        } else {
                            properties.setProperty(propertyElem.getAttributeValue(new QName("name")),
                                    propertyElem.getText());
                        }
                    } else {
                        properties
                                .setProperty(propertyElem.getAttributeValue(new QName("name")),
                                        propertyElem.getText());
                    }
                }
                persistentNotifierProperties = properties;
            } else if (elementHasText(element)) {
                String key = getKey(nameStack);
                String value = MiscellaneousUtil.resolve(element, secretResolver);
                addToConfiguration(key, APIUtil.replaceSystemProperty(value));
            } else if ("Environments".equals(localName)) {
                Iterator environmentIterator = element.getChildrenWithLocalName("Environment");
                apiGatewayEnvironments = new LinkedHashMap<String, Environment>();

                while (environmentIterator.hasNext()) {
                    Environment environment = new Environment();
                    OMElement environmentElem = (OMElement) environmentIterator.next();
                    environment.setType(environmentElem.getAttributeValue(new QName("type")));
                    String showInConsole = environmentElem.getAttributeValue(new QName("api-console"));
                    if (showInConsole != null) {
                        environment.setShowInConsole(Boolean.parseBoolean(showInConsole));
                    } else {
                        environment.setShowInConsole(true);
                    }
                    String isDefault = environmentElem.getAttributeValue(new QName("isDefault"));
                    if (isDefault != null) {
                        environment.setDefault(Boolean.parseBoolean(isDefault));
                    } else {
                        environment.setDefault(false);
                    }
                    environment.setName(APIUtil.replaceSystemProperty(
                            environmentElem.getFirstChildWithName(new QName("Name")).getText()));
                    environment.setServerURL(APIUtil.replaceSystemProperty(
                            environmentElem.getFirstChildWithName(new QName(
                                    APIConstants.API_GATEWAY_SERVER_URL)).getText()));
                    environment.setUserName(APIUtil.replaceSystemProperty(

                            environmentElem.getFirstChildWithName(new QName(
                                    APIConstants.API_GATEWAY_USERNAME)).getText()));
                    OMElement passwordElement = environmentElem.getFirstChildWithName(new QName(
                            APIConstants.API_GATEWAY_PASSWORD));
                    String value = MiscellaneousUtil.resolve(passwordElement, secretResolver);
                    environment.setPassword(APIUtil.replaceSystemProperty(value));
                    environment.setApiGatewayEndpoint(APIUtil.replaceSystemProperty(
                            environmentElem.getFirstChildWithName(new QName(
                                    APIConstants.API_GATEWAY_ENDPOINT)).getText()));
                    OMElement websocketGatewayEndpoint = environmentElem
                            .getFirstChildWithName(new QName(APIConstants.API_WEBSOCKET_GATEWAY_ENDPOINT));
                    if (websocketGatewayEndpoint != null) {
                        environment.setWebsocketGatewayEndpoint(
                                APIUtil.replaceSystemProperty(websocketGatewayEndpoint.getText()));
                    } else {
                        environment.setWebsocketGatewayEndpoint(WEBSOCKET_DEFAULT_GATEWAY_URL);
                    }
                    OMElement description =
                            environmentElem.getFirstChildWithName(new QName("Description"));
                    if (description != null) {
                        environment.setDescription(description.getText());
                    } else {
                        environment.setDescription("");
                    }
                    if (!apiGatewayEnvironments.containsKey(environment.getName())) {
                        apiGatewayEnvironments.put(environment.getName(), environment);
                    } else {
                        /*
                          This will be happen only on server startup therefore we log and continue the startup
                         */
                        log.error("Duplicate environment name found in api-manager.xml " +
                                  environment.getName());
                    }
                }
            } else if (APIConstants.EXTERNAL_API_STORES.equals(localName)) {  //Initialize 'externalAPIStores' config elements
                Iterator apistoreIterator = element.getChildrenWithLocalName("ExternalAPIStore");
                externalAPIStores = new HashSet<APIStore>();
                while (apistoreIterator.hasNext()) {
                    APIStore store = new APIStore();
                    OMElement storeElem = (OMElement) apistoreIterator.next();
                    String type = storeElem.getAttributeValue(new QName(APIConstants.EXTERNAL_API_STORE_TYPE));
                    store.setType(type); //Set Store type [eg:wso2]
                    String className = storeElem.getAttributeValue(new QName(APIConstants
                            .EXTERNAL_API_STORE_CLASS_NAME));
                    try {
                        store.setPublisher((APIPublisher) APIUtil.getClassForName(className).newInstance());
                    } catch (InstantiationException e) {
                        String msg = "One or more classes defined in" + APIConstants.EXTERNAL_API_STORE_CLASS_NAME +
                                "cannot be instantiated";
                        log.error(msg, e);
                        throw new APIManagementException(msg, e);
                    } catch (IllegalAccessException e) {
                        String msg = "One or more classes defined in" + APIConstants.EXTERNAL_API_STORE_CLASS_NAME +
                                "cannot be access";
                        log.error(msg, e);
                        throw new APIManagementException(msg, e);
                    } catch (ClassNotFoundException e) {
                        String msg = "One or more classes defined in" + APIConstants.EXTERNAL_API_STORE_CLASS_NAME +
                                "cannot be found";
                        log.error(msg, e);
                        throw new APIManagementException(msg, e);
                    }
                    String name = storeElem.getAttributeValue(new QName(APIConstants.EXTERNAL_API_STORE_ID));
                    if (name == null) {
                        log.error("The ExternalAPIStore name attribute is not defined in api-manager.xml.");
                    }
                    store.setName(name); //Set store name
                    OMElement configDisplayName = storeElem.getFirstChildWithName(new QName(APIConstants.EXTERNAL_API_STORE_DISPLAY_NAME));
                    String displayName = (configDisplayName != null) ? APIUtil.replaceSystemProperty(
                            configDisplayName.getText()) : name;
                    store.setDisplayName(displayName);//Set store display name
                    store.setEndpoint(APIUtil.replaceSystemProperty(
                            storeElem.getFirstChildWithName(new QName(
                                    APIConstants.EXTERNAL_API_STORE_ENDPOINT)).getText())); //Set store endpoint,which is used to publish APIs
                    store.setPublished(false);
                    if (APIConstants.WSO2_API_STORE_TYPE.equals(type)) {
                        OMElement password = storeElem.getFirstChildWithName(new QName(
                                APIConstants.EXTERNAL_API_STORE_PASSWORD));
                        if (password != null) {
                            String value = MiscellaneousUtil.resolve(password, secretResolver);
                            store.setPassword(APIUtil.replaceSystemProperty(value));
                            store.setUsername(APIUtil.replaceSystemProperty(
                                    storeElem.getFirstChildWithName(new QName(
                                            APIConstants.EXTERNAL_API_STORE_USERNAME)).getText())); //Set store login username [optional]
                        } else {
                            log.error("The user-credentials of API Publisher is not defined in the <ExternalAPIStore> config of api-manager.xml.");
                        }
                    }
                    externalAPIStores.add(store);
                }
            } else if (APIConstants.LOGIN_CONFIGS.equals(localName)) {
                Iterator loginConfigIterator = element.getChildrenWithLocalName(APIConstants.LOGIN_CONFIGS);
                while (loginConfigIterator.hasNext()) {
                    OMElement loginOMElement = (OMElement) loginConfigIterator.next();
                    parseLoginConfig(loginOMElement);
                }

            } else if (APIConstants.AdvancedThrottleConstants.THROTTLING_CONFIGURATIONS.equals(localName)) {
                setThrottleProperties(serverConfig);
            } else if (APIConstants.WorkflowConfigConstants.WORKFLOW.equals(localName)) {
                setWorkflowProperties(serverConfig);
            } else if (APIConstants.ApplicationAttributes.APPLICATION_ATTRIBUTES.equals(localName)) {
                Iterator iterator = element.getChildrenWithLocalName(APIConstants.ApplicationAttributes.ATTRIBUTE);
                while (iterator.hasNext()) {
                    OMElement omElement = (OMElement) iterator.next();
                    Iterator attributes = omElement.getChildElements();
                    JSONObject jsonObject = new JSONObject();
                    boolean isHidden = Boolean.parseBoolean(omElement.getAttributeValue(new QName(APIConstants.ApplicationAttributes.HIDDEN)));
                    boolean isRequired =
                            Boolean.parseBoolean(omElement.getAttributeValue(new QName(APIConstants.ApplicationAttributes.REQUIRED)));
                    jsonObject.put(APIConstants.ApplicationAttributes.HIDDEN, isHidden);
                    while (attributes.hasNext()) {
                        OMElement attribute = (OMElement) attributes.next();
                        if (attribute.getLocalName().equals(APIConstants.ApplicationAttributes.NAME)) {
                            jsonObject.put(APIConstants.ApplicationAttributes.ATTRIBUTE, attribute.getText());
                        } else if (attribute.getLocalName().equals(APIConstants.ApplicationAttributes.DESCRIPTION)) {
                            jsonObject.put(APIConstants.ApplicationAttributes.DESCRIPTION, attribute.getText());
                        } else if (attribute.getLocalName().equals(APIConstants.ApplicationAttributes.DEFAULT) && isRequired) {
                            jsonObject.put(APIConstants.ApplicationAttributes.DEFAULT, attribute.getText());
                        }
                    }
                    if (isHidden && isRequired && !jsonObject.containsKey(APIConstants.ApplicationAttributes.DEFAULT)) {
                        log.error("A default value needs to be given for required, hidden application attributes.");
                    }
                    jsonObject.put(APIConstants.ApplicationAttributes.REQUIRED, isRequired);
                    applicationAttributes.add(jsonObject);
                }
            }
            readChildElements(element, nameStack);
            nameStack.pop();
        }
    }

    public JSONArray getApplicationAttributes() {
        return applicationAttributes;
    }

    /**
     * Read the primary/secondary login configuration
     * <LoginConfig>
     * <UserIdLogin  primary="true">
     * <ClaimUri></ClaimUri>
     * </UserIdLogin>
     * <EmailLogin  primary="false">
     * <ClaimUri>http://wso2.org/claims/emailaddress</ClaimUri>
     * </EmailLogin>           loginOMElement
     * </LoginConfig>
     *
     * @param loginConfigElem
     */
    private void parseLoginConfig(OMElement loginConfigElem) {
        if (loginConfigElem != null) {
            if (log.isDebugEnabled()) {
                log.debug("Login configuration is set ");
            }
            // Primary/Secondary supported login mechanisms
            OMElement emailConfigElem = loginConfigElem.getFirstChildWithName(new QName(EMAIL_LOGIN));

            OMElement userIdConfigElem = loginConfigElem.getFirstChildWithName(new QName(USERID_LOGIN));

            Map<String, String> emailConf = new HashMap<String, String>(2);
            emailConf.put(PRIMARY_LOGIN, emailConfigElem.getAttributeValue(new QName(PRIMARY_LOGIN)));
            emailConf.put(CLAIM_URI, emailConfigElem.getFirstChildWithName(new QName(CLAIM_URI)).getText());

            Map<String, String> userIdConf = new HashMap<String, String>(2);
            userIdConf.put(PRIMARY_LOGIN, userIdConfigElem.getAttributeValue(new QName(PRIMARY_LOGIN)));
            userIdConf.put(CLAIM_URI, userIdConfigElem.getFirstChildWithName(new QName(CLAIM_URI)).getText());

            loginConfiguration.put(EMAIL_LOGIN, emailConf);
            loginConfiguration.put(USERID_LOGIN, userIdConf);
        }
    }

    private String getKey(Stack<String> nameStack) {
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < nameStack.size(); i++) {
            String name = nameStack.elementAt(i);
            key.append(name).append('.');
        }
        key.deleteCharAt(key.lastIndexOf("."));

        return key.toString();
    }
    private boolean elementHasText(OMElement element) {
        String text = element.getText();
        return text != null && text.trim().length() != 0;
    }

    private void addToConfiguration(String key, String value) {
        List<String> list = configuration.get(key);
        if (list == null) {
            list = new ArrayList<String>();
            list.add(value);
            configuration.put(key, list);
        } else {
            list.add(value);
        }
    }
    
    public Map<String, Environment> getApiGatewayEnvironments() {
        return apiGatewayEnvironments;
    }

    public Set<APIStore> getExternalAPIStores() {  //Return set of APIStores
        return externalAPIStores;
    }

    public APIStore getExternalAPIStore(
            String storeName) { //Return APIStore object,based on store name/Here we assume store name is unique.
        for (APIStore apiStore : externalAPIStores) {
            if (apiStore.getName().equals(storeName)) {
                return apiStore;
            }
        }
        return null;
    }

    /**
     * set the hostname and the port as System properties.
     * return void
     */
    private void addKeyManagerConfigsAsSystemProperties() {
        URL keyManagerURL;
        try {
            keyManagerURL = new URL(configuration.get(APIConstants.KEYMANAGER_SERVERURL).get(0));
            String hostname = keyManagerURL.getHost();
            
            int port = keyManagerURL.getPort();
            if (port == -1) {
                if (APIConstants.HTTPS_PROTOCOL.equals(keyManagerURL.getProtocol())) {
                    port = APIConstants.HTTPS_PROTOCOL_PORT;
                } else {
                    port = APIConstants.HTTP_PROTOCOL_PORT;
                }
            }           
            System.setProperty(APIConstants.KEYMANAGER_PORT, String.valueOf(port));
            
            if (hostname.equals(System.getProperty(APIConstants.CARBON_LOCALIP))) {
                System.setProperty(APIConstants.KEYMANAGER_HOSTNAME, "localhost");
            } else {
                System.setProperty(APIConstants.KEYMANAGER_HOSTNAME, hostname);
            }
            //Since this is the server startup.Ignore the exceptions,invoked at the server startup
        } catch (MalformedURLException e) {
            log.error("Exception While resolving KeyManager Server URL or Port " + e.getMessage(), e);
        }
    }

    /**
     * set workflow related configurations
     * @param element
     */
    private void setWorkflowProperties(OMElement element) {
        OMElement workflowConfigurationElement = element
                .getFirstChildWithName(new QName(APIConstants.WorkflowConfigConstants.WORKFLOW));
        if (workflowConfigurationElement != null) {
            OMElement enableWorkflowElement = workflowConfigurationElement
                    .getFirstChildWithName(new QName(APIConstants.WorkflowConfigConstants.WORKFLOW_ENABLED));
            if (enableWorkflowElement != null) {
                workflowProperties.setEnabled(JavaUtils.isTrueExplicitly(enableWorkflowElement.getText()));
            }
            OMElement workflowServerUrlElement = workflowConfigurationElement
                    .getFirstChildWithName(new QName(APIConstants.WorkflowConfigConstants.WORKFLOW_SERVER_URL));
            if (workflowServerUrlElement != null) {
                workflowProperties.setServerUrl(APIUtil.replaceSystemProperty(workflowServerUrlElement.getText()));
            }
            OMElement workflowServerUserElement = workflowConfigurationElement
                    .getFirstChildWithName(new QName(APIConstants.WorkflowConfigConstants.WORKFLOW_SERVER_USER));
            if (workflowServerUserElement != null) {
                workflowProperties.setServerUser(APIUtil.replaceSystemProperty(workflowServerUserElement.getText()));
            }
            OMElement workflowDCRUserElement = workflowConfigurationElement
                    .getFirstChildWithName(new QName(APIConstants.WorkflowConfigConstants.WORKFLOW_DCR_EP_USER));
            if (workflowDCRUserElement != null) {
                workflowProperties.setdCREndpointUser(APIUtil.replaceSystemProperty(workflowDCRUserElement.getText()));
            }
            OMElement workflowCallbackElement = workflowConfigurationElement
                    .getFirstChildWithName(new QName(APIConstants.WorkflowConfigConstants.WORKFLOW_CALLBACK));
            if (workflowCallbackElement != null) {
                workflowProperties
                        .setWorkflowCallbackAPI(APIUtil.replaceSystemProperty(workflowCallbackElement.getText()));
            }

            OMElement workflowDCREPElement = workflowConfigurationElement
                    .getFirstChildWithName(new QName(APIConstants.WorkflowConfigConstants.WORKFLOW_DCR_EP));
            if (workflowDCREPElement != null) {
                workflowProperties.setdCREndPoint(APIUtil.replaceSystemProperty(workflowDCREPElement.getText()));
            }

            OMElement workflowTokenEpElement = workflowConfigurationElement
                    .getFirstChildWithName(new QName(APIConstants.WorkflowConfigConstants.WORKFLOW_TOKEN_EP));
            if (workflowTokenEpElement != null) {
                workflowProperties.setTokenEndPoint(APIUtil.replaceSystemProperty(workflowTokenEpElement.getText()));
            }
            OMElement workflowServerPasswordOmElement = workflowConfigurationElement
                    .getFirstChildWithName(new QName(APIConstants.WorkflowConfigConstants.WORKFLOW_SERVER_PASSWORD));
            String workflowServerPassword = MiscellaneousUtil.resolve(workflowServerPasswordOmElement, secretResolver);
            workflowServerPassword = APIUtil.replaceSystemProperty(workflowServerPassword);
            workflowProperties.setServerPassword(workflowServerPassword);

            OMElement dcrEPPasswordOmElement = workflowConfigurationElement
                    .getFirstChildWithName(new QName(APIConstants.WorkflowConfigConstants.WORKFLOW_DCR_EP_PASSWORD));
            String dcrEPPassword = MiscellaneousUtil.resolve(dcrEPPasswordOmElement, secretResolver);
            dcrEPPassword = APIUtil.replaceSystemProperty(dcrEPPassword);
            workflowProperties.setdCREndpointPassword(dcrEPPassword);

        }
    }
    /**
     * set the Advance Throttle Properties into Configuration
     *
     * @param element
     */
    private void setThrottleProperties(OMElement element) {
        OMElement throttleConfigurationElement = element.getFirstChildWithName(new QName(APIConstants
                .AdvancedThrottleConstants.THROTTLING_CONFIGURATIONS));
        if (throttleConfigurationElement != null) {
            // Check advance throttling enabled
            OMElement enableAdvanceThrottlingElement = throttleConfigurationElement
                    .getFirstChildWithName(new QName(APIConstants.AdvancedThrottleConstants
                            .ENABLE_ADVANCE_THROTTLING));
            if (enableAdvanceThrottlingElement != null) {
                throttleProperties.setEnabled(JavaUtils.isTrueExplicitly(enableAdvanceThrottlingElement
                        .getText()));
            }
            // Check unlimited tier enabled
            OMElement enableUnlimitedTierElement = throttleConfigurationElement
                    .getFirstChildWithName(new QName(APIConstants.AdvancedThrottleConstants
                            .ENABLE_UNLIMITED_TIER));
            if (enableUnlimitedTierElement != null) {
                throttleProperties.setEnableUnlimitedTier(JavaUtils.isTrueExplicitly(enableUnlimitedTierElement
                        .getText()));
            }
            // Check header condition enable
            OMElement enableHeaderConditionsElement = throttleConfigurationElement
                    .getFirstChildWithName(new QName(APIConstants.AdvancedThrottleConstants
                            .ENABLE_HEADER_CONDITIONS));
            if (enableHeaderConditionsElement != null) {
                throttleProperties.setEnableHeaderConditions(JavaUtils.isTrueExplicitly(enableHeaderConditionsElement
                        .getText()));
            }
            // Check JWT condition enable
            OMElement enableJwtElement = throttleConfigurationElement
                    .getFirstChildWithName(new QName(APIConstants.AdvancedThrottleConstants
                            .ENABLE_JWT_CLAIM_CONDITIONS));
            if (enableJwtElement != null) {
                throttleProperties.setEnableJwtConditions(JavaUtils.isTrueExplicitly(enableJwtElement
                        .getText()));
            }
            // Check query param condition enable
            OMElement enableQueryParamElement = throttleConfigurationElement
                    .getFirstChildWithName(new QName(APIConstants.AdvancedThrottleConstants
                            .ENABLE_QUERY_PARAM_CONDITIONS));
            if (enableQueryParamElement != null) {
                throttleProperties.setEnableQueryParamConditions(JavaUtils.isTrueExplicitly(enableQueryParamElement
                        .getText()));
            }
            // Check subscription spike arrest enable
            OMElement enabledSubscriptionLevelSpikeArrestElement = throttleConfigurationElement
                    .getFirstChildWithName(new QName(APIConstants.AdvancedThrottleConstants
                            .ENABLE_SUBSCRIPTION_SPIKE_ARREST));
            if (enabledSubscriptionLevelSpikeArrestElement != null) {
                throttleProperties.setEnabledSubscriptionLevelSpikeArrest(JavaUtils.isTrueExplicitly
                        (enabledSubscriptionLevelSpikeArrestElement
                                .getText()));
            }
            // if advance Throttling enable
            if (throttleProperties.isEnabled()) {
                // Reading TrafficManager configuration
                OMElement trafficManagerConfigurationElement = throttleConfigurationElement.getFirstChildWithName(new
                        QName(APIConstants.AdvancedThrottleConstants.TRAFFIC_MANAGER));
                ThrottleProperties.TrafficManager trafficManager = new ThrottleProperties.TrafficManager();
                if (trafficManagerConfigurationElement != null) {
                    OMElement receiverUrlGroupElement = trafficManagerConfigurationElement.getFirstChildWithName(new
                            QName
                            (APIConstants.AdvancedThrottleConstants.DATA_PUBLISHER_CONFIGURAION_REVEIVER_URL_GROUP));
                    if (receiverUrlGroupElement != null) {
                        trafficManager.setReceiverUrlGroup(APIUtil.replaceSystemProperty(receiverUrlGroupElement
                                .getText()));
                    }
                    OMElement authUrlGroupElement = trafficManagerConfigurationElement.getFirstChildWithName(new QName
                            (APIConstants.AdvancedThrottleConstants.DATA_PUBLISHER_CONFIGURAION_AUTH_URL_GROUP));
                    if (authUrlGroupElement != null) {
                        trafficManager.setAuthUrlGroup(APIUtil.replaceSystemProperty(authUrlGroupElement.getText()));
                    }
                    OMElement dataPublisherUsernameElement = trafficManagerConfigurationElement.getFirstChildWithName
                            (new QName(APIConstants.AdvancedThrottleConstants.USERNAME));
                    if (dataPublisherUsernameElement != null) {
                        trafficManager.setUsername(APIUtil.replaceSystemProperty(dataPublisherUsernameElement.getText
                                ()));
                    }
                    OMElement dataPublisherTypeElement = trafficManagerConfigurationElement.getFirstChildWithName(new
                            QName
                            (APIConstants.AdvancedThrottleConstants.DATA_PUBLISHER_CONFIGURAION_TYPE));
                    if (dataPublisherTypeElement != null) {
                        trafficManager.setType(dataPublisherTypeElement.getText());
                    }
                    String dataPublisherConfigurationPassword;
                    OMElement dataPublisherConfigurationPasswordOmElement = trafficManagerConfigurationElement
                            .getFirstChildWithName(new QName(APIConstants.AdvancedThrottleConstants.PASSWORD));
                    dataPublisherConfigurationPassword = MiscellaneousUtil.
                            resolve(dataPublisherConfigurationPasswordOmElement, secretResolver);
                    trafficManager.setPassword(APIUtil.replaceSystemProperty(dataPublisherConfigurationPassword));
                    throttleProperties.setTrafficManager(trafficManager);
                }
                // Configuring throttle data publisher
                ThrottleProperties.DataPublisher dataPublisher = new ThrottleProperties.DataPublisher();
                OMElement dataPublisherConfigurationElement = throttleConfigurationElement.getFirstChildWithName(new
                        QName(APIConstants.AdvancedThrottleConstants.DATA_PUBLISHER_CONFIGURATION));
                if (dataPublisherConfigurationElement != null) {
                    OMElement dataPublisherEnabledElement = dataPublisherConfigurationElement
                            .getFirstChildWithName(new QName(APIConstants.AdvancedThrottleConstants.ENABLED));
                    dataPublisher.setEnabled(JavaUtils.isTrueExplicitly(dataPublisherEnabledElement.getText()));
                    dataPublisher.setAuthUrlGroup(trafficManager.getAuthUrlGroup());
                    dataPublisher.setReceiverUrlGroup(trafficManager.getReceiverUrlGroup());
                    dataPublisher.setUsername(trafficManager.getUsername());
                    dataPublisher.setPassword(trafficManager.getPassword());
                    dataPublisher.setType(trafficManager.getType());
                }
                if (dataPublisher.isEnabled()) {

                    throttleProperties.setDataPublisher(dataPublisher);

                    // Data publisher pool configuration

                    OMElement dataPublisherPoolConfigurationElement = dataPublisherConfigurationElement
                            .getFirstChildWithName(new
                                    QName
                                    (APIConstants.AdvancedThrottleConstants.DATA_PUBLISHER_POOL_CONFIGURATION));

                    ThrottleProperties.DataPublisherPool dataPublisherPool = new ThrottleProperties
                            .DataPublisherPool();
                    OMElement maxIdleElement = dataPublisherPoolConfigurationElement.getFirstChildWithName(new QName
                            (APIConstants.AdvancedThrottleConstants.DATA_PUBLISHER_POOL_CONFIGURAION_MAX_IDLE));
                    if (maxIdleElement != null) {
                        dataPublisherPool.setMaxIdle(Integer.parseInt(maxIdleElement.getText()));
                    }
                    OMElement initIdleElement = dataPublisherPoolConfigurationElement.getFirstChildWithName(new QName
                            (APIConstants.AdvancedThrottleConstants.DATA_PUBLISHER_POOL_CONFIGURAION_INIT_IDLE));
                    if (initIdleElement != null) {
                        dataPublisherPool.setInitIdleCapacity(Integer.parseInt(initIdleElement.getText()));
                    }
                    throttleProperties.setDataPublisherPool(dataPublisherPool);

                    // Data publisher thread pool configuration

                    OMElement dataPublisherThreadPoolConfigurationElement = dataPublisherConfigurationElement
                            .getFirstChildWithName(new
                                    QName
                                    (APIConstants.AdvancedThrottleConstants.DATA_PUBLISHER_THREAD_POOL_CONFIGURATION));

                    ThrottleProperties.DataPublisherThreadPool dataPublisherThreadPool = new ThrottleProperties
                            .DataPublisherThreadPool();
                    if (dataPublisherThreadPoolConfigurationElement != null) {
                        OMElement corePoolSizeElement = dataPublisherThreadPoolConfigurationElement
                                .getFirstChildWithName
                                        (new
                                                QName
                                                (APIConstants.AdvancedThrottleConstants
                                                        .DATA_PUBLISHER_THREAD_POOL_CONFIGURATION_CORE_POOL_SIZE));
                        if (corePoolSizeElement != null) {
                            dataPublisherThreadPool.setCorePoolSize(Integer.parseInt(corePoolSizeElement.getText()));
                        }
                        OMElement maximumPoolSizeElement = dataPublisherThreadPoolConfigurationElement
                                .getFirstChildWithName(new
                                        QName
                                        (APIConstants.AdvancedThrottleConstants
                                                .DATA_PUBLISHER_THREAD_POOL_CONFIGURATION_MAXMIMUM_POOL_SIZE));
                        if (maximumPoolSizeElement != null) {
                            dataPublisherThreadPool.setMaximumPoolSize(Integer.parseInt(maximumPoolSizeElement
                                    .getText()));
                        }
                        OMElement keepAliveTimeElement = dataPublisherThreadPoolConfigurationElement
                                .getFirstChildWithName
                                        (new
                                                QName
                                                (APIConstants.AdvancedThrottleConstants
                                                        .DATA_PUBLISHER_THREAD_POOL_CONFIGURATION_KEEP_ALIVE_TIME));
                        if (keepAliveTimeElement != null) {
                            dataPublisherThreadPool.setKeepAliveTime(Long.parseLong(keepAliveTimeElement.getText()));
                        }
                    }
                    throttleProperties.setDataPublisherThreadPool(dataPublisherThreadPool);
                }

                // Configuring JMSConnectionDetails
                ThrottleProperties.JMSConnectionProperties jmsConnectionProperties = new
                        ThrottleProperties
                                .JMSConnectionProperties();

                OMElement jmsConnectionDetailElement = throttleConfigurationElement.getFirstChildWithName(new
                        QName
                        (APIConstants.AdvancedThrottleConstants.JMS_CONNECTION_DETAILS));

                if (jmsConnectionDetailElement != null) {
                    OMElement jmsConnectionEnabledElement = jmsConnectionDetailElement
                            .getFirstChildWithName(new QName(APIConstants.AdvancedThrottleConstants.ENABLED));
                    jmsConnectionProperties.setEnabled(JavaUtils.isTrueExplicitly(jmsConnectionEnabledElement
                            .getText()));
                    OMElement jmsConnectionUrlElement = jmsConnectionDetailElement
                            .getFirstChildWithName(new QName
                                    (APIConstants.AdvancedThrottleConstants.SERVICE_URL));
                    if (jmsConnectionUrlElement != null) {
                        jmsConnectionProperties.setServiceUrl(APIUtil.replaceSystemProperty(jmsConnectionUrlElement
                                .getText()));
                        System.setProperty("jms.url", jmsConnectionProperties.getServiceUrl());
                    }
                    OMElement jmsConnectionUserElement = jmsConnectionDetailElement
                            .getFirstChildWithName(new QName
                                    (APIConstants.AdvancedThrottleConstants.USERNAME));
                    if (jmsConnectionUserElement != null) {
                        jmsConnectionProperties.setUsername(APIUtil.replaceSystemProperty(jmsConnectionUserElement
                                .getText()));
                        System.setProperty("jms.username", jmsConnectionProperties.getUsername());
                    }
                    OMElement jmsConnectionDestinationElement = jmsConnectionDetailElement
                            .getFirstChildWithName(new QName
                                    (APIConstants.AdvancedThrottleConstants.JMS_CONNECTION_DESTINATION));
                    if (jmsConnectionDestinationElement != null) {
                        jmsConnectionProperties.setDestination(jmsConnectionDestinationElement.getText());
                    }
                    OMElement jmsConnectionPasswordElement = jmsConnectionDetailElement.getFirstChildWithName(new
                            QName(APIConstants.AdvancedThrottleConstants.PASSWORD));
                    if (jmsConnectionPasswordElement != null) {
                        OMElement jmsConnectionPasswordOmElement = jmsConnectionDetailElement
                                .getFirstChildWithName(new QName(APIConstants.AdvancedThrottleConstants.PASSWORD));
                        String jmsConnectionPassword = MiscellaneousUtil.
                                resolve(jmsConnectionPasswordOmElement, secretResolver);
                        jmsConnectionProperties.setPassword(APIUtil.replaceSystemProperty(jmsConnectionPassword));
                        System.setProperty("jms.password", jmsConnectionProperties.getPassword());
                    }

                    OMElement jmsConnectionParameterElement = jmsConnectionDetailElement.getFirstChildWithName(new
                            QName(APIConstants.AdvancedThrottleConstants.JMS_CONNECTION_PARAMETERS));
                    if (jmsConnectionParameterElement != null) {
                        Iterator jmsProperties = jmsConnectionParameterElement.getChildElements();
                        Properties properties = new Properties();
                        while (jmsProperties.hasNext()) {
                            OMElement property = (OMElement) jmsProperties.next();
                            String value = MiscellaneousUtil.resolve(property, secretResolver);
                            properties.put(property.getLocalName(), APIUtil.replaceSystemProperty(value));
                        }
                        jmsConnectionProperties.setJmsConnectionProperties(properties);
                    }
                    // Configuring JMS Task Manager
                    ThrottleProperties.JMSConnectionProperties.JMSTaskManagerProperties jmsTaskManagerProperties =
                            new ThrottleProperties.JMSConnectionProperties.JMSTaskManagerProperties();
                    OMElement jmsTaskManagerElement = jmsConnectionDetailElement.getFirstChildWithName
                            (new QName(APIConstants.AdvancedThrottleConstants.JMS_TASK_MANAGER));
                    if (jmsTaskManagerElement != null) {
                        OMElement minThreadPoolSizeElement = jmsTaskManagerElement
                                .getFirstChildWithName(new QName
                                        (APIConstants.AdvancedThrottleConstants.MIN_THREAD_POOL_SIZE));
                        if (minThreadPoolSizeElement != null) {
                            jmsTaskManagerProperties.setMinThreadPoolSize(Integer.parseInt(minThreadPoolSizeElement
                                    .getText()));
                        }
                        OMElement maxThreadPoolSizeElement = jmsTaskManagerElement
                                .getFirstChildWithName(new QName
                                        (APIConstants.AdvancedThrottleConstants.MAX_THREAD_POOL_SIZE));
                        if (maxThreadPoolSizeElement != null) {
                            jmsTaskManagerProperties.setMaxThreadPoolSize(Integer.parseInt(maxThreadPoolSizeElement
                                    .getText()));
                        }
                        OMElement keepAliveTimeInMillisElement = jmsTaskManagerElement
                                .getFirstChildWithName(new QName
                                        (APIConstants.AdvancedThrottleConstants.KEEP_ALIVE_TIME_IN_MILLIS));
                        if (keepAliveTimeInMillisElement != null) {
                            jmsTaskManagerProperties.setKeepAliveTimeInMillis(Integer.parseInt
                                    (keepAliveTimeInMillisElement.getText()));
                        }
                        OMElement jobQueueSizeElement = jmsTaskManagerElement
                                .getFirstChildWithName(new QName
                                        (APIConstants.AdvancedThrottleConstants.JOB_QUEUE_SIZE));
                        if (keepAliveTimeInMillisElement != null) {
                            jmsTaskManagerProperties.setJobQueueSize(Integer.parseInt(jobQueueSizeElement.getText()));
                        }
                    }
                    jmsConnectionProperties.setJmsTaskManagerProperties(jmsTaskManagerProperties);
                    OMElement jmsConnectionInitialDelayElement = jmsConnectionDetailElement
                            .getFirstChildWithName(new QName(APIConstants.AdvancedThrottleConstants
                                    .BLOCK_CONDITION_RETRIEVER_INIT_DELAY));
                    if (jmsConnectionInitialDelayElement != null) {
                        jmsConnectionProperties.setInitialDelay(Long.parseLong
                                (jmsConnectionInitialDelayElement
                                        .getText()));
                    }
                }
                throttleProperties.setJmsConnectionProperties(jmsConnectionProperties);

                //Configuring default tier limits
                Map<String, Long> defaultThrottleTierLimits = new HashMap<String, Long>();
                OMElement defaultTierLimits = throttleConfigurationElement.getFirstChildWithName(new
                        QName
                        (APIConstants.AdvancedThrottleConstants.DEFAULT_THROTTLE_LIMITS));

                if (defaultTierLimits != null) {
                    OMElement subscriptionPolicyLimits = defaultTierLimits
                            .getFirstChildWithName(new QName(APIConstants.AdvancedThrottleConstants
                                    .SUBSCRIPTION_THROTTLE_LIMITS));

                    if (subscriptionPolicyLimits != null) {
                        OMElement goldTierElement = subscriptionPolicyLimits.getFirstChildWithName(new
                                QName(APIConstants.DEFAULT_SUB_POLICY_GOLD));
                        if (goldTierElement != null) {
                            defaultThrottleTierLimits.put(APIConstants.DEFAULT_SUB_POLICY_GOLD,
                                    Long.parseLong(goldTierElement.getText()));
                        }

                        OMElement silverTierElement = subscriptionPolicyLimits.getFirstChildWithName(new
                                QName(APIConstants.DEFAULT_SUB_POLICY_SILVER));
                        if (silverTierElement != null) {
                            defaultThrottleTierLimits.put(APIConstants.DEFAULT_SUB_POLICY_SILVER,
                                    Long.parseLong(silverTierElement.getText()));
                        }

                        OMElement bronzeTierElement = subscriptionPolicyLimits.getFirstChildWithName(new
                                QName(APIConstants.DEFAULT_SUB_POLICY_BRONZE));
                        if (bronzeTierElement != null) {
                            defaultThrottleTierLimits.put(APIConstants.DEFAULT_SUB_POLICY_BRONZE,
                                    Long.parseLong(bronzeTierElement.getText()));
                        }

                        OMElement unauthenticatedTierElement = subscriptionPolicyLimits.getFirstChildWithName(new
                                QName(APIConstants.DEFAULT_SUB_POLICY_UNAUTHENTICATED));
                        if (unauthenticatedTierElement != null) {
                            defaultThrottleTierLimits.put(APIConstants.DEFAULT_SUB_POLICY_UNAUTHENTICATED,
                                    Long.parseLong(unauthenticatedTierElement.getText()));
                        }
                    }

                    OMElement applicationPolicyLimits = defaultTierLimits
                            .getFirstChildWithName(new QName(APIConstants.AdvancedThrottleConstants
                                    .APPLICATION_THROTTLE_LIMITS));
                    if (subscriptionPolicyLimits != null) {
                        OMElement largeTierElement = applicationPolicyLimits.getFirstChildWithName(new
                                QName(APIConstants.DEFAULT_APP_POLICY_FIFTY_REQ_PER_MIN));
                        if (largeTierElement != null) {
                            defaultThrottleTierLimits.put(APIConstants.DEFAULT_APP_POLICY_FIFTY_REQ_PER_MIN,
                                    Long.parseLong(largeTierElement.getText()));
                        }

                        OMElement mediumTierElement = applicationPolicyLimits.getFirstChildWithName(new
                                QName(APIConstants.DEFAULT_APP_POLICY_TWENTY_REQ_PER_MIN));
                        if (mediumTierElement != null) {
                            defaultThrottleTierLimits.put(APIConstants.DEFAULT_APP_POLICY_TWENTY_REQ_PER_MIN,
                                    Long.parseLong(mediumTierElement.getText()));
                        }

                        OMElement smallTierElement = applicationPolicyLimits.getFirstChildWithName(new
                                QName(APIConstants.DEFAULT_APP_POLICY_TEN_REQ_PER_MIN));
                        if (smallTierElement != null) {
                            defaultThrottleTierLimits.put(APIConstants.DEFAULT_APP_POLICY_TEN_REQ_PER_MIN,
                                    Long.parseLong(smallTierElement.getText()));
                        }
                    }

                    OMElement resourceLevelPolicyLimits = defaultTierLimits
                            .getFirstChildWithName(new QName(APIConstants.AdvancedThrottleConstants
                                    .RESOURCE_THROTTLE_LIMITS));
                    if (resourceLevelPolicyLimits != null) {
                        OMElement ultimateTierElement = resourceLevelPolicyLimits.getFirstChildWithName(new
                                QName(APIConstants.DEFAULT_API_POLICY_FIFTY_THOUSAND_REQ_PER_MIN));
                        if (ultimateTierElement != null) {
                            defaultThrottleTierLimits.put(APIConstants.DEFAULT_API_POLICY_FIFTY_THOUSAND_REQ_PER_MIN,
                                    Long.parseLong(ultimateTierElement.getText()));
                        }

                        OMElement plusTierElement = resourceLevelPolicyLimits.getFirstChildWithName(new
                                QName(APIConstants.DEFAULT_API_POLICY_TWENTY_THOUSAND_REQ_PER_MIN));
                        if (plusTierElement != null) {
                            defaultThrottleTierLimits.put(APIConstants.DEFAULT_API_POLICY_TWENTY_THOUSAND_REQ_PER_MIN,
                                    Long.parseLong(plusTierElement.getText()));
                        }

                        OMElement basicTierElement = resourceLevelPolicyLimits.getFirstChildWithName(new
                                QName(APIConstants.DEFAULT_API_POLICY_TEN_THOUSAND_REQ_PER_MIN));
                        if (basicTierElement != null) {
                            defaultThrottleTierLimits.put(APIConstants.DEFAULT_API_POLICY_TEN_THOUSAND_REQ_PER_MIN,
                                    Long.parseLong(basicTierElement.getText()));
                        }
                    }

                }

                throttleProperties.setDefaultThrottleTierLimits(defaultThrottleTierLimits);

                //Configuring policy deployer
                OMElement policyDeployerConnectionElement = throttleConfigurationElement.getFirstChildWithName(new
                        QName(APIConstants.AdvancedThrottleConstants.POLICY_DEPLOYER_CONFIGURATION));

                ThrottleProperties.PolicyDeployer policyDeployerConfiguration = new
                        ThrottleProperties
                                .PolicyDeployer();
                if (policyDeployerConnectionElement != null) {
                    OMElement policyDeployerConnectionEnabledElement = policyDeployerConnectionElement
                            .getFirstChildWithName(new QName(APIConstants.AdvancedThrottleConstants.ENABLED));
                    policyDeployerConfiguration.setEnabled(JavaUtils.isTrueExplicitly
                            (policyDeployerConnectionEnabledElement.getText()));
                    OMElement policyDeployerServiceUrlElement = policyDeployerConnectionElement
                            .getFirstChildWithName(new QName
                                    (APIConstants.AdvancedThrottleConstants.SERVICE_URL));
                    if (policyDeployerServiceUrlElement != null) {
                        policyDeployerConfiguration.setServiceUrl(APIUtil.replaceSystemProperty
                                (policyDeployerServiceUrlElement.getText()));
                    }
                    OMElement policyDeployerServiceServiceUsernameElement = policyDeployerConnectionElement
                            .getFirstChildWithName(new QName
                                    (APIConstants.AdvancedThrottleConstants.USERNAME));
                    if (policyDeployerServiceServiceUsernameElement != null) {
                        policyDeployerConfiguration.setUsername(APIUtil.replaceSystemProperty
                                (policyDeployerServiceServiceUsernameElement.getText()));
                    }
                    OMElement policyDeployerServicePasswordElement = policyDeployerConnectionElement
                            .getFirstChildWithName(new QName(APIConstants.AdvancedThrottleConstants.PASSWORD));
                    String policyDeployerServicePassword = MiscellaneousUtil.
                            resolve(policyDeployerServicePasswordElement, secretResolver);
                    policyDeployerConfiguration.setPassword(APIUtil.replaceSystemProperty
                            (policyDeployerServicePassword));
                }
                throttleProperties.setPolicyDeployer(policyDeployerConfiguration);

                //Configuring Block Condition retriever configuration
                OMElement blockConditionRetrieverElement = throttleConfigurationElement.getFirstChildWithName(new
                        QName(APIConstants.AdvancedThrottleConstants.BLOCK_CONDITION_RETRIEVER_CONFIGURATION));

                ThrottleProperties.BlockCondition blockConditionRetrieverConfiguration = new ThrottleProperties
                        .BlockCondition();
                if (blockConditionRetrieverElement != null) {
                    OMElement blockingConditionEnabledElement = blockConditionRetrieverElement
                            .getFirstChildWithName(new QName(APIConstants.AdvancedThrottleConstants.ENABLED));
                    blockConditionRetrieverConfiguration.setEnabled(JavaUtils.isTrueExplicitly
                            (blockingConditionEnabledElement.getText()));
                    OMElement blockConditionRetrieverServiceUrlElement = blockConditionRetrieverElement
                            .getFirstChildWithName(new QName(APIConstants.AdvancedThrottleConstants.SERVICE_URL));
                    if (blockConditionRetrieverServiceUrlElement != null) {
                        blockConditionRetrieverConfiguration.setServiceUrl(APIUtil
                                .replaceSystemProperty(blockConditionRetrieverServiceUrlElement
                                        .getText()));
                    } else {
                        String serviceUrl = "https://" + System.getProperty(APIConstants.KEYMANAGER_HOSTNAME) + ":" +
                                System.getProperty(APIConstants.KEYMANAGER_PORT) + "/throttle/data/v1";
                        blockConditionRetrieverConfiguration.setServiceUrl(serviceUrl);
                    }

                    blockConditionRetrieverConfiguration.setUsername(getFirstProperty(APIConstants
                            .API_KEY_VALIDATOR_USERNAME));
                    OMElement blockConditionRetrieverThreadPoolSizeElement = blockConditionRetrieverElement
                            .getFirstChildWithName(new QName(APIConstants.AdvancedThrottleConstants
                                    .MAX_THREAD_POOL_SIZE));
                    if (blockConditionRetrieverThreadPoolSizeElement != null) {

                        blockConditionRetrieverConfiguration.setCorePoolSize
                                (Integer.parseInt(blockConditionRetrieverThreadPoolSizeElement.getText()));
                    }
                    OMElement blockConditionRetrieverInitIdleElement = blockConditionRetrieverElement
                            .getFirstChildWithName(new QName(APIConstants.AdvancedThrottleConstants
                                    .BLOCK_CONDITION_RETRIEVER_INIT_DELAY));
                    if (blockConditionRetrieverInitIdleElement != null) {
                        blockConditionRetrieverConfiguration.setInitDelay(Long.parseLong
                                (blockConditionRetrieverInitIdleElement
                                        .getText()));
                    }
                    OMElement blockConditionRetrieverTimeIntervalElement = blockConditionRetrieverElement
                            .getFirstChildWithName(new QName(APIConstants.AdvancedThrottleConstants
                                    .BLOCK_CONDITION_RETRIEVER_PERIOD));
                    if (blockConditionRetrieverTimeIntervalElement != null) {
                        blockConditionRetrieverConfiguration.setPeriod(Long.parseLong
                                (blockConditionRetrieverTimeIntervalElement
                                        .getText()));
                    }
                    blockConditionRetrieverConfiguration.setPassword(getFirstProperty(APIConstants
                            .API_KEY_VALIDATOR_PASSWORD));
                }
                throttleProperties.setBlockCondition(blockConditionRetrieverConfiguration);

            }
        }
    }

    public ThrottleProperties getThrottleProperties() {
        return throttleProperties;
    }

    public WorkflowProperties getWorkflowProperties() {
        return workflowProperties;
    }
    
}
