/*
*  Copyright (c) 2005-2011, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/

package org.wso2.carbon.apimgt.impl.utils;

import com.google.gson.Gson;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.description.TransportOutDescription;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.axis2.util.JavaUtils;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.xerces.util.SecurityManager;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.w3c.dom.Document;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.APIMgtAuthorizationFailedException;
import org.wso2.carbon.apimgt.api.APIMgtInternalException;
import org.wso2.carbon.apimgt.api.LoginPostExecutor;
import org.wso2.carbon.apimgt.api.NewPostLoginExecutor;
import org.wso2.carbon.apimgt.api.PasswordResolver;
import org.wso2.carbon.apimgt.api.doc.model.APIDefinition;
import org.wso2.carbon.apimgt.api.doc.model.APIResource;
import org.wso2.carbon.apimgt.api.doc.model.Operation;
import org.wso2.carbon.apimgt.api.doc.model.Parameter;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.apimgt.api.model.APIPublisher;
import org.wso2.carbon.apimgt.api.model.APIStatus;
import org.wso2.carbon.apimgt.api.model.APIStore;
import org.wso2.carbon.apimgt.api.model.Application;
import org.wso2.carbon.apimgt.api.model.CORSConfiguration;
import org.wso2.carbon.apimgt.api.model.Documentation;
import org.wso2.carbon.apimgt.api.model.DocumentationType;
import org.wso2.carbon.apimgt.api.model.KeyManagerConfiguration;
import org.wso2.carbon.apimgt.api.model.Label;
import org.wso2.carbon.apimgt.api.model.Provider;
import org.wso2.carbon.apimgt.api.model.Scope;
import org.wso2.carbon.apimgt.api.model.Tier;
import org.wso2.carbon.apimgt.api.model.URITemplate;
import org.wso2.carbon.apimgt.api.model.WSDLArchiveInfo;
import org.wso2.carbon.apimgt.api.model.policy.APIPolicy;
import org.wso2.carbon.apimgt.api.model.policy.ApplicationPolicy;
import org.wso2.carbon.apimgt.api.model.policy.BandwidthLimit;
import org.wso2.carbon.apimgt.api.model.policy.Limit;
import org.wso2.carbon.apimgt.api.model.policy.Policy;
import org.wso2.carbon.apimgt.api.model.policy.PolicyConstants;
import org.wso2.carbon.apimgt.api.model.policy.QuotaPolicy;
import org.wso2.carbon.apimgt.api.model.policy.RequestCountLimit;
import org.wso2.carbon.apimgt.api.model.policy.SubscriptionPolicy;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.APIMRegistryServiceImpl;
import org.wso2.carbon.apimgt.impl.APIManagerAnalyticsConfiguration;
import org.wso2.carbon.apimgt.impl.APIManagerConfiguration;
import org.wso2.carbon.apimgt.impl.DefaultPasswordResolverImpl;
import org.wso2.carbon.apimgt.impl.PasswordResolverFactory;
import org.wso2.carbon.apimgt.impl.ThrottlePolicyDeploymentManager;
import org.wso2.carbon.apimgt.impl.clients.ApplicationManagementServiceClient;
import org.wso2.carbon.apimgt.impl.clients.OAuthAdminClient;
import org.wso2.carbon.apimgt.impl.clients.UserInformationRecoveryClient;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.impl.dto.APIKeyValidationInfoDTO;
import org.wso2.carbon.apimgt.impl.dto.ConditionDto;
import org.wso2.carbon.apimgt.impl.dto.Environment;
import org.wso2.carbon.apimgt.impl.dto.ThrottleProperties;
import org.wso2.carbon.apimgt.impl.dto.UserRegistrationConfigDTO;
import org.wso2.carbon.apimgt.impl.factory.KeyManagerHolder;
import org.wso2.carbon.apimgt.impl.internal.APIManagerComponent;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.impl.soaptorest.WSDLSOAPOperationExtractor;
import org.wso2.carbon.apimgt.impl.soaptorest.util.SOAPOperationBindingUtils;
import org.wso2.carbon.apimgt.impl.template.APITemplateException;
import org.wso2.carbon.apimgt.impl.template.ThrottlePolicyTemplateBuilder;
import org.wso2.carbon.apimgt.keymgt.client.SubscriberKeyMgtClient;
import org.wso2.carbon.base.MultitenantConstants;
import org.wso2.carbon.base.ServerConfiguration;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.core.commons.stub.loggeduserinfo.ExceptionException;
import org.wso2.carbon.core.commons.stub.loggeduserinfo.LoggedUserInfo;
import org.wso2.carbon.core.commons.stub.loggeduserinfo.LoggedUserInfoAdminStub;
import org.wso2.carbon.core.multitenancy.utils.TenantAxisUtils;
import org.wso2.carbon.core.util.CryptoException;
import org.wso2.carbon.core.util.CryptoUtil;
import org.wso2.carbon.core.util.PermissionUpdateUtil;
import org.wso2.carbon.governance.api.common.dataobjects.GovernanceArtifact;
import org.wso2.carbon.governance.api.endpoints.EndpointManager;
import org.wso2.carbon.governance.api.endpoints.dataobjects.Endpoint;
import org.wso2.carbon.governance.api.exception.GovernanceException;
import org.wso2.carbon.governance.api.generic.GenericArtifactManager;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifact;
import org.wso2.carbon.governance.api.util.GovernanceConstants;
import org.wso2.carbon.governance.api.util.GovernanceUtils;
import org.wso2.carbon.governance.lcm.util.CommonUtil;
import org.wso2.carbon.identity.core.util.IdentityConfigParser;
import org.wso2.carbon.identity.core.util.IdentityCoreConstants;
import org.wso2.carbon.identity.oauth.config.OAuthServerConfiguration;
import org.wso2.carbon.identity.user.profile.stub.UserProfileMgtServiceStub;
import org.wso2.carbon.identity.user.profile.stub.UserProfileMgtServiceUserProfileExceptionException;
import org.wso2.carbon.identity.user.profile.stub.types.UserProfileDTO;
import org.wso2.carbon.registry.core.ActionConstants;
import org.wso2.carbon.registry.core.Association;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.Tag;
import org.wso2.carbon.registry.core.config.Mount;
import org.wso2.carbon.registry.core.config.RegistryContext;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.jdbc.realm.RegistryAuthorizationManager;
import org.wso2.carbon.registry.core.pagination.PaginationContext;
import org.wso2.carbon.registry.core.secure.AuthorizationFailedException;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.registry.core.service.TenantRegistryLoader;
import org.wso2.carbon.registry.core.session.UserRegistry;
import org.wso2.carbon.registry.core.utils.RegistryUtils;
import org.wso2.carbon.registry.indexing.indexer.IndexerException;
import org.wso2.carbon.registry.indexing.solr.SolrClient;
import org.wso2.carbon.user.api.Permission;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.api.UserStoreManager;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserRealm;
import org.wso2.carbon.user.core.common.AbstractUserStoreManager;
import org.wso2.carbon.user.core.config.RealmConfigXMLProcessor;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.user.mgt.UserMgtConstants;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.carbon.utils.ConfigurationContextService;
import org.wso2.carbon.utils.FileUtil;
import org.wso2.carbon.utils.NetworkUtils;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.nio.charset.Charset;
import java.rmi.RemoteException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.cache.Cache;
import javax.cache.CacheConfiguration;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * This class contains the utility methods used by the implementations of APIManager, APIProvider
 * and APIConsumer interfaces.
 */
public final class APIUtil {

    private static final Log log = LogFactory.getLog(APIUtil.class);

    private static final Log audit = CarbonConstants.AUDIT_LOG;

    private static boolean isContextCacheInitialized = false;

    public static final String DISABLE_ROLE_VALIDATION_AT_SCOPE_CREATION = "disableRoleValidationAtScopeCreation";

    private static final int ENTITY_EXPANSION_LIMIT = 0;

    private static final String DESCRIPTION = "Allows [1] request(s) per minute.";

    private static final int DEFAULT_TENANT_IDLE_MINS = 30;
    private static long tenantIdleTimeMillis;
    private static Set<String> currentLoadingTenants = new HashSet<String>();

    private static volatile Set<String> whiteListedScopes;
    private static boolean isPublisherRoleCacheEnabled = true;

    public static final String STRICT = "Strict";
    public static final String ALLOW_ALL = "AllowAll";
    public static final String DEFAULT_AND_LOCALHOST = "DefaultAndLocalhost";
    public static final String HOST_NAME_VERIFIER = "httpclient.hostnameVerifier";
    public static String multiGrpAppSharing = null;

    private static final String CONFIG_ELEM_OAUTH = "OAuth";
    private static final String REVOKE = "revoke";
    private static final String TOKEN = "token";
    private static final String GRANT_TYPE_NAME = "<GrantTypeName>";

    //Need tenantIdleTime to check whether the tenant is in idle state in loadTenantConfig method
    static {
        tenantIdleTimeMillis =
                Long.parseLong(System.getProperty(
                        org.wso2.carbon.utils.multitenancy.MultitenantConstants.TENANT_IDLE_TIME,
                        String.valueOf(DEFAULT_TENANT_IDLE_MINS)))
                        * 60 * 1000;
    }

    private static String hostAddress = null;

    /**
     * To initialize the publisherRoleCache configurations, based on configurations.
     */
    public static void init() {
        APIManagerConfiguration apiManagerConfiguration = ServiceReferenceHolder.getInstance()
                .getAPIManagerConfigurationService().getAPIManagerConfiguration();
        String isPublisherRoleCacheEnabledConfiguration = apiManagerConfiguration
                .getFirstProperty(APIConstants.PUBLISHER_ROLE_CACHE_ENABLED);
        isPublisherRoleCacheEnabled = isPublisherRoleCacheEnabledConfiguration == null || Boolean
                .parseBoolean(isPublisherRoleCacheEnabledConfiguration);
    }

    /**
     * This method used to get API from governance artifact
     *
     * @param artifact API artifact
     * @param registry Registry
     * @return API
     * @throws APIManagementException if failed to get API from artifact
     */
    public static API getAPI(GovernanceArtifact artifact, Registry registry)
            throws APIManagementException {

        API api;
        try {
            String providerName = artifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER);
            String apiName = artifact.getAttribute(APIConstants.API_OVERVIEW_NAME);
            String apiVersion = artifact.getAttribute(APIConstants.API_OVERVIEW_VERSION);
            APIIdentifier apiIdentifier = new APIIdentifier(providerName, apiName, apiVersion);
            int apiId = ApiMgtDAO.getInstance().getAPIID(apiIdentifier, null);

            if (apiId == -1) {
                return null;
            }
            api = new API(apiIdentifier);
            // set rating
            String artifactPath = GovernanceUtils.getArtifactPath(registry, artifact.getId());
            api = setResourceProperties(api, registry, artifactPath);
            api.setRating(getAverageRating(apiId));
            //set description
            api.setDescription(artifact.getAttribute(APIConstants.API_OVERVIEW_DESCRIPTION));
            //set last access time
            api.setLastUpdated(registry.get(artifactPath).getLastModified());
            //set uuid
            api.setUUID(artifact.getId());
            //setting api ID for scope retrieval
            api.getId().setApplicationId(Integer.toString(apiId));
            // set url
            api.setStatus(getLcStateFromArtifact(artifact));
            api.setThumbnailUrl(artifact.getAttribute(APIConstants.API_OVERVIEW_THUMBNAIL_URL));
            api.setWsdlUrl(artifact.getAttribute(APIConstants.API_OVERVIEW_WSDL));
            api.setWadlUrl(artifact.getAttribute(APIConstants.API_OVERVIEW_WADL));
            api.setTechnicalOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_TEC_OWNER));
            api.setTechnicalOwnerEmail(artifact.getAttribute(APIConstants.API_OVERVIEW_TEC_OWNER_EMAIL));
            api.setBusinessOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_BUSS_OWNER));
            api.setBusinessOwnerEmail(artifact.getAttribute(APIConstants.API_OVERVIEW_BUSS_OWNER_EMAIL));
            api.setVisibility(artifact.getAttribute(APIConstants.API_OVERVIEW_VISIBILITY));
            api.setVisibleRoles(artifact.getAttribute(APIConstants.API_OVERVIEW_VISIBLE_ROLES));
            api.setVisibleTenants(artifact.getAttribute(APIConstants.API_OVERVIEW_VISIBLE_TENANTS));
            api.setEndpointSecured(Boolean.parseBoolean(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_SECURED)));
            api.setEndpointAuthDigest(Boolean.parseBoolean(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_AUTH_DIGEST)));
            api.setEndpointUTUsername(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_USERNAME));
            if (!((APIConstants.DEFAULT_MODIFIED_ENDPOINT_PASSWORD)
                    .equals(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_PASSWORD)))) {
                api.setEndpointUTPassword(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_PASSWORD));
            } else { //If APIEndpointPasswordRegistryHandler is enabled take password from the registry hidden property
                api.setEndpointUTPassword(getActualEpPswdFromHiddenProperty(api, registry));
            }
            api.setTransports(artifact.getAttribute(APIConstants.API_OVERVIEW_TRANSPORTS));
            api.setInSequence(artifact.getAttribute(APIConstants.API_OVERVIEW_INSEQUENCE));
            api.setOutSequence(artifact.getAttribute(APIConstants.API_OVERVIEW_OUTSEQUENCE));
            api.setFaultSequence(artifact.getAttribute(APIConstants.API_OVERVIEW_FAULTSEQUENCE));
            api.setResponseCache(artifact.getAttribute(APIConstants.API_OVERVIEW_RESPONSE_CACHING));
            api.setImplementation(artifact.getAttribute(APIConstants.PROTOTYPE_OVERVIEW_IMPLEMENTATION));
            api.setProductionMaxTps(artifact.getAttribute(APIConstants.API_PRODUCTION_THROTTLE_MAXTPS));

            int cacheTimeout = APIConstants.API_RESPONSE_CACHE_TIMEOUT;
            try {
                cacheTimeout = Integer.parseInt(artifact.getAttribute(APIConstants.API_OVERVIEW_CACHE_TIMEOUT));
            } catch (NumberFormatException e) {
                //ignore
            }

            api.setCacheTimeout(cacheTimeout);

            api.setEndpointConfig(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_CONFIG));

            api.setRedirectURL(artifact.getAttribute(APIConstants.API_OVERVIEW_REDIRECT_URL));
            api.setApiOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_OWNER));
            api.setAdvertiseOnly(Boolean.parseBoolean(artifact.getAttribute(APIConstants.API_OVERVIEW_ADVERTISE_ONLY)));

            api.setSubscriptionAvailability(artifact.getAttribute(APIConstants.API_OVERVIEW_SUBSCRIPTION_AVAILABILITY));
            api.setSubscriptionAvailableTenants(artifact.getAttribute(APIConstants.API_OVERVIEW_SUBSCRIPTION_AVAILABLE_TENANTS));

            String tenantDomainName = MultitenantUtils.getTenantDomain(replaceEmailDomainBack(providerName));
            int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager()
                    .getTenantId(tenantDomainName);

            boolean isGlobalThrottlingEnabled = APIUtil.isAdvanceThrottlingEnabled();


            if (isGlobalThrottlingEnabled) {
                String apiLevelTier = ApiMgtDAO.getInstance().getAPILevelTier(apiId);
                api.setApiLevelPolicy(apiLevelTier);
            }

            String tiers = artifact.getAttribute(APIConstants.API_OVERVIEW_TIER);
            Map<String, Tier> definedTiers = getTiers(tenantId);
            Set<Tier> availableTier = getAvailableTiers(definedTiers, tiers, apiName);
            api.addAvailableTiers(availableTier);
            api.setMonetizationCategory(getAPIMonetizationCategory(availableTier, tenantDomainName));


            api.setContext(artifact.getAttribute(APIConstants.API_OVERVIEW_CONTEXT));
            // We set the context template here
            api.setContextTemplate(artifact.getAttribute(APIConstants.API_OVERVIEW_CONTEXT_TEMPLATE));
            api.setLatest(Boolean.parseBoolean(artifact.getAttribute(APIConstants.API_OVERVIEW_IS_LATEST)));


            Set<URITemplate> uriTemplates = new LinkedHashSet<URITemplate>();
            List<String> uriTemplateNames = new ArrayList<String>();

            Set<Scope> scopes = ApiMgtDAO.getInstance().getAPIScopes(api.getId());
            api.setScopes(scopes);

            HashMap<String, String> urlPatternsSet;
            urlPatternsSet = ApiMgtDAO.getInstance().getURITemplatesPerAPIAsString(api.getId());

            HashMap<String, String> resourceScopesMap;
            resourceScopesMap = ApiMgtDAO.getInstance().getResourceToScopeMapping(api.getId());

            Set<String> urlPatternsKeySet = urlPatternsSet.keySet();
            String resourceScopeKey;
            for (String urlPattern : urlPatternsKeySet) {
                URITemplate uriTemplate = new URITemplate();
                String[] urlPatternComponents = urlPattern.split("::");
                String uTemplate = (urlPatternComponents.length >= 1) ? urlPatternComponents[0] : null;
                String method = (urlPatternComponents.length >= 2) ? urlPatternComponents[1] : null;
                String authType = (urlPatternComponents.length >= 3) ? urlPatternComponents[2] : null;
                String throttlingTier = (urlPatternComponents.length >= 4) ? urlPatternComponents[3] : null;
                String mediationScript = (urlPatternComponents.length >= 5) ? urlPatternComponents[4] : null;
                uriTemplate.setHTTPVerb(method);
                uriTemplate.setAuthType(authType);
                uriTemplate.setThrottlingTier(throttlingTier);
                uriTemplate.setHttpVerbs(method);
                uriTemplate.setAuthTypes(authType);
                uriTemplate.setUriTemplate(uTemplate);
                uriTemplate.setResourceURI(api.getUrl());
                uriTemplate.setResourceSandboxURI(api.getSandboxUrl());
                uriTemplate.setThrottlingTiers(throttlingTier);
                uriTemplate.setMediationScript(mediationScript);
                resourceScopeKey = APIUtil.getResourceKey(api.getContext(), apiVersion, uTemplate, method);
                uriTemplate.setScopes(findScopeByKey(scopes, resourceScopesMap.get(resourceScopeKey)));
                //Checking for duplicate uri template names
                if (uriTemplateNames.contains(uTemplate)) {
                    for (URITemplate tmp : uriTemplates) {
                        if (uTemplate.equals(tmp.getUriTemplate())) {
                            tmp.setHttpVerbs(method);
                            tmp.setAuthTypes(authType);
                            tmp.setThrottlingTiers(throttlingTier);
                            resourceScopeKey = APIUtil.getResourceKey(api.getContext(), apiVersion, uTemplate, method);
                            tmp.setScopes(findScopeByKey(scopes, resourceScopesMap.get(resourceScopeKey)));
                            break;
                        }
                    }
                } else {
                    uriTemplates.add(uriTemplate);
                }
                uriTemplateNames.add(uTemplate);
            }
            api.setUriTemplates(uriTemplates);
            api.setAsDefaultVersion(Boolean.parseBoolean(artifact.getAttribute(APIConstants.API_OVERVIEW_IS_DEFAULT_VERSION)));
            Set<String> tags = new HashSet<String>();
            Tag[] tag = registry.getTags(artifactPath);
            for (Tag tag1 : tag) {
                tags.add(tag1.getTagName());
            }
            api.addTags(tags);
            api.setLastUpdated(registry.get(artifactPath).getLastModified());
            api.setImplementation(artifact.getAttribute(APIConstants.PROTOTYPE_OVERVIEW_IMPLEMENTATION));
            String environments = artifact.getAttribute(APIConstants.API_OVERVIEW_ENVIRONMENTS);
            api.setEnvironments(extractEnvironmentsForAPI(environments));
            api.setCorsConfiguration(getCorsConfigurationFromArtifact(artifact));
            api.setAuthorizationHeader(artifact.getAttribute(APIConstants.API_OVERVIEW_AUTHORIZATION_HEADER));
            api.setApiSecurity(artifact.getAttribute(APIConstants.API_OVERVIEW_API_SECURITY));

        } catch (GovernanceException e) {
            String msg = "Failed to get API for artifact ";
            throw new APIManagementException(msg, e);
        } catch (RegistryException e) {
            String msg = "Failed to get LastAccess time or Rating";
            throw new APIManagementException(msg, e);
        } catch (UserStoreException e) {
            String msg = "Failed to get User Realm of API Provider";
            throw new APIManagementException(msg, e);
        }
        return api;
    }

    /**
     * This Method is different from getAPI method, as this one returns
     * URLTemplates without aggregating duplicates. This is to be used for building synapse config.
     *
     * @param artifact
     * @param registry
     * @return API
     * @throws org.wso2.carbon.apimgt.api.APIManagementException
     */
    public static API getAPIForPublishing(GovernanceArtifact artifact, Registry registry)
            throws APIManagementException {

        API api;
        try {
            String providerName = artifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER);
            String apiName = artifact.getAttribute(APIConstants.API_OVERVIEW_NAME);
            String apiVersion = artifact.getAttribute(APIConstants.API_OVERVIEW_VERSION);
            APIIdentifier apiIdentifier = new APIIdentifier(providerName, apiName, apiVersion);
            int apiId = ApiMgtDAO.getInstance().getAPIID(apiIdentifier, null);

            if (apiId == -1) {
                return null;
            }

            api = new API(apiIdentifier);
            //set uuid
            api.setUUID(artifact.getId());
            // set rating
            String artifactPath = GovernanceUtils.getArtifactPath(registry, artifact.getId());
            api = setResourceProperties(api, registry, artifactPath);
            api.setRating(getAverageRating(apiId));
            //set description
            api.setDescription(artifact.getAttribute(APIConstants.API_OVERVIEW_DESCRIPTION));
            //set last access time
            api.setLastUpdated(registry.get(artifactPath).getLastModified());
            // set url
            api.setStatus(getLcStateFromArtifact(artifact));
            api.setThumbnailUrl(artifact.getAttribute(APIConstants.API_OVERVIEW_THUMBNAIL_URL));
            api.setWsdlUrl(artifact.getAttribute(APIConstants.API_OVERVIEW_WSDL));
            api.setWadlUrl(artifact.getAttribute(APIConstants.API_OVERVIEW_WADL));
            api.setTechnicalOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_TEC_OWNER));
            api.setTechnicalOwnerEmail(artifact.getAttribute(APIConstants.API_OVERVIEW_TEC_OWNER_EMAIL));
            api.setBusinessOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_BUSS_OWNER));
            api.setBusinessOwnerEmail(artifact.getAttribute(APIConstants.API_OVERVIEW_BUSS_OWNER_EMAIL));
            api.setVisibility(artifact.getAttribute(APIConstants.API_OVERVIEW_VISIBILITY));
            api.setVisibleRoles(artifact.getAttribute(APIConstants.API_OVERVIEW_VISIBLE_ROLES));
            api.setVisibleTenants(artifact.getAttribute(APIConstants.API_OVERVIEW_VISIBLE_TENANTS));
            api.setEndpointSecured(Boolean.parseBoolean(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_SECURED)));
            api.setEndpointAuthDigest(Boolean.parseBoolean(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_AUTH_DIGEST)));
            api.setEndpointUTUsername(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_USERNAME));
            if (!((APIConstants.DEFAULT_MODIFIED_ENDPOINT_PASSWORD)
                    .equals(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_PASSWORD)))) {
                api.setEndpointUTPassword(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_PASSWORD));
            } else { //If APIEndpointPasswordRegistryHandler is enabled take password from the registry hidden property
                api.setEndpointUTPassword(getActualEpPswdFromHiddenProperty(api, registry));
            }
            api.setTransports(artifact.getAttribute(APIConstants.API_OVERVIEW_TRANSPORTS));
            api.setInSequence(artifact.getAttribute(APIConstants.API_OVERVIEW_INSEQUENCE));
            api.setOutSequence(artifact.getAttribute(APIConstants.API_OVERVIEW_OUTSEQUENCE));
            api.setFaultSequence(artifact.getAttribute(APIConstants.API_OVERVIEW_FAULTSEQUENCE));
            api.setResponseCache(artifact.getAttribute(APIConstants.API_OVERVIEW_RESPONSE_CACHING));
            api.setImplementation(artifact.getAttribute(APIConstants.PROTOTYPE_OVERVIEW_IMPLEMENTATION));
            api.setType(artifact.getAttribute(APIConstants.API_OVERVIEW_TYPE));
            api.setProductionMaxTps(artifact.getAttribute(APIConstants.API_PRODUCTION_THROTTLE_MAXTPS));
            api.setSandboxMaxTps(artifact.getAttribute(APIConstants.API_SANDBOX_THROTTLE_MAXTPS));

            int cacheTimeout = APIConstants.API_RESPONSE_CACHE_TIMEOUT;
            try {
                String strCacheTimeout = artifact.getAttribute(APIConstants.API_OVERVIEW_CACHE_TIMEOUT);
                if (strCacheTimeout != null && !strCacheTimeout.isEmpty()) {
                    cacheTimeout = Integer.parseInt(strCacheTimeout);
                }
            } catch (NumberFormatException e) {
                if (log.isWarnEnabled()) {
                    log.warn("Error while retrieving cache timeout from the registry for " + apiIdentifier);
                }
                // ignore the exception and use default cache timeout value
            }

            api.setCacheTimeout(cacheTimeout);

            api.setEndpointConfig(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_CONFIG));

            api.setRedirectURL(artifact.getAttribute(APIConstants.API_OVERVIEW_REDIRECT_URL));
            api.setApiOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_OWNER));
            api.setAdvertiseOnly(Boolean.parseBoolean(artifact.getAttribute(APIConstants.API_OVERVIEW_ADVERTISE_ONLY)));
            api.setType(artifact.getAttribute(APIConstants.API_OVERVIEW_TYPE));
            api.setSubscriptionAvailability(artifact.getAttribute(APIConstants.API_OVERVIEW_SUBSCRIPTION_AVAILABILITY));
            api.setSubscriptionAvailableTenants(artifact.getAttribute(APIConstants.API_OVERVIEW_SUBSCRIPTION_AVAILABLE_TENANTS));

            String tenantDomainName = MultitenantUtils.getTenantDomain(replaceEmailDomainBack(providerName));
            int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager()
                    .getTenantId(tenantDomainName);

            APIManagerConfiguration config = ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService()
                    .getAPIManagerConfiguration();
            boolean isGlobalThrottlingEnabled = APIUtil.isAdvanceThrottlingEnabled();

            if (isGlobalThrottlingEnabled) {
                String apiLevelTier = ApiMgtDAO.getInstance().getAPILevelTier(apiId);
                api.setApiLevelPolicy(apiLevelTier);
            }

            String tiers = artifact.getAttribute(APIConstants.API_OVERVIEW_TIER);
            Map<String, Tier> definedTiers = getTiers(tenantId);
            Set<Tier> availableTier = getAvailableTiers(definedTiers, tiers, apiName);
            api.addAvailableTiers(availableTier);

            // This contains the resolved context
            api.setContext(artifact.getAttribute(APIConstants.API_OVERVIEW_CONTEXT));
            // We set the context template here
            api.setContextTemplate(artifact.getAttribute(APIConstants.API_OVERVIEW_CONTEXT_TEMPLATE));
            api.setLatest(Boolean.parseBoolean(artifact.getAttribute(APIConstants.API_OVERVIEW_IS_LATEST)));


            Set<URITemplate> uriTemplates = new LinkedHashSet<URITemplate>();
            List<String> uriTemplateNames = new ArrayList<String>();

            Set<Scope> scopes = ApiMgtDAO.getInstance().getAPIScopes(api.getId());
            api.setScopes(scopes);

            HashMap<String, String> urlPatternsSet;
            urlPatternsSet = ApiMgtDAO.getInstance().getURITemplatesPerAPIAsString(api.getId());
            HashMap<String, String> resourceScopes;
            resourceScopes = ApiMgtDAO.getInstance().getResourceToScopeMapping(api.getId());

            Set<String> urlPatternsKeySet = urlPatternsSet.keySet();
            String resourceScopeKey;
            for (String urlPattern : urlPatternsKeySet) {
                URITemplate uriTemplate = new URITemplate();
                String[] urlPatternComponents = urlPattern.split("::");
                String uTemplate = (urlPatternComponents.length >= 1) ? urlPatternComponents[0] : null;
                String method = (urlPatternComponents.length >= 2) ? urlPatternComponents[1] : null;
                String authType = (urlPatternComponents.length >= 3) ? urlPatternComponents[2] : null;
                String throttlingTier = (urlPatternComponents.length >= 4) ? urlPatternComponents[3] : null;
                String mediationScript = (urlPatternComponents.length >= 5) ? urlPatternComponents[4] : null;
                uriTemplate.setHTTPVerb(method);
                uriTemplate.setAuthType(authType);
                uriTemplate.setThrottlingTier(throttlingTier);
                uriTemplate.setHttpVerbs(method);
                uriTemplate.setAuthTypes(authType);
                uriTemplate.setUriTemplate(uTemplate);
                uriTemplate.setResourceURI(api.getUrl());
                uriTemplate.setResourceSandboxURI(api.getSandboxUrl());
                uriTemplate.setThrottlingTiers(throttlingTier);
                uriTemplate.setMediationScript(mediationScript);
                uriTemplate.setMediationScripts(method, mediationScript);
                resourceScopeKey = APIUtil.getResourceKey(api.getContext(), apiVersion, uTemplate, method);
                uriTemplate.setScopes(findScopeByKey(scopes, resourceScopes.get(resourceScopeKey)));
                //Checking for duplicate uri template names

                if (uriTemplateNames.contains(uTemplate)) {
                    for (URITemplate tmp : uriTemplates) {
                        if (uTemplate.equals(tmp.getUriTemplate())) {
                            tmp.setHttpVerbs(method);
                            tmp.setAuthTypes(authType);
                            tmp.setThrottlingTiers(throttlingTier);
                            tmp.setMediationScripts(method, mediationScript);
                            resourceScopeKey = APIUtil.getResourceKey(api.getContext(), apiVersion, uTemplate, method);
                            tmp.setScopes(findScopeByKey(scopes, resourceScopes.get(resourceScopeKey)));
                            break;
                        }
                    }
                } else {
                    uriTemplates.add(uriTemplate);
                }
                uriTemplateNames.add(uTemplate);
            }

            if (APIConstants.IMPLEMENTATION_TYPE_INLINE.equalsIgnoreCase(api.getImplementation())) {
                for (URITemplate template : uriTemplates) {
                    template.setMediationScript(template.getAggregatedMediationScript());
                }
            }

            api.setUriTemplates(uriTemplates);
            api.setAsDefaultVersion(Boolean.parseBoolean(artifact.getAttribute(APIConstants.API_OVERVIEW_IS_DEFAULT_VERSION)));
            Set<String> tags = new HashSet<String>();
            Tag[] tag = registry.getTags(artifactPath);
            for (Tag tag1 : tag) {
                tags.add(tag1.getTagName());
            }
            api.addTags(tags);
            api.setLastUpdated(registry.get(artifactPath).getLastModified());
            api.setCreatedTime(String.valueOf(registry.get(artifactPath).getCreatedTime().getTime()));
            api.setImplementation(artifact.getAttribute(APIConstants.PROTOTYPE_OVERVIEW_IMPLEMENTATION));
            String environments = artifact.getAttribute(APIConstants.API_OVERVIEW_ENVIRONMENTS);
            api.setEnvironments(extractEnvironmentsForAPI(environments));
            api.setCorsConfiguration(getCorsConfigurationFromArtifact(artifact));
            api.setAuthorizationHeader(artifact.getAttribute(APIConstants.API_OVERVIEW_AUTHORIZATION_HEADER));
            api.setApiSecurity(artifact.getAttribute(APIConstants.API_OVERVIEW_API_SECURITY));
            //set data and status related to monetization
            api.setMonetizationStatus(Boolean.parseBoolean(artifact.getAttribute
                    (APIConstants.API_MONETIZATION_STATUS)));
            String monetizationInfo = artifact.getAttribute(APIConstants.API_MONETIZATION_PROPERTIES);
            if (StringUtils.isNotBlank(monetizationInfo)) {
                JSONParser parser = new JSONParser();
                JSONObject jsonObj = (JSONObject) parser.parse(monetizationInfo);
                api.setMonetizationProperties(jsonObj);
            }
            api.setGatewayLabels(getLabelsFromAPIGovernanceArtifact(artifact, api.getId().getProviderName()));

            //get endpoint config string from artifact, parse it as a json and set the environment list configured with
            //non empty URLs to API object
            try {
                api.setEnvironmentList(extractEnvironmentListForAPI(
                        artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_CONFIG)));
            } catch (ParseException e) {
                String msg = "Failed to parse endpoint config JSON of API: " + apiName + " " + apiVersion;
                log.error(msg, e);
                throw new APIManagementException(msg, e);
            } catch (ClassCastException e) {
                String msg = "Invalid endpoint config JSON found in API: " + apiName + " " + apiVersion;
                log.error(msg, e);
                throw new APIManagementException(msg, e);
            }

        } catch (GovernanceException e) {
            String msg = "Failed to get API for artifact ";
            throw new APIManagementException(msg, e);
        } catch (RegistryException e) {
            String msg = "Failed to get LastAccess time or Rating";
            throw new APIManagementException(msg, e);
        } catch (UserStoreException e) {
            String msg = "Failed to get User Realm of API Provider";
            throw new APIManagementException(msg, e);
        } catch (ParseException e) {
            String msg = "Failed to get parse monetization information.";
            throw new APIManagementException(msg, e);
        }
        return api;
    }

    /**
     * This method return the gateway labels of an API
     *
     * @param artifact API artifact
     * @param apiProviderName name of API provider
     * @return List<Label> list of gateway labels
     */
    private static List<Label> getLabelsFromAPIGovernanceArtifact(GovernanceArtifact artifact, String apiProviderName)
            throws GovernanceException, APIManagementException {
        String[] labelArray = artifact.getAttributes(APIConstants.API_LABELS_GATEWAY_LABELS);
        List<Label> gatewayLabelListForAPI = new ArrayList<>();

        if (labelArray != null && labelArray.length > 0) {
            String tenantDomain = MultitenantUtils.getTenantDomain
                    (replaceEmailDomainBack(apiProviderName));
            List<Label> allLabelList = APIUtil.getAllLabels(tenantDomain);
            for (String labelName : labelArray) {
                Label label = new Label();
                //set the name
                label.setName(labelName);
                //set the description and access URLs
                for (Label currentLabel : allLabelList) {
                    if (labelName.equalsIgnoreCase(currentLabel.getName())) {
                        label.setDescription(currentLabel.getDescription());
                        label.setAccessUrls(currentLabel.getAccessUrls());
                    }
                }
                gatewayLabelListForAPI.add(label);
            }
        }
        return gatewayLabelListForAPI;
    }

    /**
     * This method used to extract environment list configured with non empty URLs.
     *
     * @param endpointConfigs (Eg: {"production_endpoints":{"url":"http://www.test.com/v1/xxx","config":null,
     *                              "template_not_supported":false},"endpoint_type":"http"})
     * @return Set<String>
     */
    public static Set<String> extractEnvironmentListForAPI(String endpointConfigs)
            throws ParseException, ClassCastException {
        Set<String> environmentList = new HashSet<String>();
        if (endpointConfigs != null) {
            JSONParser parser = new JSONParser();
            JSONObject endpointConfigJson = (JSONObject) parser.parse(endpointConfigs);
            if (endpointConfigJson.containsKey(APIConstants.API_DATA_PRODUCTION_ENDPOINTS) &&
                    isEndpointURLNonEmpty(endpointConfigJson.get(APIConstants.API_DATA_PRODUCTION_ENDPOINTS))) {
                environmentList.add(APIConstants.API_KEY_TYPE_PRODUCTION);
            }
            if (endpointConfigJson.containsKey(APIConstants.API_DATA_SANDBOX_ENDPOINTS) &&
                    isEndpointURLNonEmpty(endpointConfigJson.get(APIConstants.API_DATA_SANDBOX_ENDPOINTS))) {
                environmentList.add(APIConstants.API_KEY_TYPE_SANDBOX);
            }
        }
        return environmentList;
    }

    /**
     * This method used to check whether the endpoints JSON object has a non empty URL.
     *
     * @param endpoints (Eg: {"url":"http://www.test.com/v1/xxx","config":null,"template_not_supported":false})
     * @return boolean
     */
    public static boolean isEndpointURLNonEmpty(Object endpoints) {
        if (endpoints instanceof JSONObject) {
            JSONObject endpointJson = (JSONObject) endpoints;
            if (endpointJson.containsKey(APIConstants.API_DATA_URL) &&
                    endpointJson.get(APIConstants.API_DATA_URL) != null) {
                String url = (endpointJson.get(APIConstants.API_DATA_URL)).toString();
                if (StringUtils.isNotBlank(url)) {
                    return true;
                }
            }
        } else if (endpoints instanceof JSONArray) {
            JSONArray endpointsJson = (JSONArray) endpoints;
            for (int i = 0; i < endpointsJson.size(); i++) {
                if (isEndpointURLNonEmpty(endpointsJson.get(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static API getAPI(GovernanceArtifact artifact)
            throws APIManagementException {

        API api;
        try {
            String providerName = artifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER);
            String apiName = artifact.getAttribute(APIConstants.API_OVERVIEW_NAME);
            String apiVersion = artifact.getAttribute(APIConstants.API_OVERVIEW_VERSION);
            APIIdentifier apiIdentifier = new APIIdentifier(providerName, apiName, apiVersion);
            api = new API(apiIdentifier);
            int apiId = ApiMgtDAO.getInstance().getAPIID(apiIdentifier, null);
            if (apiId == -1) {
                return null;
            }
            //set uuid
            api.setUUID(artifact.getId());
            api.setRating(getAverageRating(apiId));
            api.setThumbnailUrl(artifact.getAttribute(APIConstants.API_OVERVIEW_THUMBNAIL_URL));
            api.setStatus(getLcStateFromArtifact(artifact));
            api.setContext(artifact.getAttribute(APIConstants.API_OVERVIEW_CONTEXT));
            api.setVisibility(artifact.getAttribute(APIConstants.API_OVERVIEW_VISIBILITY));
            api.setVisibleRoles(artifact.getAttribute(APIConstants.API_OVERVIEW_VISIBLE_ROLES));
            api.setVisibleTenants(artifact.getAttribute(APIConstants.API_OVERVIEW_VISIBLE_TENANTS));
            api.setTransports(artifact.getAttribute(APIConstants.API_OVERVIEW_TRANSPORTS));
            api.setInSequence(artifact.getAttribute(APIConstants.API_OVERVIEW_INSEQUENCE));
            api.setOutSequence(artifact.getAttribute(APIConstants.API_OVERVIEW_OUTSEQUENCE));
            api.setFaultSequence(artifact.getAttribute(APIConstants.API_OVERVIEW_FAULTSEQUENCE));
            api.setDescription(artifact.getAttribute(APIConstants.API_OVERVIEW_DESCRIPTION));
            api.setResponseCache(artifact.getAttribute(APIConstants.API_OVERVIEW_RESPONSE_CACHING));
            api.setType(artifact.getAttribute(APIConstants.API_OVERVIEW_TYPE));
            int cacheTimeout = APIConstants.API_RESPONSE_CACHE_TIMEOUT;
            try {
                cacheTimeout = Integer.parseInt(artifact.getAttribute(APIConstants.API_OVERVIEW_CACHE_TIMEOUT));
            } catch (NumberFormatException e) {
                //ignore
            }
            api.setCacheTimeout(cacheTimeout);

            boolean isGlobalThrottlingEnabled = APIUtil.isAdvanceThrottlingEnabled();

            if (isGlobalThrottlingEnabled) {
                String apiLevelTier = ApiMgtDAO.getInstance().getAPILevelTier(apiId);
                api.setApiLevelPolicy(apiLevelTier);

                Set<Tier> availablePolicy = new HashSet<Tier>();
                String[] subscriptionPolicy = ApiMgtDAO.getInstance().getPolicyNames(PolicyConstants.POLICY_LEVEL_SUB, replaceEmailDomainBack(providerName));
                List<String> definedPolicyNames = Arrays.asList(subscriptionPolicy);
                String policies = artifact.getAttribute(APIConstants.API_OVERVIEW_TIER);
                if (policies != null && !"".equals(policies)) {
                    String[] policyNames = policies.split("\\|\\|");
                    for (String policyName : policyNames) {
                        if (definedPolicyNames.contains(policyName) || APIConstants.UNLIMITED_TIER.equals(policyName)) {
                            Tier p = new Tier(policyName);
                            availablePolicy.add(p);
                        } else {
                            log.warn("Unknown policy: " + policyName + " found on API: " + apiName);
                        }
                    }
                }

                api.addAvailableTiers(availablePolicy);
                String tenantDomainName = MultitenantUtils.getTenantDomain(replaceEmailDomainBack(providerName));
                api.setMonetizationCategory(getAPIMonetizationCategory(availablePolicy, tenantDomainName));
            } else {
                //deprecated throttling method
                Set<Tier> availableTier = new HashSet<Tier>();
                String tiers = artifact.getAttribute(APIConstants.API_OVERVIEW_TIER);
                String tenantDomainName = MultitenantUtils.getTenantDomain(replaceEmailDomainBack(providerName));
                if (tiers != null) {
                    String[] tierNames = tiers.split("\\|\\|");
                    for (String tierName : tierNames) {
                        Tier tier = new Tier(tierName);
                        availableTier.add(tier);

                    }

                    api.addAvailableTiers(availableTier);
                    api.setMonetizationCategory(getAPIMonetizationCategory(availableTier, tenantDomainName));
                } else {
                    api.setMonetizationCategory(getAPIMonetizationCategory(availableTier, tenantDomainName));
                }
            }

            api.setRedirectURL(artifact.getAttribute(APIConstants.API_OVERVIEW_REDIRECT_URL));
            api.setApiOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_OWNER));
            api.setAdvertiseOnly(Boolean.parseBoolean(artifact.getAttribute(APIConstants.API_OVERVIEW_ADVERTISE_ONLY)));

            api.setEndpointConfig(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_CONFIG));

            api.setSubscriptionAvailability(artifact.getAttribute(APIConstants.API_OVERVIEW_SUBSCRIPTION_AVAILABILITY));
            api.setSubscriptionAvailableTenants(artifact.getAttribute(APIConstants.API_OVERVIEW_SUBSCRIPTION_AVAILABLE_TENANTS));

            api.setAsDefaultVersion(Boolean.parseBoolean(artifact.getAttribute(APIConstants.API_OVERVIEW_IS_DEFAULT_VERSION)));
            api.setImplementation(artifact.getAttribute(APIConstants.PROTOTYPE_OVERVIEW_IMPLEMENTATION));
            api.setTechnicalOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_TEC_OWNER));
            api.setTechnicalOwnerEmail(artifact.getAttribute(APIConstants.API_OVERVIEW_TEC_OWNER_EMAIL));
            api.setBusinessOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_BUSS_OWNER));
            api.setBusinessOwnerEmail(artifact.getAttribute(APIConstants.API_OVERVIEW_BUSS_OWNER_EMAIL));

            ArrayList<URITemplate> urlPatternsList;
            urlPatternsList = ApiMgtDAO.getInstance().getAllURITemplates(api.getContext(), api.getId().getVersion());
            Set<URITemplate> uriTemplates = new HashSet<URITemplate>(urlPatternsList);

            for (URITemplate uriTemplate : uriTemplates) {
                uriTemplate.setResourceURI(api.getUrl());
                uriTemplate.setResourceSandboxURI(api.getSandboxUrl());

            }
            api.setUriTemplates(uriTemplates);
            String environments = artifact.getAttribute(APIConstants.API_OVERVIEW_ENVIRONMENTS);
            api.setEnvironments(extractEnvironmentsForAPI(environments));
            api.setCorsConfiguration(getCorsConfigurationFromArtifact(artifact));
            api.setAuthorizationHeader(artifact.getAttribute(APIConstants.API_OVERVIEW_AUTHORIZATION_HEADER));
            api.setApiSecurity(artifact.getAttribute(APIConstants.API_OVERVIEW_API_SECURITY));

            //get endpoint config string from artifact, parse it as a json and set the environment list configured with
            //non empty URLs to API object
            try {
                api.setEnvironmentList(extractEnvironmentListForAPI(
                        artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_CONFIG)));
            } catch (ParseException e) {
                String msg = "Failed to parse endpoint config JSON of API: " + apiName + " " + apiVersion;
                log.error(msg, e);
                throw new APIManagementException(msg, e);
            } catch (ClassCastException e) {
                String msg = "Invalid endpoint config JSON found in API: " + apiName + " " + apiVersion;
                log.error(msg, e);
                throw new APIManagementException(msg, e);
            }
        } catch (GovernanceException e) {
            String msg = "Failed to get API from artifact ";
            throw new APIManagementException(msg, e);
        }
        return api;
    }

    /**
     * This method is used to get an API in the Light Weight manner.
     * @param artifact  generic artfact
     * @return this will return an API for the selected artifact.
     * @throws APIManagementException , if invalid json config for the API or Api cannot be retrieved from the artifact
     */
    public static API getLightWeightAPI(GovernanceArtifact artifact)
            throws APIManagementException {

        API api;
        try {
            String providerName = artifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER);
            String apiName = artifact.getAttribute(APIConstants.API_OVERVIEW_NAME);
            String apiVersion = artifact.getAttribute(APIConstants.API_OVERVIEW_VERSION);
            APIIdentifier apiIdentifier = new APIIdentifier(providerName, apiName, apiVersion);
            api = new API(apiIdentifier);
            int apiId = ApiMgtDAO.getInstance().getAPIID(apiIdentifier, null);
            if (apiId == -1) {
                return null;
            }
            //set uuid
            api.setUUID(artifact.getId());
            api.setRating(getAverageRating(apiId));
            api.setThumbnailUrl(artifact.getAttribute(APIConstants.API_OVERVIEW_THUMBNAIL_URL));
            api.setStatus(getLcStateFromArtifact(artifact));
            api.setContext(artifact.getAttribute(APIConstants.API_OVERVIEW_CONTEXT));
            api.setVisibility(artifact.getAttribute(APIConstants.API_OVERVIEW_VISIBILITY));
            api.setVisibleRoles(artifact.getAttribute(APIConstants.API_OVERVIEW_VISIBLE_ROLES));
            api.setVisibleTenants(artifact.getAttribute(APIConstants.API_OVERVIEW_VISIBLE_TENANTS));
            api.setTransports(artifact.getAttribute(APIConstants.API_OVERVIEW_TRANSPORTS));
            api.setInSequence(artifact.getAttribute(APIConstants.API_OVERVIEW_INSEQUENCE));
            api.setOutSequence(artifact.getAttribute(APIConstants.API_OVERVIEW_OUTSEQUENCE));
            api.setFaultSequence(artifact.getAttribute(APIConstants.API_OVERVIEW_FAULTSEQUENCE));
            api.setDescription(artifact.getAttribute(APIConstants.API_OVERVIEW_DESCRIPTION));
            api.setResponseCache(artifact.getAttribute(APIConstants.API_OVERVIEW_RESPONSE_CACHING));
            api.setType(artifact.getAttribute(APIConstants.API_OVERVIEW_TYPE));
            int cacheTimeout = APIConstants.API_RESPONSE_CACHE_TIMEOUT;
            try {
                cacheTimeout = Integer.parseInt(artifact.getAttribute(APIConstants.API_OVERVIEW_CACHE_TIMEOUT));
            } catch (NumberFormatException e) {
                //ignore
            }
            api.setCacheTimeout(cacheTimeout);

            boolean isGlobalThrottlingEnabled = APIUtil.isAdvanceThrottlingEnabled();

            if (isGlobalThrottlingEnabled) {
                String apiLevelTier = ApiMgtDAO.getInstance().getAPILevelTier(apiId);
                api.setApiLevelPolicy(apiLevelTier);

                Set<Tier> availablePolicy = new HashSet<Tier>();
                String[] subscriptionPolicy = ApiMgtDAO.getInstance().getPolicyNames(PolicyConstants.POLICY_LEVEL_SUB,
                        replaceEmailDomainBack(providerName));
                List<String> definedPolicyNames = Arrays.asList(subscriptionPolicy);
                String policies = artifact.getAttribute(APIConstants.API_OVERVIEW_TIER);
                if (policies != null && !"".equals(policies)) {
                    String[] policyNames = policies.split("\\|\\|");
                    for (String policyName : policyNames) {
                        if (definedPolicyNames.contains(policyName) || APIConstants.UNLIMITED_TIER.equals(policyName)) {
                            Tier p = new Tier(policyName);
                            availablePolicy.add(p);
                        } else {
                            log.warn("Unknown policy: " + policyName + " found on API: " + apiName);
                        }
                    }
                }

                api.addAvailableTiers(availablePolicy);
                String tenantDomainName = MultitenantUtils.getTenantDomain(replaceEmailDomainBack(providerName));
                api.setMonetizationCategory(getAPIMonetizationCategory(availablePolicy, tenantDomainName));
            } else {
                //deprecated throttling method
                Set<Tier> availableTier = new HashSet<Tier>();
                String tiers = artifact.getAttribute(APIConstants.API_OVERVIEW_TIER);
                String tenantDomainName = MultitenantUtils.getTenantDomain(replaceEmailDomainBack(providerName));
                if (tiers != null) {
                    String[] tierNames = tiers.split("\\|\\|");
                    for (String tierName : tierNames) {
                        Tier tier = new Tier(tierName);
                        availableTier.add(tier);

                    }

                    api.addAvailableTiers(availableTier);
                    api.setMonetizationCategory(getAPIMonetizationCategory(availableTier, tenantDomainName));
                } else {
                    api.setMonetizationCategory(getAPIMonetizationCategory(availableTier, tenantDomainName));
                }
            }

            api.setRedirectURL(artifact.getAttribute(APIConstants.API_OVERVIEW_REDIRECT_URL));
            api.setApiOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_OWNER));
            api.setAdvertiseOnly(Boolean.parseBoolean(artifact.getAttribute(APIConstants.API_OVERVIEW_ADVERTISE_ONLY)));

            api.setEndpointConfig(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_CONFIG));

            api.setSubscriptionAvailability(artifact.getAttribute(APIConstants.API_OVERVIEW_SUBSCRIPTION_AVAILABILITY));
            api.setSubscriptionAvailableTenants(artifact.getAttribute(
                    APIConstants.API_OVERVIEW_SUBSCRIPTION_AVAILABLE_TENANTS));

            api.setAsDefaultVersion(Boolean.parseBoolean(artifact.getAttribute(
                    APIConstants.API_OVERVIEW_IS_DEFAULT_VERSION)));
            api.setImplementation(artifact.getAttribute(APIConstants.PROTOTYPE_OVERVIEW_IMPLEMENTATION));
            api.setTechnicalOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_TEC_OWNER));
            api.setTechnicalOwnerEmail(artifact.getAttribute(APIConstants.API_OVERVIEW_TEC_OWNER_EMAIL));
            api.setBusinessOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_BUSS_OWNER));
            api.setBusinessOwnerEmail(artifact.getAttribute(APIConstants.API_OVERVIEW_BUSS_OWNER_EMAIL));
            String environments = artifact.getAttribute(APIConstants.API_OVERVIEW_ENVIRONMENTS);
            api.setEnvironments(extractEnvironmentsForAPI(environments));
            api.setCorsConfiguration(getCorsConfigurationFromArtifact(artifact));

            try {
                api.setEnvironmentList(extractEnvironmentListForAPI(
                        artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_CONFIG)));
            } catch (ParseException e) {
                String msg = "Failed to parse endpoint config JSON of API: " + apiName + " " + apiVersion;
                log.error(msg, e);
                throw new APIManagementException(msg, e);
            } catch (ClassCastException e) {
                String msg = "Invalid endpoint config JSON found in API: " + apiName + " " + apiVersion;
                log.error(msg, e);
                throw new APIManagementException(msg, e);
            }

        } catch (GovernanceException e) {
            String msg = "Failed to get API from artifact ";
            throw new APIManagementException(msg, e);
        }
        return api;
    }
    /**
     * This method used to get Provider from provider artifact
     *
     * @param artifact provider artifact
     * @return Provider
     * @throws APIManagementException if failed to get Provider from provider artifact.
     */
    public static Provider getProvider(GenericArtifact artifact) throws APIManagementException {
        Provider provider;
        try {
            provider = new Provider(artifact.getAttribute(APIConstants.PROVIDER_OVERVIEW_NAME));
            provider.setDescription(artifact.getAttribute(APIConstants.PROVIDER_OVERVIEW_DESCRIPTION));
            provider.setEmail(artifact.getAttribute(APIConstants.PROVIDER_OVERVIEW_EMAIL));

        } catch (GovernanceException e) {
            String msg = "Failed to get provider ";
            log.error(msg, e);
            throw new APIManagementException(msg, e);
        }
        return provider;
    }

    /**
     * Returns a list of scopes when passed the Provider Name and Scope Key
     *
     * @param scopeKey
     * @param provider
     * @return
     * @throws APIManagementException
     */
    public static Set<Scope> getScopeByScopeKey(String scopeKey, String provider) throws APIManagementException {
        Set<Scope> scopeSet = null;
        String tenantDomainName = MultitenantUtils.getTenantDomain(replaceEmailDomainBack(provider));
        try {
            int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager()
                    .getTenantId(tenantDomainName);
            scopeSet = ApiMgtDAO.getInstance().getAPIScopesByScopeKey(scopeKey, tenantId);
        } catch (UserStoreException e) {
            String msg = "Error while retrieving Scopes";
            log.error(msg, e);
            handleException(msg);
        }
        return scopeSet;
    }

    /**
     * Create Governance artifact from given attributes
     *
     * @param artifact initial governance artifact
     * @param api      API object with the attributes value
     * @return GenericArtifact
     * @throws org.wso2.carbon.apimgt.api.APIManagementException if failed to create API
     */
    public static GenericArtifact createAPIArtifactContent(GenericArtifact artifact, API api)
            throws APIManagementException {
        try {
            String apiStatus = api.getStatus();
            artifact.setAttribute(APIConstants.API_OVERVIEW_NAME, api.getId().getApiName());
            artifact.setAttribute(APIConstants.API_OVERVIEW_VERSION, api.getId().getVersion());

            artifact.setAttribute(APIConstants.API_OVERVIEW_IS_DEFAULT_VERSION, String.valueOf(api.isDefaultVersion()));

            artifact.setAttribute(APIConstants.API_OVERVIEW_CONTEXT, api.getContext());
            artifact.setAttribute(APIConstants.API_OVERVIEW_PROVIDER, api.getId().getProviderName());
            artifact.setAttribute(APIConstants.API_OVERVIEW_DESCRIPTION, api.getDescription());
            artifact.setAttribute(APIConstants.API_OVERVIEW_WSDL, api.getWsdlUrl());
            artifact.setAttribute(APIConstants.API_OVERVIEW_WADL, api.getWadlUrl());
            artifact.setAttribute(APIConstants.API_OVERVIEW_THUMBNAIL_URL, api.getThumbnailUrl());
            artifact.setAttribute(APIConstants.API_OVERVIEW_STATUS, apiStatus);
            artifact.setAttribute(APIConstants.API_OVERVIEW_TEC_OWNER, api.getTechnicalOwner());
            artifact.setAttribute(APIConstants.API_OVERVIEW_TEC_OWNER_EMAIL, api.getTechnicalOwnerEmail());
            artifact.setAttribute(APIConstants.API_OVERVIEW_BUSS_OWNER, api.getBusinessOwner());
            artifact.setAttribute(APIConstants.API_OVERVIEW_BUSS_OWNER_EMAIL, api.getBusinessOwnerEmail());
            artifact.setAttribute(APIConstants.API_OVERVIEW_VISIBILITY, api.getVisibility());
            artifact.setAttribute(APIConstants.API_OVERVIEW_VISIBLE_ROLES, api.getVisibleRoles());
            artifact.setAttribute(APIConstants.API_OVERVIEW_VISIBLE_TENANTS, api.getVisibleTenants());
            artifact.setAttribute(APIConstants.API_OVERVIEW_ENDPOINT_SECURED, Boolean.toString(api.isEndpointSecured()));
            artifact.setAttribute(APIConstants.API_OVERVIEW_ENDPOINT_AUTH_DIGEST, Boolean.toString(api.isEndpointAuthDigest()));
            artifact.setAttribute(APIConstants.API_OVERVIEW_ENDPOINT_USERNAME, api.getEndpointUTUsername());
            artifact.setAttribute(APIConstants.API_OVERVIEW_ENDPOINT_PASSWORD, api.getEndpointUTPassword());
            artifact.setAttribute(APIConstants.API_OVERVIEW_TRANSPORTS, api.getTransports());
            artifact.setAttribute(APIConstants.API_OVERVIEW_INSEQUENCE, api.getInSequence());
            artifact.setAttribute(APIConstants.API_OVERVIEW_OUTSEQUENCE, api.getOutSequence());
            artifact.setAttribute(APIConstants.API_OVERVIEW_FAULTSEQUENCE, api.getFaultSequence());
            artifact.setAttribute(APIConstants.API_OVERVIEW_RESPONSE_CACHING, api.getResponseCache());
            artifact.setAttribute(APIConstants.API_OVERVIEW_CACHE_TIMEOUT, Integer.toString(api.getCacheTimeout()));

            artifact.setAttribute(APIConstants.API_OVERVIEW_REDIRECT_URL, api.getRedirectURL());
            artifact.setAttribute(APIConstants.API_OVERVIEW_OWNER, api.getApiOwner());
            artifact.setAttribute(APIConstants.API_OVERVIEW_ADVERTISE_ONLY, Boolean.toString(api.isAdvertiseOnly()));

            artifact.setAttribute(APIConstants.API_OVERVIEW_ENDPOINT_CONFIG, api.getEndpointConfig());

            artifact.setAttribute(APIConstants.API_OVERVIEW_SUBSCRIPTION_AVAILABILITY, api.getSubscriptionAvailability());
            artifact.setAttribute(APIConstants.API_OVERVIEW_SUBSCRIPTION_AVAILABLE_TENANTS, api.getSubscriptionAvailableTenants());

            artifact.setAttribute(APIConstants.PROTOTYPE_OVERVIEW_IMPLEMENTATION, api.getImplementation());

            artifact.setAttribute(APIConstants.API_PRODUCTION_THROTTLE_MAXTPS, api.getProductionMaxTps());
            artifact.setAttribute(APIConstants.API_SANDBOX_THROTTLE_MAXTPS, api.getSandboxMaxTps());
            artifact.setAttribute(APIConstants.API_OVERVIEW_AUTHORIZATION_HEADER, api.getAuthorizationHeader());
            artifact.setAttribute(APIConstants.API_OVERVIEW_API_SECURITY, api.getApiSecurity());

            //Validate if the API has an unsupported context before setting it in the artifact
            String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
            if (APIConstants.SUPER_TENANT_DOMAIN.equals(tenantDomain)) {
                String invalidContext = File.separator + APIConstants.VERSION_PLACEHOLDER;
                if (invalidContext.equals(api.getContextTemplate())) {
                    throw new APIManagementException(
                            "API : " + api.getId() + " has an unsupported context : " + api.getContextTemplate());
                }
            } else {
                String invalidContext =
                        APIConstants.TENANT_PREFIX + tenantDomain + File.separator + APIConstants.VERSION_PLACEHOLDER;
                if (invalidContext.equals(api.getContextTemplate())) {
                    throw new APIManagementException(
                            "API : " + api.getId() + " has an unsupported context : " + api.getContextTemplate());
                }
            }
            // This is to support the pluggable version strategy.
            artifact.setAttribute(APIConstants.API_OVERVIEW_CONTEXT_TEMPLATE, api.getContextTemplate());
            artifact.setAttribute(APIConstants.API_OVERVIEW_VERSION_TYPE, "context");
            artifact.setAttribute(APIConstants.API_OVERVIEW_TYPE, api.getType());

            StringBuilder policyBuilder = new StringBuilder();
            for (Tier tier : api.getAvailableTiers()) {
                policyBuilder.append(tier.getName());
                policyBuilder.append("||");
            }

            String policies = policyBuilder.toString();

            if (!"".equals(policies)) {
                policies = policies.substring(0, policies.length() - 2);
                artifact.setAttribute(APIConstants.API_OVERVIEW_TIER, policies);
            }

            StringBuilder tiersBuilder = new StringBuilder();
            for (Tier tier : api.getAvailableTiers()) {
                tiersBuilder.append(tier.getName());
                tiersBuilder.append("||");
            }

            String tiers = tiersBuilder.toString();

            if (!"".equals(tiers)) {
                tiers = tiers.substring(0, tiers.length() - 2);
                artifact.setAttribute(APIConstants.API_OVERVIEW_TIER, tiers);
            }

            if (APIConstants.PUBLISHED.equals(apiStatus)) {
                artifact.setAttribute(APIConstants.API_OVERVIEW_IS_LATEST, "true");
            }
            String[] keys = artifact.getAttributeKeys();
            for (String key : keys) {
                if (key.contains("URITemplate")) {
                    artifact.removeAttribute(key);
                }
            }

            Set<URITemplate> uriTemplateSet = api.getUriTemplates();
            int i = 0;
            for (URITemplate uriTemplate : uriTemplateSet) {
                artifact.addAttribute(APIConstants.API_URI_PATTERN + i, uriTemplate.getUriTemplate());
                artifact.addAttribute(APIConstants.API_URI_HTTP_METHOD + i, uriTemplate.getHTTPVerb());
                artifact.addAttribute(APIConstants.API_URI_AUTH_TYPE + i, uriTemplate.getAuthType());

                i++;

            }
            artifact.setAttribute(APIConstants.API_OVERVIEW_ENVIRONMENTS, writeEnvironmentsToArtifact(api));

            artifact.setAttribute(APIConstants.API_OVERVIEW_CORS_CONFIGURATION,
                    APIUtil.getCorsConfigurationJsonFromDto(api.getCorsConfiguration()));

            //attaching micro-gateway labels to the API
            attachLabelsToAPIArtifact(artifact, api, tenantDomain);

            //set monetization status (i.e - enabled or disabled)
            artifact.setAttribute(APIConstants.API_MONETIZATION_STATUS, Boolean.toString(api.getMonetizationStatus()));
            //set additional monetization data
            if (api.getMonetizationProperties() != null) {
                artifact.setAttribute(APIConstants.API_MONETIZATION_PROPERTIES,
                        api.getMonetizationProperties().toJSONString());
            }

            String apiSecurity = artifact.getAttribute(APIConstants.API_OVERVIEW_API_SECURITY);
            if (apiSecurity != null && !apiSecurity.contains(APIConstants.DEFAULT_API_SECURITY_OAUTH2)) {
                artifact.setAttribute(APIConstants.API_OVERVIEW_TIER, "");
            }
        } catch (GovernanceException e) {
            String msg = "Failed to create API for : " + api.getId().getApiName();
            log.error(msg, e);
            throw new APIManagementException(msg, e);
        }
        return artifact;
    }

    /**
     * This method is used to attach micro-gateway labels to the given API
     *
     * @param artifact genereic artifact
     * @param api API
     * @param tenantDomain domain name of the tenant
     * @throws APIManagementException if failed to attach micro-gateway labels
     */
    public static void attachLabelsToAPIArtifact(GenericArtifact artifact, API api, String tenantDomain)
            throws APIManagementException {

        //get all labels in the tenant
        List<Label> gatewayLabelList = APIUtil.getAllLabels(tenantDomain);
        //validation is performed here to cover all actions related to API artifact updates
        if (!gatewayLabelList.isEmpty()) {
            //put available gateway labels to a list for validation purpose
            List<String> availableGatewayLabelListNames = new ArrayList<>();
            for (Label x : gatewayLabelList) {
                availableGatewayLabelListNames.add(x.getName());
            }
            try {
                //clear all the existing labels first
                artifact.removeAttribute(APIConstants.API_LABELS_GATEWAY_LABELS);
                //if there are labels attached to the API object, add them to the artifact
                if (api.getGatewayLabels() != null) {
                    //validate and add each label to the artifact
                    List<Label> candidateLabelsList = api.getGatewayLabels();
                    for (Label label : candidateLabelsList) {
                        String candidateLabel = label.getName();
                        //validation step, add the label only if it exists in the available gateway labels
                        if (availableGatewayLabelListNames.contains(candidateLabel)) {
                            artifact.addAttribute(APIConstants.API_LABELS_GATEWAY_LABELS, candidateLabel);
                        } else {
                            log.warn("Label name : " + candidateLabel + " does not exist in the tenant : " +
                                    tenantDomain + ", hence skipping it.");
                        }
                    }
                }
            } catch (GovernanceException e) {
                String msg = "Failed to add labels for API : " + api.getId().getApiName();
                log.error(msg, e);
                throw new APIManagementException(msg, e);
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("No predefined labels in the tenant : " + tenantDomain + " . Skipped adding all labels");
            }
        }
    }

    /**
     * Create the Documentation from artifact
     *
     * @param artifact Documentation artifact
     * @return Documentation
     * @throws APIManagementException if failed to create Documentation from artifact
     */
    public static Documentation getDocumentation(GenericArtifact artifact) throws APIManagementException {

        Documentation documentation;

        try {
            DocumentationType type;
            String docType = artifact.getAttribute(APIConstants.DOC_TYPE);

            if (docType.equalsIgnoreCase(DocumentationType.HOWTO.getType())) {
                type = DocumentationType.HOWTO;
            } else if (docType.equalsIgnoreCase(DocumentationType.PUBLIC_FORUM.getType())) {
                type = DocumentationType.PUBLIC_FORUM;
            } else if (docType.equalsIgnoreCase(DocumentationType.SUPPORT_FORUM.getType())) {
                type = DocumentationType.SUPPORT_FORUM;
            } else if (docType.equalsIgnoreCase(DocumentationType.API_MESSAGE_FORMAT.getType())) {
                type = DocumentationType.API_MESSAGE_FORMAT;
            } else if (docType.equalsIgnoreCase(DocumentationType.SAMPLES.getType())) {
                type = DocumentationType.SAMPLES;
            } else {
                type = DocumentationType.OTHER;
            }
            documentation = new Documentation(type, artifact.getAttribute(APIConstants.DOC_NAME));
            documentation.setId(artifact.getId());
            documentation.setSummary(artifact.getAttribute(APIConstants.DOC_SUMMARY));
            String visibilityAttr = artifact.getAttribute(APIConstants.DOC_VISIBILITY);
            Documentation.DocumentVisibility documentVisibility = Documentation.DocumentVisibility.API_LEVEL;

            if (visibilityAttr != null) {
                if (visibilityAttr.equals(Documentation.DocumentVisibility.API_LEVEL.name())) {
                    documentVisibility = Documentation.DocumentVisibility.API_LEVEL;
                } else if (visibilityAttr.equals(Documentation.DocumentVisibility.PRIVATE.name())) {
                    documentVisibility = Documentation.DocumentVisibility.PRIVATE;
                } else if (visibilityAttr.equals(Documentation.DocumentVisibility.OWNER_ONLY.name())) {
                    documentVisibility = Documentation.DocumentVisibility.OWNER_ONLY;
                }
            }
            documentation.setVisibility(documentVisibility);

            Documentation.DocumentSourceType docSourceType = Documentation.DocumentSourceType.INLINE;
            String artifactAttribute = artifact.getAttribute(APIConstants.DOC_SOURCE_TYPE);

            if (Documentation.DocumentSourceType.URL.name().equals(artifactAttribute)) {
                docSourceType = Documentation.DocumentSourceType.URL;
                documentation.setSourceUrl(artifact.getAttribute(APIConstants.DOC_SOURCE_URL));
            } else if (Documentation.DocumentSourceType.FILE.name().equals(artifactAttribute)) {
                docSourceType = Documentation.DocumentSourceType.FILE;
                documentation.setFilePath(prependWebContextRoot(artifact.getAttribute(APIConstants.DOC_FILE_PATH)));
            } else if (Documentation.DocumentSourceType.MARKDOWN.name().equals(artifactAttribute)) {
                docSourceType = Documentation.DocumentSourceType.MARKDOWN;
            }
            documentation.setSourceType(docSourceType);
            if (documentation.getType() == DocumentationType.OTHER) {
                documentation.setOtherTypeName(artifact.getAttribute(APIConstants.DOC_OTHER_TYPE_NAME));
            }

        } catch (GovernanceException e) {
            throw new APIManagementException("Failed to get documentation from artifact", e);
        }
        return documentation;
    }

    /**
     * Create the Documentation from artifact
     *
     * @param artifact Documentation artifact
     * @return Documentation
     * @throws APIManagementException if failed to create Documentation from artifact
     */
    public static Documentation getDocumentation(GenericArtifact artifact, String docCreatorName)
            throws APIManagementException {

        Documentation documentation;

        try {
            DocumentationType type;
            String docType = artifact.getAttribute(APIConstants.DOC_TYPE);

            if (docType.equalsIgnoreCase(DocumentationType.HOWTO.getType())) {
                type = DocumentationType.HOWTO;
            } else if (docType.equalsIgnoreCase(DocumentationType.PUBLIC_FORUM.getType())) {
                type = DocumentationType.PUBLIC_FORUM;
            } else if (docType.equalsIgnoreCase(DocumentationType.SUPPORT_FORUM.getType())) {
                type = DocumentationType.SUPPORT_FORUM;
            } else if (docType.equalsIgnoreCase(DocumentationType.API_MESSAGE_FORMAT.getType())) {
                type = DocumentationType.API_MESSAGE_FORMAT;
            } else if (docType.equalsIgnoreCase(DocumentationType.SAMPLES.getType())) {
                type = DocumentationType.SAMPLES;
            } else {
                type = DocumentationType.OTHER;
            }
            documentation = new Documentation(type, artifact.getAttribute(APIConstants.DOC_NAME));
            documentation.setId(artifact.getId());
            documentation.setSummary(artifact.getAttribute(APIConstants.DOC_SUMMARY));

            String visibilityAttr = artifact.getAttribute(APIConstants.DOC_VISIBILITY);
            Documentation.DocumentVisibility documentVisibility = Documentation.DocumentVisibility.API_LEVEL;
            if (visibilityAttr != null) {
                if (visibilityAttr.equals(Documentation.DocumentVisibility.API_LEVEL.name())) {
                    documentVisibility = Documentation.DocumentVisibility.API_LEVEL;
                } else if (visibilityAttr.equals(Documentation.DocumentVisibility.PRIVATE.name())) {
                    documentVisibility = Documentation.DocumentVisibility.PRIVATE;
                } else if (visibilityAttr.equals(Documentation.DocumentVisibility.OWNER_ONLY.name())) {
                    documentVisibility = Documentation.DocumentVisibility.OWNER_ONLY;
                }
            }
            documentation.setVisibility(documentVisibility);

            Documentation.DocumentSourceType docSourceType = Documentation.DocumentSourceType.INLINE;
            String artifactAttribute = artifact.getAttribute(APIConstants.DOC_SOURCE_TYPE);

            if (artifactAttribute.equals(Documentation.DocumentSourceType.MARKDOWN.name())) {
                docSourceType = Documentation.DocumentSourceType.MARKDOWN;
            } else if (artifactAttribute.equals(Documentation.DocumentSourceType.URL.name())) {
                docSourceType = Documentation.DocumentSourceType.URL;
            } else if (artifactAttribute.equals(Documentation.DocumentSourceType.FILE.name())) {
                docSourceType = Documentation.DocumentSourceType.FILE;
            }

            documentation.setSourceType(docSourceType);
            if ("URL".equals(artifact.getAttribute(APIConstants.DOC_SOURCE_TYPE))) {
                documentation.setSourceUrl(artifact.getAttribute(APIConstants.DOC_SOURCE_URL));
            }

            if (docSourceType == Documentation.DocumentSourceType.FILE) {
                String filePath = prependTenantPrefix(artifact.getAttribute(APIConstants.DOC_FILE_PATH), docCreatorName);
                documentation.setFilePath(prependWebContextRoot(filePath));
            }

            if (documentation.getType() == DocumentationType.OTHER) {
                documentation.setOtherTypeName(artifact.getAttribute(APIConstants.DOC_OTHER_TYPE_NAME));
            }

        } catch (GovernanceException e) {
            throw new APIManagementException("Failed to get documentation from artifact: " + e);
        }
        return documentation;
    }

    public static APIStatus getApiStatus(String status) throws APIManagementException {
        APIStatus apiStatus = null;
        for (APIStatus aStatus : APIStatus.values()) {
            if (aStatus.getStatus().equalsIgnoreCase(status)) {
                apiStatus = aStatus;
            }
        }
        return apiStatus;
    }

    public static String getLcStateFromArtifact(GovernanceArtifact artifact) throws GovernanceException {
        String state = (artifact.getLifecycleState() != null) ?
                artifact.getLifecycleState() :
                artifact.getAttribute(APIConstants.API_OVERVIEW_STATUS);
        return (state != null) ? state.toUpperCase() : null;
    }


    /**
     * Prepends the Tenant Prefix to a registry path. ex: /t/test1.com
     *
     * @param postfixUrl path to be prepended.
     * @return Path prepended with he Tenant domain prefix.
     */
    public static String prependTenantPrefix(String postfixUrl, String username) {
        String tenantDomain = MultitenantUtils.getTenantDomain(replaceEmailDomainBack(username));
        if (!(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain))) {
            String tenantPrefix = "/t/";
            postfixUrl = tenantPrefix + tenantDomain + postfixUrl;
        }

        return postfixUrl;
    }

    /**
     * Prepends the webcontextroot to a registry path.
     *
     * @param postfixUrl path to be prepended.
     * @return Path prepended with he WebContext root.
     */
    public static String prependWebContextRoot(String postfixUrl) {
        String webContext = CarbonUtils.getServerConfiguration().getFirstProperty("WebContextRoot");
        if (webContext != null && !"/".equals(webContext)) {
            postfixUrl = webContext + postfixUrl;
        }
        return postfixUrl;
    }

    /**
     * Utility method for creating storage path for an icon.
     *
     * @param identifier APIIdentifier
     * @return Icon storage path.
     */
    public static String getIconPath(APIIdentifier identifier) {
        String artifactPath = APIConstants.API_IMAGE_LOCATION + RegistryConstants.PATH_SEPARATOR +
                identifier.getProviderName() + RegistryConstants.PATH_SEPARATOR +
                identifier.getApiName() + RegistryConstants.PATH_SEPARATOR + identifier.getVersion();
        return artifactPath + RegistryConstants.PATH_SEPARATOR + APIConstants.API_ICON_IMAGE;
    }

    /**
     * Utility method for get registry path for wsdl archive.
     *
     * @param identifier APIIdentifier
     * @return wsdl archive path
     */
    public static String getWsdlArchivePath(APIIdentifier identifier) {
        return APIConstants.API_WSDL_RESOURCE_LOCATION + APIConstants.API_WSDL_ARCHIVE_LOCATION +
                identifier.getProviderName() + APIConstants.WSDL_PROVIDER_SEPERATOR + identifier.getApiName() +
                identifier.getVersion() + APIConstants.ZIP_FILE_EXTENSION;
    }

    /**
     * Utility method to generate the path for a file.
     *
     * @param identifier APIIdentifier
     * @return Generated path.
     * @fileName File name.
     */
    public static String getDocumentationFilePath(APIIdentifier identifier, String fileName) {
        return APIUtil.getAPIDocPath(identifier) + APIConstants.DOCUMENT_FILE_DIR +
                RegistryConstants.PATH_SEPARATOR + fileName;
    }

    public static String getOpenAPIDefinitionFilePath(String apiName, String apiVersion, String apiProvider) {
        return APIConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR + apiProvider + RegistryConstants.PATH_SEPARATOR +
                apiName + RegistryConstants.PATH_SEPARATOR + apiVersion + RegistryConstants.PATH_SEPARATOR;
    }

    public static String getWSDLDefinitionFilePath(String apiName, String apiVersion, String apiProvider) {
        return APIConstants.API_WSDL_RESOURCE_LOCATION + apiProvider + "--" + apiName + apiVersion + ".wsdl";
    }

    /**
     * Utility method to get api path from APIIdentifier
     *
     * @param identifier APIIdentifier
     * @return API path
     */
    public static String getAPIPath(APIIdentifier identifier) {
        return APIConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR +
                identifier.getProviderName() + RegistryConstants.PATH_SEPARATOR +
                identifier.getApiName() + RegistryConstants.PATH_SEPARATOR +
                identifier.getVersion() + APIConstants.API_RESOURCE_NAME;
    }

    /**
     * Utility method to get api identifier from api path.
     *
     * @param apiPath Path of the API in registry
     * @return relevant API Identifier
     */
    public static APIIdentifier getAPIIdentifier(String apiPath) {
        int length = (APIConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR).length();
        if (!apiPath.contains(APIConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR)) {
            length = (APIConstants.API_IMAGE_LOCATION + RegistryConstants.PATH_SEPARATOR).length();
        }
        if (length <= 0) {
            length = (APIConstants.API_DOC_LOCATION + RegistryConstants.PATH_SEPARATOR).length();
        }
        String relativePath = apiPath.substring(length);
        String[] values = relativePath.split(RegistryConstants.PATH_SEPARATOR);
        if (values.length > 3) {
            return new APIIdentifier(values[0], values[1], values[2]);
        }
        return null;
    }

    /**
     * Utility method to get API provider path
     *
     * @param identifier APIIdentifier
     * @return API provider path
     */
    public static String getAPIProviderPath(APIIdentifier identifier) {
        return APIConstants.API_LOCATION + RegistryConstants.PATH_SEPARATOR + identifier.getProviderName();
    }

    /**
     * Utility method to get documentation path
     *
     * @param apiId APIIdentifier
     * @return Doc path
     */
    public static String getAPIDocPath(APIIdentifier apiId) {
        return APIConstants.API_LOCATION + RegistryConstants.PATH_SEPARATOR +
                apiId.getProviderName() + RegistryConstants.PATH_SEPARATOR +
                apiId.getApiName() + RegistryConstants.PATH_SEPARATOR +
                apiId.getVersion() + RegistryConstants.PATH_SEPARATOR +
                APIConstants.DOC_DIR + RegistryConstants.PATH_SEPARATOR;
    }

    /**
     * Utility method to get documentation content file path
     *
     * @param apiId             APIIdentifier
     * @param documentationName String
     * @return Doc content path
     */
    public static String getAPIDocContentPath(APIIdentifier apiId, String documentationName) {
        return getAPIDocPath(apiId) + RegistryConstants.PATH_SEPARATOR + documentationName;
    }

    /**
     * This utility method used to create documentation artifact content
     *
     * @param artifact      GovernanceArtifact
     * @param apiId         APIIdentifier
     * @param documentation Documentation
     * @return GenericArtifact
     * @throws APIManagementException if failed to get GovernanceArtifact from Documentation
     */
    public static GenericArtifact createDocArtifactContent(GenericArtifact artifact, APIIdentifier apiId,
                                                           Documentation documentation) throws APIManagementException {
        try {
            artifact.setAttribute(APIConstants.DOC_NAME, documentation.getName());
            artifact.setAttribute(APIConstants.DOC_SUMMARY, documentation.getSummary());
            artifact.setAttribute(APIConstants.DOC_TYPE, documentation.getType().getType());
            artifact.setAttribute(APIConstants.DOC_VISIBILITY, documentation.getVisibility().name());

            Documentation.DocumentSourceType sourceType = documentation.getSourceType();

            switch (sourceType) {
                case INLINE:
                    sourceType = Documentation.DocumentSourceType.INLINE;
                    break;
                case MARKDOWN:
                    sourceType = Documentation.DocumentSourceType.MARKDOWN;
                    break;
                case URL:
                    sourceType = Documentation.DocumentSourceType.URL;
                    break;
                case FILE: {
                    sourceType = Documentation.DocumentSourceType.FILE;
                }
                break;
                default:
                    throw new APIManagementException("Unknown sourceType " + sourceType + " provided for documentation");
            }
            //Documentation Source URL is a required field in the documentation.rxt for migrated setups
            //Therefore setting a default value if it is not set. 
            if (documentation.getSourceUrl() == null) {
                documentation.setSourceUrl(" ");
            }
            artifact.setAttribute(APIConstants.DOC_SOURCE_TYPE, sourceType.name());
            artifact.setAttribute(APIConstants.DOC_SOURCE_URL, documentation.getSourceUrl());
            artifact.setAttribute(APIConstants.DOC_FILE_PATH, documentation.getFilePath());
            artifact.setAttribute(APIConstants.DOC_OTHER_TYPE_NAME, documentation.getOtherTypeName());
            String basePath = apiId.getProviderName() + RegistryConstants.PATH_SEPARATOR +
                    apiId.getApiName() + RegistryConstants.PATH_SEPARATOR + apiId.getVersion();
            artifact.setAttribute(APIConstants.DOC_API_BASE_PATH, basePath);
        } catch (GovernanceException e) {
            String msg = "Failed to create doc artifact content from :" + documentation.getName();
            log.error(msg, e);
            throw new APIManagementException(msg, e);
        }
        return artifact;
    }

    /**
     * this method used to initialized the ArtifactManager
     *
     * @param registry Registry
     * @param key      , key name of the key
     * @return GenericArtifactManager
     * @throws APIManagementException if failed to initialized GenericArtifactManager
     */
    public static GenericArtifactManager getArtifactManager(Registry registry, String key) throws APIManagementException {
        GenericArtifactManager artifactManager = null;

        try {
            GovernanceUtils.loadGovernanceArtifacts((UserRegistry) registry);
            if (GovernanceUtils.findGovernanceArtifactConfiguration(key, registry) != null) {
                artifactManager = new GenericArtifactManager(registry, key);
            } else {
                log.warn("Couldn't find GovernanceArtifactConfiguration of RXT: " + key +
                        ". Tenant id set in registry : " + ((UserRegistry) registry).getTenantId() +
                        ", Tenant domain set in PrivilegedCarbonContext: " +
                        PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId());
            }
        } catch (RegistryException e) {
            String msg = "Failed to initialize GenericArtifactManager";
            log.error(msg, e);
            throw new APIManagementException(msg, e);
        }
        return artifactManager;
    }

    public static void handleException(String msg) throws APIManagementException {
        log.error(msg);
        throw new APIManagementException(msg);
    }

    public static void handleException(String msg, Throwable t) throws APIManagementException {
        log.error(msg, t);
        throw new APIManagementException(msg, t);
    }

    public static void handleInternalException(String msg, Throwable t) throws APIMgtInternalException {
        log.error(msg, t);
        throw new APIMgtInternalException(msg, t);
    }

    public static void handleAuthFailureException(String msg) throws APIMgtAuthorizationFailedException {
        log.error(msg);
        throw new APIMgtAuthorizationFailedException(msg);
    }
    
    public static SubscriberKeyMgtClient getKeyManagementClient() throws APIManagementException {

        KeyManagerConfiguration configuration = KeyManagerHolder.getKeyManagerInstance().getKeyManagerConfiguration();
        String serverURL = configuration.getParameter(APIConstants.AUTHSERVER_URL);
        String username = configuration.getParameter(APIConstants.KEY_MANAGER_USERNAME);
        String password = configuration.getParameter(APIConstants.KEY_MANAGER_PASSWORD);

        if (serverURL == null) {
            handleException("API key manager URL unspecified");
        }

        if (username == null || password == null) {
            handleException("Authentication credentials for API key manager unspecified");
        }

        try {
            return new SubscriberKeyMgtClient(serverURL, username, password);
        } catch (Exception e) {
            handleException("Error while initializing the subscriber key management client", e);
            return null;
        }
    }

    public static OAuthAdminClient getOauthAdminClient() throws APIManagementException {

        try {
            return new OAuthAdminClient();
        } catch (Exception e) {
            handleException("Error while initializing the OAuth admin client", e);
            return null;
        }
    }

    public static UserInformationRecoveryClient getUserInformationRecoveryClient() throws APIManagementException {

        try {
            return new UserInformationRecoveryClient();
        } catch (Exception e) {
            handleException("Error while initializing the User information recovery client", e);
            return null;
        }
    }


    public static ApplicationManagementServiceClient getApplicationManagementServiceClient() throws APIManagementException {
        try {
            return new ApplicationManagementServiceClient();
        } catch (Exception e) {
            handleException("Error while initializing the Application Management Service client", e);
            return null;
        }
    }

    /**
     * Method used to create the file name of the wsdl to be stored in the registry
     *
     * @param provider   Name of the provider of the API
     * @param apiName    Name of the API
     * @param apiVersion API Version
     * @return WSDL file name
     */
    public static String createWsdlFileName(String provider, String apiName, String apiVersion) {
        return provider + "--" + apiName + apiVersion + ".wsdl";
    }

    /**
     * Crate an WSDL from given wsdl url. Reset the endpoint details to gateway node
     * *
     *
     * @param registry - Governance Registry space to save the WSDL
     * @param api      -API instance
     * @return Path of the created resource
     * @throws APIManagementException If an error occurs while adding the WSDL
     */

    public static String createWSDL(Registry registry, API api) throws RegistryException, APIManagementException {

        try {
            String wsdlResourcePath =
                    APIConstants.API_WSDL_RESOURCE_LOCATION + createWsdlFileName(api.getId().getProviderName(),
                            api.getId().getApiName(), api.getId().getVersion());

            String absoluteWSDLResourcePath = RegistryUtils
                    .getAbsolutePath(RegistryContext.getBaseInstance(), RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH)
                    + wsdlResourcePath;

            APIMWSDLReader wsdlReader = new APIMWSDLReader(api.getWsdlUrl());
            OMElement wsdlContentEle;
            String wsdRegistryPath;

            String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
            if (org.wso2.carbon.utils.multitenancy.MultitenantConstants.SUPER_TENANT_DOMAIN_NAME
                    .equalsIgnoreCase(tenantDomain)) {
                wsdRegistryPath =
                        RegistryConstants.PATH_SEPARATOR + "registry" + RegistryConstants.PATH_SEPARATOR + "resource"
                                + absoluteWSDLResourcePath;
            } else {
                wsdRegistryPath = "/t/" + tenantDomain + RegistryConstants.PATH_SEPARATOR + "registry"
                        + RegistryConstants.PATH_SEPARATOR + "resource" + absoluteWSDLResourcePath;
            }

            Resource wsdlResource = registry.newResource();
            // isWSDL2Document(api.getWsdlUrl()) method only understands http or file system urls.
            // Hence if this is a registry url, should not go in to the following if block
            if (!api.getWsdlUrl().matches(wsdRegistryPath) && (api.getWsdlUrl().startsWith("http:") || api.getWsdlUrl()
                    .startsWith("https:") || api.getWsdlUrl().startsWith("file:"))) {
                if (isWSDL2Document(api.getWsdlUrl())) {
                    wsdlContentEle = wsdlReader.readAndCleanWsdl2(api);
                    wsdlResource.setContent(wsdlContentEle.toString());
                } else {
                    wsdlContentEle = wsdlReader.readAndCleanWsdl(api);
                    wsdlResource.setContent(wsdlContentEle.toString());
                }

                registry.put(wsdlResourcePath, wsdlResource);
                //set the anonymous role for wsld resource to avoid basicauth security.
                String[] visibleRoles = null;
                if (api.getVisibleRoles() != null) {
                    visibleRoles = api.getVisibleRoles().split(",");
                }
                setResourcePermissions(api.getId().getProviderName(), api.getVisibility(), visibleRoles,
                        wsdlResourcePath);
            } else {
                byte[] wsdl = (byte[]) registry.get(wsdlResourcePath).getContent();
                if (isWSDL2Resource(wsdl)) {
                    wsdlContentEle = wsdlReader.updateWSDL2(wsdl, api);
                    wsdlResource.setContent(wsdlContentEle.toString());
                } else {
                    wsdlContentEle = wsdlReader.updateWSDL(wsdl, api);
                    wsdlResource.setContent(wsdlContentEle.toString());
                }

                registry.put(wsdlResourcePath, wsdlResource);
                //set the anonymous role for wsld resource to avoid basicauth security.
                String[] visibleRoles = null;
                if (api.getVisibleRoles() != null) {
                    visibleRoles = api.getVisibleRoles().split(",");
                }
                setResourcePermissions(api.getId().getProviderName(), api.getVisibility(), visibleRoles,
                        wsdlResourcePath);
            }

            //set the wsdl resource permlink as the wsdlURL.
            api.setWsdlUrl(getRegistryResourceHTTPPermlink(absoluteWSDLResourcePath));

            return wsdlResourcePath;

        } catch (RegistryException e) {
            String msg = "Failed to add WSDL " + api.getWsdlUrl() + " to the registry";
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } catch (APIManagementException e) {
            String msg = "Failed to process the WSDL : " + api.getWsdlUrl();
            log.error(msg, e);
            throw new APIManagementException(msg, e);
        }
    }

    /**
     * Save the provided wsdl archive file to the registry for the api
     *
     * @param registry Governance Registry space to save the WSDL
     * @param api      API instance
     * @return
     * @throws RegistryException
     * @throws APIManagementException
     */
    public static String saveWSDLArchive(Registry registry, API api) throws RegistryException, APIManagementException {
        String wsdlArchiveResourcePath =
                APIConstants.API_WSDL_RESOURCE_LOCATION + APIConstants.API_WSDL_ARCHIVE_LOCATION + api.getId()
                        .getProviderName() + APIConstants.WSDL_PROVIDER_SEPERATOR + api.getId().getApiName() +
                        api.getId().getVersion() + APIConstants.ZIP_FILE_EXTENSION;
        String absoluteWSDLResourcePath = RegistryUtils
                .getAbsolutePath(RegistryContext.getBaseInstance(), RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH)
                + wsdlArchiveResourcePath;
        try {
            if (api.getWsdlArchive() != null) {
                Resource wsdlResource = registry.newResource();
                wsdlResource.setContentStream(api.getWsdlArchive().getContent());
                wsdlResource.setMediaType(api.getWsdlArchive().getContentType());
                registry.put(wsdlArchiveResourcePath, wsdlResource);
                String[] visibleRoles = null;
                if (api.getVisibleRoles() != null) {
                    visibleRoles = api.getVisibleRoles().split(",");
                }
                setResourcePermissions(api.getId().getProviderName(), api.getVisibility(), visibleRoles,
                        wsdlArchiveResourcePath);
                api.setWsdlUrl(getRegistryResourceHTTPPermlink(absoluteWSDLResourcePath));
            }
        } catch (RegistryException e) {
            String msg = "Failed to add WSDL Archive " + api.getWsdlUrl() + " to the registry";
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } catch (APIManagementException e) {
            String msg = "Failed to process the WSDL Archive: " + api.getWsdlUrl();
            log.error(msg, e);
            throw new APIManagementException(msg, e);
        }
        return wsdlArchiveResourcePath;
    }

    public static WSDLArchiveInfo extractAndValidateWSDLArchive(InputStream inputStream) throws APIManagementException {
        String path = System.getProperty(APIConstants.JAVA_IO_TMPDIR) + File.separator
                + APIConstants.WSDL_ARCHIVES_TEMP_FOLDER + File.separator + UUID.randomUUID().toString();
        String archivePath = path + File.separator + APIConstants.WSDL_ARCHIVE_ZIP_FILE;
        String extractedLocation = APIFileUtil
                .extractUploadedArchive(inputStream, APIConstants.API_WSDL_EXTRACTED_DIRECTORY, archivePath, path);
        if (log.isDebugEnabled()) {
            log.debug("Successfully extracted WSDL archive. Location: " + extractedLocation);
        }
        WSDLSOAPOperationExtractor processor = SOAPOperationBindingUtils.getWSDLProcessor(extractedLocation);
        if (!processor.canProcess()) {
            throw new APIManagementException(processor.getClass().getName() + " was unable to process the WSDL");
        }
        return new WSDLArchiveInfo(path, APIConstants.WSDL_ARCHIVE_ZIP_FILE);
    }

    /**
     * Given a URL, this method checks if the underlying document is a WSDL2
     *
     * @param url URL to check
     * @return true if the underlying document is a WSDL2
     * @throws APIManagementException if error occurred while validating the URI
     */
    public static boolean isWSDL2Document(String url) throws APIManagementException {
        APIMWSDLReader wsdlReader = new APIMWSDLReader(url);
        return wsdlReader.isWSDL2BaseURI();
    }

    /**
     * Given a wsdl resource, this method checks if the underlying document is a WSDL2
     *
     * @param wsdl byte array of wsdl definition saved in registry
     * @return true if wsdl2 definition
     * @throws APIManagementException
     */
    private static boolean isWSDL2Resource(byte[] wsdl) throws APIManagementException {
        String wsdl2NameSpace = "http://www.w3.org/ns/wsdl";
        String wsdlContent = new String(wsdl);
        return wsdlContent.indexOf(wsdl2NameSpace) > 0;
    }

    /**
     * Read the GateWay Endpoint from the APIConfiguration. If multiple Gateway
     * environments defined,
     * take only the production node's Endpoint.
     * Else, pick what is available as the gateway node.
     *
     * @return {@link String} - Gateway URL
     */

    public static String getGatewayendpoint(String transports) {

        String gatewayURLs;

        Map<String, Environment> gatewayEnvironments = ServiceReferenceHolder.getInstance()
                .getAPIManagerConfigurationService()
                .getAPIManagerConfiguration()
                .getApiGatewayEnvironments();
        if (gatewayEnvironments.size() > 1) {
            for (Environment environment : gatewayEnvironments.values()) {
                if (APIConstants.GATEWAY_ENV_TYPE_HYBRID.equals(environment.getType())) {
                    gatewayURLs = environment.getApiGatewayEndpoint(); // This might have http,https
                    // pick correct endpoint
                    return APIUtil.extractHTTPSEndpoint(gatewayURLs, transports);
                }
            }
            for (Environment environment : gatewayEnvironments.values()) {
                if (APIConstants.GATEWAY_ENV_TYPE_PRODUCTION.equals(environment.getType())) {
                    gatewayURLs = environment.getApiGatewayEndpoint(); // This might have http,https
                    // pick correct endpoint
                    return APIUtil.extractHTTPSEndpoint(gatewayURLs, transports);
                }
            }
            for (Environment environment : gatewayEnvironments.values()) {
                if (APIConstants.GATEWAY_ENV_TYPE_SANDBOX.equals(environment.getType())) {
                    gatewayURLs = environment.getApiGatewayEndpoint(); // This might have http,https
                    // pick correct endpoint
                    return APIUtil.extractHTTPSEndpoint(gatewayURLs, transports);
                }
            }
        } else {
            gatewayURLs = ((Environment) gatewayEnvironments.values().toArray()[0]).getApiGatewayEndpoint();
            return extractHTTPSEndpoint(gatewayURLs, transports);
        }

        return null;
    }

    /**
     * Read the GateWay Endpoint from the APIConfiguration. If multiple Gateway
     * environments defined, get the gateway endpoint according to the environment type
     *
     * @param transports      transports allowed for gateway endpoint
     * @param environmentName gateway environment name
     * @param environmentType gateway environment type
     * @return Gateway URL
     */
    public static String getGatewayEndpoint(String transports, String environmentName, String environmentType)
            throws APIManagementException {
        String gatewayURLs;
        String gatewayEndpoint = "";

        Map<String, Environment> gatewayEnvironments = ServiceReferenceHolder.getInstance()
                .getAPIManagerConfigurationService().getAPIManagerConfiguration().getApiGatewayEnvironments();
        Environment environment = gatewayEnvironments.get(environmentName);
        if(environment.getType().equals(environmentType)) {
            gatewayURLs = environment.getApiGatewayEndpoint();
            gatewayEndpoint = extractHTTPSEndpoint(gatewayURLs, transports);
            if (log.isDebugEnabled()) {
                log.debug("Gateway urls are: " + gatewayURLs + " and the url with the correct transport is: "
                        + gatewayEndpoint);
            }
        } else {
            handleException("Environment type mismatch for environment: " + environmentName +
                    " for the environment types: " + environment.getType() + " and " + environmentType);
        }
        return gatewayEndpoint;
    }

    /**
     * Gateway endpoint  has HTTP and HTTPS endpoints.
     * If both are defined pick HTTPS only. Else, pick whatever available.
     * eg: <GatewayEndpoint>http://${carbon.local.ip}:${http.nio.port},
     * https://${carbon.local.ip}:${https.nio.port}</GatewayEndpoint>
     *
     * @param gatewayURLs - String contains comma separated gateway urls.
     * @return {@link String} - Returns HTTPS gateway endpoint
     */

    private static String extractHTTPSEndpoint(String gatewayURLs, String transports) {
        String gatewayURL;
        String gatewayHTTPURL = null;
        String gatewayHTTPSURL = null;
        boolean httpsEnabled = false;
        String[] gatewayURLsArray = gatewayURLs.split(",");
        String[] transportsArray = transports.split(",");

        for (String transport : transportsArray) {
            if (transport.startsWith(APIConstants.HTTPS_PROTOCOL)) {
                httpsEnabled = true;
            }
        }
        if (gatewayURLsArray.length > 1) {
            for (String url : gatewayURLsArray) {
                if (url.startsWith("https:")) {
                    gatewayHTTPSURL = url;
                } else {
                    if (!url.startsWith("ws:")) {
                        gatewayHTTPURL = url;
                    }
                }
            }

            if (httpsEnabled) {
                gatewayURL = gatewayHTTPSURL;
            } else {
                gatewayURL = gatewayHTTPURL;
            }
        } else {
            gatewayURL = gatewayURLs;
        }
        return gatewayURL;
    }

    /**
     * Create an Endpoint
     *
     * @param endpointUrl Endpoint url
     * @param registry    Registry space to save the endpoint
     * @return Path of the created resource
     * @throws APIManagementException If an error occurs while adding the endpoint
     */
    public static String createEndpoint(String endpointUrl, Registry registry) throws APIManagementException {
        try {
            EndpointManager endpointManager = new EndpointManager(registry);
            Endpoint endpoint = endpointManager.newEndpoint(endpointUrl);
            endpointManager.addEndpoint(endpoint);
            return GovernanceUtils.getArtifactPath(registry, endpoint.getId());
        } catch (RegistryException e) {
            String msg = "Failed to import endpoint " + endpointUrl + " to registry ";
            log.error(msg, e);
            throw new APIManagementException(msg, e);
        }
    }

    /**
     * Sorts the list of tiers according to the number of requests allowed per minute in each tier in descending order.
     *
     * @param tiers - The list of tiers to be sorted
     * @return - The sorted list.
     */
    public static List<Tier> sortTiers(Set<Tier> tiers) {
        List<Tier> tierList = new ArrayList<Tier>();
        tierList.addAll(tiers);
        Collections.sort(tierList);
        return tierList;
    }

    /**
     * Returns a set of External API Stores as defined in the underlying governance
     * registry.
     *
     * @return a Map of tier names and Tier objects - possibly empty
     * @throws APIManagementException if an error occurs when loading tiers from the registry
     */
    public static Set<APIStore> getExternalStores(int tenantId) throws APIManagementException {
        // First checking if ExternalStores are defined in api-manager.xml
        Set<APIStore> externalAPIStores = ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService()
                .getAPIManagerConfiguration().getExternalAPIStores();
        // If defined, return Store Config provided there.
        if (externalAPIStores != null && !externalAPIStores.isEmpty()) {
            return externalAPIStores;
        }
        // Else Read the config from Tenant's Registry.
        externalAPIStores = new HashSet<APIStore>();
        try {
            UserRegistry registry = ServiceReferenceHolder.getInstance().getRegistryService()
                    .getGovernanceSystemRegistry(tenantId);
            if (registry.resourceExists(APIConstants.EXTERNAL_API_STORES_LOCATION)) {
                Resource resource = registry.get(APIConstants.EXTERNAL_API_STORES_LOCATION);
                String content = new String((byte[]) resource.getContent(), Charset.defaultCharset());
                OMElement element = AXIOMUtil.stringToOM(content);
                Iterator apistoreIterator = element.getChildrenWithLocalName("ExternalAPIStore");

                while (apistoreIterator.hasNext()) {
                    APIStore store = new APIStore();
                    OMElement storeElem = (OMElement) apistoreIterator.next();
                    String type = storeElem.getAttributeValue(new QName(APIConstants.EXTERNAL_API_STORE_TYPE));
                    String className =
                            storeElem.getAttributeValue(new QName(APIConstants.EXTERNAL_API_STORE_CLASS_NAME));
                    store.setPublisher((APIPublisher) getClassForName(className).newInstance());
                    store.setType(type); //Set Store type [eg:wso2]
                    String name = storeElem.getAttributeValue(new QName(APIConstants.EXTERNAL_API_STORE_ID));
                    if (name == null) {
                        log.error("The ExternalAPIStore name attribute is not defined in api-manager.xml.");
                    }
                    store.setName(name); //Set store name
                    OMElement configDisplayName = storeElem.getFirstChildWithName
                            (new QName(APIConstants.EXTERNAL_API_STORE_DISPLAY_NAME));
                    String displayName = (configDisplayName != null) ? replaceSystemProperty(
                            configDisplayName.getText()) : name;
                    store.setDisplayName(displayName);//Set store display name
                    store.setEndpoint(replaceSystemProperty(storeElem.getFirstChildWithName(
                            new QName(APIConstants.EXTERNAL_API_STORE_ENDPOINT)).getText()));
                    //Set store endpoint, which is used to publish APIs
                    store.setPublished(false);
                    if (APIConstants.WSO2_API_STORE_TYPE.equals(type)) {
                        OMElement password = storeElem.getFirstChildWithName(new QName(
                                APIConstants.EXTERNAL_API_STORE_PASSWORD));
                        if (password != null) {

                            String value = password.getText();
                            PasswordResolver passwordResolver = PasswordResolverFactory.getInstance();
                            store.setPassword(replaceSystemProperty(passwordResolver.getPassword(value)));
                            store.setUsername(replaceSystemProperty(storeElem.getFirstChildWithName(
                                    new QName(APIConstants.EXTERNAL_API_STORE_USERNAME)).getText()));
                            //Set store login username
                        } else {
                            log.error("The user-credentials of API Publisher is not defined in the <ExternalAPIStore> " +
                                    "config of api-manager.xml.");
                        }
                    }
                    externalAPIStores.add(store);
                }

            }
        } catch (RegistryException e) {
            String msg = "Error while retrieving External Stores Configuration from registry";
            log.error(msg, e);
            throw new APIManagementException(msg, e);
        } catch (XMLStreamException e) {
            String msg = "Malformed XML found in the External Stores Configuration resource";
            log.error(msg, e);
            throw new APIManagementException(msg, e);
        } catch (ClassNotFoundException e) {
            String msg = "One or more classes defined in APIConstants.EXTERNAL_API_STORE_CLASS_NAME cannot be found";
            log.error(msg, e);
            throw new APIManagementException(msg, e);
        } catch (InstantiationException e) {
            String msg = "One or more classes defined in APIConstants.EXTERNAL_API_STORE_CLASS_NAME cannot be load";
            log.error(msg, e);
            throw new APIManagementException(msg, e);
        } catch (IllegalAccessException e) {
            String msg = "One or more classes defined in APIConstants.EXTERNAL_API_STORE_CLASS_NAME cannot be access";
            log.error(msg, e);
            throw new APIManagementException(msg, e);
        }
        return externalAPIStores;
    }


    /**
     * Returns the External API Store Configuration with the given Store Name
     *
     * @param apiStoreName
     * @return
     * @throws APIManagementException
     */
    public static APIStore getExternalAPIStore(String apiStoreName, int tenantId) throws APIManagementException {
        Set<APIStore> externalAPIStoresConfig = APIUtil.getExternalStores(tenantId);
        for (APIStore apiStoreConfig : externalAPIStoresConfig) {
            if (apiStoreConfig.getName().equals(apiStoreName)) {
                return apiStoreConfig;
            }
        }
        return null;
    }

    /**
     * Returns an unfiltered map of API availability tiers as defined in the underlying governance
     * registry.
     *
     * @return Map<String, Tier> an unfiltered Map of tier names and Tier objects - possibly empty
     * @throws APIManagementException if an error occurs when loading tiers from the registry
     */
    public static Map<String, Tier> getAllTiers() throws APIManagementException {
        if (!APIUtil.isAdvanceThrottlingEnabled()) {
            try {
                Registry registry = ServiceReferenceHolder.getInstance().getRegistryService().
                        getGovernanceSystemRegistry();

                return getAllTiers(registry, APIConstants.API_TIER_LOCATION, MultitenantConstants.SUPER_TENANT_ID);
            } catch (RegistryException e) {
                log.error(APIConstants.MSG_TIER_RET_ERROR, e);
                throw new APIManagementException(APIConstants.MSG_TIER_RET_ERROR, e);
            } catch (XMLStreamException e) {
                log.error(APIConstants.MSG_MALFORMED_XML_ERROR, e);
                throw new APIManagementException(APIConstants.MSG_MALFORMED_XML_ERROR, e);
            }
        } else {
            return getTiersFromPolicies(PolicyConstants.POLICY_LEVEL_SUB, MultitenantConstants.SUPER_TENANT_ID);
        }
    }

    /**
     * Returns an unfiltered map of API availability tiers of the tenant as defined in the underlying governance
     * registry.
     *
     * @return Map<String, Tier> an unfiltered Map of tier names and Tier objects - possibly empty
     * @throws APIManagementException if an error occurs when loading tiers from the registry
     */
    public static Map<String, Tier> getAllTiers(int tenantId) throws APIManagementException {
        if (!APIUtil.isAdvanceThrottlingEnabled()) {
            try {
                Registry registry = ServiceReferenceHolder.getInstance().getRegistryService().
                        getGovernanceSystemRegistry(tenantId);

                return getAllTiers(registry, APIConstants.API_TIER_LOCATION, tenantId);
            } catch (RegistryException e) {
                log.error(APIConstants.MSG_TIER_RET_ERROR, e);
                throw new APIManagementException(APIConstants.MSG_TIER_RET_ERROR, e);
            } catch (XMLStreamException e) {
                log.error(APIConstants.MSG_MALFORMED_XML_ERROR, e);
                throw new APIManagementException(APIConstants.MSG_MALFORMED_XML_ERROR, e);
            }
        } else {
            return getTiersFromPolicies(PolicyConstants.POLICY_LEVEL_SUB, tenantId);
        }
    }

    /**
     * Returns a map of API availability tiers as defined in the underlying governance
     * registry.
     *
     * @return a Map of tier names and Tier objects - possibly empty
     * @throws APIManagementException if an error occurs when loading tiers from the registry
     */
    public static Map<String, Tier> getTiers() throws APIManagementException {
        if (!APIUtil.isAdvanceThrottlingEnabled()) {
            try {
                Registry registry = ServiceReferenceHolder.getInstance().getRegistryService().
                        getGovernanceSystemRegistry();
                return getTiers(registry, APIConstants.API_TIER_LOCATION, MultitenantConstants.SUPER_TENANT_ID);
            } catch (RegistryException e) {
                log.error(APIConstants.MSG_TIER_RET_ERROR, e);
                throw new APIManagementException(APIConstants.MSG_TIER_RET_ERROR, e);
            }
        } else {
            return getTiersFromPolicies(PolicyConstants.POLICY_LEVEL_SUB, MultitenantConstants.SUPER_TENANT_ID);
        }
    }


    /**
     * Returns a map of API availability tiers as defined in the underlying governance
     * registry.
     *
     * @return a Map of tier names and Tier objects - possibly empty
     * @throws APIManagementException if an error occurs when loading tiers from the registry
     */
    public static Map<String, Tier> getAdvancedSubsriptionTiers() throws APIManagementException {
        return getAdvancedSubsriptionTiers(MultitenantConstants.SUPER_TENANT_ID);
    }

    /**
     * Returns a map of API subscription tiers of the tenant as defined in database
     * registry.
     *
     * @return a Map of tier names and Tier objects - possibly empty
     * @throws APIManagementException if an error occurs when loading tiers from the registry
     */
    public static Map<String, Tier> getAdvancedSubsriptionTiers(int tenantId) throws APIManagementException {
        return APIUtil.getTiersFromPolicies(PolicyConstants.POLICY_LEVEL_SUB, tenantId);
    }

    /**
     * Returns a map of API availability tiers of the tenant as defined in the underlying governance
     * registry.
     *
     * @return a Map of tier names and Tier objects - possibly empty
     * @throws APIManagementException if an error occurs when loading tiers from the registry
     */
    public static Map<String, Tier> getTiers(int tenantId) throws APIManagementException {
        if (!APIUtil.isAdvanceThrottlingEnabled()) {
            try {
                Registry registry = ServiceReferenceHolder.getInstance().getRegistryService().
                        getGovernanceSystemRegistry(tenantId);
                return getTiers(registry, APIConstants.API_TIER_LOCATION, tenantId);
            } catch (RegistryException e) {
                log.error(APIConstants.MSG_TIER_RET_ERROR, e);
                throw new APIManagementException(APIConstants.MSG_TIER_RET_ERROR, e);
            }
        } else {
            return getTiersFromPolicies(PolicyConstants.POLICY_LEVEL_SUB, tenantId);
        }
    }

    /**
     * Returns a map of API availability tiers of the tenant as defined in the underlying governance
     * registry.
     *
     * @return a Map of tier names and Tier objects - possibly empty
     * @throws APIManagementException if an error occurs when loading tiers from the registry
     */
    public static Map<String, Tier> getTiers(int tierType, String tenantDomain) throws APIManagementException {
        if (!APIUtil.isAdvanceThrottlingEnabled()) {
            boolean isTenantFlowStarted = false;
            try {
                PrivilegedCarbonContext.startTenantFlow();
                isTenantFlowStarted = true;

                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
                int tenantId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();

                Registry registry = ServiceReferenceHolder.getInstance().getRegistryService().
                        getGovernanceSystemRegistry(tenantId);

                if (tierType == APIConstants.TIER_API_TYPE) {
                    return getTiers(registry, APIConstants.API_TIER_LOCATION, tenantId);
                } else if (tierType == APIConstants.TIER_RESOURCE_TYPE) {
                    return getTiers(registry, APIConstants.RES_TIER_LOCATION, tenantId);
                } else if (tierType == APIConstants.TIER_APPLICATION_TYPE) {
                    return getTiers(registry, APIConstants.APP_TIER_LOCATION, tenantId);
                } else {
                    throw new APIManagementException("No such a tier type : " + tierType);
                }
            } catch (RegistryException e) {
                log.error(APIConstants.MSG_TIER_RET_ERROR, e);
                throw new APIManagementException(APIConstants.MSG_TIER_RET_ERROR, e);
            } finally {
                if (isTenantFlowStarted) {
                    PrivilegedCarbonContext.endTenantFlow();
                }
            }
        } else {
            boolean isTenantFlowStarted = false;
            try {
                PrivilegedCarbonContext.startTenantFlow();
                isTenantFlowStarted = true;
                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
                int tenantId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();
                if (tierType == APIConstants.TIER_API_TYPE) {
                    return getTiersFromPolicies(PolicyConstants.POLICY_LEVEL_SUB, tenantId);
                } else if (tierType == APIConstants.TIER_RESOURCE_TYPE) {
                    return getTiersFromPolicies(PolicyConstants.POLICY_LEVEL_API, tenantId);
                } else if (tierType == APIConstants.TIER_APPLICATION_TYPE) {
                    return getTiersFromPolicies(PolicyConstants.POLICY_LEVEL_APP, tenantId);
                } else {
                    throw new APIManagementException("No such a tier type : " + tierType);
                }
            } finally {
                if (isTenantFlowStarted) {
                    PrivilegedCarbonContext.endTenantFlow();
                }
            }
        }
    }


    /**
     * Retrieves unfiltered list of all available tiers from registry.
     * Result will contains all the tiers including unauthenticated tier which is
     * filtered out in   getTiers}
     *
     * @param registry     registry to access tiers config
     * @param tierLocation registry location of tiers config
     * @return Map<String, Tier> containing all available tiers
     * @throws RegistryException      when registry action fails
     * @throws XMLStreamException     when xml parsing fails
     * @throws APIManagementException when fails to retrieve tier attributes
     */
    private static Map<String, Tier> getAllTiers(Registry registry, String tierLocation, int tenantId)
            throws RegistryException, XMLStreamException, APIManagementException {
        // We use a treeMap here to keep the order
        Map<String, Tier> tiers = new TreeMap<String, Tier>();

        if (registry.resourceExists(tierLocation)) {
            Resource resource = registry.get(tierLocation);
            String content = new String((byte[]) resource.getContent(), Charset.defaultCharset());

            OMElement element = AXIOMUtil.stringToOM(content);
            OMElement assertion = element.getFirstChildWithName(APIConstants.ASSERTION_ELEMENT);
            Iterator policies = assertion.getChildrenWithName(APIConstants.POLICY_ELEMENT);

            while (policies.hasNext()) {
                OMElement policy = (OMElement) policies.next();
                OMElement id = policy.getFirstChildWithName(APIConstants.THROTTLE_ID_ELEMENT);

                String tierName = id.getText();

                // Constructing the tier object
                Tier tier = new Tier(tierName);
                tier.setPolicyContent(policy.toString().getBytes(Charset.defaultCharset()));

                if (id.getAttribute(APIConstants.THROTTLE_ID_DISPLAY_NAME_ELEMENT) != null) {
                    tier.setDisplayName(id.getAttributeValue(APIConstants.THROTTLE_ID_DISPLAY_NAME_ELEMENT));
                } else {
                    tier.setDisplayName(tierName);
                }
                String desc;
                try {
                    long requestPerMin = APIDescriptionGenUtil.getAllowedCountPerMinute(policy);
                    tier.setRequestsPerMin(requestPerMin);

                    long requestCount = APIDescriptionGenUtil.getAllowedRequestCount(policy);
                    tier.setRequestCount(requestCount);

                    long unitTime = APIDescriptionGenUtil.getTimeDuration(policy);
                    tier.setUnitTime(unitTime);

                    if (requestPerMin >= 1) {
                        desc = DESCRIPTION.replaceAll("\\[1\\]", Long.toString(requestPerMin));
                    } else {
                        desc = DESCRIPTION;
                    }
                    tier.setDescription(desc);

                } catch (APIManagementException ex) {
                    // If there is any issue in getting the request counts or the time duration, that means this tier
                    // information can not be used for throttling. Hence we log this exception and continue the flow
                    // to the next tier.
                    log.warn("Unable to get the request count/time duration information for : " + tier.getName() + ". "
                            + ex.getMessage());
                    continue;
                }


                // Get all the attributes of the tier.
                Map<String, Object> tierAttributes = APIDescriptionGenUtil.getTierAttributes(policy);
                if (!tierAttributes.isEmpty()) {
                    // The description, billing plan and the stop on quota reach properties are also stored as attributes
                    // of the tier attributes. Hence we extract them from the above attributes map.
                    Iterator<Entry<String, Object>> attributeIterator = tierAttributes.entrySet().iterator();
                    while (attributeIterator.hasNext()) {
                        Entry<String, Object> entry = attributeIterator.next();

                        if (APIConstants.THROTTLE_TIER_DESCRIPTION_ATTRIBUTE.equals(entry.getKey())
                                && entry.getValue() instanceof String) {

                            tier.setDescription((String) entry.getValue());

                            // We remove the attribute from the map
                            attributeIterator.remove();
                            continue;

                        }
                        if (APIConstants.THROTTLE_TIER_PLAN_ATTRIBUTE.equals(entry.getKey())
                                && entry.getValue() instanceof String) {

                            tier.setTierPlan((String) entry.getValue());

                            // We remove the attribute from the map
                            attributeIterator.remove();
                            continue;

                        }
                        if (APIConstants.THROTTLE_TIER_QUOTA_ACTION_ATTRIBUTE.equals(entry.getKey())
                                && entry.getValue() instanceof String) {

                            tier.setStopOnQuotaReached(Boolean.parseBoolean((String) entry.getValue()));

                            // We remove the attribute from the map
                            attributeIterator.remove();
                            // We do not need a continue since this is the last statement.

                        }
                    }
                    tier.setTierAttributes(tierAttributes);
                }
                tiers.put(tierName, tier);
            }
        }

        if (isEnabledUnlimitedTier()) {
            Tier tier = new Tier(APIConstants.UNLIMITED_TIER);
            tier.setDescription(APIConstants.UNLIMITED_TIER_DESC);
            tier.setDisplayName(APIConstants.UNLIMITED_TIER);
            tier.setRequestsPerMin(Long.MAX_VALUE);

            if (isUnlimitedTierPaid(getTenantDomainFromTenantId(tenantId))) {
                tier.setTierPlan(APIConstants.COMMERCIAL_TIER_PLAN);
            } else {
                tier.setTierPlan(APIConstants.BILLING_PLAN_FREE);
            }

            tiers.put(tier.getName(), tier);
        }

        return tiers;
    }

    /**
     * Retrieves filtered list of available tiers from registry. This method will not return Unauthenticated
     * tier in the list. Use  to retrieve all tiers without
     * any filtering.
     *
     * @param registry     registry to access tiers config
     * @param tierLocation registry location of tiers config
     * @return map containing available tiers
     * @throws APIManagementException when fails to retrieve tier attributes
     */
    private static Map<String, Tier> getTiers(Registry registry, String tierLocation, int tenantId) throws APIManagementException {
        Map<String, Tier> tiers = null;
        try {
            tiers = getAllTiers(registry, tierLocation, tenantId);
            tiers.remove(APIConstants.UNAUTHENTICATED_TIER);
        } catch (RegistryException e) {
            handleException(APIConstants.MSG_TIER_RET_ERROR, e);
        } catch (XMLStreamException e) {
            handleException(APIConstants.MSG_MALFORMED_XML_ERROR, e);
        } catch (APIManagementException e) {
            handleException("Unable to get tier attributes", e);
        } catch (Exception e) {

            // generic exception is caught to catch exceptions thrown from map remove method
            handleException("Unable to remove Unauthenticated tier from tiers list", e);
        }
        return tiers;
    }

    /**
     * This method deletes a given tier from tier xml file, for a given tenant
     *
     * @param tier     tier to be deleted
     * @param tenantId id of the tenant
     * @throws APIManagementException if error occurs while getting registry resource or processing XML
     */
    public static void deleteTier(Tier tier, int tenantId) throws APIManagementException {
        try {
            Registry registry = ServiceReferenceHolder.getInstance().getRegistryService().
                    getGovernanceSystemRegistry(tenantId);
            if (registry.resourceExists(APIConstants.API_TIER_LOCATION)) {
                Resource resource = registry.get(APIConstants.API_TIER_LOCATION);
                String content = new String((byte[]) resource.getContent(), Charset.defaultCharset());
                OMElement element = AXIOMUtil.stringToOM(content);
                OMElement assertion = element.getFirstChildWithName(APIConstants.ASSERTION_ELEMENT);
                Iterator policies = assertion.getChildrenWithName(APIConstants.POLICY_ELEMENT);
                boolean foundTier = false;

                String tierName = null;
                while (policies.hasNext()) {
                    OMElement policy = (OMElement) policies.next();
                    OMElement id = policy.getFirstChildWithName(APIConstants.THROTTLE_ID_ELEMENT);
                    tierName = tier.getName();
                    if (tierName != null && tierName.equalsIgnoreCase(id.getText())) {
                        foundTier = true;
                        policies.remove();
                        break;
                    }
                }
                if (!foundTier) {
                    log.error("Tier doesn't exist : " + tierName);
                    throw new APIManagementException("Tier doesn't exist : " + tierName);
                }
                resource.setContent(element.toString());
                registry.put(APIConstants.API_TIER_LOCATION, resource);
            }
        } catch (RegistryException e) {
            log.error(APIConstants.MSG_TIER_RET_ERROR, e);
            throw new APIManagementException(e.getMessage());
        } catch (XMLStreamException e) {
            log.error(APIConstants.MSG_MALFORMED_XML_ERROR, e);
            throw new APIManagementException(e.getMessage());
        }
    }

    /**
     * Returns the tier display name for a particular tier
     *
     * @return the relevant tier display name
     * @throws APIManagementException if an error occurs when loading tiers from the registry
     */
    public static String getTierDisplayName(int tenantId, String tierName) throws APIManagementException {
        String displayName = null;
        if (APIConstants.UNLIMITED_TIER.equals(tierName)) {
            return APIConstants.UNLIMITED_TIER;
        }
        try {
            Registry registry = ServiceReferenceHolder.getInstance().getRegistryService().
                    getGovernanceSystemRegistry(tenantId);
            if (registry.resourceExists(APIConstants.API_TIER_LOCATION)) {
                Resource resource = registry.get(APIConstants.API_TIER_LOCATION);
                String content = new String((byte[]) resource.getContent(), Charset.defaultCharset());
                OMElement element = AXIOMUtil.stringToOM(content);
                OMElement assertion = element.getFirstChildWithName(APIConstants.ASSERTION_ELEMENT);
                Iterator policies = assertion.getChildrenWithName(APIConstants.POLICY_ELEMENT);

                while (policies.hasNext()) {
                    OMElement policy = (OMElement) policies.next();
                    OMElement id = policy.getFirstChildWithName(APIConstants.THROTTLE_ID_ELEMENT);
                    if (id.getText().equals(tierName)) {
                        if (id.getAttribute(APIConstants.THROTTLE_ID_DISPLAY_NAME_ELEMENT) != null) {
                            displayName = id.getAttributeValue(APIConstants.THROTTLE_ID_DISPLAY_NAME_ELEMENT);
                        } else if (displayName == null) {
                            displayName = id.getText();
                        }
                    }
                }
            }
        } catch (RegistryException e) {
            log.error(APIConstants.MSG_TIER_RET_ERROR, e);
            throw new APIManagementException(APIConstants.MSG_TIER_RET_ERROR, e);
        } catch (XMLStreamException e) {
            log.error(APIConstants.MSG_MALFORMED_XML_ERROR, e);
            throw new APIManagementException(APIConstants.MSG_MALFORMED_XML_ERROR, e);
        }
        return displayName;
    }

    /**
     * Checks whether the specified user has the specified permission.
     *
     * @param username   A username
     * @param permission A valid Carbon permission
     * @throws APIManagementException If the user does not have the specified permission or if an error occurs
     */
    public static void checkPermission(String username, String permission)
            throws APIManagementException {
        if (username == null) {
            throw new APIManagementException("Attempt to execute privileged operation as" +
                    " the anonymous user");
        }

        if (isPermissionCheckDisabled()) {
            log.debug("Permission verification is disabled by APIStore configuration");
            return;
        }

        String tenantDomain = MultitenantUtils.getTenantDomain(username);
        PrivilegedCarbonContext.startTenantFlow();
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);

        boolean authorized;
        try {
            int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().
                    getTenantId(tenantDomain);

            if (!org.wso2.carbon.utils.multitenancy.MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
                org.wso2.carbon.user.api.AuthorizationManager manager =
                        ServiceReferenceHolder.getInstance()
                                .getRealmService()
                                .getTenantUserRealm(tenantId)
                                .getAuthorizationManager();
                authorized =
                        manager.isUserAuthorized(MultitenantUtils.getTenantAwareUsername(username), permission,
                                CarbonConstants.UI_PERMISSION_ACTION);
            } else {
                // On the first login attempt to publisher (without browsing the
                // store), the user realm will be null.
                if (ServiceReferenceHolder.getUserRealm() == null) {
                    ServiceReferenceHolder.setUserRealm((UserRealm) ServiceReferenceHolder.getInstance()
                            .getRealmService()
                            .getTenantUserRealm(tenantId));
                }
                authorized =
                        AuthorizationManager.getInstance()
                                .isUserAuthorized(MultitenantUtils.getTenantAwareUsername(username),
                                        permission);
            }
            if (!authorized) {
                throw new APIManagementException("User '" + username + "' does not have the " +
                        "required permission: " + permission);
            }
        } catch (UserStoreException e) {
            throw new APIManagementException("Error while checking the user:" + username + " authorized or not", e);
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
    }



    /**
     * Checks whether the specified user has the specified permission.
     *
     * @param userNameWithoutChange A username
     * @param permission            A valid Carbon permission
     * @throws APIManagementException If the user does not have the specified permission or if an error occurs
     */
    public static boolean hasPermission(String userNameWithoutChange, String permission)
            throws APIManagementException {
        boolean authorized = false;
        if (userNameWithoutChange == null) {
            throw new APIManagementException("Attempt to execute privileged operation as" +
                    " the anonymous user");
        }

        if (isPermissionCheckDisabled()) {
            log.debug("Permission verification is disabled by APIStore configuration");
            authorized = true;
            return authorized;
        }

        if (APIConstants.Permissions.APIM_ADMIN.equals(permission)) {
            Integer value = getValueFromCache(APIConstants.API_PUBLISHER_ADMIN_PERMISSION_CACHE, userNameWithoutChange);
            if (value != null) {
                return value == 1;
            }
        }

        String tenantDomain = MultitenantUtils.getTenantDomain(userNameWithoutChange);
        PrivilegedCarbonContext.startTenantFlow();
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);

        try {
            int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().
                    getTenantId(tenantDomain);

            if (!org.wso2.carbon.utils.multitenancy.MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
                org.wso2.carbon.user.api.AuthorizationManager manager =
                        ServiceReferenceHolder.getInstance()
                                .getRealmService()
                                .getTenantUserRealm(tenantId)
                                .getAuthorizationManager();
                authorized =
                        manager.isUserAuthorized(MultitenantUtils.getTenantAwareUsername(userNameWithoutChange), permission,
                                CarbonConstants.UI_PERMISSION_ACTION);
            } else {
                // On the first login attempt to publisher (without browsing the
                // store), the user realm will be null.
                if (ServiceReferenceHolder.getUserRealm() == null) {
                    ServiceReferenceHolder.setUserRealm((UserRealm) ServiceReferenceHolder.getInstance()
                            .getRealmService()
                            .getTenantUserRealm(tenantId));
                }
                authorized =
                        AuthorizationManager.getInstance()
                                .isUserAuthorized(MultitenantUtils.getTenantAwareUsername(userNameWithoutChange),
                                        permission);
            }
            if (APIConstants.Permissions.APIM_ADMIN.equals(permission)) {
                addToRolesCache(APIConstants.API_PUBLISHER_ADMIN_PERMISSION_CACHE, userNameWithoutChange,
                        authorized ? 1 : 2);
            }

        } catch (UserStoreException e) {
            throw new APIManagementException("Error while checking the user:" + userNameWithoutChange + " authorized or not", e);
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }

        return authorized;
    }

    /**
     * Checks whether the disablePermissionCheck parameter enabled
     *
     * @return boolean
     */
    public static boolean isPermissionCheckDisabled() {
        APIManagerConfiguration config = ServiceReferenceHolder.getInstance().
                getAPIManagerConfigurationService().getAPIManagerConfiguration();
        String disablePermissionCheck = config.getFirstProperty(APIConstants.API_STORE_DISABLE_PERMISSION_CHECK);
        if (disablePermissionCheck == null) {
            return false;
        }

        return Boolean.parseBoolean(disablePermissionCheck);
    }

    /**
     * Checks whether the specified user has the specified permission without throwing
     * any exceptions.
     *
     * @param username   A username
     * @param permission A valid Carbon permission
     * @return true if the user has the specified permission and false otherwise
     */
    public static boolean checkPermissionQuietly(String username, String permission) {
        try {
            checkPermission(username, permission);
            return true;
        } catch (APIManagementException ignore) {
            // Ignore the exception.
            // Logging it on debug mode so if needed we can see the exception stacktrace.
            if (log.isDebugEnabled()) {
                log.debug("User does not have permission", ignore);
            }
            return false;
        }
    }

    /**
     * Gets the information of the logged in User.
     *
     * @param cookie     Cookie of the previously logged in session.
     * @param serviceUrl Url of the authentication service.
     * @return LoggedUserInfo object containing details of the logged in user.
     * @throws ExceptionException
     * @throws RemoteException
     */
    public static LoggedUserInfo getLoggedInUserInfo(String cookie, String serviceUrl) throws RemoteException, ExceptionException {
        LoggedUserInfoAdminStub stub = new LoggedUserInfoAdminStub(null,
                serviceUrl + "LoggedUserInfoAdmin");
        ServiceClient client = stub._getServiceClient();
        Options options = client.getOptions();
        options.setManageSession(true);
        options.setProperty(HTTPConstants.COOKIE_STRING, cookie);
        return stub.getUserInfo();
    }

    /**
     * Get user profiles of user
     *
     * @param username username
     * @return default user profile of user
     * @throws APIManagementException
     */
    public static UserProfileDTO getUserDefaultProfile(String username) throws APIManagementException {
        APIManagerConfiguration apiManagerConfiguration = ServiceReferenceHolder.getInstance()
                .getAPIManagerConfigurationService().getAPIManagerConfiguration();
        String url = apiManagerConfiguration.getFirstProperty(APIConstants.API_KEY_VALIDATOR_URL);
        String errorMsg = "Error while getting profile of user ";
        try {
            UserProfileMgtServiceStub stub = new UserProfileMgtServiceStub(
                    ServiceReferenceHolder.getContextService().getClientConfigContext(),
                    url + APIConstants.USER_PROFILE_MGT_SERVICE);
            ServiceClient gatewayServiceClient = stub._getServiceClient();
            CarbonUtils.setBasicAccessSecurityHeaders(
                    apiManagerConfiguration.getFirstProperty(APIConstants.API_KEY_VALIDATOR_USERNAME),
                    apiManagerConfiguration.getFirstProperty(APIConstants.API_KEY_VALIDATOR_PASSWORD),
                    gatewayServiceClient);
            UserProfileDTO[] profiles = stub.getUserProfiles(username);
            for (UserProfileDTO dto : profiles) {
                if (APIConstants.USER_DEFAULT_PROFILE.equals(dto.getProfileName())) {
                    return dto;
                }
            }
        } catch (AxisFault axisFault) {
            //here we are going to log the error message and return because in this case, current user cannot fetch
            //profile of another user (due to cross tenant isolation, not allowed to access user details etc.)
            log.error("Cannot access user profile of : " + username);
            return null;
        } catch (RemoteException e) {
            handleException(errorMsg + username, e);
        } catch (UserProfileMgtServiceUserProfileExceptionException e) {
            handleException(errorMsg + username, e);
        }
        return null;
    }


    /**
     * Retrieves the role list of a user
     *
     * @param username        A username
     * @param username A username
     * @throws APIManagementException If an error occurs
     */
    public static String[] getListOfRoles(String username) throws APIManagementException {
        if (username == null) {
            throw new APIManagementException("Attempt to execute privileged operation as" +
                    " the anonymous user");
        }

        String[] roles = null;

        roles = getValueFromCache(APIConstants.API_USER_ROLE_CACHE, username);
        if (roles != null) {
            return roles;
        }
        String tenantDomain = MultitenantUtils.getTenantDomain(username);
        try {
            if (!org.wso2.carbon.utils.multitenancy.MultitenantConstants.SUPER_TENANT_DOMAIN_NAME
                    .equals(tenantDomain)) {
                int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager()
                        .getTenantId(tenantDomain);
                UserStoreManager manager = ServiceReferenceHolder.getInstance().getRealmService()
                        .getTenantUserRealm(tenantId).getUserStoreManager();
                roles = manager.getRoleListOfUser(MultitenantUtils.getTenantAwareUsername(username));
            } else {
                roles = AuthorizationManager.getInstance()
                        .getRolesOfUser(MultitenantUtils.getTenantAwareUsername(username));
            }
            addToRolesCache(APIConstants.API_USER_ROLE_CACHE, username, roles);
            return roles;
        } catch (UserStoreException e) {
            throw new APIManagementException("UserStoreException while trying the role list of the user " + username,
                    e);
        }
    }

    /**
     * To add the value to a cache.
     *
     * @param cacheName - Name of the Cache
     * @param key       - Key of the entry that need to be added.
     * @param value     - Value of the entry that need to be added.
     */
    protected static <T> void addToRolesCache(String cacheName, String key, T value) {
        if (isPublisherRoleCacheEnabled) {
            if (log.isDebugEnabled()) {
                log.debug("Publisher role cache is enabled, adding the roles for the " + key + " to the cache "
                        + cacheName + "'");
            }
            Caching.getCacheManager(APIConstants.API_MANAGER_CACHE_MANAGER).getCache(cacheName).put(key, value);
        }
    }

    /**
     * To get the value from the cache.
     *
     * @param cacheName Name of the cache.
     * @param key       Key of the cache entry.
     * @return Role list from the cache, if a values exists, otherwise null.
     */
    protected static <T> T getValueFromCache(String cacheName, String key) {
        if (isPublisherRoleCacheEnabled) {
            if (log.isDebugEnabled()) {
                log.debug("Publisher role cache is enabled, retrieving the roles for  " + key + " from the cache "
                        + cacheName + "'");
            }
            Cache<String, T> rolesCache = Caching.getCacheManager(APIConstants.API_MANAGER_CACHE_MANAGER)
                    .getCache(cacheName);
            return rolesCache.get(key);
        }
        return null;
    }

    /**
     * Retrieves the list of user roles without throwing any exceptions.
     *
     * @param username A username
     * @return the list of roles to which the user belongs to.
     */
    public static String[] getListOfRolesQuietly(String username) {
        try {
            return getListOfRoles(username);
        } catch (APIManagementException e) {
            return new String[0];
        }
    }

    /**
     * Sets permission for uploaded file resource.
     *
     * @param filePath Registry path for the uploaded file
     * @throws APIManagementException
     */

    public static void setFilePermission(String filePath) throws APIManagementException {
        try {
            String filePathString = filePath.replaceFirst("/registry/resource/", "");
            org.wso2.carbon.user.api.AuthorizationManager accessControlAdmin = ServiceReferenceHolder.getInstance().
                    getRealmService().getTenantUserRealm(MultitenantConstants.SUPER_TENANT_ID).
                    getAuthorizationManager();
            if (!accessControlAdmin.isRoleAuthorized(CarbonConstants.REGISTRY_ANONNYMOUS_ROLE_NAME,
                    filePathString, ActionConstants.GET)) {
                accessControlAdmin.authorizeRole(CarbonConstants.REGISTRY_ANONNYMOUS_ROLE_NAME,
                        filePathString, ActionConstants.GET);
            }
        } catch (UserStoreException e) {
            throw new APIManagementException("Error while setting up permissions for file location", e);
        }
    }

    /**
     * This method used to get API from governance artifact specific to copyAPI
     *
     * @param artifact API artifact
     * @param registry Registry
     * @return API
     * @throws APIManagementException if failed to get API from artifact
     */
    public static API getAPI(GovernanceArtifact artifact, Registry registry, APIIdentifier oldId, String oldContext)
            throws APIManagementException {

        API api;
        try {
            String providerName = artifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER);
            String apiName = artifact.getAttribute(APIConstants.API_OVERVIEW_NAME);
            String apiVersion = artifact.getAttribute(APIConstants.API_OVERVIEW_VERSION);
            api = new API(new APIIdentifier(providerName, apiName, apiVersion));
            int apiId = ApiMgtDAO.getInstance().getAPIID(oldId, null);
            if (apiId == -1) {
                return null;
            }
            // set rating
            String artifactPath = GovernanceUtils.getArtifactPath(registry, artifact.getId());
            BigDecimal bigDecimal = BigDecimal.valueOf(registry.getAverageRating(artifactPath));
            BigDecimal res = bigDecimal.setScale(1, RoundingMode.HALF_UP);
            api.setRating(res.floatValue());
            //set description
            api.setDescription(artifact.getAttribute(APIConstants.API_OVERVIEW_DESCRIPTION));
            //set last access time
            api.setLastUpdated(registry.get(artifactPath).getLastModified());
            //set uuid
            api.setUUID(artifact.getId());
            // set url
            api.setStatus(getLcStateFromArtifact(artifact));
            api.setThumbnailUrl(artifact.getAttribute(APIConstants.API_OVERVIEW_THUMBNAIL_URL));
            api.setWsdlUrl(artifact.getAttribute(APIConstants.API_OVERVIEW_WSDL));
            api.setWadlUrl(artifact.getAttribute(APIConstants.API_OVERVIEW_WADL));
            api.setTechnicalOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_TEC_OWNER));
            api.setTechnicalOwnerEmail(artifact.getAttribute(APIConstants.API_OVERVIEW_TEC_OWNER_EMAIL));
            api.setBusinessOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_BUSS_OWNER));
            api.setBusinessOwnerEmail(artifact.getAttribute(APIConstants.API_OVERVIEW_BUSS_OWNER_EMAIL));
            api.setEndpointSecured(Boolean.parseBoolean(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_SECURED)));
            api.setEndpointAuthDigest(Boolean.parseBoolean(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_AUTH_DIGEST)));
            api.setEndpointUTUsername(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_USERNAME));
            if (!((APIConstants.DEFAULT_MODIFIED_ENDPOINT_PASSWORD)
                    .equals(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_PASSWORD)))) {
                api.setEndpointUTPassword(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_PASSWORD));
            } else { //If APIEndpointPasswordRegistryHandler is enabled take password from the registry hidden property
                api.setEndpointUTPassword(getActualEpPswdFromHiddenProperty(api, registry));
            }
            api.setTransports(artifact.getAttribute(APIConstants.API_OVERVIEW_TRANSPORTS));

            api.setEndpointConfig(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_CONFIG));

            api.setRedirectURL(artifact.getAttribute(APIConstants.API_OVERVIEW_REDIRECT_URL));
            api.setApiOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_OWNER));
            api.setAdvertiseOnly(Boolean.parseBoolean(artifact.getAttribute(APIConstants.API_OVERVIEW_ADVERTISE_ONLY)));

            api.setSubscriptionAvailability(artifact.getAttribute(APIConstants.API_OVERVIEW_SUBSCRIPTION_AVAILABILITY));
            api.setSubscriptionAvailableTenants(artifact.getAttribute(APIConstants.API_OVERVIEW_SUBSCRIPTION_AVAILABLE_TENANTS));

            api.setResponseCache(artifact.getAttribute(APIConstants.API_OVERVIEW_RESPONSE_CACHING));
            api.setImplementation(artifact.getAttribute(APIConstants.PROTOTYPE_OVERVIEW_IMPLEMENTATION));
            api.setVisibility(artifact.getAttribute(APIConstants.API_OVERVIEW_VISIBILITY));

            String tenantDomainName = MultitenantUtils.getTenantDomain(replaceEmailDomainBack(providerName));
            int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager()
                    .getTenantId(tenantDomainName);

            boolean isGlobalThrottlingEnabled = APIUtil.isAdvanceThrottlingEnabled();

            if (isGlobalThrottlingEnabled) {
                String apiLevelTier = ApiMgtDAO.getInstance().getAPILevelTier(apiId);
                api.setApiLevelPolicy(apiLevelTier);
            }

            String tiers = artifact.getAttribute(APIConstants.API_OVERVIEW_TIER);
            Map<String, Tier> definedTiers = getTiers(tenantId);
            Set<Tier> availableTier = getAvailableTiers(definedTiers, tiers, apiName);
            api.addAvailableTiers(availableTier);


            api.setContext(artifact.getAttribute(APIConstants.API_OVERVIEW_CONTEXT));
            api.setContextTemplate(artifact.getAttribute(APIConstants.API_OVERVIEW_CONTEXT_TEMPLATE));
            api.setLatest(Boolean.parseBoolean(artifact.getAttribute(APIConstants.API_OVERVIEW_IS_LATEST)));
            ArrayList<URITemplate> urlPatternsList;

            Set<Scope> scopes = ApiMgtDAO.getInstance().getAPIScopes(oldId);
            api.setScopes(scopes);

            HashMap<String, String> resourceScopes;
            resourceScopes = ApiMgtDAO.getInstance().getResourceToScopeMapping(oldId);

            urlPatternsList = ApiMgtDAO.getInstance().getAllURITemplates(oldContext, oldId.getVersion());
            Set<URITemplate> uriTemplates = new HashSet<URITemplate>(urlPatternsList);

            for (URITemplate uriTemplate : uriTemplates) {
                uriTemplate.setResourceURI(api.getUrl());
                uriTemplate.setResourceSandboxURI(api.getSandboxUrl());
                String resourceScopeKey = APIUtil.getResourceKey(oldContext, oldId.getVersion(), uriTemplate.getUriTemplate(), uriTemplate.getHTTPVerb());
                uriTemplate.setScope(findScopeByKey(scopes, resourceScopes.get(resourceScopeKey)));

            }
            api.setUriTemplates(uriTemplates);

            Set<String> tags = new HashSet<String>();
            Tag[] tag = registry.getTags(artifactPath);
            for (Tag tag1 : tag) {
                tags.add(tag1.getTagName());
            }
            api.addTags(tags);
            api.setLastUpdated(registry.get(artifactPath).getLastModified());
            api.setAsDefaultVersion(Boolean.parseBoolean(artifact.getAttribute(APIConstants.API_OVERVIEW_IS_DEFAULT_VERSION)));

            String environments = artifact.getAttribute(APIConstants.API_OVERVIEW_ENVIRONMENTS);
            api.setEnvironments(extractEnvironmentsForAPI(environments));
            api.setCorsConfiguration(getCorsConfigurationFromArtifact(artifact));

        } catch (GovernanceException e) {
            String msg = "Failed to get API fro artifact ";
            throw new APIManagementException(msg, e);
        } catch (RegistryException e) {
            String msg = "Failed to get LastAccess time or Rating";
            throw new APIManagementException(msg, e);
        } catch (UserStoreException e) {
            String msg = "Failed to get User Realm of API Provider";
            throw new APIManagementException(msg, e);
        }
        return api;
    }

    public static boolean checkAccessTokenPartitioningEnabled() {
        return OAuthServerConfiguration.getInstance().isAccessTokenPartitioningEnabled();
    }

    public static boolean checkUserNameAssertionEnabled() {
        return OAuthServerConfiguration.getInstance().isUserNameAssertionEnabled();
    }

    public static String[] getAvailableKeyStoreTables() throws APIManagementException {
        String[] keyStoreTables = new String[0];
        Map<String, String> domainMappings = getAvailableUserStoreDomainMappings();
        if (domainMappings != null) {
            keyStoreTables = new String[domainMappings.size()];
            int i = 0;
            for (Entry<String, String> e : domainMappings.entrySet()) {
                String value = e.getValue();
                keyStoreTables[i] = APIConstants.ACCESS_TOKEN_STORE_TABLE + "_" + value.trim();
                i++;
            }
        }
        return keyStoreTables;
    }

    public static Map<String, String> getAvailableUserStoreDomainMappings() throws
            APIManagementException {
        Map<String, String> userStoreDomainMap = new HashMap<String, String>();
        String domainsStr = OAuthServerConfiguration.getInstance().getAccessTokenPartitioningDomains();
        if (domainsStr != null) {
            String[] userStoreDomainsArr = domainsStr.split(",");
            for (String anUserStoreDomainsArr : userStoreDomainsArr) {
                String[] mapping = anUserStoreDomainsArr.trim().split(":"); //A:foo.com , B:bar.com
                if (mapping.length < 2) {
                    throw new APIManagementException("Domain mapping has not defined");
                }
                userStoreDomainMap.put(mapping[1].trim(), mapping[0].trim()); //key=domain & value=mapping
            }
        }
        return userStoreDomainMap;
    }

    public static String getAccessTokenStoreTableFromUserId(String userId)
            throws APIManagementException {
        String accessTokenStoreTable = APIConstants.ACCESS_TOKEN_STORE_TABLE;
        String userStore;
        if (userId != null) {
            String[] strArr = userId.split("/");
            if (strArr.length > 1) {
                userStore = strArr[0];
                Map<String, String> availableDomainMappings = getAvailableUserStoreDomainMappings();
                if (availableDomainMappings != null &&
                        availableDomainMappings.containsKey(userStore)) {
                    accessTokenStoreTable = accessTokenStoreTable + "_" +
                            availableDomainMappings.get(userStore);
                }
            }
        }
        return accessTokenStoreTable;
    }

    public static String getAccessTokenStoreTableFromAccessToken(String apiKey)
            throws APIManagementException {
        String userId = getUserIdFromAccessToken(apiKey); //i.e: 'foo.com/admin' or 'admin'
        return getAccessTokenStoreTableFromUserId(userId);
    }

    public static String getUserIdFromAccessToken(String apiKey) {
        String userId = null;
        String decodedKey = new String(Base64.decodeBase64(apiKey.getBytes(Charset.defaultCharset())), Charset.defaultCharset());
        String[] tmpArr = decodedKey.split(":");
        if (tmpArr.length == 2) { //tmpArr[0]= userStoreDomain & tmpArr[1] = userId
            userId = tmpArr[1];
        }
        return userId;
    }

    /**
     * validates if an accessToken has expired or not
     *
     * @param accessTokenDO
     * @return true if token has expired else false
     */
    public static boolean isAccessTokenExpired(APIKeyValidationInfoDTO accessTokenDO) {
        long validityPeriod = accessTokenDO.getValidityPeriod();
        long issuedTime = accessTokenDO.getIssuedTime();
        long timestampSkew = OAuthServerConfiguration.getInstance().getTimeStampSkewInSeconds() * 1000;
        long currentTime = System.currentTimeMillis();

        //If the validity period is not an never expiring value
        if (validityPeriod != Long.MAX_VALUE &&
                // For cases where validityPeriod is closer to Long.MAX_VALUE (then issuedTime + validityPeriod would spill
                // over and would produce a negative value)
                (currentTime - timestampSkew) > validityPeriod) {
            //check the validity of cached OAuth2AccessToken Response

            if ((currentTime - timestampSkew) > (issuedTime + validityPeriod)) {
                accessTokenDO.setValidationStatus(APIConstants.KeyValidationStatus.API_AUTH_INVALID_CREDENTIALS);
                return true;
            }
        }

        return false;
    }

    /**
     * When an input is having '@',replace it with '-AT-' [This is required to persist API data in registry,as registry
     * paths don't allow '@' sign.]
     *
     * @param input inputString
     * @return String modifiedString
     */
    public static String replaceEmailDomain(String input) {
        if (input != null && input.contains(APIConstants.EMAIL_DOMAIN_SEPARATOR)) {
            input = input.replace(APIConstants.EMAIL_DOMAIN_SEPARATOR, APIConstants.EMAIL_DOMAIN_SEPARATOR_REPLACEMENT);
        }
        return input;
    }

    /**
     * When an input is having '-AT-',replace it with @ [This is required to persist API data between registry and database]
     *
     * @param input inputString
     * @return String modifiedString
     */
    public static String replaceEmailDomainBack(String input) {
        if (input != null && input.contains(APIConstants.EMAIL_DOMAIN_SEPARATOR_REPLACEMENT)) {
            input = input.replace(APIConstants.EMAIL_DOMAIN_SEPARATOR_REPLACEMENT,
                    APIConstants.EMAIL_DOMAIN_SEPARATOR);
        }
        return input;
    }

    public static void copyResourcePermissions(String username, String sourceArtifactPath, String targetArtifactPath)
            throws APIManagementException {
        String sourceResourcePath = RegistryUtils.getAbsolutePath(RegistryContext.getBaseInstance(),
                APIUtil.getMountedPath(RegistryContext.getBaseInstance(),
                        RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH)
                        + sourceArtifactPath);

        String targetResourcePath = RegistryUtils.getAbsolutePath(RegistryContext.getBaseInstance(),
                APIUtil.getMountedPath(RegistryContext.getBaseInstance(),
                        RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH)
                        + targetArtifactPath);

        String tenantDomain = MultitenantUtils.getTenantDomain(APIUtil.replaceEmailDomainBack(username));

        try {
            int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().getTenantId(tenantDomain);
            org.wso2.carbon.user.api.AuthorizationManager authManager = ServiceReferenceHolder.getInstance().getRealmService().
                    getTenantUserRealm(tenantId).getAuthorizationManager();
            String[] allowedRoles = authManager.getAllowedRolesForResource(sourceResourcePath, ActionConstants.GET);

            if (allowedRoles != null) {

                for (String allowedRole : allowedRoles) {
                    authManager.authorizeRole(allowedRole, targetResourcePath, ActionConstants.GET);
                }
            }

        } catch (UserStoreException e) {
            throw new APIManagementException("Error while adding role permissions to API", e);
        }
    }

    /**
     * This function is to set resource permissions based on its visibility
     *
     * @param username     Username
     * @param visibility   API visibility
     * @param roles        Authorized roles
     * @param artifactPath API resource path
     * @throws APIManagementException Throwing exception
     */
    public static void setResourcePermissions(String username, String visibility, String[] roles, String
            artifactPath) throws APIManagementException {
        setResourcePermissions(username, visibility, roles, artifactPath, null);
    }

    /**
     * This function is to set resource permissions based on its visibility
     *
     * @param visibility   API visibility
     * @param roles        Authorized roles
     * @param artifactPath API resource path
     * @param registry     Registry
     * @throws APIManagementException Throwing exception
     */
    public static void setResourcePermissions(String username, String visibility, String[] roles, String
            artifactPath, Registry registry) throws APIManagementException {
        try {
            String resourcePath = RegistryUtils.getAbsolutePath(RegistryContext.getBaseInstance(),
                    APIUtil.getMountedPath(RegistryContext.getBaseInstance(),
                            RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH)
                            + artifactPath);
            Resource registryResource = null;

            if (registry != null && registry.resourceExists(artifactPath)) {
                registryResource = registry.get(artifactPath);
            }
            StringBuilder publisherAccessRoles = new StringBuilder(APIConstants.NULL_USER_ROLE_LIST);

            if (registryResource != null) {
                String publisherRole = registryResource.getProperty(APIConstants.PUBLISHER_ROLES);
                if (publisherRole != null) {
                    publisherAccessRoles = new StringBuilder(publisherRole);
                }
                if (StringUtils.isEmpty(publisherAccessRoles.toString())) {
                    publisherAccessRoles = new StringBuilder(APIConstants.NULL_USER_ROLE_LIST);
                }

                if (visibility.equalsIgnoreCase(APIConstants.API_GLOBAL_VISIBILITY)) {
                    registryResource.setProperty(APIConstants.STORE_VIEW_ROLES, APIConstants.NULL_USER_ROLE_LIST);
                    publisherAccessRoles = new StringBuilder(APIConstants.NULL_USER_ROLE_LIST); // set publisher
                    // access roles null since store visibility is global. We do not need to add any roles to
                    // store_view_role property.
                } else {
                    registryResource.setProperty(APIConstants.STORE_VIEW_ROLES, publisherAccessRoles.toString());
                }
            }
            String tenantDomain = MultitenantUtils.getTenantDomain(APIUtil.replaceEmailDomainBack(username));
            if (!org.wso2.carbon.utils.multitenancy.MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
                int tenantId = ServiceReferenceHolder.getInstance().getRealmService().
                        getTenantManager().getTenantId(tenantDomain);
                // calculate resource path
                RegistryAuthorizationManager authorizationManager = new RegistryAuthorizationManager
                        (ServiceReferenceHolder.getUserRealm());
                resourcePath = authorizationManager.computePathOnMount(resourcePath);
                org.wso2.carbon.user.api.AuthorizationManager authManager =
                        ServiceReferenceHolder.getInstance().getRealmService().
                                getTenantUserRealm(tenantId).getAuthorizationManager();
                if (visibility != null && APIConstants.API_RESTRICTED_VISIBILITY.equalsIgnoreCase(visibility)) {
                    boolean isRoleEveryOne = false;
                    /*If no roles have defined, authorize for everyone role */
                    if (roles != null) {
                        if (roles.length == 1 && "".equals(roles[0])) {
                            authManager.authorizeRole(APIConstants.EVERYONE_ROLE, resourcePath, ActionConstants.GET);
                            isRoleEveryOne = true;
                        } else {
                            for (String role : roles) {
                                if (APIConstants.EVERYONE_ROLE.equalsIgnoreCase(role.trim())) {
                                    isRoleEveryOne = true;
                                }
                                authManager.authorizeRole(role.trim(), resourcePath, ActionConstants.GET);
                                publisherAccessRoles.append(",").append(role.trim().toLowerCase());
                            }
                        }
                    }
                    if (!isRoleEveryOne) {
                        authManager.denyRole(APIConstants.EVERYONE_ROLE, resourcePath, ActionConstants.GET);
                    }
                    authManager.denyRole(APIConstants.ANONYMOUS_ROLE, resourcePath, ActionConstants.GET);
                } else if (visibility != null && APIConstants.API_PRIVATE_VISIBILITY.equalsIgnoreCase(visibility)) {
                    authManager.authorizeRole(APIConstants.EVERYONE_ROLE, resourcePath, ActionConstants.GET);
                    authManager.denyRole(APIConstants.ANONYMOUS_ROLE, resourcePath, ActionConstants.GET);
                } else if (visibility != null && APIConstants.DOC_OWNER_VISIBILITY.equalsIgnoreCase(visibility)) {

                    /*If no roles have defined, deny access for everyone & anonymous role */
                    if (roles == null) {
                        authManager.denyRole(APIConstants.EVERYONE_ROLE, resourcePath, ActionConstants.GET);
                        authManager.denyRole(APIConstants.ANONYMOUS_ROLE, resourcePath, ActionConstants.GET);
                    } else {
                        for (String role : roles) {
                            authManager.denyRole(role.trim(), resourcePath, ActionConstants.GET);

                        }
                    }
                } else {
                    authManager.authorizeRole(APIConstants.EVERYONE_ROLE, resourcePath, ActionConstants.GET);
                    authManager.authorizeRole(APIConstants.ANONYMOUS_ROLE, resourcePath, ActionConstants.GET);
                }
            } else {
                RegistryAuthorizationManager authorizationManager = new RegistryAuthorizationManager
                        (ServiceReferenceHolder.getUserRealm());

                if (visibility != null && APIConstants.API_RESTRICTED_VISIBILITY.equalsIgnoreCase(visibility)) {
                    boolean isRoleEveryOne = false;
                    if (roles != null) {
                        for (String role : roles) {
                            if (APIConstants.EVERYONE_ROLE.equalsIgnoreCase(role.trim())) {
                                isRoleEveryOne = true;
                            }
                            authorizationManager.authorizeRole(role.trim(), resourcePath, ActionConstants.GET);
                            publisherAccessRoles.append(",").append(role.toLowerCase());
                        }
                    }
                    if (!isRoleEveryOne) {
                        authorizationManager.denyRole(APIConstants.EVERYONE_ROLE, resourcePath, ActionConstants.GET);
                    }
                    authorizationManager.denyRole(APIConstants.ANONYMOUS_ROLE, resourcePath, ActionConstants.GET);

                } else if (visibility != null && APIConstants.API_PRIVATE_VISIBILITY.equalsIgnoreCase(visibility)) {
                    authorizationManager.authorizeRole(APIConstants.EVERYONE_ROLE, resourcePath, ActionConstants.GET);
                    authorizationManager.denyRole(APIConstants.ANONYMOUS_ROLE, resourcePath, ActionConstants.GET);
                } else if (visibility != null && APIConstants.DOC_OWNER_VISIBILITY.equalsIgnoreCase(visibility)) {
                     /*If no roles have defined, deny access for everyone & anonymous role */
                    if (roles == null) {
                        authorizationManager.denyRole(APIConstants.EVERYONE_ROLE, resourcePath, ActionConstants.GET);
                        authorizationManager.denyRole(APIConstants.ANONYMOUS_ROLE, resourcePath, ActionConstants.GET);
                    } else {
                        for (String role : roles) {
                            authorizationManager.denyRole(role.trim(), resourcePath, ActionConstants.GET);

                        }
                    }
                } else {
                    authorizationManager.authorizeRole(APIConstants.EVERYONE_ROLE, resourcePath, ActionConstants.GET);
                    authorizationManager.authorizeRole(APIConstants.ANONYMOUS_ROLE, resourcePath, ActionConstants.GET);
                }
            }
            if (registryResource != null) {
                registryResource.setProperty(APIConstants.STORE_VIEW_ROLES, publisherAccessRoles.toString());
                registry.put(artifactPath, registryResource);
            }

        } catch (UserStoreException e) {
            throw new APIManagementException("Error while adding role permissions to API", e);
        } catch (RegistryException e) {
            throw new APIManagementException("Registry exception while adding role permissions to API", e);
        }
    }

    /**
     * This function is to set resource permissions based on its visibility
     *
     * @param artifactPath API resource path
     * @throws APIManagementException Throwing exception
     */
    public static void clearResourcePermissions(String artifactPath, APIIdentifier apiId, int tenantId)
            throws APIManagementException {
        try {
            String resourcePath = RegistryUtils.getAbsolutePath(RegistryContext.getBaseInstance(),
                    APIUtil.getMountedPath(RegistryContext.getBaseInstance(),
                            RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH) + artifactPath);
            String tenantDomain = MultitenantUtils
                    .getTenantDomain(APIUtil.replaceEmailDomainBack(apiId.getProviderName()));
            if (!org.wso2.carbon.utils.multitenancy.MultitenantConstants.SUPER_TENANT_DOMAIN_NAME
                    .equals(tenantDomain)) {
                org.wso2.carbon.user.api.AuthorizationManager authManager = ServiceReferenceHolder.getInstance()
                        .getRealmService().getTenantUserRealm(tenantId).getAuthorizationManager();
                authManager.clearResourceAuthorizations(resourcePath);
            } else {
                RegistryAuthorizationManager authorizationManager = new RegistryAuthorizationManager(
                        ServiceReferenceHolder.getUserRealm());
                authorizationManager.clearResourceAuthorizations(resourcePath);
            }
        } catch (UserStoreException e) {
            handleException("Error while adding role permissions to API", e);
        }
    }

    public static void loadTenantAPIPolicy(String tenant, int tenantID) throws APIManagementException {

        String tierBasePath = CarbonUtils.getCarbonHome() + File.separator + "repository" + File.separator + "resources"
                + File.separator + "default-tiers" + File.separator;

        String apiTierFilePath = tierBasePath + APIConstants.DEFAULT_API_TIER_FILE_NAME;
        String appTierFilePath = tierBasePath + APIConstants.DEFAULT_APP_TIER_FILE_NAME;
        String resTierFilePath = tierBasePath + APIConstants.DEFAULT_RES_TIER_FILE_NAME;

        loadTenantAPIPolicy(tenantID, APIConstants.API_TIER_LOCATION, apiTierFilePath);
        loadTenantAPIPolicy(tenantID, APIConstants.APP_TIER_LOCATION, appTierFilePath);
        loadTenantAPIPolicy(tenantID, APIConstants.RES_TIER_LOCATION, resTierFilePath);
    }

    /**
     * Load the throttling policy  to the registry for tenants
     *
     * @param tenantID
     * @param location
     * @param fileName
     * @throws APIManagementException
     */
    private static void loadTenantAPIPolicy(int tenantID, String location, String fileName)
            throws APIManagementException {
        InputStream inputStream = null;

        try {
            RegistryService registryService = ServiceReferenceHolder.getInstance().getRegistryService();

            UserRegistry govRegistry = registryService.getGovernanceSystemRegistry(tenantID);

            if (govRegistry.resourceExists(location)) {
                if (log.isDebugEnabled()) {
                    log.debug("Tier policies already uploaded to the tenant's registry space");
                }
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Adding API tier policies to the tenant's registry");
            }
            File defaultTiers = new File(fileName);
            if (!defaultTiers.exists()) {
                log.info("Default tier policies not found in : " + fileName);
                return;
            }
            inputStream = FileUtils.openInputStream(defaultTiers);
            byte[] data = IOUtils.toByteArray(inputStream);
            Resource resource = govRegistry.newResource();
            resource.setContent(data);
            govRegistry.put(location, resource);

        } catch (RegistryException e) {
            throw new APIManagementException("Error while saving policy information to the registry", e);
        } catch (IOException e) {
            throw new APIManagementException("Error while reading policy file content", e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    log.error("Error when closing input stream", e);
                }
            }
        }
    }

    /**
     * Load the External API Store Configuration  to the registry
     *
     * @param tenantID
     * @throws org.wso2.carbon.apimgt.api.APIManagementException
     */

    public static void loadTenantExternalStoreConfig(int tenantID) throws APIManagementException {
        try {
            RegistryService registryService = ServiceReferenceHolder.getInstance().getRegistryService();

            UserRegistry govRegistry = registryService.getGovernanceSystemRegistry(tenantID);

            if (govRegistry.resourceExists(APIConstants.EXTERNAL_API_STORES_LOCATION)) {
                log.debug("External Stores configuration already uploaded to the registry");
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Adding External Stores configuration to the tenant's registry");
            }
            InputStream inputStream =
                    APIManagerComponent.class.getResourceAsStream("/externalstores/default-external-api-stores.xml");
            byte[] data = IOUtils.toByteArray(inputStream);
            Resource resource = govRegistry.newResource();
            resource.setContent(data);
            govRegistry.put(APIConstants.EXTERNAL_API_STORES_LOCATION, resource);

            /*set resource permission*/
            org.wso2.carbon.user.api.AuthorizationManager authManager =
                    ServiceReferenceHolder.getInstance().getRealmService().getTenantUserRealm(tenantID).
                            getAuthorizationManager();
            String resourcePath = RegistryUtils.getAbsolutePath(RegistryContext.getBaseInstance(),
                    APIUtil.getMountedPath(RegistryContext.getBaseInstance(), RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH)
                            + APIConstants.EXTERNAL_API_STORES_LOCATION);
            authManager.denyRole(APIConstants.EVERYONE_ROLE, resourcePath, ActionConstants.GET);

        } catch (RegistryException e) {
            throw new APIManagementException("Error while saving External Stores configuration information to the " +
                    "registry", e);
        } catch (IOException e) {
            throw new APIManagementException("Error while reading External Stores configuration file content", e);
        } catch (UserStoreException e) {
            throw new APIManagementException("Error while setting permission to External Stores configuration file", e);
        }
    }

    /**
     * Load the Google Analytics Configuration  to the registry
     *
     * @param tenantID
     * @throws APIManagementException
     */

    public static void loadTenantGAConfig(int tenantID) throws APIManagementException {
        InputStream inputStream = null;
        try {
            RegistryService registryService =
                    ServiceReferenceHolder.getInstance()
                            .getRegistryService();

            UserRegistry govRegistry = registryService.getGovernanceSystemRegistry(tenantID);

            if (govRegistry.resourceExists(APIConstants.GA_CONFIGURATION_LOCATION)) {
                log.debug("Google Analytics configuration already uploaded to the registry");
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Adding Google Analytics configuration to the tenant's registry");
            }
            inputStream = APIManagerComponent.class.getResourceAsStream("/statistics/default-ga-config.xml");
            byte[] data = IOUtils.toByteArray(inputStream);
            Resource resource = govRegistry.newResource();
            resource.setContent(data);
            govRegistry.put(APIConstants.GA_CONFIGURATION_LOCATION, resource);

            /*set resource permission*/
            org.wso2.carbon.user.api.AuthorizationManager authManager =
                    ServiceReferenceHolder.getInstance().getRealmService().
                            getTenantUserRealm(tenantID).getAuthorizationManager();
            String resourcePath = RegistryUtils.getAbsolutePath(RegistryContext.getBaseInstance(),
                    APIUtil.getMountedPath(RegistryContext.getBaseInstance(),
                            RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH) + APIConstants.GA_CONFIGURATION_LOCATION);
            authManager.denyRole(APIConstants.EVERYONE_ROLE, resourcePath, ActionConstants.GET);

        } catch (RegistryException e) {
            throw new APIManagementException("Error while saving Google Analytics configuration information to the registry", e);
        } catch (IOException e) {
            throw new APIManagementException("Error while reading Google Analytics configuration file content", e);
        } catch (UserStoreException e) {
            throw new APIManagementException("Error while setting permission to Google Analytics configuration file", e);
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                if (log.isWarnEnabled()) {
                    log.warn("Error while closing the input stream", e);
                }
            }
        }
    }

    public static void loadTenantWorkFlowExtensions(int tenantID)
            throws APIManagementException {
        // TODO: Merge different resource loading methods and create a single method.
        try {
            RegistryService registryService = ServiceReferenceHolder.getInstance().getRegistryService();

            UserRegistry govRegistry = registryService.getGovernanceSystemRegistry(tenantID);

            if (govRegistry.resourceExists(APIConstants.WORKFLOW_EXECUTOR_LOCATION)) {
                log.debug("External Stores configuration already uploaded to the registry");
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Adding External Stores configuration to the tenant's registry");
            }
            InputStream inputStream =
                    APIManagerComponent.class.getResourceAsStream("/workflowextensions/default-workflow-extensions.xml");
            byte[] data = IOUtils.toByteArray(inputStream);
            Resource resource = govRegistry.newResource();
            resource.setContent(data);
            resource.setMediaType(APIConstants.WORKFLOW_MEDIA_TYPE);
            govRegistry.put(APIConstants.WORKFLOW_EXECUTOR_LOCATION, resource);

        } catch (RegistryException e) {
            throw new APIManagementException("Error while saving External Stores configuration information to the registry", e);
        } catch (IOException e) {
            throw new APIManagementException("Error while reading External Stores configuration file content", e);
        }
    }

    /**
     * @param tenantId
     * @throws APIManagementException
     */
    public static void loadTenantSelfSignUpConfigurations(int tenantId)
            throws APIManagementException {
        try {
            RegistryService registryService = ServiceReferenceHolder.getInstance().getRegistryService();
            UserRegistry govRegistry = registryService.getGovernanceSystemRegistry(tenantId);

            if (govRegistry.resourceExists(APIConstants.SELF_SIGN_UP_CONFIG_LOCATION)) {
                log.debug("Self signup configuration already uploaded to the registry");
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Adding Self signup configuration to the tenant's registry");
            }
            InputStream inputStream;
            if (tenantId == org.wso2.carbon.utils.multitenancy.MultitenantConstants.SUPER_TENANT_ID) {
                inputStream =
                        APIManagerComponent.class.getResourceAsStream("/signupconfigurations/default-sign-up-config.xml");
            } else {
                inputStream =
                        APIManagerComponent.class.getResourceAsStream("/signupconfigurations/tenant-sign-up-config.xml");
            }
            byte[] data = IOUtils.toByteArray(inputStream);
            Resource resource = govRegistry.newResource();
            resource.setContent(data);
            resource.setMediaType(APIConstants.SELF_SIGN_UP_CONFIG_MEDIA_TYPE);
            govRegistry.put(APIConstants.SELF_SIGN_UP_CONFIG_LOCATION, resource);

        } catch (RegistryException e) {
            throw new APIManagementException("Error while saving Self signup configuration information to the registry", e);
        } catch (IOException e) {
            throw new APIManagementException("Error while reading Self signup configuration file content", e);
        }
    }

    public static void loadTenantConf(int tenantID) throws APIManagementException {
        RegistryService registryService = ServiceReferenceHolder.getInstance().getRegistryService();
        try {
            UserRegistry registry = registryService.getConfigSystemRegistry(tenantID);
            if (registry.resourceExists(APIConstants.API_TENANT_CONF_LOCATION)) {
                log.debug("Tenant conf already uploaded to the registry");
                return;
            }

            String tenantConfLocation = CarbonUtils.getCarbonHome() + File.separator +
                    APIConstants.RESOURCE_FOLDER_LOCATION + File.separator +
                    APIConstants.API_TENANT_CONF;

            File tenantConfFile = new File(tenantConfLocation);

            byte[] data;

            if (tenantConfFile.exists()) { // Load conf from resources directory in pack if it exists
                FileInputStream fileInputStream = new FileInputStream(tenantConfFile);
                data = IOUtils.toByteArray(fileInputStream);
            } else { // Fallback to loading the conf that is stored at jar level if file does not exist in pack
                InputStream inputStream = APIManagerComponent.class.getResourceAsStream("/tenant/" + APIConstants.API_TENANT_CONF);
                data = IOUtils.toByteArray(inputStream);
            }

            log.debug("Adding tenant config to the registry");
            Resource resource = registry.newResource();
            resource.setMediaType(APIConstants.APPLICATION_JSON_MEDIA_TYPE);
            resource.setContent(data);

            registry.put(APIConstants.API_TENANT_CONF_LOCATION, resource);
        } catch (RegistryException e) {
            throw new APIManagementException("Error while saving tenant conf to the registry", e);
        } catch (IOException e) {
            throw new APIManagementException("Error while reading tenant conf file content", e);
        }
    }

    /**
     * @param tenantId
     * @throws APIManagementException
     */
    public static void createSelfSignUpRoles(int tenantId) throws APIManagementException {
        try {
            RegistryService registryService = ServiceReferenceHolder.getInstance().getRegistryService();
            UserRegistry govRegistry = registryService.getGovernanceSystemRegistry(tenantId);
            if (govRegistry.resourceExists(APIConstants.SELF_SIGN_UP_CONFIG_LOCATION)) {
                Resource resource = govRegistry.get(APIConstants.SELF_SIGN_UP_CONFIG_LOCATION);
                InputStream content = resource.getContentStream();
                DocumentBuilderFactory factory = getSecuredDocumentBuilder();
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                DocumentBuilder parser = factory.newDocumentBuilder();
                Document dc = parser.parse(content);
                boolean enableSubscriberRoleCreation = isSubscriberRoleCreationEnabled(tenantId);
                String signUpDomain = dc.getElementsByTagName(APIConstants.SELF_SIGN_UP_REG_DOMAIN_ELEM).item(0)
                        .getFirstChild().getNodeValue();

                if (enableSubscriberRoleCreation) {
                    int roleLength = dc.getElementsByTagName(APIConstants.SELF_SIGN_UP_REG_ROLE_NAME_ELEMENT)
                            .getLength();

                    for (int i = 0; i < roleLength; i++) {
                        String roleName = dc.getElementsByTagName(APIConstants.SELF_SIGN_UP_REG_ROLE_NAME_ELEMENT)
                                .item(i).getFirstChild().getNodeValue();
                        boolean isExternalRole = Boolean.parseBoolean(dc
                                .getElementsByTagName(APIConstants.SELF_SIGN_UP_REG_ROLE_IS_EXTERNAL).item(i)
                                .getFirstChild().getNodeValue());
                        if (roleName != null) {
                            // If isExternalRole==false ;create the subscriber role as an internal role
                            if (isExternalRole && signUpDomain != null) {
                                roleName = signUpDomain.toUpperCase() + CarbonConstants.DOMAIN_SEPARATOR + roleName;
                            } else {
                                roleName = UserCoreConstants.INTERNAL_DOMAIN + CarbonConstants.DOMAIN_SEPARATOR
                                        + roleName;
                            }
                            createSubscriberRole(roleName, tenantId);
                        }
                    }
                }
            }
            if (log.isDebugEnabled()) {
                log.debug("Adding Self signup configuration to the tenant's registry");
            }

        } catch (RegistryException e) {
            throw new APIManagementException("Error while getting Self signup role information from the registry", e);
        } catch (ParserConfigurationException e) {
            throw new APIManagementException("Error while getting Self signup role information from the registry", e);
        } catch (SAXException e) {
            throw new APIManagementException("Error while getting Self signup role information from the registry", e);
        } catch (IOException e) {
            throw new APIManagementException("Error while getting Self signup role information from the registry", e);
        }
    }

    /**
     * Returns whether subscriber role creation enabled for the given tenant in tenant-conf.json
     *
     * @param tenantId id of the tenant
     * @return true if subscriber role creation enabled in tenant-conf.json
     */
    public static boolean isSubscriberRoleCreationEnabled(int tenantId) throws APIManagementException {
        String tenantDomain = getTenantDomainFromTenantId(tenantId);
        JSONObject defaultRoles = getTenantDefaultRoles(tenantDomain);
        boolean isSubscriberRoleCreationEnabled = false;
        if (defaultRoles != null) {
            JSONObject subscriberRoleConfig = (JSONObject) defaultRoles
                    .get(APIConstants.API_TENANT_CONF_DEFAULT_ROLES_SUBSCRIBER_ROLE);
            isSubscriberRoleCreationEnabled = isRoleCreationEnabled(subscriberRoleConfig);
        }
        return isSubscriberRoleCreationEnabled;
    }

    /**
     * Create default roles specified in APIM per-tenant configuration file
     *
     * @param tenantId id of the tenant
     * @throws APIManagementException
     */
    public static void createDefaultRoles(int tenantId) throws APIManagementException {
        String tenantDomain = getTenantDomainFromTenantId(tenantId);
        JSONObject defaultRoles = getTenantDefaultRoles(tenantDomain);

        if (defaultRoles != null) {
            // create publisher role if it's creation is enabled in tenant-conf.json
            JSONObject publisherRoleConfig = (JSONObject) defaultRoles
                    .get(APIConstants.API_TENANT_CONF_DEFAULT_ROLES_PUBLISHER_ROLE);
            if (isRoleCreationEnabled(publisherRoleConfig)) {
                String publisherRoleName = String.valueOf(publisherRoleConfig
                        .get(APIConstants.API_TENANT_CONF_DEFAULT_ROLES_ROLENAME));
                if (!StringUtils.isBlank(publisherRoleName)) {
                    createPublisherRole(publisherRoleName, tenantId);
                }
            }

            // create creator role if it's creation is enabled in tenant-conf.json
            JSONObject creatorRoleConfig = (JSONObject) defaultRoles
                    .get(APIConstants.API_TENANT_CONF_DEFAULT_ROLES_CREATOR_ROLE);
            if (isRoleCreationEnabled(creatorRoleConfig)) {
                String creatorRoleName = String.valueOf(creatorRoleConfig
                        .get(APIConstants.API_TENANT_CONF_DEFAULT_ROLES_ROLENAME));
                if (!StringUtils.isBlank(creatorRoleName)) {
                    createCreatorRole(creatorRoleName, tenantId);
                }
            }

            createSelfSignUpRoles(tenantId);
        }
    }

    /**
     * Returns whether role creation enabled for the provided role config
     *
     * @param roleConfig role config in tenat-conf.json
     * @return true if role creation enabled for the provided role config
     */
    private static boolean isRoleCreationEnabled(JSONObject roleConfig) {
        boolean roleCreationEnabled = false;
        if (roleConfig != null && roleConfig.get(
                APIConstants.API_TENANT_CONF_DEFAULT_ROLES_CREATE_ON_TENANT_LOAD) != null && (Boolean) (roleConfig.get(
                APIConstants.API_TENANT_CONF_DEFAULT_ROLES_CREATE_ON_TENANT_LOAD))) {
            roleCreationEnabled = true;
        }
        return roleCreationEnabled;
    }

    public static boolean isAnalyticsEnabled() {
        return APIManagerAnalyticsConfiguration.getInstance().isAnalyticsEnabled();
    }

    /**
     * Returns whether API Publisher Access Control is enabled or not
     *
     * @return true if publisher access control enabled
     */
    public static boolean isAccessControlEnabled() {
        boolean accessControlEnabled = false;
        APIManagerConfiguration config = ServiceReferenceHolder.getInstance().
                getAPIManagerConfigurationService().getAPIManagerConfiguration();
        if (config.getFirstProperty(APIConstants.API_PUBLISHER_ENABLE_ACCESS_CONTROL_LEVELS) != null && config.getFirstProperty(APIConstants.API_PUBLISHER_ENABLE_ACCESS_CONTROL_LEVELS).equals("true")) {
            accessControlEnabled = true;
        }
        return accessControlEnabled;
    }

    /**
     * Add all the custom sequences of given type to registry
     *
     * @param registry           Registry instance
     * @param customSequenceType Custom sequence type which is in/out or fault
     * @throws APIManagementException
     */
    public static void addDefinedAllSequencesToRegistry(UserRegistry registry,
                                                        String customSequenceType)
            throws APIManagementException {

        InputStream inSeqStream = null;
        String seqFolderLocation =
                CarbonUtils.getCarbonHome() + File.separator + APIConstants.API_CUSTOM_SEQUENCES_FOLDER_LOCATION
                        + File.separator + customSequenceType;

        try {
            File inSequenceDir = new File(seqFolderLocation);
            File[] sequences;
            sequences = inSequenceDir.listFiles();

            if (sequences != null) {
                //Tracks whether new sequences are there to deploy
                boolean availableNewSequences = false;
                //Tracks whether json_fault.xml is in the registry
                boolean jsonFaultSeqInRegistry = false;

                for (File sequenceFile : sequences) {
                    String sequenceFileName = sequenceFile.getName();
                    String regResourcePath =
                            APIConstants.API_CUSTOM_SEQUENCE_LOCATION + '/' +
                                    customSequenceType + '/' + sequenceFileName;
                    if (registry.resourceExists(regResourcePath)) {
                        if (APIConstants.API_CUSTOM_SEQ_JSON_FAULT.equals(sequenceFileName)) {
                            jsonFaultSeqInRegistry = true;
                        }
                        if (log.isDebugEnabled()) {
                            log.debug("The sequence file with the name " + sequenceFileName
                                    + " already exists in the registry path " + regResourcePath);
                        }
                    } else {
                        availableNewSequences = true;
                        if (log.isDebugEnabled()) {
                            log.debug(
                                    "Adding sequence file with the name " + sequenceFileName + " to the registry path "
                                            + regResourcePath);
                        }

                        inSeqStream = new FileInputStream(sequenceFile);
                        byte[] inSeqData = IOUtils.toByteArray(inSeqStream);
                        Resource inSeqResource = registry.newResource();
                        inSeqResource.setContent(inSeqData);

                        registry.put(regResourcePath, inSeqResource);
                    }

                }
                //On the fly migration of json_fault.xml for 2.0.0 to 2.1.0
                if (APIConstants.API_CUSTOM_SEQUENCE_TYPE_FAULT.equals(customSequenceType) &&
                        availableNewSequences && jsonFaultSeqInRegistry) {
                    String oldFaultStatHandler = "org.wso2.carbon.apimgt.usage.publisher.APIMgtFaultHandler";
                    String newFaultStatHandler = "org.wso2.carbon.apimgt.gateway.handlers.analytics.APIMgtFaultHandler";
                    String regResourcePath =
                            APIConstants.API_CUSTOM_SEQUENCE_LOCATION + '/' +
                                    customSequenceType + '/' + APIConstants.API_CUSTOM_SEQ_JSON_FAULT;
                    Resource jsonFaultSeqResource = registry.get(regResourcePath);
                    String oldJsonFaultSeqContent = new String((byte[]) jsonFaultSeqResource.getContent(),
                            Charset.defaultCharset());
                    if (oldJsonFaultSeqContent != null && oldJsonFaultSeqContent.contains(oldFaultStatHandler)) {
                        String newJsonFaultContent =
                                oldJsonFaultSeqContent.replace(oldFaultStatHandler, newFaultStatHandler);
                        jsonFaultSeqResource.setContent(newJsonFaultContent);
                        registry.put(regResourcePath, jsonFaultSeqResource);
                    }

                }
            } else {
                log.error(
                        "Custom sequence template location unavailable for custom sequence type " +
                                customSequenceType + " : " + seqFolderLocation
                );
            }

        } catch (RegistryException e) {
            throw new APIManagementException(
                    "Error while saving defined sequences to the registry ", e);
        } catch (IOException e) {
            throw new APIManagementException("Error while reading defined sequence ", e);
        } finally {
            IOUtils.closeQuietly(inSeqStream);
        }

    }

    /**
     * Adds the sequences defined in repository/resources/customsequences folder to tenant registry
     *
     * @param tenantID tenant Id
     * @throws APIManagementException
     */
    public static void writeDefinedSequencesToTenantRegistry(int tenantID)
            throws APIManagementException {
        try {

            RegistryService registryService = ServiceReferenceHolder.getInstance().getRegistryService();
            UserRegistry govRegistry = registryService.getGovernanceSystemRegistry(tenantID);

            //Add all custom in,out and fault sequences to tenant registry
            APIUtil.addDefinedAllSequencesToRegistry(govRegistry, APIConstants.API_CUSTOM_SEQUENCE_TYPE_IN);
            APIUtil.addDefinedAllSequencesToRegistry(govRegistry, APIConstants.API_CUSTOM_SEQUENCE_TYPE_OUT);
            APIUtil.addDefinedAllSequencesToRegistry(govRegistry, APIConstants.API_CUSTOM_SEQUENCE_TYPE_FAULT);

        } catch (RegistryException e) {
            throw new APIManagementException(
                    "Error while saving defined sequences to the registry of tenant with id " + tenantID, e);
        }
    }

    /**
     * Load the  API RXT to the registry for tenants
     *
     * @param tenant
     * @param tenantID
     * @throws APIManagementException
     */

    public static void loadloadTenantAPIRXT(String tenant, int tenantID) throws APIManagementException {
        RegistryService registryService = ServiceReferenceHolder.getInstance().getRegistryService();
        UserRegistry registry = null;
        try {

            registry = registryService.getGovernanceSystemRegistry(tenantID);
        } catch (RegistryException e) {
            throw new APIManagementException("Error when create registry instance ", e);
        }

        String rxtDir = CarbonUtils.getCarbonHome() + File.separator + "repository" + File.separator + "resources" +
                File.separator + "rxts";
        File file = new File(rxtDir);
        FilenameFilter filenameFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                // if the file extension is .rxt return true, else false
                return name.endsWith(".rxt");
            }
        };
        String[] rxtFilePaths = file.list(filenameFilter);

        if (rxtFilePaths == null) {
            throw new APIManagementException("rxt files not found in directory " + rxtDir);
        }

        for (String rxtPath : rxtFilePaths) {
            String resourcePath = GovernanceConstants.RXT_CONFIGS_PATH + RegistryConstants.PATH_SEPARATOR + rxtPath;

            //This is  "registry" is a governance registry instance, therefore calculate the relative path to governance.
            String govRelativePath = RegistryUtils.getRelativePathToOriginal(resourcePath,
                    APIUtil.getMountedPath(RegistryContext.getBaseInstance(),
                            RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH));
            try {
                // calculate resource path
                RegistryAuthorizationManager authorizationManager = new RegistryAuthorizationManager
                        (ServiceReferenceHolder.getUserRealm());
                resourcePath = authorizationManager.computePathOnMount(resourcePath);

                org.wso2.carbon.user.api.AuthorizationManager authManager = ServiceReferenceHolder.getInstance().getRealmService().
                        getTenantUserRealm(tenantID).getAuthorizationManager();

                if (registry.resourceExists(govRelativePath)) {
                    // set anonymous user permission to RXTs
                    authManager.authorizeRole(APIConstants.ANONYMOUS_ROLE, resourcePath, ActionConstants.GET);
                    continue;
                }

                String rxt = FileUtil.readFileToString(rxtDir + File.separator + rxtPath);
                Resource resource = registry.newResource();
                resource.setContent(rxt.getBytes(Charset.defaultCharset()));
                resource.setMediaType(APIConstants.RXT_MEDIA_TYPE);
                registry.put(govRelativePath, resource);


                authManager.authorizeRole(APIConstants.ANONYMOUS_ROLE, resourcePath, ActionConstants.GET);

            } catch (UserStoreException e) {
                throw new APIManagementException("Error while adding role permissions to API", e);
            } catch (IOException e) {
                String msg = "Failed to read rxt files";
                throw new APIManagementException(msg, e);
            } catch (RegistryException e) {
                String msg = "Failed to add rxt to registry ";
                throw new APIManagementException(msg, e);
            }
        }

    }

    /**
     * Converting the user store domain name to uppercase.
     *
     * @param username Username to be modified
     * @return Username with domain name set to uppercase.
     */
    public static String setDomainNameToUppercase(String username) {
        String modifiedName = username;
        if (username != null) {
            String[] nameParts = username.split(CarbonConstants.DOMAIN_SEPARATOR);
            if (nameParts.length > 1) {
                modifiedName = nameParts[0].toUpperCase() + CarbonConstants.DOMAIN_SEPARATOR + nameParts[1];
            }
        }

        return modifiedName;
    }

    /**
     * Create APIM Subscriber role with the given name in specified tenant
     *
     * @param roleName role name
     * @param tenantId id of the tenant
     * @throws APIManagementException
     */
    public static void createSubscriberRole(String roleName, int tenantId) throws APIManagementException {
        Permission[] subscriberPermissions = new Permission[]{
                new Permission(APIConstants.Permissions.LOGIN, UserMgtConstants.EXECUTE_ACTION),
                new Permission(APIConstants.Permissions.API_SUBSCRIBE, UserMgtConstants.EXECUTE_ACTION)};
        createRole(roleName, subscriberPermissions, tenantId);
    }

    /**
     * Create APIM Publisher roles with the given name in specified tenant
     *
     * @param roleName role name
     * @param tenantId id of the tenant
     * @throws APIManagementException
     */
    public static void createPublisherRole(String roleName, int tenantId) throws APIManagementException {
        Permission[] publisherPermissions = new Permission[]{
                new Permission(APIConstants.Permissions.LOGIN, UserMgtConstants.EXECUTE_ACTION),
                new Permission(APIConstants.Permissions.API_PUBLISH, UserMgtConstants.EXECUTE_ACTION)};
        createRole(roleName, publisherPermissions, tenantId);
    }

    /**
     * Create APIM Creator roles with the given name in specified tenant
     *
     * @param roleName role name
     * @param tenantId id of the tenant
     * @throws APIManagementException
     */
    public static void createCreatorRole(String roleName, int tenantId) throws APIManagementException {
        Permission[] creatorPermissions = new Permission[]{
                new Permission(APIConstants.Permissions.LOGIN, UserMgtConstants.EXECUTE_ACTION),
                new Permission(APIConstants.Permissions.API_CREATE, UserMgtConstants.EXECUTE_ACTION),
                new Permission(APIConstants.Permissions.CONFIGURE_GOVERNANCE, UserMgtConstants.EXECUTE_ACTION),
                new Permission(APIConstants.Permissions.RESOURCE_GOVERN, UserMgtConstants.EXECUTE_ACTION)};
        createRole(roleName, creatorPermissions, tenantId);
    }

    /**
     * Creates a role with a given set of permissions for the specified tenant
     *
     * @param roleName    role name
     * @param permissions a set of permissions to be associated with the role
     * @param tenantId    id of the tenant
     * @throws APIManagementException
     */
    public static void createRole(String roleName, Permission[] permissions, int tenantId)
            throws APIManagementException {
        try {
            RealmService realmService = ServiceReferenceHolder.getInstance().getRealmService();
            UserRealm realm;
            org.wso2.carbon.user.api.UserRealm tenantRealm;
            UserStoreManager manager;

            if (tenantId < 0) {
                realm = realmService.getBootstrapRealm();
                manager = realm.getUserStoreManager();
            } else {
                tenantRealm = realmService.getTenantUserRealm(tenantId);
                manager = tenantRealm.getUserStoreManager();
            }
            if (!manager.isExistingRole(roleName)) {
                if (log.isDebugEnabled()) {
                    log.debug("Creating role: " + roleName);
                }
                String tenantAdminName = ServiceReferenceHolder.getInstance().getRealmService()
                        .getTenantUserRealm(tenantId).getRealmConfiguration().getAdminUserName();
                String[] userList = new String[]{tenantAdminName};
                manager.addRole(roleName, userList, permissions);
            }
        } catch (UserStoreException e) {
            throw new APIManagementException("Error while creating role: " + roleName, e);
        }
    }

    public void setupSelfRegistration(APIManagerConfiguration config, int tenantId) throws APIManagementException {
        boolean enabled = Boolean.parseBoolean(config.getFirstProperty(APIConstants.SELF_SIGN_UP_ENABLED));
        if (!enabled) {
            return;
        }
        // Create the subscriber role as an internal role
        String role = UserCoreConstants.INTERNAL_DOMAIN + CarbonConstants.DOMAIN_SEPARATOR
                + config.getFirstProperty(APIConstants.SELF_SIGN_UP_ROLE);
        if ((UserCoreConstants.INTERNAL_DOMAIN + CarbonConstants.DOMAIN_SEPARATOR).equals(role)) {
            // Required parameter missing - Throw an exception and interrupt startup
            throw new APIManagementException("Required subscriber role parameter missing "
                    + "in the self sign up configuration");
        }

        try {
            RealmService realmService = ServiceReferenceHolder.getInstance().getRealmService();
            UserRealm realm;
            org.wso2.carbon.user.api.UserRealm tenantRealm;
            UserStoreManager manager;

            if (tenantId < 0) {
                realm = realmService.getBootstrapRealm();
                manager = realm.getUserStoreManager();
            } else {
                tenantRealm = realmService.getTenantUserRealm(tenantId);
                manager = tenantRealm.getUserStoreManager();
            }
            if (!manager.isExistingRole(role)) {
                if (log.isDebugEnabled()) {
                    log.debug("Creating subscriber role: " + role);
                }
                Permission[] subscriberPermissions = new Permission[]{
                        new Permission("/permission/admin/login", UserMgtConstants.EXECUTE_ACTION),
                        new Permission(APIConstants.Permissions.API_SUBSCRIBE, UserMgtConstants.EXECUTE_ACTION)};
                String tenantAdminName = ServiceReferenceHolder.getInstance().getRealmService()
                        .getTenantUserRealm(tenantId).getRealmConfiguration().getAdminUserName();
                String[] userList = new String[]{tenantAdminName};
                manager.addRole(role, userList, subscriberPermissions);
            }
        } catch (UserStoreException e) {
            throw new APIManagementException("Error while creating subscriber role: " + role + " - "
                    + "Self registration might not function properly.", e);
        }
    }

    public static String removeAnySymbolFromUriTempate(String uriTemplate) {
        if (uriTemplate != null) {
            int anySymbolIndex = uriTemplate.indexOf("/*");
            if (anySymbolIndex != -1) {
                return uriTemplate.substring(0, anySymbolIndex);
            }
        }
        return uriTemplate;
    }

    public static float getAverageRating(APIIdentifier apiId) throws APIManagementException {
        return ApiMgtDAO.getInstance().getAverageRating(apiId);
    }

    public static float getAverageRating(int apiId) throws APIManagementException {
        return ApiMgtDAO.getInstance().getAverageRating(apiId);
    }

    public static List<Tenant> getAllTenantsWithSuperTenant() throws UserStoreException {
        Tenant[] tenants = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().getAllTenants();
        ArrayList<Tenant> tenantArrayList = new ArrayList<Tenant>();
        Collections.addAll(tenantArrayList, tenants);
        Tenant superAdminTenant = new Tenant();
        superAdminTenant.setDomain(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);
        superAdminTenant.setId(org.wso2.carbon.utils.multitenancy.MultitenantConstants.SUPER_TENANT_ID);
        superAdminTenant.setAdminName(CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME);
        tenantArrayList.add(superAdminTenant);
        return tenantArrayList;
    }

    /**
     * In multi tenant environment, publishers should allow only to revoke the tokens generated within his domain.
     * Super tenant should not see the tenant created tokens and vise versa. This method is used to check the logged in
     * user have permissions to revoke a given users tokens.
     *
     * @param loggedInUser   current logged in user to publisher
     * @param authorizedUser access token owner
     * @return
     */
    public static boolean isLoggedInUserAuthorizedToRevokeToken(String loggedInUser, String authorizedUser) {
        String loggedUserTenantDomain = MultitenantUtils.getTenantDomain(loggedInUser);
        String authorizedUserTenantDomain = MultitenantUtils.getTenantDomain(authorizedUser);

        if (MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(loggedUserTenantDomain) && MultitenantConstants
                .SUPER_TENANT_DOMAIN_NAME.equals(authorizedUserTenantDomain)) {
            return true;
        } else if (authorizedUserTenantDomain.equals(loggedUserTenantDomain)) {
            return true;
        }

        return false;
    }

    public static int getApplicationId(String appName, String userId) throws APIManagementException {
        return ApiMgtDAO.getInstance().getApplicationId(appName, userId);
    }

    public static int getApplicationId(String appName, String userId, String groupId) throws APIManagementException {
        Application application = ApiMgtDAO.getInstance().getApplicationByName(appName, userId, groupId);
        if (application != null) {
            return application.getId();
        } else {
            return 0;
        }
    }

    public static boolean isAPIManagementEnabled() {
        return Boolean.parseBoolean(CarbonUtils.getServerConfiguration().getFirstProperty("APIManagement.Enabled"));
    }

    public static boolean isLoadAPIContextsAtStartup() {
        return Boolean.parseBoolean(CarbonUtils.getServerConfiguration().getFirstProperty(
                "APIManagement.LoadAPIContextsInServerStartup"));
    }

    public static Set<APIStore> getExternalAPIStores(int tenantId) throws APIManagementException {
        SortedSet<APIStore> apistoreSet = new TreeSet<APIStore>(new APIStoreNameComparator());
        apistoreSet.addAll(getExternalStores(tenantId));
        return apistoreSet;

    }

    public static boolean isAllowDisplayAPIsWithMultipleStatus() {
        APIManagerConfiguration config = ServiceReferenceHolder.getInstance().
                getAPIManagerConfigurationService().getAPIManagerConfiguration();
        String displayAllAPIs = config.getFirstProperty(APIConstants.API_STORE_DISPLAY_ALL_APIS);
        if (displayAllAPIs == null) {
            log.warn("The configurations related to show deprecated APIs in APIStore " +
                    "are missing in api-manager.xml.");
            return false;
        }
        return Boolean.parseBoolean(displayAllAPIs);
    }

    public static boolean isAllowDisplayMultipleVersions() {
        APIManagerConfiguration config = ServiceReferenceHolder.getInstance().
                getAPIManagerConfigurationService().getAPIManagerConfiguration();

        String displayMultiVersions = config.getFirstProperty(APIConstants.API_STORE_DISPLAY_MULTIPLE_VERSIONS);
        if (displayMultiVersions == null) {
            log.warn("The configurations related to show multiple versions of API in APIStore " +
                    "are missing in api-manager.xml.");
            return false;
        }
        return Boolean.parseBoolean(displayMultiVersions);
    }

    public static boolean updateNullThrottlingTierAtStartup() {
        APIManagerConfiguration configuration = ServiceReferenceHolder.getInstance().
                getAPIManagerConfigurationService().getAPIManagerConfiguration();
        String isNullThrottlingTierUpdateEnabled = configuration
                .getFirstProperty("StartupConfiguration.UpdateNullThrottlingTier");
        return isNullThrottlingTierUpdateEnabled == null || Boolean.parseBoolean(isNullThrottlingTierUpdateEnabled);
    }

    public static Set<APIStore> getExternalAPIStores(Set<APIStore> inputStores, int tenantId)
            throws APIManagementException {
        SortedSet<APIStore> apiStores = new TreeSet<APIStore>(new APIStoreNameComparator());
        apiStores.addAll(getExternalStores(tenantId));
        //Retains only the stores that contained in configuration
        inputStores.retainAll(apiStores);
        boolean exists = false;
        if (!apiStores.isEmpty()) {
            for (APIStore store : apiStores) {
                for (APIStore inputStore : inputStores) {
                    if (inputStore.getName().equals(store.getName())) { // If the configured apistore already stored in
                        // db,ignore adding it again
                        exists = true;
                    }
                }
                if (!exists) {
                    inputStores.add(store);
                }
                exists = false;
            }
        }
        return inputStores;
    }

    public static boolean isAPIsPublishToExternalAPIStores(int tenantId)
            throws APIManagementException {
        return !getExternalStores(tenantId).isEmpty();
    }

    public static boolean isAPIGatewayKeyCacheEnabled() {
        try {
            APIManagerConfiguration config = ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService()
                    .getAPIManagerConfiguration();
            String serviceURL = config.getFirstProperty(APIConstants.GATEWAY_TOKEN_CACHE_ENABLED);
            return Boolean.parseBoolean(serviceURL);
        } catch (Exception e) {
            log.error("Did not found valid API Validation Information cache configuration. Use default configuration"
                    + e);
        }
        return true;
    }


    public static Cache getAPIContextCache() {
        CacheManager contextCacheManager = Caching.getCacheManager(APIConstants.API_CONTEXT_CACHE_MANAGER).
                getCache(APIConstants.API_CONTEXT_CACHE).getCacheManager();
        if (!isContextCacheInitialized) {
            isContextCacheInitialized = true;
            return contextCacheManager.<String, Boolean>createCacheBuilder(APIConstants.API_CONTEXT_CACHE_MANAGER).
                    setExpiry(CacheConfiguration.ExpiryType.MODIFIED, new CacheConfiguration.Duration(TimeUnit.DAYS,
                            APIConstants.API_CONTEXT_CACHE_EXPIRY_TIME_IN_DAYS)).setStoreByValue(false).build();
        } else {
            return Caching.getCacheManager(APIConstants.API_CONTEXT_CACHE_MANAGER).getCache(APIConstants.API_CONTEXT_CACHE);
        }
    }

    /**
     * Get active tenant domains
     *
     * @return
     * @throws UserStoreException
     */
    public static Set<String> getActiveTenantDomains() throws UserStoreException {
        Set<String> tenantDomains;
        Tenant[] tenants = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().getAllTenants();
        if (tenants == null || tenants.length == 0) {
            tenantDomains = Collections.<String>emptySet();
        } else {
            tenantDomains = new HashSet<String>();
            for (Tenant tenant : tenants) {
                if (tenant.isActive()) {
                    tenantDomains.add(tenant.getDomain());
                }
            }
            if (!tenantDomains.isEmpty()) {
                tenantDomains.add(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);
            }
        }
        return tenantDomains;
    }


    /**
     * Retrieves the role list of system
     *
     * @throws APIManagementException If an error occurs
     */
    public static String[] getRoleNames(String username) throws APIManagementException {

        String tenantDomain = MultitenantUtils.getTenantDomain(username);
        try {
            if (!org.wso2.carbon.utils.multitenancy.MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
                int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager()
                        .getTenantId(tenantDomain);
                UserStoreManager manager = ServiceReferenceHolder.getInstance().getRealmService()
                        .getTenantUserRealm(tenantId).getUserStoreManager();

                return manager.getRoleNames();
            } else {
                return AuthorizationManager.getInstance().getRoleNames();
            }
        } catch (UserStoreException e) {
            log.error("Error while getting all the roles", e);
            return new String[0];

        }
    }

    /**
     * Check whether the user has the given role
     *
     * @throws UserStoreException
     * @throws APIManagementException
     */
    public static boolean isUserInRole(String user, String role) throws UserStoreException, APIManagementException {
        String tenantDomain = MultitenantUtils.getTenantDomain(APIUtil.replaceEmailDomainBack(user));
        UserRegistrationConfigDTO signupConfig = SelfSignUpUtil.getSignupConfiguration(tenantDomain);
        user = SelfSignUpUtil.getDomainSpecificUserName(user, signupConfig);
        String tenantAwareUserName = MultitenantUtils.getTenantAwareUsername(user);
        RealmService realmService = ServiceReferenceHolder.getInstance().getRealmService();
        int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager()
                .getTenantId(tenantDomain);
        UserRealm realm = (UserRealm) realmService.getTenantUserRealm(tenantId);
        org.wso2.carbon.user.core.UserStoreManager manager = realm.getUserStoreManager();
        AbstractUserStoreManager abstractManager = (AbstractUserStoreManager) manager;
        return abstractManager.isUserInRole(tenantAwareUserName, role);
    }

    /**
     * check whether given role is exist
     *
     * @param userName logged user
     * @param roleName role name need to check
     * @return true if exist and false if not
     * @throws APIManagementException If an error occurs
     */
    public static boolean isRoleNameExist(String userName, String roleName) throws APIManagementException {
        if (roleName == null || StringUtils.isEmpty(roleName.trim())) {
            return true;
        }

        //disable role validation if "disableRoleValidationAtScopeCreation" system property is set
        String disableRoleValidation = System.getProperty(DISABLE_ROLE_VALIDATION_AT_SCOPE_CREATION);
        if (Boolean.parseBoolean(disableRoleValidation)) {
            return true;
        }

        org.wso2.carbon.user.api.UserStoreManager userStoreManager;
        try {
            RealmService realmService = ServiceReferenceHolder.getInstance().getRealmService();
            int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager()
                    .getTenantId(MultitenantUtils.getTenantDomain(userName));
            userStoreManager = realmService.getTenantUserRealm(tenantId).getUserStoreManager();

            String[] roles = roleName.split(",");
            for (String role : roles) {
                if (!userStoreManager.isExistingRole(role.trim())) {
                    return false;
                }
            }
        } catch (org.wso2.carbon.user.api.UserStoreException e) {
            log.error("Error when getting the list of roles", e);
            return false;
        }
        return true;
    }

    /**
     * Create API Definition in JSON
     *
     * @param api API
     * @throws org.wso2.carbon.apimgt.api.APIManagementException if failed to generate the content and save
     * @deprecated
     */

    @Deprecated
    public static String createSwaggerJSONContent(API api) throws APIManagementException {
        APIIdentifier identifier = api.getId();

        APIManagerConfiguration config = ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService()
                .getAPIManagerConfiguration();

        Environment environment = (Environment) config.getApiGatewayEnvironments().values().toArray()[0];
        String endpoints = environment.getApiGatewayEndpoint();
        String[] endpointsSet = endpoints.split(",");
        String apiContext = api.getContext();
        String version = identifier.getVersion();
        Set<URITemplate> uriTemplates = api.getUriTemplates();
        String description = api.getDescription();

        // With the new context version strategy, the URL prefix is the apiContext. the verison will be embedded in
        // the apiContext.
        String urlPrefix = apiContext;

        if (endpointsSet.length < 1) {
            throw new APIManagementException("Error in creating JSON representation of the API"
                    + identifier.getApiName());
        }
        if (description == null || "".equals(description)) {
            description = "";
        } else {
            description = description.trim();
        }

        Map<String, List<Operation>> uriTemplateDefinitions = new HashMap<String, List<Operation>>();
        List<APIResource> apis = new ArrayList<APIResource>();
        for (URITemplate template : uriTemplates) {
            List<Operation> ops;
            List<Parameter> parameters;
            String path = urlPrefix + APIUtil.removeAnySymbolFromUriTempate(template.getUriTemplate());
            /* path exists in uriTemplateDefinitions */
            if (uriTemplateDefinitions.get(path) != null) {
                ops = uriTemplateDefinitions.get(path);
                parameters = new ArrayList<Parameter>();

                String httpVerb = template.getHTTPVerb();
                /* For GET and DELETE Parameter name - Query Parameters */
                if (Constants.Configuration.HTTP_METHOD_GET.equals(httpVerb)
                        || Constants.Configuration.HTTP_METHOD_DELETE.equals(httpVerb)) {
                    Parameter queryParam = new Parameter(APIConstants.OperationParameter.QUERY_PARAM_NAME,
                            APIConstants.OperationParameter.QUERY_PARAM_DESCRIPTION,
                            APIConstants.OperationParameter.PAYLOAD_PARAM_TYPE, false, false, "String");
                    parameters.add(queryParam);
                } else {/* For POST, PUT and PATCH Parameter name - Payload */
                    Parameter payLoadParam = new Parameter(APIConstants.OperationParameter.PAYLOAD_PARAM_NAME,
                            APIConstants.OperationParameter.PAYLOAD_PARAM_DESCRIPTION,
                            APIConstants.OperationParameter.PAYLOAD_PARAM_TYPE, false, false, "String");
                    parameters.add(payLoadParam);
                }

                Parameter authParam = new Parameter(APIConstants.OperationParameter.AUTH_PARAM_NAME,
                        APIConstants.OperationParameter.AUTH_PARAM_DESCRIPTION,
                        APIConstants.OperationParameter.AUTH_PARAM_TYPE, false, false, "String");
                parameters.add(authParam);
                if (!"OPTIONS".equals(httpVerb)) {
                    Operation op = new Operation(httpVerb, description, description, parameters);
                    ops.add(op);
                }
            } else {/* path not exists in uriTemplateDefinitions */
                ops = new ArrayList<Operation>();
                parameters = new ArrayList<Parameter>();

                String httpVerb = template.getHTTPVerb();
                /* For GET and DELETE Parameter name - Query Parameters */
                if (Constants.Configuration.HTTP_METHOD_GET.equals(httpVerb)
                        || Constants.Configuration.HTTP_METHOD_DELETE.equals(httpVerb)) {
                    Parameter queryParam = new Parameter(APIConstants.OperationParameter.QUERY_PARAM_NAME,
                            APIConstants.OperationParameter.QUERY_PARAM_DESCRIPTION,
                            APIConstants.OperationParameter.PAYLOAD_PARAM_TYPE, false, false, "String");
                    parameters.add(queryParam);
                } else {/* For POST,PUT and PATCH Parameter name - Payload */
                    Parameter payLoadParam = new Parameter(APIConstants.OperationParameter.PAYLOAD_PARAM_NAME,
                            APIConstants.OperationParameter.PAYLOAD_PARAM_DESCRIPTION,
                            APIConstants.OperationParameter.PAYLOAD_PARAM_TYPE, false, false, "String");
                    parameters.add(payLoadParam);
                }
                Parameter authParam = new Parameter(APIConstants.OperationParameter.AUTH_PARAM_NAME,
                        APIConstants.OperationParameter.AUTH_PARAM_DESCRIPTION,
                        APIConstants.OperationParameter.AUTH_PARAM_TYPE, false, false, "String");
                parameters.add(authParam);
                if (!"OPTIONS".equals(httpVerb)) {
                    Operation op = new Operation(httpVerb, description, description, parameters);
                    ops.add(op);
                }
                uriTemplateDefinitions.put(path, ops);
            }
        }

        final Set<Entry<String, List<Operation>>> entries = uriTemplateDefinitions.entrySet();

        for (Entry entry : entries) {
            APIResource apiResource = new APIResource((String) entry.getKey(), description,
                    (List<Operation>) entry.getValue());
            apis.add(apiResource);
        }
        APIDefinition apidefinition = new APIDefinition(version, APIConstants.SWAGGER_VERSION, endpointsSet[0],
                apiContext, apis);

        Gson gson = new Gson();
        return gson.toJson(apidefinition);
    }

    /**
     * Helper method to get tenantId from userName
     *
     * @param userName user name
     * @return tenantId
     */
    public static int getTenantId(String userName) {
        //get tenant domain from user name
        String tenantDomain = MultitenantUtils.getTenantDomain(userName);
        return getTenantIdFromTenantDomain(tenantDomain);
    }

    /**
     * Helper method to get tenantId from tenantDomain
     *
     * @param tenantDomain tenant Domain
     * @return tenantId
     */
    public static int getTenantIdFromTenantDomain(String tenantDomain) {
        RealmService realmService = ServiceReferenceHolder.getInstance().getRealmService();

        if (realmService == null || tenantDomain == null) {
            return MultitenantConstants.SUPER_TENANT_ID;
        }

        try {
            return realmService.getTenantManager().getTenantId(tenantDomain);
        } catch (UserStoreException e) {
            log.error(e.getMessage(), e);
        }

        return -1;
    }

    /**
     * Helper method to get tenantDomain from tenantId
     *
     * @param tenantId tenant Id
     * @return tenantId
     */
    public static String getTenantDomainFromTenantId(int tenantId) {
        RealmService realmService = ServiceReferenceHolder.getInstance().getRealmService();

        if (realmService == null) {
            return MultitenantConstants.SUPER_TENANT_DOMAIN_NAME;
        }

        try {
            return realmService.getTenantManager().getDomain(tenantId);
        } catch (UserStoreException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    public static int getSuperTenantId() {
        return MultitenantConstants.SUPER_TENANT_ID;
    }

    /**
     * Helper method to get username with tenant domain.
     *
     * @param userName
     * @return userName with tenant domain
     */
    public static String getUserNameWithTenantSuffix(String userName) {
        String userNameWithTenantPrefix = userName;
        String tenantDomain = MultitenantUtils.getTenantDomain(userName);
        if (userName != null && !userName.endsWith("@" + MultitenantConstants.SUPER_TENANT_DOMAIN_NAME)
                && MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
            userNameWithTenantPrefix = userName + "@" + tenantDomain;
        }

        return userNameWithTenantPrefix;
    }

    /**
     * Build OMElement from inputstream
     *
     * @param inputStream
     * @return
     * @throws Exception
     */
    public static OMElement buildOMElement(InputStream inputStream) throws Exception {
        XMLStreamReader parser;
        StAXOMBuilder builder;
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            parser = factory.createXMLStreamReader(inputStream);
            builder = new StAXOMBuilder(parser);
        } catch (XMLStreamException e) {
            String msg = "Error in initializing the parser.";
            log.error(msg, e);
            throw new Exception(msg, e);
        }

        return builder.getDocumentElement();
    }


    /**
     * Get stored in sequences, out sequences and fault sequences from the governanceSystem registry
     *
     * @param sequenceName -The sequence to be retrieved
     * @param tenantId
     * @param direction    - Direction indicates which sequences to fetch. Values would be
     *                     "in", "out" or "fault"
     * @return
     * @throws APIManagementException
     */
    public static OMElement getCustomSequence(String sequenceName, int tenantId, String direction,
                                              APIIdentifier identifier) throws APIManagementException {
        org.wso2.carbon.registry.api.Collection seqCollection = null;

        try {
            UserRegistry registry = ServiceReferenceHolder.getInstance().getRegistryService()
                    .getGovernanceSystemRegistry(tenantId);

            if ("in".equals(direction)) {
                seqCollection = (org.wso2.carbon.registry.api.Collection) registry
                        .get(APIConstants.API_CUSTOM_INSEQUENCE_LOCATION);
            } else if ("out".equals(direction)) {
                seqCollection = (org.wso2.carbon.registry.api.Collection) registry
                        .get(APIConstants.API_CUSTOM_OUTSEQUENCE_LOCATION);
            } else if ("fault".equals(direction)) {
                seqCollection = (org.wso2.carbon.registry.api.Collection) registry
                        .get(APIConstants.API_CUSTOM_FAULTSEQUENCE_LOCATION);
            }

            if (seqCollection == null) {
                seqCollection = (org.wso2.carbon.registry.api.Collection) registry.get(getSequencePath(identifier,
                        direction));

            }

            if (seqCollection != null) {
                String[] childPaths = seqCollection.getChildren();

                for (String childPath : childPaths) {
                    Resource sequence = registry.get(childPath);
                    OMElement seqElment = APIUtil.buildOMElement(sequence.getContentStream());
                    if (sequenceName.equals(seqElment.getAttributeValue(new QName("name")))) {
                        return seqElment;
                    }
                }
            }

            // If the sequence not found the default sequences, check in custom sequences

            seqCollection = (org.wso2.carbon.registry.api.Collection) registry.get(getSequencePath(identifier,
                    direction));
            if (seqCollection != null) {
                String[] childPaths = seqCollection.getChildren();

                for (String childPath : childPaths) {
                    Resource sequence = registry.get(childPath);
                    OMElement seqElment = APIUtil.buildOMElement(sequence.getContentStream());
                    if (sequenceName.equals(seqElment.getAttributeValue(new QName("name")))) {
                        return seqElment;
                    }
                }
            }

        } catch (Exception e) {
            String msg = "Issue is in accessing the Registry";
            log.error(msg);
            throw new APIManagementException(msg, e);
        }
        return null;
    }

    /**
     * Returns true if the sequence is a per API one
     *
     * @param sequenceName
     * @param tenantId
     * @param identifier   API identifier
     * @param sequenceType in/out/fault
     * @return true/false
     * @throws APIManagementException
     */
    public static boolean isPerAPISequence(String sequenceName, int tenantId, APIIdentifier identifier,
                                           String sequenceType) throws APIManagementException {
        org.wso2.carbon.registry.api.Collection seqCollection = null;
        try {
            UserRegistry registry = ServiceReferenceHolder.getInstance().getRegistryService()
                    .getGovernanceSystemRegistry(tenantId);

            // If the sequence not found the default sequences, check in custom sequences

            if (registry.resourceExists(getSequencePath(identifier, sequenceType))) {

                seqCollection = (org.wso2.carbon.registry.api.Collection) registry.get(getSequencePath(identifier,
                        sequenceType));
                if (seqCollection != null) {
                    String[] childPaths = seqCollection.getChildren();

                    for (String childPath : childPaths) {
                        Resource sequence = registry.get(childPath);
                        OMElement seqElment = APIUtil.buildOMElement(sequence.getContentStream());
                        if (sequenceName.equals(seqElment.getAttributeValue(new QName("name")))) {
                            return true;
                        }
                    }
                }
            }


        } catch (RegistryException e) {
            String msg = "Error while retrieving registry for tenant " + tenantId;
            log.error(msg);
            throw new APIManagementException(msg, e);
        } catch (org.wso2.carbon.registry.api.RegistryException e) {
            String msg = "Error while processing the " + sequenceType + " sequences of " + identifier
                    + " in the registry";
            log.error(msg);
            throw new APIManagementException(msg, e);
        } catch (Exception e) {
            throw new APIManagementException(e.getMessage(), e);
        }
        return false;
    }

    /**
     * Returns uuid correspond to the given sequence name and direction
     *
     * @param sequenceName name of the  sequence
     * @param tenantId     logged in user's tenantId
     * @param direction    in/out/fault
     * @param identifier   API identifier
     * @return uuid of the given mediation sequence or null
     * @throws APIManagementException If failed to get the uuid of the mediation sequence
     */
    public static String getMediationSequenceUuid(String sequenceName, int tenantId, String direction,
                                                  APIIdentifier identifier) throws
            APIManagementException {
        org.wso2.carbon.registry.api.Collection seqCollection = null;
        String seqCollectionPath;

        try {
            UserRegistry registry = ServiceReferenceHolder.getInstance().getRegistryService()
                    .getGovernanceSystemRegistry(tenantId);

            if ("in".equals(direction)) {
                seqCollection = (org.wso2.carbon.registry.api.Collection) registry
                        .get(APIConstants.API_CUSTOM_SEQUENCE_LOCATION + File.separator +
                                APIConstants.API_CUSTOM_SEQUENCE_TYPE_IN);
            } else if ("out".equals(direction)) {
                seqCollection = (org.wso2.carbon.registry.api.Collection) registry
                        .get(APIConstants.API_CUSTOM_SEQUENCE_LOCATION + File.separator +
                                APIConstants.API_CUSTOM_SEQUENCE_TYPE_OUT);
            } else if ("fault".equals(direction)) {
                seqCollection = (org.wso2.carbon.registry.api.Collection) registry
                        .get(APIConstants.API_CUSTOM_SEQUENCE_LOCATION + File.separator +
                                APIConstants.API_CUSTOM_SEQUENCE_TYPE_FAULT);
            }

            if (seqCollection == null) {
                seqCollection = (org.wso2.carbon.registry.api.Collection) registry.get
                        (getSequencePath(identifier,
                                direction));

            }
            if (seqCollection != null) {
                String[] childPaths = seqCollection.getChildren();
                for (String childPath : childPaths) {
                    Resource sequence = registry.get(childPath);
                    OMElement seqElment = APIUtil.buildOMElement(sequence.getContentStream());
                    String seqElmentName = seqElment.getAttributeValue(new QName("name"));
                    if (sequenceName.equals(seqElmentName)) {
                        return sequence.getUUID();
                    }
                }
            }

            // If the sequence not found the default sequences, check in custom sequences

            seqCollection = (org.wso2.carbon.registry.api.Collection) registry.get
                    (getSequencePath(identifier, direction));
            if (seqCollection != null) {
                String[] childPaths = seqCollection.getChildren();
                for (String childPath : childPaths) {
                    Resource sequence = registry.get(childPath);
                    OMElement seqElment = APIUtil.buildOMElement(sequence.getContentStream());
                    if (sequenceName.equals(seqElment.getAttributeValue(new QName("name")))) {
                        return sequence.getUUID();
                    }
                }
            }

        } catch (Exception e) {
            String msg = "Issue is in accessing the Registry";
            log.error(msg);
            throw new APIManagementException(msg, e);
        }
        return null;
    }

    /**
     * Returns true if sequence is set
     *
     * @param sequence
     * @return
     */
    public static boolean isSequenceDefined(String sequence) {
        return sequence != null && !"none".equals(sequence);
    }

    /**
     * Return the sequence extension name.
     * eg: admin--testAPi--v1.00
     *
     * @param api
     * @return
     */
    public static String getSequenceExtensionName(API api) {
        return api.getId().getProviderName() + "--" + api.getId().getApiName() + ":v" + api.getId().getVersion();
    }

    /**
     * @param token
     * @return
     */
    public static String decryptToken(String token) throws CryptoException {
        APIManagerConfiguration config = ServiceReferenceHolder.getInstance().
                getAPIManagerConfigurationService().getAPIManagerConfiguration();

        if (Boolean.parseBoolean(config.getFirstProperty(APIConstants.ENCRYPT_TOKENS_ON_PERSISTENCE))) {
            return new String(CryptoUtil.getDefaultCryptoUtil().base64DecodeAndDecrypt(token), Charset.defaultCharset());
        }

        String enableTokenHashMode = config.getFirstProperty(APIConstants.HASH_TOKENS_ON_PERSISTENCE);
        if (enableTokenHashMode != null && Boolean.parseBoolean(enableTokenHashMode)) {
            return null;
        }
        return token;
    }

    /**
     * @param token
     * @return
     */
    public static String encryptToken(String token) throws CryptoException, APIManagementException {
        APIManagerConfiguration config = ServiceReferenceHolder.getInstance().
                getAPIManagerConfigurationService().getAPIManagerConfiguration();

        if (Boolean.parseBoolean(config.getFirstProperty(APIConstants.ENCRYPT_TOKENS_ON_PERSISTENCE))) {
            return CryptoUtil.getDefaultCryptoUtil().encryptAndBase64Encode(token.getBytes(Charset.defaultCharset()));
        }

        String enableTokenHashMode = config.getFirstProperty(APIConstants.HASH_TOKENS_ON_PERSISTENCE);
        if (enableTokenHashMode != null && Boolean.parseBoolean(enableTokenHashMode)) {
            return hash(token);
        }
        return token;
    }

    /**
     * Method to generate hash value.
     *
     * @param plainText Plain text value.
     * @return hashed value.
     */
    private static String hash(String plainText) throws APIManagementException {

        if (log.isDebugEnabled()) {
            log.debug("Hashing the token for " + plainText);
        }

        if (StringUtils.isEmpty(plainText)) {
            throw new APIManagementException("plainText value is null or empty to be hash.");
        }

        MessageDigest messageDigest = null;
        byte[] hash = null;
        String hashAlgorithm = OAuthServerConfiguration.getInstance().getHashAlgorithm();
        if (log.isDebugEnabled()) {
            log.debug("Getting the hash algorithm from the configuration: " + hashAlgorithm);
        }
        try {
            messageDigest = MessageDigest.getInstance(hashAlgorithm);
            messageDigest.update(plainText.getBytes());
            hash = messageDigest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new APIManagementException(
                    "Error while retrieving MessageDigest for the provided hash algorithm: " + hashAlgorithm, e);
        }
        JSONObject object = new JSONObject();
        object.put("algorithm", hashAlgorithm);
        object.put("hash", bytesToHex(hash));

        return object.toString();
    }

    private static String bytesToHex(byte[] bytes) {

        StringBuilder result = new StringBuilder();
        for (byte byt : bytes) {
            result.append(Integer.toString((byt & 0xff) + 0x100, 16).substring(1));
        }
        return result.toString();
    }

    public static void loadTenantRegistry(int tenantId) throws RegistryException {
        TenantRegistryLoader tenantRegistryLoader = APIManagerComponent.getTenantRegistryLoader();
        ServiceReferenceHolder.getInstance().getIndexLoaderService().loadTenantIndex(tenantId);
        tenantRegistryLoader.loadTenantRegistry(tenantId);
    }

    /**
     * This is to get the registry resource's HTTP permlink path.
     * Once this issue is fixed (https://wso2.org/jira/browse/REGISTRY-2110),
     * we can remove this method, and get permlink from the resource.
     *
     * @param path - Registry resource path
     * @return {@link String} -HTTP permlink
     */
    public static String getRegistryResourceHTTPPermlink(String path) {
        String schemeHttp = APIConstants.HTTP_PROTOCOL;
        String schemeHttps = APIConstants.HTTPS_PROTOCOL;

        ConfigurationContextService contetxservice = ServiceReferenceHolder.getContextService();
        //First we will try to generate http permalink and if its disabled then only we will consider https
        int port = CarbonUtils.getTransportProxyPort(contetxservice.getServerConfigContext(), schemeHttp);
        if (port == -1) {
            port = CarbonUtils.getTransportPort(contetxservice.getServerConfigContext(), schemeHttp);
        }
        //getting https parameters if http is disabled. If proxy port is not present we will go for default port
        if (port == -1) {
            port = CarbonUtils.getTransportProxyPort(contetxservice.getServerConfigContext(), schemeHttps);
        }
        if (port == -1) {
            port = CarbonUtils.getTransportPort(contetxservice.getServerConfigContext(), schemeHttps);
        }

        String webContext = ServerConfiguration.getInstance().getFirstProperty("WebContextRoot");

        if (webContext == null || "/".equals(webContext)) {
            webContext = "";
        }
        RegistryService registryService = ServiceReferenceHolder.getInstance().getRegistryService();
        String version = "";
        if (registryService == null) {
            log.error("Registry Service has not been set.");
        } else if (path != null) {
            try {
                String[] versions = registryService.getRegistry(
                        CarbonConstants.REGISTRY_SYSTEM_USERNAME,
                        CarbonContext.getThreadLocalCarbonContext().getTenantId()).getVersions(path);
                if (versions != null && versions.length > 0) {
                    version = versions[0].substring(versions[0].lastIndexOf(";version:"));
                }
            } catch (RegistryException e) {
                log.error("An error occurred while determining the latest version of the " +
                        "resource at the given path: " + path, e);
            }
        }
        if (port != -1 && path != null) {
            String tenantDomain =
                    PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain(true);
            return webContext +
                    ((tenantDomain != null &&
                            !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) ?
                            "/" + MultitenantConstants.TENANT_AWARE_URL_PREFIX + "/" + tenantDomain :
                            "") +
                    "/registry/resource" +
                    org.wso2.carbon.registry.app.Utils.encodeRegistryPath(path) + version;
        }
        return null;
    }


    public static boolean isSandboxEndpointsExists(API api) {
        JSONParser parser = new JSONParser();
        JSONObject config = null;
        try {
            config = (JSONObject) parser.parse(api.getEndpointConfig());

            if (config.containsKey("sandbox_endpoints")) {
                return true;
            }
        } catch (ParseException e) {
            log.error(APIConstants.MSG_JSON_PARSE_ERROR, e);
        } catch (ClassCastException e) {
            log.error(APIConstants.MSG_JSON_PARSE_ERROR, e);
        }
        return false;
    }

    public static boolean isProductionEndpointsExists(API api) {
        JSONParser parser = new JSONParser();
        JSONObject config = null;
        try {
            config = (JSONObject) parser.parse(api.getEndpointConfig());

            if (config.containsKey("production_endpoints")) {
                return true;
            }
        } catch (ParseException e) {
            log.error(APIConstants.MSG_JSON_PARSE_ERROR, e);
        } catch (ClassCastException e) {
            log.error(APIConstants.MSG_JSON_PARSE_ERROR, e);
        }
        return false;
    }

    /**
     * This method used to get API minimum information from governance artifact
     *
     * @param artifact API artifact
     * @param registry Registry
     * @return API
     * @throws APIManagementException if failed to get API from artifact
     */
    public static API getAPIInformation(GovernanceArtifact artifact, Registry registry) throws APIManagementException {
        API api;
        try {
            String providerName = artifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER);
            String apiName = artifact.getAttribute(APIConstants.API_OVERVIEW_NAME);
            String apiVersion = artifact.getAttribute(APIConstants.API_OVERVIEW_VERSION);
            api = new API(new APIIdentifier(providerName, apiName, apiVersion));
            //set uuid
            api.setUUID(artifact.getId());
            api.setThumbnailUrl(artifact.getAttribute(APIConstants.API_OVERVIEW_THUMBNAIL_URL));
            api.setStatus(getLcStateFromArtifact(artifact));
            api.setContext(artifact.getAttribute(APIConstants.API_OVERVIEW_CONTEXT));
            api.setVisibility(artifact.getAttribute(APIConstants.API_OVERVIEW_VISIBILITY));
            api.setVisibleRoles(artifact.getAttribute(APIConstants.API_OVERVIEW_VISIBLE_ROLES));
            api.setVisibleTenants(artifact.getAttribute(APIConstants.API_OVERVIEW_VISIBLE_TENANTS));
            api.setTransports(artifact.getAttribute(APIConstants.API_OVERVIEW_TRANSPORTS));
            api.setInSequence(artifact.getAttribute(APIConstants.API_OVERVIEW_INSEQUENCE));
            api.setOutSequence(artifact.getAttribute(APIConstants.API_OVERVIEW_OUTSEQUENCE));
            api.setFaultSequence(artifact.getAttribute(APIConstants.API_OVERVIEW_FAULTSEQUENCE));
            api.setDescription(artifact.getAttribute(APIConstants.API_OVERVIEW_DESCRIPTION));
            api.setRedirectURL(artifact.getAttribute(APIConstants.API_OVERVIEW_REDIRECT_URL));
            api.setBusinessOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_BUSS_OWNER));
            api.setApiOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_OWNER));
            api.setAdvertiseOnly(Boolean.parseBoolean(artifact.getAttribute(APIConstants.API_OVERVIEW_ADVERTISE_ONLY)));
            String environments = artifact.getAttribute(APIConstants.API_OVERVIEW_ENVIRONMENTS);
            api.setEnvironments(extractEnvironmentsForAPI(environments));
            api.setCorsConfiguration(getCorsConfigurationFromArtifact(artifact));
            String artifactPath = GovernanceUtils.getArtifactPath(registry, artifact.getId());
            api.setLastUpdated(registry.get(artifactPath).getLastModified());
            api.setCreatedTime(String.valueOf(registry.get(artifactPath).getCreatedTime().getTime()));
            api.setGatewayLabels(getLabelsFromAPIGovernanceArtifact(artifact, providerName));
        } catch (GovernanceException e) {
            String msg = "Failed to get API from artifact ";
            throw new APIManagementException(msg, e);
        } catch (RegistryException e) {
            String msg = "Failed to get LastAccess time or Rating";
            throw new APIManagementException(msg, e);
        }
        return api;
    }

    /**
     * Get the cache key of the ResourceInfoDTO
     *
     * @param apiContext  - Context of the API
     * @param apiVersion  - API Version
     * @param resourceUri - The resource uri Ex: /name/version
     * @param httpMethod  - The http method. Ex: GET, POST
     * @return - The cache key
     */
    public static String getResourceInfoDTOCacheKey(String apiContext, String apiVersion,
                                                    String resourceUri, String httpMethod) {
        return apiContext + "/" + apiVersion + resourceUri + ":" + httpMethod;
    }


    /**
     * Get the key of the Resource ( used in scopes)
     *
     * @param api      - API
     * @param template - URI Template
     * @return - The resource key
     */
    public static String getResourceKey(API api, URITemplate template) {
        return APIUtil.getResourceKey(api.getContext(), api.getId().getVersion(), template.getUriTemplate(),
                template.getHTTPVerb());
    }

    /**
     * Get the key of the Resource ( used in scopes)
     *
     * @param apiContext  - Context of the API
     * @param apiVersion  - API Version
     * @param resourceUri - The resource uri Ex: /name/version
     * @param httpMethod  - The http method. Ex: GET, POST
     * @return - The resource key
     */
    public static String getResourceKey(String apiContext, String apiVersion, String resourceUri, String httpMethod) {
        return apiContext + "/" + apiVersion + resourceUri + ":" + httpMethod;
    }

    /**
     * Find scope object in a set based on the key
     *
     * @param scopes - Set of scopes
     * @param key    - Key to search with
     * @return Scope - scope object
     */
    public static Scope findScopeByKey(Set<Scope> scopes, String key) {
        for (Scope scope : scopes) {
            if (scope.getKey().equals(key)) {
                return scope;
            }
        }
        return null;
    }

    /**
     * Get the cache key of the APIInfoDTO
     *
     * @param apiContext - Context of the API
     * @param apiVersion - API Version
     * @return - The cache key of the APIInfoDTO
     */
    public static String getAPIInfoDTOCacheKey(String apiContext, String apiVersion) {
        return apiContext + "/" + apiVersion;
    }

    /**
     * Get the cache key of the Access Token
     *
     * @param accessToken - The access token which is cached
     * @param apiContext  - The context of the API
     * @param apiVersion  - The version of the API
     * @param resourceUri - The value of the resource url
     * @param httpVerb    - The http method. Ex: GET, POST
     * @param authLevel   - Required Authentication level. Ex: Application/Application User
     * @return - The Key which will be used to cache the access token
     */
    public static String getAccessTokenCacheKey(String accessToken, String apiContext, String apiVersion,
                                                String resourceUri, String httpVerb, String authLevel) {
        return accessToken + ':' + apiContext + '/' + apiVersion + resourceUri + ':' + httpVerb + ':' + authLevel;
    }


    /**
     * Resolves system properties and replaces in given in text
     *
     * @param text
     * @return System properties resolved text
     */
    public static String replaceSystemProperty(String text) {
        int indexOfStartingChars = -1;
        int indexOfClosingBrace;

        // The following condition deals with properties.
        // Properties are specified as ${system.property},
        // and are assumed to be System properties
        while (indexOfStartingChars < text.indexOf("${")
                && (indexOfStartingChars = text.indexOf("${")) != -1
                && (indexOfClosingBrace = text.indexOf('}')) != -1) { // Is a
            // property
            // used?
            String sysProp = text.substring(indexOfStartingChars + 2,
                    indexOfClosingBrace);
            String propValue = System.getProperty(sysProp);

            if (propValue == null) {
                if ("carbon.context".equals(sysProp)) {
                    propValue = ServiceReferenceHolder.getContextService().getServerConfigContext().getContextRoot();
                } else if ("admin.username".equals(sysProp) || "admin.password".equals(sysProp)) {
                    try {
                        RealmConfiguration realmConfig =
                                new RealmConfigXMLProcessor().buildRealmConfigurationFromFile();
                        if ("admin.username".equals(sysProp)) {
                            propValue = realmConfig.getAdminUserName();
                        } else {
                            propValue = realmConfig.getAdminPassword();
                        }
                    } catch (UserStoreException e) {
                        // Can't throw an exception because the server is
                        // starting and can't be halted.
                        log.error("Unable to build the Realm Configuration", e);
                        return null;
                    }
                }
            }
            //Derive original text value with resolved system property value
            if (propValue != null) {
                text = text.substring(0, indexOfStartingChars) + propValue
                        + text.substring(indexOfClosingBrace + 1);
            }
            if ("carbon.home".equals(sysProp) && propValue != null
                    && ".".equals(propValue)) {
                text = new File(".").getAbsolutePath() + File.separator + text;
            }
        }
        return text;
    }

    public static String encryptPassword(String plainTextPassword) throws APIManagementException {
        try {
            return CryptoUtil.getDefaultCryptoUtil().encryptAndBase64Encode(plainTextPassword.getBytes(Charset.defaultCharset()));
        } catch (CryptoException e) {
            String errorMsg = "Error while encrypting the password. " + e.getMessage();
            throw new APIManagementException(errorMsg, e);
        }
    }

    /**
     * Search Apis by Doc Content
     *
     * @param registry     - Registry which is searched
     * @param tenantID     - Tenant id of logged in domain
     * @param username     - Logged in username
     * @param searchTerm   - Search value for doc
     * @param searchClient - Search client
     * @return - Documentation to APIs map
     * @throws APIManagementException - If failed to get ArtifactManager for given tenant
     */
    public static Map<Documentation, API> searchAPIsByDoc(Registry registry, int tenantID, String username,
                                                          String searchTerm, String searchClient) throws APIManagementException {
        Map<Documentation, API> apiDocMap = new HashMap<Documentation, API>();

        try {
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setUsername(username);
            GenericArtifactManager artifactManager = APIUtil.getArtifactManager(registry,
                    APIConstants.API_KEY);
            if (artifactManager == null) {
                String errorMessage = "Artifact manager is null when searching APIs by docs in tenant ID " + tenantID;
                log.error(errorMessage);
                throw new APIManagementException(errorMessage);
            }
            GenericArtifactManager docArtifactManager = APIUtil.getArtifactManager(registry,
                    APIConstants.DOCUMENTATION_KEY);
            if (docArtifactManager == null) {
                String errorMessage = "Doc artifact manager is null when searching APIs by docs in tenant ID " +
                        tenantID;
                log.error(errorMessage);
                throw new APIManagementException(errorMessage);
            }
            SolrClient client = SolrClient.getInstance();
            Map<String, String> fields = new HashMap<String, String>();
            fields.put(APIConstants.DOCUMENTATION_SEARCH_PATH_FIELD, "*" + APIConstants.API_ROOT_LOCATION + "*");
            fields.put(APIConstants.DOCUMENTATION_SEARCH_MEDIA_TYPE_FIELD, "*");

            if (tenantID == -1) {
                tenantID = MultitenantConstants.SUPER_TENANT_ID;
            }
            //PaginationContext.init(0, 10000, "ASC", APIConstants.DOCUMENTATION_SEARCH_PATH_FIELD, Integer.MAX_VALUE);
            SolrDocumentList documentList = client.query(searchTerm, tenantID, fields);

            org.wso2.carbon.user.api.AuthorizationManager manager = ServiceReferenceHolder.getInstance().
                    getRealmService().getTenantUserRealm(tenantID).
                    getAuthorizationManager();

            username = MultitenantUtils.getTenantAwareUsername(username);

            for (SolrDocument document : documentList) {
                String filePath = (String) document.getFieldValue("path_s");
                int index = filePath.indexOf(APIConstants.APIMGT_REGISTRY_LOCATION);
                filePath = filePath.substring(index);
                Association[] associations = registry.getAllAssociations(filePath);
                API api = null;
                Documentation doc = null;
                for (Association association : associations) {
                    boolean isAuthorized;
                    String documentationPath = association.getSourcePath();
                    String path = RegistryUtils.getAbsolutePath(RegistryContext.getBaseInstance(),
                            APIUtil.getMountedPath(RegistryContext.getBaseInstance(),
                                    RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH) + documentationPath);
                    if (CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME.equalsIgnoreCase(username)) {
                        isAuthorized = manager.isRoleAuthorized(APIConstants.ANONYMOUS_ROLE, path, ActionConstants.GET);
                    } else {
                        isAuthorized = manager.isUserAuthorized(username, path, ActionConstants.GET);
                    }

                    if (isAuthorized) {
                        Resource docResource = registry.get(documentationPath);
                        String docArtifactId = docResource.getUUID();
                        if (docArtifactId != null) {
                            GenericArtifact docArtifact = docArtifactManager.getGenericArtifact(docArtifactId);
                            doc = APIUtil.getDocumentation(docArtifact);
                        }

                        Association[] docAssociations = registry.getAssociations(documentationPath, APIConstants.DOCUMENTATION_ASSOCIATION);
                        /* There will be only one document association, for a document path which is by its owner API*/
                        if (docAssociations.length > 0) {

                            String apiPath = docAssociations[0].getSourcePath();
                            path = RegistryUtils.getAbsolutePath(RegistryContext.getBaseInstance(),
                                    APIUtil.getMountedPath(RegistryContext.getBaseInstance(),
                                            RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH) + apiPath);
                            if (CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME.equalsIgnoreCase(username)) {
                                isAuthorized = manager.isRoleAuthorized(APIConstants.ANONYMOUS_ROLE, path, ActionConstants.GET);
                            } else {
                                isAuthorized = manager.isUserAuthorized(username, path, ActionConstants.GET);
                            }

                            if (isAuthorized) {
                                Resource resource = registry.get(apiPath);
                                String apiArtifactId = resource.getUUID();
                                if (apiArtifactId != null) {
                                    GenericArtifact apiArtifact = artifactManager.getGenericArtifact(apiArtifactId);
                                    api = APIUtil.getAPI(apiArtifact, registry);
                                } else {
                                    throw new GovernanceException("artifact id is null of " + apiPath);
                                }
                            }
                        }
                    }

                    if (doc != null && api != null) {
                        if (APIConstants.STORE_CLIENT.equals(searchClient)) {
                            if (APIConstants.PUBLISHED.equals(api.getStatus()) ||
                                    APIConstants.PROTOTYPED.equals(api.getStatus())) {
                                apiDocMap.put(doc, api);
                            }
                        } else {
                            apiDocMap.put(doc, api);
                        }
                    }
                }
            }
        } catch (IndexerException e) {
            handleException("Failed to search APIs with type Doc", e);
        } catch (RegistryException e) {
            handleException("Failed to search APIs with type Doc", e);
        } catch (UserStoreException e) {
            handleException("Failed to search APIs with type Doc", e);
        }
        return apiDocMap;
    }


    public static Map<String, Object> searchAPIsByURLPattern(Registry registry, String searchTerm, int start, int end)
            throws APIManagementException {
        SortedSet<API> apiSet = new TreeSet<API>(new APINameComparator());
        List<API> apiList = new ArrayList<API>();
        final String searchValue = searchTerm.trim();
        Map<String, Object> result = new HashMap<String, Object>();
        int totalLength = 0;
        String criteria;
        Map<String, List<String>> listMap = new HashMap<String, List<String>>();
        GenericArtifact[] genericArtifacts = new GenericArtifact[0];
        GenericArtifactManager artifactManager = null;
        try {
            artifactManager = APIUtil.getArtifactManager(registry, APIConstants.API_KEY);
            if (artifactManager == null) {
                String errorMessage = "Artifact manager is null when searching APIs by URL pattern " + searchTerm;
                log.error(errorMessage);
                throw new APIManagementException(errorMessage);
            }
            PaginationContext.init(0, 10000, "ASC", APIConstants.API_OVERVIEW_NAME, Integer.MAX_VALUE);
            if (artifactManager != null) {
                for (int i = 0; i < 20; i++) { //This need to fix in future.We don't have a way to get max value of
                    // "url_template" entry stores in registry,unless we search in each API
                    criteria = APIConstants.API_URI_PATTERN + i;
                    listMap.put(criteria, new ArrayList<String>() {
                        {
                            add(searchValue);
                        }
                    });
                    genericArtifacts = (GenericArtifact[]) ArrayUtils.addAll(genericArtifacts, artifactManager
                            .findGenericArtifacts(listMap));
                }
                if (genericArtifacts == null || genericArtifacts.length == 0) {
                    result.put("apis", apiSet);
                    result.put("length", 0);
                    return result;
                }
                totalLength = genericArtifacts.length;
                StringBuilder apiNames = new StringBuilder();
                for (GenericArtifact artifact : genericArtifacts) {
                    if (artifact == null) {
                        log.error("Failed to retrieve an artifact when searching APIs by URL pattern : " + searchTerm +
                                " , continuing with next artifact.");
                        continue;
                    }
                    if (apiNames.indexOf(artifact.getAttribute(APIConstants.API_OVERVIEW_NAME)) < 0) {
                        String status = APIUtil.getLcStateFromArtifact(artifact);
                        if (isAllowDisplayAPIsWithMultipleStatus()) {
                            if (APIConstants.PUBLISHED.equals(status) || APIConstants.DEPRECATED.equals(status)) {
                                API api = APIUtil.getAPI(artifact, registry);
                                if (api != null) {
                                    apiList.add(api);
                                    apiNames.append(api.getId().getApiName());
                                }
                            }
                        } else {
                            if (APIConstants.PUBLISHED.equals(status)) {
                                API api = APIUtil.getAPI(artifact, registry);
                                if (api != null) {
                                    apiList.add(api);
                                    apiNames.append(api.getId().getApiName());
                                }
                            }
                        }
                    }
                    totalLength = apiList.size();
                }
                if (totalLength <= ((start + end) - 1)) {
                    end = totalLength;
                }
                for (int i = start; i < end; i++) {
                    apiSet.add(apiList.get(i));
                }
            }
        } catch (APIManagementException e) {
            handleException("Failed to search APIs with input url-pattern", e);
        } catch (GovernanceException e) {
            handleException("Failed to search APIs with input url-pattern", e);
        }
        result.put("apis", apiSet);
        result.put("length", totalLength);
        return result;
    }

    /**
     * This method will check the validity of given url. WSDL url should be
     * contain http, https or file system patch
     * otherwise we will mark it as invalid wsdl url. How ever here we do not
     * validate wsdl content.
     *
     * @param wsdlURL wsdl url tobe tested
     * @return true if its valid url else fale
     */
    public static boolean isValidWSDLURL(String wsdlURL, boolean required) {
        if (wsdlURL != null && !"".equals(wsdlURL)) {
            if (wsdlURL.startsWith("http:") || wsdlURL.startsWith("https:") ||
                    wsdlURL.startsWith("file:") || (wsdlURL.startsWith("/registry") && !wsdlURL.endsWith(".zip"))) {
                return true;
            }
        } else if (!required) {
            // If the WSDL in not required and URL is empty, then we don't need
            // to add debug log.
            // Hence returning.
            return false;
        }

        if (log.isDebugEnabled()) {
            log.debug("WSDL url validation failed. Provided wsdl url is not valid url: " + wsdlURL);
        }
        return false;
    }

    /**
     * load tenant axis configurations.
     *
     * @param tenantDomain
     */
    public static void loadTenantConfig(String tenantDomain) {
        final String finalTenantDomain = tenantDomain;
        ConfigurationContext ctx =
                ServiceReferenceHolder.getContextService().getServerConfigContext();

        //Cannot use the tenantDomain directly because it's getting locked in createTenantConfigurationContext()
        // method in TenantAxisUtils
        String accessFlag = tenantDomain + "@WSO2";

        long lastAccessed = TenantAxisUtils.getLastAccessed(tenantDomain, ctx);
        //Only if the tenant is in unloaded state, we do the loading
        if (System.currentTimeMillis() - lastAccessed >= tenantIdleTimeMillis) {
            synchronized (accessFlag.intern()) {
                // Currently loading tenants are added to a set.
                // If a tenant domain is in the set it implies that particular tenant is being loaded.
                // Therefore if and only if the set does not contain the tenant.
                if (!currentLoadingTenants.contains(tenantDomain)) {
                    //Only one concurrent request is allowed to add to the currentLoadingTenants
                    currentLoadingTenants.add(tenantDomain);
                    ctx.getThreadPool().execute(new Runnable() {
                        @Override
                        public void run() {
                            Thread.currentThread().setName("APIMHostObjectUtils-loadTenantConfig-thread");
                            try {
                                PrivilegedCarbonContext.startTenantFlow();
                                PrivilegedCarbonContext.getThreadLocalCarbonContext()
                                        .setTenantDomain(finalTenantDomain, true);
                                ConfigurationContext ctx = ServiceReferenceHolder.getContextService()
                                        .getServerConfigContext();
                                TenantAxisUtils.getTenantAxisConfiguration(finalTenantDomain, ctx);
                            } catch (Exception e) {
                                log.error("Error while creating axis configuration for tenant " + finalTenantDomain, e);
                            } finally {
                                //only after the tenant is loaded completely, the tenant domain is removed from the set
                                currentLoadingTenants.remove(finalTenantDomain);
                                PrivilegedCarbonContext.endTenantFlow();
                            }
                        }
                    });
                }
            }
        }
    }

    /**
     * load tenant axis configurations.
     *
     * @param tenantDomain
     */
    public static void loadTenantConfigBlockingMode(String tenantDomain) {

        try {
            ConfigurationContext ctx = ServiceReferenceHolder.getContextService().getServerConfigContext();
            TenantAxisUtils.getTenantAxisConfiguration(tenantDomain, ctx);
        } catch (Exception e) {
            log.error("Error while creating axis configuration for tenant " + tenantDomain, e);
        }
    }

    public static String extractCustomerKeyFromAuthHeader(Map headersMap) {

        //From 1.0.7 version of this component onwards remove the OAuth authorization header from
        // the message is configurable. So we dont need to remove headers at this point.
        String authHeader = (String) headersMap.get(HttpHeaders.AUTHORIZATION);
        if (authHeader == null) {
            return null;
        }

        if (authHeader.startsWith("OAuth ") || authHeader.startsWith("oauth ")) {
            authHeader = authHeader.substring(authHeader.indexOf("o"));
        }

        String[] headers = authHeader.split(APIConstants.OAUTH_HEADER_SPLITTER);
        for (String header : headers) {
            String[] elements = header.split(APIConstants.CONSUMER_KEY_SEGMENT_DELIMITER);
            if (elements.length > 1) {
                int j = 0;
                boolean isConsumerKeyHeaderAvailable = false;
                for (String element : elements) {
                    if (!"".equals(element.trim())) {
                        if (APIConstants.CONSUMER_KEY_SEGMENT.equals(elements[j].trim())) {
                            isConsumerKeyHeaderAvailable = true;
                        } else if (isConsumerKeyHeaderAvailable) {
                            return removeLeadingAndTrailing(elements[j].trim());
                        }
                    }
                    j++;
                }
            }
        }
        return null;
    }

    private static String removeLeadingAndTrailing(String base) {
        String result = base;

        if (base.startsWith("\"") || base.endsWith("\"")) {
            result = base.replace("\"", "");
        }
        return result.trim();
    }

    /**
     * This method will return mounted path of the path if the path
     * is mounted. Else path will be returned.
     *
     * @param registryContext Registry Context instance which holds path mappings
     * @param path            default path of the registry
     * @return mounted path or path
     */
    public static String getMountedPath(RegistryContext registryContext, String path) {
        if (registryContext != null && path != null) {
            List<Mount> mounts = registryContext.getMounts();
            if (mounts != null) {
                for (Mount mount : mounts) {
                    if (path.equals(mount.getPath())) {
                        return mount.getTargetPath();
                    }
                }
            }
        }
        return path;
    }

    /**
     * Returns a map of gateway / store domains for the tenant
     *
     * @return a Map of domain names for tenant
     * @throws org.wso2.carbon.apimgt.api.APIManagementException if an error occurs when loading tiers from the registry
     */
    public static Map<String, String> getDomainMappings(String tenantDomain, String appType)
            throws APIManagementException {
        Map<String, String> domains = new HashMap<String, String>();
        String resourcePath;
        try {
            Registry registry = ServiceReferenceHolder.getInstance().getRegistryService().
                    getGovernanceSystemRegistry();
            resourcePath = APIConstants.API_DOMAIN_MAPPINGS.replace("<tenant-id>", tenantDomain);
            if (registry.resourceExists(resourcePath)) {
                Resource resource = registry.get(resourcePath);
                String content = new String((byte[]) resource.getContent(), Charset.defaultCharset());
                JSONParser parser = new JSONParser();
                JSONObject mappings = (JSONObject) parser.parse(content);
                if (mappings.get(appType) != null) {
                    mappings = (JSONObject) mappings.get(appType);
                    for (Object o : mappings.entrySet()) {
                        Entry thisEntry = (Entry) o;
                        String key = (String) thisEntry.getKey();
                        //Instead strictly comparing customUrl, checking whether name is starting with customUrl
                        //to allow users to add multiple URLs if needed
                        if (!StringUtils.isEmpty(key) && key.startsWith(APIConstants.CUSTOM_URL)) {
                            String value = (String) thisEntry.getValue();
                            domains.put(key, value);
                        }
                    }
                }
            }
        } catch (RegistryException e) {
            String msg = "Error while retrieving gateway domain mappings from registry";
            log.error(msg, e);
            throw new APIManagementException(msg, e);
        } catch (ClassCastException e) {
            String msg = "Invalid JSON found in the gateway tenant domain mappings";
            log.error(msg, e);
            throw new APIManagementException(msg, e);
        } catch (ParseException e) {
            String msg = "Malformed JSON found in the gateway tenant domain mappings";
            log.error(msg, e);
            throw new APIManagementException(msg, e);
        }
        return domains;
    }

    /**
     * This method used to Downloaded Uploaded Documents from publisher
     *
     * @param userName     logged in username
     * @param resourceUrl  resource want to download
     * @param tenantDomain loggedUserTenantDomain
     * @return map that contains Data of the resource
     * @throws APIManagementException
     */
    public static Map<String, Object> getDocument(String userName, String resourceUrl, String tenantDomain)
            throws APIManagementException {
        Map<String, Object> documentMap = new HashMap<String, Object>();

        InputStream inStream = null;
        String[] resourceSplitPath =
                resourceUrl.split(RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH);
        if (resourceSplitPath.length == 2) {
            resourceUrl = resourceSplitPath[1];
        } else {
            handleException("Invalid resource Path " + resourceUrl);
        }
        Resource apiDocResource;
        Registry registryType = null;
        boolean isTenantFlowStarted = false;
        try {
            int tenantId;
            if (tenantDomain != null && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
                PrivilegedCarbonContext.startTenantFlow();
                isTenantFlowStarted = true;

                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
                tenantId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();
            } else {
                tenantId = MultitenantConstants.SUPER_TENANT_ID;
            }

            userName = MultitenantUtils.getTenantAwareUsername(userName);
            registryType = ServiceReferenceHolder
                    .getInstance().
                            getRegistryService().getGovernanceUserRegistry(userName, tenantId);
            if (registryType.resourceExists(resourceUrl)) {
                apiDocResource = registryType.get(resourceUrl);
                inStream = apiDocResource.getContentStream();
                documentMap.put("Data", inStream);
                documentMap.put("contentType", apiDocResource.getMediaType());
                String[] content = apiDocResource.getPath().split("/");
                documentMap.put("name", content[content.length - 1]);
            }
        } catch (RegistryException e) {
            String msg = "Couldn't retrieve registry for User " + userName + " Tenant " + tenantDomain;
            log.error(msg, e);
            handleException(msg, e);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
        return documentMap;
    }

    /**
     * this method used to set environments values to api object.
     *
     * @param environments environments values in json format
     * @return set of environments that Published
     */
    public static Set<String> extractEnvironmentsForAPI(String environments) {
        Set<String> environmentStringSet = null;
        if (environments == null) {
            environmentStringSet = new HashSet<String>(
                    ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService()
                            .getAPIManagerConfiguration().getApiGatewayEnvironments().keySet());
        } else {
            //handle not to publish to any of the gateways
            if (APIConstants.API_GATEWAY_NONE.equals(environments)) {
                environmentStringSet = new HashSet<String>();
            }
            //handle to set published gateways nto api object
            else if (!"".equals(environments)) {
                String[] publishEnvironmentArray = environments.split(",");
                environmentStringSet = new HashSet<String>(Arrays.asList(publishEnvironmentArray));
                environmentStringSet.remove(APIConstants.API_GATEWAY_NONE);
            }
            //handle to publish to any of the gateways when api creating stage
            else if ("".equals(environments)) {
                environmentStringSet = new HashSet<String>(
                        ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService()
                                .getAPIManagerConfiguration().getApiGatewayEnvironments().keySet());
            }
        }
        return environmentStringSet;
    }

    /**
     * This method used to set environment values to governance artifact of API .
     *
     * @param api API object with the attributes value
     */
    public static String writeEnvironmentsToArtifact(API api) {
        StringBuilder publishedEnvironments = new StringBuilder();
        Set<String> apiEnvironments = api.getEnvironments();
        if (apiEnvironments != null) {
            for (String environmentName : apiEnvironments) {
                publishedEnvironments.append(environmentName).append(',');
            }

            if (apiEnvironments.isEmpty()) {
                publishedEnvironments.append("none,");
            }

            if (!publishedEnvironments.toString().isEmpty()) {
                publishedEnvironments.deleteCharAt(publishedEnvironments.length() - 1);
            }
        }
        return publishedEnvironments.toString();
    }

    /**
     * This method used to get the currently published gateway environments of an API .
     *
     * @param api API object with the attributes value
     */
    public static List<Environment> getEnvironmentsOfAPI(API api) {
        Map<String, Environment> gatewayEnvironments = ServiceReferenceHolder.getInstance()
                .getAPIManagerConfigurationService()
                .getAPIManagerConfiguration()
                .getApiGatewayEnvironments();
        Set<String> apiEnvironments = api.getEnvironments();
        List<Environment> returnEnvironments = new ArrayList<Environment>();

        for (Environment environment : gatewayEnvironments.values()) {
            for (String apiEnvironment : apiEnvironments) {
                if (environment.getName().equals(apiEnvironment)) {
                    returnEnvironments.add(environment);
                    break;
                }
            }
        }
        return returnEnvironments;
    }

    /**
     * Given the apps and the application name to check for, it will check if the application already exists.
     *
     * @param apps The collection of applications
     * @param name The application to be checked if exists
     * @return true - if an application of the name <name> already exists in the collection <apps>
     * false-  if an application of the name <name>  does not already exists in the collection <apps>
     */
    public static boolean doesApplicationExist(Application[] apps, String name) {
        boolean doesApplicationExist = false;
        if (apps != null) {
            for (Application app : apps) {
                if (app.getName().equals(name)) {
                    doesApplicationExist = true;
                }
            }
        }
        return doesApplicationExist;
    }

    /**
     * Read the group id extractor class reference from api-manager.xml.
     *
     * @return group id extractor class reference.
     */
    public static String getGroupingExtractorImplementation() {
        APIManagerConfiguration config = ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService()
                .getAPIManagerConfiguration();
        return config.getFirstProperty(APIConstants.API_STORE_GROUP_EXTRACTOR_IMPLEMENTATION);
    }

    /**
     * Read the REST API group id extractor class reference from api-manager.xml.
     *
     * @return REST API group id extractor class reference.
     */
    public static String getRESTApiGroupingExtractorImplementation() {
        APIManagerConfiguration config = ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService()
                .getAPIManagerConfiguration();
        String restApiGroupingExtractor = config
                .getFirstProperty(APIConstants.API_STORE_REST_API_GROUP_EXTRACTOR_IMPLEMENTATION);
        if (StringUtils.isEmpty(restApiGroupingExtractor)) {
            restApiGroupingExtractor = getGroupingExtractorImplementation();
        }
        return restApiGroupingExtractor;
    }

    /**
     * This method will update the permission cache of the tenant which is related to the given usename
     *
     * @param username User name to find the relevant tenant
     * @throws UserStoreException if the permission update failed
     */
    public static void updatePermissionCache(String username) throws UserStoreException {
        String tenantDomain = MultitenantUtils.getTenantDomain(username);
        int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().getTenantId(tenantDomain);
        PermissionUpdateUtil.updatePermissionTree(tenantId);
    }

    /**
     * Check whether given application name is available under current subscriber or group
     *
     * @param subscriber      subscriber name
     * @param applicationName application name
     * @param groupId         group of the subscriber
     * @return true if application is available for the subscriber
     * @throws APIManagementException if failed to get applications for given subscriber
     */
    public static boolean isApplicationExist(String subscriber, String applicationName, String groupId)
            throws APIManagementException {
        return ApiMgtDAO.getInstance().isApplicationExist(applicationName, subscriber, groupId);
    }

    /**
     * Check whether the new user has an application
     *
     * @param subscriber      subscriber name
     * @param applicationName application name
     * @return true if application is available for the subscriber
     * @throws APIManagementException if failed to get applications for given subscriber
     */
    public static boolean isApplicationOwnedBySubscriber(String subscriber, String applicationName)
            throws APIManagementException {
        return ApiMgtDAO.getInstance().isApplicationOwnedBySubscriber(applicationName, subscriber);
    }

    public static String getHostAddress() {

        if (hostAddress != null) {
            return hostAddress;
        }
        hostAddress = ServerConfiguration.getInstance().getFirstProperty(APIConstants.API_MANAGER_HOSTNAME);
        if (null == hostAddress) {
            if (getLocalAddress() != null) {
                hostAddress = getLocalAddress().getHostName();
            }
            if (hostAddress == null) {
                hostAddress = APIConstants.API_MANAGER_HOSTNAME_UNKNOWN;
            }
            return hostAddress;
        } else {
            return hostAddress;
        }
    }

    private static InetAddress getLocalAddress() {
        Enumeration<NetworkInterface> ifaces = null;
        try {
            ifaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            log.error("Failed to get host address", e);
        }
        if (ifaces != null) {
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                Enumeration<InetAddress> addresses = iface.getInetAddresses();

                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr;
                    }
                }
            }
        }

        return null;
    }

    public static boolean isStringArray(Object[] args) {

        for (Object arg : args) {
            if (!(arg instanceof String)) {
                return false;
            }
        }
        return true;
    }

    public static String appendDomainWithUser(String username, String domain) {
        if (username.contains(APIConstants.EMAIL_DOMAIN_SEPARATOR) || username.contains(APIConstants.EMAIL_DOMAIN_SEPARATOR_REPLACEMENT) || MultitenantConstants.SUPER_TENANT_NAME.equalsIgnoreCase(username)) {
            return username;
        }
        return username + APIConstants.EMAIL_DOMAIN_SEPARATOR + domain;
    }

    /*
    *  Util method to convert a java object to a json object
    *
    */
    public static String convertToString(Object obj) {
        Gson gson = new Gson();
        return gson.toJson(obj);
    }


    public static String getSequencePath(APIIdentifier identifier, String pathFlow) {
        String artifactPath = APIConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR +
                replaceEmailDomain(identifier.getProviderName()) + RegistryConstants.PATH_SEPARATOR +
                identifier.getApiName() + RegistryConstants.PATH_SEPARATOR + identifier.getVersion();
        return artifactPath + RegistryConstants.PATH_SEPARATOR + pathFlow + RegistryConstants.PATH_SEPARATOR;
    }

    private static String getAPIMonetizationCategory(Set<Tier> tiers, String tenantDomain)
            throws APIManagementException {
        boolean isPaidFound = false;
        boolean isFreeFound = false;
        for (Tier tier : tiers) {
            if (isTierPaid(tier.getName(), tenantDomain)) {
                isPaidFound = true;
            } else {
                isFreeFound = true;

                if (isPaidFound) {
                    break;
                }
            }
        }

        if (!isPaidFound) {
            return APIConstants.API_CATEGORY_FREE;
        } else if (!isFreeFound) {
            return APIConstants.API_CATEGORY_PAID;
        } else {
            return APIConstants.API_CATEGORY_FREEMIUM;
        }
    }

    private static boolean isTierPaid(String tierName, String tenantDomainName) throws APIManagementException {
        String tenantDomain = tenantDomainName;
        if (tenantDomain == null) {
            tenantDomain = MultitenantConstants.SUPER_TENANT_DOMAIN_NAME;
        }
        if (APIConstants.UNLIMITED_TIER.equalsIgnoreCase(tierName)) {
            return isUnlimitedTierPaid(tenantDomain);
        }

        boolean isPaid = false;
        Tier tier = getTierFromCache(tierName, tenantDomain);

        if (tier != null) {
            final Map<String, Object> tierAttributes = tier.getTierAttributes();

            if (tierAttributes != null) {
                String isPaidValue = tier.getTierPlan();

                if (isPaidValue != null && APIConstants.COMMERCIAL_TIER_PLAN.equals(isPaidValue)) {
                    isPaid = true;
                }
            }
        } else {
            throw new APIManagementException("Tier " + tierName + "cannot be found");
        }
        return isPaid;
    }

    private static boolean isUnlimitedTierPaid(String tenantDomain) throws APIManagementException {
        JSONObject apiTenantConfig = null;
        try {
            String content = null;

            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);

            int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager()
                    .getTenantId(tenantDomain);
            Registry registry = ServiceReferenceHolder.getInstance().getRegistryService()
                    .getConfigSystemRegistry(tenantId);

            if (registry.resourceExists(APIConstants.API_TENANT_CONF_LOCATION)) {
                Resource resource = registry.get(APIConstants.API_TENANT_CONF_LOCATION);
                content = new String((byte[]) resource.getContent(), Charset.defaultCharset());
            }

            if (content != null) {
                JSONParser parser = new JSONParser();
                apiTenantConfig = (JSONObject) parser.parse(content);
            }
        } catch (UserStoreException e) {
            handleException("UserStoreException thrown when getting API tenant config from registry", e);
        } catch (RegistryException e) {
            handleException("RegistryException thrown when getting API tenant config from registry", e);
        } catch (ParseException e) {
            handleException("ParseException thrown when passing API tenant config from registry", e);
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }

        if (apiTenantConfig != null) {
            Object value = apiTenantConfig.get(APIConstants.API_TENANT_CONF_IS_UNLIMITED_TIER_PAID);

            if (value != null) {
                return Boolean.parseBoolean(value.toString());
            } else {
                throw new APIManagementException(APIConstants.API_TENANT_CONF_IS_UNLIMITED_TIER_PAID
                        + " config does not exist for tenant " + tenantDomain);
            }
        }

        return false;
    }

    public static Tier getTierFromCache(String tierName, String tenantDomain) throws APIManagementException {
        Map<String, Tier> tierMap = null;

        try {
            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);

            if (getTiersCache().containsKey(tierName)) {
                tierMap = (Map<String, Tier>) getTiersCache().get(tierName);
            } else {
                int requestedTenantId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();
                if (!APIUtil.isAdvanceThrottlingEnabled()) {
                    if (requestedTenantId == 0) {
                        tierMap = APIUtil.getTiers();
                    } else {
                        tierMap = APIUtil.getTiers(requestedTenantId);
                    }
                } else {
                    if (requestedTenantId == 0) {
                        tierMap = APIUtil.getAdvancedSubsriptionTiers();
                    } else {
                        tierMap = APIUtil.getAdvancedSubsriptionTiers(requestedTenantId);
                    }
                }
                getTiersCache().put(tierName, tierMap);
            }
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }

        return tierMap.get(tierName);
    }


    public static void clearTiersCache(String tenantDomain) {
        try {
            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);

            getTiersCache().removeAll();
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
    }

    private static Cache getTiersCache() {
        return Caching.getCacheManager(APIConstants.API_MANAGER_CACHE_MANAGER).
                getCache(APIConstants.TIERS_CACHE);
    }

    /**
     * Util method to return the artifact from a registry resource path
     *
     * @param apiIdentifier
     * @param registry
     * @return
     * @throws APIManagementException
     */
    public static GenericArtifact getAPIArtifact(APIIdentifier apiIdentifier, Registry registry)
            throws APIManagementException {
        String apiPath = APIUtil.getAPIPath(apiIdentifier);
        GenericArtifactManager artifactManager = APIUtil.getArtifactManager(registry, APIConstants.API_KEY);
        if (artifactManager == null) {
            String errorMessage = "Artifact manager is null when getting generic artifact for API " +
                    apiIdentifier.getApiName();
            log.error(errorMessage);
            throw new APIManagementException(errorMessage);
        }
        try {
            Resource apiResource = registry.get(apiPath);
            String artifactId = apiResource.getUUID();
            if (artifactId == null) {
                throw new APIManagementException("artifact id is null for : " + apiPath);
            }
            return artifactManager.getGenericArtifact(artifactId);
        } catch (RegistryException e) {
            handleException("Failed to get API artifact from : " + apiPath, e);
            return null;
        }
    }

    /**
     * Return a http client instance
     *
     * @param port      - server port
     * @param protocol- service endpoint protocol http/https
     * @return
     */
    public static HttpClient getHttpClient(int port, String protocol) {
        SchemeRegistry registry = new SchemeRegistry();
        SSLSocketFactory socketFactory = SSLSocketFactory.getSocketFactory();
        String hostnameVerifierOption = System.getProperty(HOST_NAME_VERIFIER);
        String sslValue = null;

        AxisConfiguration axis2Config = ServiceReferenceHolder.getContextService().getServerConfigContext()
                .getAxisConfiguration();
        org.apache.axis2.description.Parameter sslVerifyClient = axis2Config.getTransportIn(APIConstants.HTTPS_PROTOCOL)
                .getParameter(APIConstants.SSL_VERIFY_CLIENT);
        if (sslVerifyClient != null) {
            sslValue = (String) sslVerifyClient.getValue();
        }

        X509HostnameVerifier hostnameVerifier;
        if (ALLOW_ALL.equalsIgnoreCase(hostnameVerifierOption)) {
            hostnameVerifier = SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;
        } else if (STRICT.equalsIgnoreCase(hostnameVerifierOption)) {
            hostnameVerifier = SSLSocketFactory.STRICT_HOSTNAME_VERIFIER;
        } else {
            hostnameVerifier = SSLSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER;
        }
        socketFactory.setHostnameVerifier(hostnameVerifier);

        if (APIConstants.HTTPS_PROTOCOL.equals(protocol)) {
            try {
                if (APIConstants.SSL_VERIFY_CLIENT_STATUS_REQUIRE.equals(sslValue)) {
                    socketFactory = createSocketFactory();
                    socketFactory.setHostnameVerifier(hostnameVerifier);
                }
                if (port >= 0) {
                    registry.register(new Scheme(APIConstants.HTTPS_PROTOCOL, port, socketFactory));
                } else {
                    registry.register(new Scheme(APIConstants.HTTPS_PROTOCOL, 443, socketFactory));
                }
            } catch (APIManagementException e) {
                log.error(e);
            }
        } else if (APIConstants.HTTP_PROTOCOL.equals(protocol)) {
            if (port >= 0) {
                registry.register(new Scheme(APIConstants.HTTP_PROTOCOL, port, PlainSocketFactory.getSocketFactory()));
            } else {
                registry.register(new Scheme(APIConstants.HTTP_PROTOCOL, 80, PlainSocketFactory.getSocketFactory()));
            }
        }
        HttpParams params = new BasicHttpParams();
        ThreadSafeClientConnManager tcm = new ThreadSafeClientConnManager(registry);
        return new DefaultHttpClient(tcm, params);

    }

    private static SSLSocketFactory createSocketFactory() throws APIManagementException {
        KeyStore keyStore;
        String keyStorePath = null;
        String keyStorePassword;
        try {
            keyStorePath = CarbonUtils.getServerConfiguration().getFirstProperty("Security.KeyStore.Location");
            keyStorePassword = CarbonUtils.getServerConfiguration()
                    .getFirstProperty("Security.KeyStore.Password");
            keyStore = KeyStore.getInstance("JKS");
            keyStore.load(new FileInputStream(keyStorePath), keyStorePassword.toCharArray());
            SSLSocketFactory sslSocketFactory = new SSLSocketFactory(keyStore, keyStorePassword);

            return sslSocketFactory;

        } catch (KeyStoreException e) {
            handleException("Failed to read from Key Store", e);
        } catch (CertificateException e) {
            handleException("Failed to read Certificate", e);
        } catch (NoSuchAlgorithmException e) {
            handleException("Failed to load Key Store from " + keyStorePath, e);
        } catch (IOException e) {
            handleException("Key Store not found in " + keyStorePath, e);
        } catch (UnrecoverableKeyException e) {
            handleException("Failed to load key from" + keyStorePath, e);
        } catch (KeyManagementException e) {
            handleException("Failed to load key from" + keyStorePath, e);
        }
        return null;
    }

    /**
     * This method will return a relative URL for given registry resource which we can used to retrieve the resource
     * from the web UI. For example, URI for a thumbnail icon of a tag can be generated from this method.
     *
     * @param resourceType Type of the registry resource. Based on this value the way URL is generate can be changed.
     * @param tenantDomain tenant domain of the resource
     * @param resourcePath path of the resource
     * @return relative path of the registry resource from the web context level
     */
    public static String getRegistryResourcePathForUI(APIConstants.RegistryResourceTypesForUI resourceType, String
            tenantDomain, String resourcePath) {
        StringBuilder resourcePathBuilder = new StringBuilder();
        if (APIConstants.RegistryResourceTypesForUI.TAG_THUMBNAIL.equals(resourceType)) {
            if (tenantDomain != null && !"".equals(tenantDomain)
                    && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
                // The compiler will concatenate the 2 constants. If we use the builder to append the 2 constants, then
                // it will happen during the runtime.
                resourcePathBuilder.append(RegistryConstants.PATH_SEPARATOR + MultitenantConstants
                        .TENANT_AWARE_URL_PREFIX + RegistryConstants.PATH_SEPARATOR).append(tenantDomain);
            }
            // The compiler will concatenate the 2 constants. If we use the builder to append the 2 constants, then
            // it will happen during the runtime.
            resourcePathBuilder.append(APIConstants.REGISTRY_RESOURCE_PREFIX + RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH);
            resourcePathBuilder.append(resourcePath);
        }
        return resourcePathBuilder.toString();
    }

    /**
     * Gets the  class given the class name.
     *
     * @param className the fully qualified name of the class.
     * @return an instance of the class with the given name
     * @throws ClassNotFoundException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */

    public static Class getClassForName(String className) throws ClassNotFoundException, IllegalAccessException,
            InstantiationException {
        return Class.forName(className);
    }

    /**
     * This method will check the validity of given url.
     * otherwise we will mark it as invalid url.
     *
     * @param url url tobe tested
     * @return true if its valid url else fale
     */
    public static boolean isValidURL(String url) {

        if (url == null) {
            return false;
        }
        try {
            URL urlVal = new URL(url);
            // If there are no issues, then this is a valid URL. Hence returning true.
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }


    /**
     * @param tenantDomain Tenant domain to be used to get configurations for REST API scopes
     * @return JSON object which contains configuration for REST API scopes
     * @throws APIManagementException
     */
    public static JSONObject getTenantRESTAPIScopesConfig(String tenantDomain) throws APIManagementException {
        JSONObject apiTenantConfig = null;
        JSONObject restAPIConfigJSON = null;
        try {
            String content = new APIMRegistryServiceImpl().getConfigRegistryResourceContent(tenantDomain,
                    APIConstants.API_TENANT_CONF_LOCATION);

            if (content != null) {
                JSONParser parser = new JSONParser();
                apiTenantConfig = (JSONObject) parser.parse(content);
                if (apiTenantConfig != null) {
                    Object value = apiTenantConfig.get(APIConstants.REST_API_SCOPES_CONFIG);
                    if (value != null) {
                        restAPIConfigJSON = (JSONObject) value;
                    } else {
                        throw new APIManagementException("RESTAPIScopes" + " config does not exist for tenant "
                                + tenantDomain);
                    }
                }
            }
        } catch (UserStoreException e) {
            handleException("UserStoreException thrown when getting API tenant config from registry", e);
        } catch (RegistryException e) {
            handleException("RegistryException thrown when getting API tenant config from registry", e);
        } catch (ParseException e) {
            handleException("ParseException thrown when passing API tenant config from registry", e);
        }
        return restAPIConfigJSON;
    }

    /**
     * @param tenantDomain Tenant domain to be used to get default role configurations
     * @return JSON object which contains configuration for default roles
     * @throws APIManagementException
     */
    public static JSONObject getTenantDefaultRoles(String tenantDomain) throws APIManagementException {
        JSONObject apiTenantConfig;
        JSONObject defaultRolesConfigJSON = null;
        try {
            String content = new APIMRegistryServiceImpl().getConfigRegistryResourceContent(tenantDomain,
                    APIConstants.API_TENANT_CONF_LOCATION);

            if (content != null) {
                JSONParser parser = new JSONParser();
                apiTenantConfig = (JSONObject) parser.parse(content);
                if (apiTenantConfig != null) {
                    Object value = apiTenantConfig.get(APIConstants.API_TENANT_CONF_DEFAULT_ROLES);
                    if (value != null) {
                        defaultRolesConfigJSON = (JSONObject) value;
                    } else {
                        //Config might not exist for migrated environments from previous release
                        if (log.isDebugEnabled()) {
                            log.debug(APIConstants.API_TENANT_CONF_DEFAULT_ROLES + " config does not exist for tenant "
                                    + tenantDomain);
                        }
                    }
                }
            }
        } catch (UserStoreException e) {
            handleException("Error while retrieving user realm for tenant " + tenantDomain, e);
        } catch (RegistryException e) {
            handleException("Error while retrieving tenant configuration file for tenant " + tenantDomain, e);
        } catch (ParseException e) {
            handleException(
                    "Error while parsing tenant configuration file while retrieving default roles for tenant "
                            + tenantDomain, e);
        }
        return defaultRolesConfigJSON;
    }

    /**
     * @param config JSON configuration object with scopes and associated roles
     * @return Map of scopes which contains scope names and associated role list
     */
    public static Map<String, String> getRESTAPIScopesFromConfig(JSONObject config) {
        Map<String, String> scopes = new HashMap<String, String>();
        JSONArray scopesArray = (JSONArray) config.get("Scope");
        for (Object scopeObj : scopesArray) {
            JSONObject scope = (JSONObject) scopeObj;
            String scopeName = scope.get(APIConstants.REST_API_SCOPE_NAME).toString();
            String scopeRoles = scope.get(APIConstants.REST_API_SCOPE_ROLE).toString();
            scopes.put(scopeName, scopeRoles);
        }
        return scopes;
    }

    /**
     * Determines if the scope is specified in the whitelist.
     *
     * @param scope - The scope key to check
     * @return - 'true' if the scope is white listed. 'false' if not.
     */
    public static boolean isWhiteListedScope(String scope) {

        if (whiteListedScopes == null) {
            APIManagerConfiguration configuration = ServiceReferenceHolder.getInstance().
                    getAPIManagerConfigurationService().getAPIManagerConfiguration();

            // Read scope whitelist from Configuration.
            List<String> whitelist = configuration.getProperty(APIConstants.WHITELISTED_SCOPES);

            // If whitelist is null, default scopes will be put.
            if (whitelist == null) {
                whitelist = new ArrayList<String>();
                whitelist.add(APIConstants.OPEN_ID_SCOPE_NAME);
                whitelist.add(APIConstants.DEVICE_SCOPE_PATTERN);
            }

            whiteListedScopes = new HashSet<String>(whitelist);
        }

        for (String scopeTobeSkipped : whiteListedScopes) {
            if (scope.matches(scopeTobeSkipped)) {
                return true;
            }
        }
        return false;
    }

    public static int getManagementTransportPort (String mgtTransport){
        AxisConfiguration axisConfiguration = ServiceReferenceHolder
                .getContextService().getServerConfigContext().getAxisConfiguration();
        int mgtTransportPort = CarbonUtils.getTransportProxyPort(axisConfiguration, mgtTransport);
        if (mgtTransportPort <= 0) {
            mgtTransportPort = CarbonUtils.getTransportPort(axisConfiguration, mgtTransport);
        }
        return mgtTransportPort;
    }

    public static String getServerURL() throws APIManagementException {
        String hostName = ServerConfiguration.getInstance().getFirstProperty(APIConstants.HOST_NAME);

        try {
            if (hostName == null) {
                hostName = NetworkUtils.getLocalHostname();
            }
        } catch (SocketException e) {
            throw new APIManagementException("Error while trying to read hostname.", e);
        }

        String mgtTransport = CarbonUtils.getManagementTransport();
        int mgtTransportPort = getManagementTransportPort(mgtTransport);
        String serverUrl = mgtTransport + "://" + hostName.toLowerCase();
        // If it's well known HTTPS port, skip adding port
        if (mgtTransportPort != APIConstants.DEFAULT_HTTPS_PORT) {
            serverUrl += ":" + mgtTransportPort;
        }
        // If ProxyContextPath is defined then append it
        String proxyContextPath = ServerConfiguration.getInstance().getFirstProperty(APIConstants.PROXY_CONTEXT_PATH);
        if (proxyContextPath != null && !proxyContextPath.trim().isEmpty()) {
            if (proxyContextPath.charAt(0) == '/') {
                serverUrl += proxyContextPath;
            } else {
                serverUrl += "/" + proxyContextPath;
            }
        }

        return serverUrl;
    }

    /**
     * Extract the provider of the API from name
     *
     * @param apiVersion   - API Name with version
     * @param tenantDomain - tenant domain of the API
     * @return API publisher name
     */
    public static String getAPIProviderFromRESTAPI(String apiVersion, String tenantDomain) {
        int index = apiVersion.indexOf("--");
        if (StringUtils.isEmpty(tenantDomain)) {
            tenantDomain = org.wso2.carbon.utils.multitenancy.MultitenantConstants.SUPER_TENANT_DOMAIN_NAME;
        }
        String apiProvider;
        if (index != -1) {
            apiProvider = apiVersion.substring(0, index);
            if (apiProvider.contains(APIConstants.EMAIL_DOMAIN_SEPARATOR_REPLACEMENT)) {
                apiProvider = apiProvider.replace(APIConstants.EMAIL_DOMAIN_SEPARATOR_REPLACEMENT,
                        APIConstants.EMAIL_DOMAIN_SEPARATOR);
            }
            if (!apiProvider.endsWith(tenantDomain)) {
                apiProvider = apiProvider + '@' + tenantDomain;
            }
            return apiProvider;
        }
        return null;
    }

    /**
     * Get the API Provider name by giving the api name version and the tenant which it belongs to
     * @param apiName Name of the API
     * @param apiVersion Version of the API
     * @param tenant Tenant name
     * @return Provider name who created the API
     * @throws APIManagementException
     */
    public static String getAPIProviderFromAPINameVersionTenant(String apiName, String apiVersion, String tenant)
            throws APIManagementException {
        return ApiMgtDAO.getInstance().getAPIProviderByNameAndVersion(apiName, apiVersion, tenant);
    }

    /**
     * Used to generate CORS Configuration object from CORS Configuration Json
     *
     * @param jsonString json representation of CORS configuration
     * @return CORSConfiguration Object
     */
    public static CORSConfiguration getCorsConfigurationDtoFromJson(String jsonString) {
        return new Gson().fromJson(jsonString, CORSConfiguration.class);

    }

    /**
     * Used to generate Json string from CORS Configuration object
     *
     * @param corsConfiguration CORSConfiguration Object
     * @return Json string according to CORSConfiguration Object
     */
    public static String getCorsConfigurationJsonFromDto(CORSConfiguration corsConfiguration) {
        return new Gson().toJson(corsConfiguration);
    }

    /**
     * Used to get access control allowed headers according to the api-manager.xml
     *
     * @return access control allowed headers string
     */
    public static String getAllowedHeaders() {
        return ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().getAPIManagerConfiguration().
                getFirstProperty(APIConstants.CORS_CONFIGURATION_ACCESS_CTL_ALLOW_HEADERS);
    }

    /**
     * Used to get access control allowed methods define in api-manager.xml
     *
     * @return access control allowed methods string
     */
    public static String getAllowedMethods() {
        return ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().getAPIManagerConfiguration().
                getFirstProperty(APIConstants.CORS_CONFIGURATION_ACCESS_CTL_ALLOW_METHODS);
    }

    /**
     * Used to get access control expose headers define in api-manager.xml
     *
     * @return access control expose headers string
     */
    public static String getExposedHeaders() {
        return ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().getAPIManagerConfiguration().
                getFirstProperty(APIConstants.CORS_CONFIGURATION_ACCESS_CTL_EXPOSE_HEADERS);
    }

    /**
     * Used to get access control allowed credential define in api-manager.xml
     *
     * @return true if access control allow credential enabled
     */
    public static boolean isAllowCredentials() {
        String allowCredentials =
                ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().getAPIManagerConfiguration().
                        getFirstProperty(APIConstants.CORS_CONFIGURATION_ACCESS_CTL_ALLOW_CREDENTIALS);
        return Boolean.parseBoolean(allowCredentials);
    }

    /**
     * Used to get CORS Configuration enabled from api-manager.xml
     *
     * @return true if CORS-Configuration is enabled in api-manager.xml
     */
    public static boolean isCORSEnabled() {
        String corsEnabled =
                ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().getAPIManagerConfiguration().
                        getFirstProperty(APIConstants.CORS_CONFIGURATION_ENABLED);

        return Boolean.parseBoolean(corsEnabled);
    }

    /**
     * Used to get access control allowed origins define in api-manager.xml
     *
     * @return allow origins list defined in api-manager.xml
     */
    public static String getAllowedOrigins() {
        return ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().getAPIManagerConfiguration().
                getFirstProperty(APIConstants.CORS_CONFIGURATION_ACCESS_CTL_ALLOW_ORIGIN);

    }

    /**
     * Used to get CORSConfiguration according to the API artifact
     *
     * @param artifact registry artifact for the API
     * @return CORS Configuration object extract from the artifact
     * @throws GovernanceException if attribute couldn't fetch from the artifact.
     */
    public static CORSConfiguration getCorsConfigurationFromArtifact(GovernanceArtifact artifact)
            throws GovernanceException {
        CORSConfiguration corsConfiguration = APIUtil.getCorsConfigurationDtoFromJson(
                artifact.getAttribute(APIConstants.API_OVERVIEW_CORS_CONFIGURATION));
        if (corsConfiguration == null) {
            corsConfiguration = getDefaultCorsConfiguration();
        }
        return corsConfiguration;
    }

    /**
     * Used to get Default CORS Configuration object according to configuration define in api-manager.xml
     *
     * @return CORSConfiguration object accordine to the defined values in api-manager.xml
     */
    public static CORSConfiguration getDefaultCorsConfiguration() {
        List<String> allowHeadersStringSet = Arrays.asList(getAllowedHeaders().split(","));
        List<String> allowMethodsStringSet = Arrays.asList(getAllowedMethods().split(","));
        List<String> allowOriginsStringSet = Arrays.asList(getAllowedOrigins().split(","));
        return new CORSConfiguration(false, allowOriginsStringSet, false, allowHeadersStringSet, allowMethodsStringSet);
    }

    /**
     * Used to get API name from synapse API Name
     *
     * @param api_version API name from synapse configuration
     * @return api name according to the tenant
     */
    public static String getAPINamefromRESTAPI(String api_version) {
        int index = api_version.indexOf("--");
        String api;
        if (index != -1) {
            api_version = api_version.substring(index + 2);
        }
        api = api_version.split(":")[0];
        index = api.indexOf("--");
        if (index != -1) {
            api = api.substring(index + 2);
        }
        return api;
    }

    /**
     * @param stakeHolder value "publisher" for publisher value "subscriber" for subscriber value "admin-dashboard" for admin
     *                    Return all alert types.
     * @return Hashmap of alert types.
     * @throws APIManagementException
     */
    public static HashMap<Integer, String> getAllAlertTypeByStakeHolder(String stakeHolder) throws APIManagementException {
        HashMap<Integer, String> map;
        map = ApiMgtDAO.getInstance().getAllAlertTypesByStakeHolder(stakeHolder);
        return map;
    }

    /**
     * @param userName    user name with tenant domain ex: admin@carbon.super
     * @param stakeHolder value "p" for publisher value "s" for subscriber value "a" for admin
     * @return map of saved values of alert types.
     * @throws APIManagementException
     */
    public static List<Integer> getSavedAlertTypesIdsByUserNameAndStakeHolder(String userName, String stakeHolder) throws APIManagementException {

        List<Integer> list;
        list = ApiMgtDAO.getInstance().getSavedAlertTypesIdsByUserNameAndStakeHolder(userName, stakeHolder);
        return list;

    }

    /**
     * This util method retrieves saved email list by user and stakeHolder name
     *
     * @param userName    user name with tenant ID.
     * @param stakeHolder if its publisher values should "p", if it is store value is "s" if admin dashboard value is "a"
     * @return List of eamil list.
     * @throws APIManagementException
     */
    public static List<String> retrieveSavedEmailList(String userName, String stakeHolder) throws APIManagementException {

        List<String> list;
        list = ApiMgtDAO.getInstance().retrieveSavedEmailList(userName, stakeHolder);

        return list;
    }

    private static boolean isDefaultQuotaPolicyContentAware(Policy policy) {
        if (PolicyConstants.BANDWIDTH_TYPE.equalsIgnoreCase(policy.getDefaultQuotaPolicy().getType())) {
            return true;
        }
        return false;
    }

    public static void addDefaultSuperTenantAdvancedThrottlePolicies() throws APIManagementException {
        int tenantId = MultitenantConstants.SUPER_TENANT_ID;
        ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();

        /* Check if 'Unlimited' policy is available in AM_POLICY_APPLICATION table, to determine whether the default policies are loaded into the database at lease once.
           If yes, default policies won't be added to database again.
        */
        if (apiMgtDAO.isPolicyExist(PolicyConstants.POLICY_LEVEL_APP, tenantId, APIConstants.DEFAULT_APP_POLICY_UNLIMITED)) {
            log.debug("Default Throttling Policies are not written into the database again, as they were added once at initial server startup");
            return;
        }

        long[] requestCount = new long[]{50, 20, 10, Integer.MAX_VALUE};
        //Adding application level throttle policies
        String[] appPolicies = new String[]{APIConstants.DEFAULT_APP_POLICY_FIFTY_REQ_PER_MIN, APIConstants.DEFAULT_APP_POLICY_TWENTY_REQ_PER_MIN,
                APIConstants.DEFAULT_APP_POLICY_TEN_REQ_PER_MIN, APIConstants.DEFAULT_APP_POLICY_UNLIMITED};
        String[] appPolicyDecs = new String[]{APIConstants.DEFAULT_APP_POLICY_LARGE_DESC, APIConstants.DEFAULT_APP_POLICY_MEDIUM_DESC,
                APIConstants.DEFAULT_APP_POLICY_SMALL_DESC, APIConstants.DEFAULT_APP_POLICY_UNLIMITED_DESC};
        String policyName;
        //Add application level throttle policies
        for (int i = 0; i < appPolicies.length; i++) {
            policyName = appPolicies[i];
            if (!apiMgtDAO.isPolicyExist(PolicyConstants.POLICY_LEVEL_APP, tenantId, policyName)) {
                ApplicationPolicy applicationPolicy = new ApplicationPolicy(policyName);
                applicationPolicy.setDisplayName(policyName);
                applicationPolicy.setDescription(appPolicyDecs[i]);
                applicationPolicy.setTenantId(tenantId);
                applicationPolicy.setDeployed(true);
                QuotaPolicy defaultQuotaPolicy = new QuotaPolicy();
                RequestCountLimit requestCountLimit = new RequestCountLimit();
                requestCountLimit.setRequestCount(requestCount[i]);
                requestCountLimit.setUnitTime(1);
                requestCountLimit.setTimeUnit(APIConstants.TIME_UNIT_MINUTE);
                defaultQuotaPolicy.setType(PolicyConstants.REQUEST_COUNT_TYPE);
                defaultQuotaPolicy.setLimit(requestCountLimit);
                applicationPolicy.setDefaultQuotaPolicy(defaultQuotaPolicy);
                apiMgtDAO.addApplicationPolicy(applicationPolicy);
            }
        }

        //Adding Subscription level policies
        long[] requestCountSubPolicies = new long[]{5000, 2000, 1000, 500, Integer.MAX_VALUE};
        String[] subPolicies = new String[]{APIConstants.DEFAULT_SUB_POLICY_GOLD, APIConstants.DEFAULT_SUB_POLICY_SILVER,
                APIConstants.DEFAULT_SUB_POLICY_BRONZE, APIConstants.DEFAULT_SUB_POLICY_UNAUTHENTICATED, APIConstants.DEFAULT_SUB_POLICY_UNLIMITED};
        String[] subPolicyDecs = new String[]{APIConstants.DEFAULT_SUB_POLICY_GOLD_DESC, APIConstants.DEFAULT_SUB_POLICY_SILVER_DESC,
                APIConstants.DEFAULT_SUB_POLICY_BRONZE_DESC, APIConstants.DEFAULT_SUB_POLICY_UNAUTHENTICATED_DESC, APIConstants.DEFAULT_SUB_POLICY_UNLIMITED_DESC};
        for (int i = 0; i < subPolicies.length; i++) {
            policyName = subPolicies[i];
            if (!apiMgtDAO.isPolicyExist(PolicyConstants.POLICY_LEVEL_SUB, tenantId, policyName)) {
                SubscriptionPolicy subscriptionPolicy = new SubscriptionPolicy(policyName);
                subscriptionPolicy.setDisplayName(policyName);
                subscriptionPolicy.setDescription(subPolicyDecs[i]);
                subscriptionPolicy.setTenantId(tenantId);
                subscriptionPolicy.setDeployed(true);
                QuotaPolicy defaultQuotaPolicy = new QuotaPolicy();
                RequestCountLimit requestCountLimit = new RequestCountLimit();
                requestCountLimit.setRequestCount(requestCountSubPolicies[i]);
                requestCountLimit.setUnitTime(1);
                requestCountLimit.setTimeUnit(APIConstants.TIME_UNIT_MINUTE);
                defaultQuotaPolicy.setType(PolicyConstants.REQUEST_COUNT_TYPE);
                defaultQuotaPolicy.setLimit(requestCountLimit);
                subscriptionPolicy.setDefaultQuotaPolicy(defaultQuotaPolicy);
                subscriptionPolicy.setStopOnQuotaReach(true);
                subscriptionPolicy.setBillingPlan(APIConstants.BILLING_PLAN_FREE);
                apiMgtDAO.addSubscriptionPolicy(subscriptionPolicy);
            }
        }

        //Adding Resource level policies
        String[] apiPolicies = new String[]{APIConstants.DEFAULT_API_POLICY_FIFTY_THOUSAND_REQ_PER_MIN, APIConstants.DEFAULT_API_POLICY_TWENTY_THOUSAND_REQ_PER_MIN,
                APIConstants.DEFAULT_API_POLICY_TEN_THOUSAND_REQ_PER_MIN, APIConstants.DEFAULT_API_POLICY_UNLIMITED};
        String[] apiPolicyDecs = new String[]{APIConstants.DEFAULT_API_POLICY_ULTIMATE_DESC, APIConstants.DEFAULT_API_POLICY_PLUS_DESC,
                APIConstants.DEFAULT_API_POLICY_BASIC_DESC, APIConstants.DEFAULT_API_POLICY_UNLIMITED_DESC};
        long[] requestCountApiPolicies = new long[]{50000, 20000, 10000, Integer.MAX_VALUE};
        for (int i = 0; i < apiPolicies.length; i++) {
            policyName = apiPolicies[i];
            if (!apiMgtDAO.isPolicyExist(PolicyConstants.POLICY_LEVEL_API, tenantId, policyName)) {
                APIPolicy apiPolicy = new APIPolicy(policyName);
                apiPolicy.setDisplayName(policyName);
                apiPolicy.setDescription(apiPolicyDecs[i]);
                apiPolicy.setTenantId(tenantId);
                apiPolicy.setUserLevel(APIConstants.API_POLICY_API_LEVEL);
                apiPolicy.setDeployed(true);
                QuotaPolicy defaultQuotaPolicy = new QuotaPolicy();
                RequestCountLimit requestCountLimit = new RequestCountLimit();
                requestCountLimit.setRequestCount(requestCountApiPolicies[i]);
                requestCountLimit.setUnitTime(1);
                requestCountLimit.setTimeUnit(APIConstants.TIME_UNIT_MINUTE);
                defaultQuotaPolicy.setType(PolicyConstants.REQUEST_COUNT_TYPE);
                defaultQuotaPolicy.setLimit(requestCountLimit);
                apiPolicy.setDefaultQuotaPolicy(defaultQuotaPolicy);
                apiMgtDAO.addAPIPolicy(apiPolicy);
            }
        }
    }

    public static void addDefaultTenantAdvancedThrottlePolicies(String tenantDomain, int tenantId) throws APIManagementException {
        ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();

        /* Check if 'Unlimited' policy is available in AM_POLICY_APPLICATION table, to determine whether the default policies are written into the database at lease once.
           If yes, default policies won't be added to database again.
        */
        if (apiMgtDAO.isPolicyExist(PolicyConstants.POLICY_LEVEL_APP, tenantId, APIConstants.DEFAULT_APP_POLICY_UNLIMITED)) {
            log.debug("Default Throttling Policies are not written into the database again, as they were added once, at initial tenant loading");
            return;
        }

        ThrottlePolicyDeploymentManager deploymentManager = ThrottlePolicyDeploymentManager.getInstance();
        ThrottlePolicyTemplateBuilder policyBuilder = new ThrottlePolicyTemplateBuilder();
        Map<String, Long> defualtLimits = ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().getAPIManagerConfiguration()
                .getThrottleProperties().getDefaultThrottleTierLimits();
        long tenPerMinTier = defualtLimits.containsKey(APIConstants.DEFAULT_APP_POLICY_TEN_REQ_PER_MIN) ?
                defualtLimits.get(APIConstants.DEFAULT_APP_POLICY_TEN_REQ_PER_MIN) : 10;
        long twentyPerMinTier = defualtLimits.containsKey(APIConstants.DEFAULT_APP_POLICY_TWENTY_REQ_PER_MIN) ?
                defualtLimits.get(APIConstants.DEFAULT_APP_POLICY_TWENTY_REQ_PER_MIN) : 20;
        long fiftyPerMinTier = defualtLimits.containsKey(APIConstants.DEFAULT_APP_POLICY_FIFTY_REQ_PER_MIN) ?
                defualtLimits.get(APIConstants.DEFAULT_APP_POLICY_FIFTY_REQ_PER_MIN) : 50;
        long[] requestCount = new long[]{fiftyPerMinTier, twentyPerMinTier, tenPerMinTier, Integer.MAX_VALUE};
        //Adding application level throttle policies
        String[] appPolicies = new String[]{APIConstants.DEFAULT_APP_POLICY_FIFTY_REQ_PER_MIN, APIConstants.DEFAULT_APP_POLICY_TWENTY_REQ_PER_MIN,
                APIConstants.DEFAULT_APP_POLICY_TEN_REQ_PER_MIN, APIConstants.DEFAULT_APP_POLICY_UNLIMITED};
        String[] appPolicyDecs = new String[]{APIConstants.DEFAULT_APP_POLICY_LARGE_DESC, APIConstants.DEFAULT_APP_POLICY_MEDIUM_DESC,
                APIConstants.DEFAULT_APP_POLICY_SMALL_DESC, APIConstants.DEFAULT_APP_POLICY_UNLIMITED_DESC};
        String policyName;
        //Add application level throttle policies
        for (int i = 0; i < appPolicies.length; i++) {
            policyName = appPolicies[i];
            boolean needDeployment = false;
            ApplicationPolicy applicationPolicy = new ApplicationPolicy(policyName);
            applicationPolicy.setDisplayName(policyName);
            applicationPolicy.setDescription(appPolicyDecs[i]);
            applicationPolicy.setTenantId(tenantId);
            applicationPolicy.setDeployed(false);
            applicationPolicy.setTenantDomain(tenantDomain);
            QuotaPolicy defaultQuotaPolicy = new QuotaPolicy();
            RequestCountLimit requestCountLimit = new RequestCountLimit();
            requestCountLimit.setRequestCount(requestCount[i]);
            requestCountLimit.setUnitTime(1);
            requestCountLimit.setTimeUnit(APIConstants.TIME_UNIT_MINUTE);
            defaultQuotaPolicy.setType(PolicyConstants.REQUEST_COUNT_TYPE);
            defaultQuotaPolicy.setLimit(requestCountLimit);
            applicationPolicy.setDefaultQuotaPolicy(defaultQuotaPolicy);

            if (!apiMgtDAO.isPolicyExist(PolicyConstants.POLICY_LEVEL_APP, tenantId, policyName)) {
                apiMgtDAO.addApplicationPolicy(applicationPolicy);
                needDeployment = true;
            }

            if (!apiMgtDAO.isPolicyDeployed(PolicyConstants.POLICY_LEVEL_APP, tenantId, policyName)) {
                needDeployment = true;
            }

            if (needDeployment) {
                String policyString;
                try {
                    policyString = policyBuilder.getThrottlePolicyForAppLevel(applicationPolicy);
                    String policyFile = applicationPolicy.getTenantDomain() + "_" + PolicyConstants.POLICY_LEVEL_APP +
                            "_" + applicationPolicy.getPolicyName();
                    if (!APIConstants.DEFAULT_APP_POLICY_UNLIMITED.equalsIgnoreCase(policyName)) {
                        deploymentManager.deployPolicyToGlobalCEP(policyString);
                    }
                    apiMgtDAO.setPolicyDeploymentStatus(PolicyConstants.POLICY_LEVEL_APP, applicationPolicy.getPolicyName(),
                            applicationPolicy.getTenantId(), true);
                } catch (APITemplateException e) {
                    throw new APIManagementException("Error while adding default subscription policy" + applicationPolicy.getPolicyName(), e);
                }
            }
        }

        long bronzeTierLimit = defualtLimits.containsKey(APIConstants.DEFAULT_SUB_POLICY_BRONZE) ?
                defualtLimits.get(APIConstants.DEFAULT_SUB_POLICY_BRONZE) : 1000;
        long silverTierLimit = defualtLimits.containsKey(APIConstants.DEFAULT_SUB_POLICY_SILVER) ?
                defualtLimits.get(APIConstants.DEFAULT_SUB_POLICY_SILVER) : 2000;
        long goldTierLimit = defualtLimits.containsKey(APIConstants.DEFAULT_SUB_POLICY_GOLD) ?
                defualtLimits.get(APIConstants.DEFAULT_SUB_POLICY_GOLD) : 5000;
        long unauthenticatedTierLimit = defualtLimits.containsKey(APIConstants.DEFAULT_APP_POLICY_FIFTY_REQ_PER_MIN) ?
                defualtLimits.get(APIConstants.DEFAULT_SUB_POLICY_UNAUTHENTICATED) : 500;
        //Adding Subscription level policies
        long[] requestCountSubPolicies = new long[]{goldTierLimit, silverTierLimit, bronzeTierLimit, unauthenticatedTierLimit, Integer.MAX_VALUE};
        String[] subPolicies = new String[]{APIConstants.DEFAULT_SUB_POLICY_GOLD, APIConstants.DEFAULT_SUB_POLICY_SILVER,
                APIConstants.DEFAULT_SUB_POLICY_BRONZE, APIConstants.DEFAULT_SUB_POLICY_UNAUTHENTICATED, APIConstants.DEFAULT_SUB_POLICY_UNLIMITED};
        String[] subPolicyDecs = new String[]{APIConstants.DEFAULT_SUB_POLICY_GOLD_DESC, APIConstants.DEFAULT_SUB_POLICY_SILVER_DESC,
                APIConstants.DEFAULT_SUB_POLICY_BRONZE_DESC, APIConstants.DEFAULT_SUB_POLICY_UNAUTHENTICATED_DESC, APIConstants.DEFAULT_SUB_POLICY_UNLIMITED_DESC};
        for (int i = 0; i < subPolicies.length; i++) {
            policyName = subPolicies[i];
            boolean needDeployment = false;
            SubscriptionPolicy subscriptionPolicy = new SubscriptionPolicy(policyName);
            subscriptionPolicy.setDisplayName(policyName);
            subscriptionPolicy.setDescription(subPolicyDecs[i]);
            subscriptionPolicy.setTenantId(tenantId);
            subscriptionPolicy.setDeployed(false);
            subscriptionPolicy.setTenantDomain(tenantDomain);
            QuotaPolicy defaultQuotaPolicy = new QuotaPolicy();
            RequestCountLimit requestCountLimit = new RequestCountLimit();
            requestCountLimit.setRequestCount(requestCountSubPolicies[i]);
            requestCountLimit.setUnitTime(1);
            requestCountLimit.setTimeUnit(APIConstants.TIME_UNIT_MINUTE);
            defaultQuotaPolicy.setType(PolicyConstants.REQUEST_COUNT_TYPE);
            defaultQuotaPolicy.setLimit(requestCountLimit);
            subscriptionPolicy.setDefaultQuotaPolicy(defaultQuotaPolicy);
            subscriptionPolicy.setStopOnQuotaReach(true);
            subscriptionPolicy.setBillingPlan(APIConstants.BILLING_PLAN_FREE);

            if (!apiMgtDAO.isPolicyExist(PolicyConstants.POLICY_LEVEL_SUB, tenantId, policyName)) {
                apiMgtDAO.addSubscriptionPolicy(subscriptionPolicy);
                needDeployment = true;
            }

            if (!apiMgtDAO.isPolicyDeployed(PolicyConstants.POLICY_LEVEL_SUB, tenantId, policyName)) {
                needDeployment = true;
            }

            if (needDeployment) {
                String policyString;
                try {
                    policyString = policyBuilder.getThrottlePolicyForSubscriptionLevel(subscriptionPolicy);
                    String policyFile = subscriptionPolicy.getTenantDomain() + "_" + PolicyConstants.POLICY_LEVEL_SUB +
                            "_" + subscriptionPolicy.getPolicyName();
                    if (!APIConstants.DEFAULT_SUB_POLICY_UNLIMITED.equalsIgnoreCase(policyName)) {
                        deploymentManager.deployPolicyToGlobalCEP(policyString);
                    }
                    apiMgtDAO.setPolicyDeploymentStatus(PolicyConstants.POLICY_LEVEL_SUB, subscriptionPolicy.getPolicyName(),
                            subscriptionPolicy.getTenantId(), true);
                } catch (APITemplateException e) {
                    throw new APIManagementException("Error while adding default application policy " + subscriptionPolicy.getPolicyName(), e);
                }
            }
        }

        long tenThousandPerMinTier = defualtLimits.containsKey(APIConstants.DEFAULT_API_POLICY_TEN_THOUSAND_REQ_PER_MIN) ?
                defualtLimits.get(APIConstants.DEFAULT_API_POLICY_TEN_THOUSAND_REQ_PER_MIN) : 10000;
        long twentyThousandPerMinTier = defualtLimits.containsKey(APIConstants.DEFAULT_API_POLICY_TWENTY_THOUSAND_REQ_PER_MIN) ?
                defualtLimits.get(APIConstants.DEFAULT_API_POLICY_TWENTY_THOUSAND_REQ_PER_MIN) : 20000;
        long fiftyThousandPerMinTier = defualtLimits.containsKey(APIConstants.DEFAULT_API_POLICY_FIFTY_THOUSAND_REQ_PER_MIN) ?
                defualtLimits.get(APIConstants.DEFAULT_API_POLICY_FIFTY_THOUSAND_REQ_PER_MIN) : 50000;
        long[] requestCountAPIPolicies = new long[]{fiftyThousandPerMinTier, twentyThousandPerMinTier, tenThousandPerMinTier, Integer.MAX_VALUE};

        //Adding Resource level policies
        String[] apiPolicies = new String[]{APIConstants.DEFAULT_API_POLICY_FIFTY_THOUSAND_REQ_PER_MIN, APIConstants.DEFAULT_API_POLICY_TWENTY_THOUSAND_REQ_PER_MIN,
                APIConstants.DEFAULT_API_POLICY_TEN_THOUSAND_REQ_PER_MIN, APIConstants.DEFAULT_API_POLICY_UNLIMITED};
        String[] apiPolicyDecs = new String[]{APIConstants.DEFAULT_API_POLICY_ULTIMATE_DESC, APIConstants.DEFAULT_API_POLICY_PLUS_DESC,
                APIConstants.DEFAULT_API_POLICY_BASIC_DESC, APIConstants.DEFAULT_API_POLICY_UNLIMITED_DESC};
        for (int i = 0; i < apiPolicies.length; i++) {
            boolean needDeployment = false;
            policyName = apiPolicies[i];
            APIPolicy apiPolicy = new APIPolicy(policyName);
            apiPolicy.setDisplayName(policyName);
            apiPolicy.setDescription(apiPolicyDecs[i]);
            apiPolicy.setTenantId(tenantId);
            apiPolicy.setUserLevel(APIConstants.API_POLICY_API_LEVEL);
            apiPolicy.setDeployed(false);
            apiPolicy.setTenantDomain(tenantDomain);
            QuotaPolicy defaultQuotaPolicy = new QuotaPolicy();
            RequestCountLimit requestCountLimit = new RequestCountLimit();
            requestCountLimit.setRequestCount(requestCountAPIPolicies[i]);
            requestCountLimit.setUnitTime(1);
            requestCountLimit.setTimeUnit(APIConstants.TIME_UNIT_MINUTE);
            defaultQuotaPolicy.setType(PolicyConstants.REQUEST_COUNT_TYPE);
            defaultQuotaPolicy.setLimit(requestCountLimit);
            apiPolicy.setDefaultQuotaPolicy(defaultQuotaPolicy);

            if (!apiMgtDAO.isPolicyExist(PolicyConstants.POLICY_LEVEL_API, tenantId, policyName)) {
                apiMgtDAO.addAPIPolicy(apiPolicy);
            }

            if (!apiMgtDAO.isPolicyDeployed(PolicyConstants.POLICY_LEVEL_API, tenantId, policyName)) {
                needDeployment = true;
            }

            if (needDeployment) {
                String policyString;
                try {
                    policyString = policyBuilder.getThrottlePolicyForAPILevelDefault(apiPolicy);
                    String policyFile = apiPolicy.getTenantDomain() + "_" + PolicyConstants.POLICY_LEVEL_API +
                            "_" + apiPolicy.getPolicyName() + "_default";
                    if (!APIConstants.DEFAULT_API_POLICY_UNLIMITED.equalsIgnoreCase(policyName)) {
                        deploymentManager.deployPolicyToGlobalCEP(policyString);
                    }
                    apiMgtDAO.setPolicyDeploymentStatus(PolicyConstants.POLICY_LEVEL_API, apiPolicy.getPolicyName(),
                            apiPolicy.getTenantId(), true);
                } catch (APITemplateException e) {
                    throw new APIManagementException("Error while adding default api policy " + apiPolicy.getPolicyName(), e);
                }
            }
        }
    }

    /**
     * Used to get advence throttling is enable
     *
     * @return condition of advance throttling
     */
    public static boolean isAdvanceThrottlingEnabled() {
        return ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().getAPIManagerConfiguration()
                .getThrottleProperties().isEnabled();
    }

    /**
     * Used to get unlimited throttling tier is enable
     *
     * @return condition of enable unlimited tier
     */
    public static boolean isEnabledUnlimitedTier() {
        ThrottleProperties throttleProperties = ServiceReferenceHolder.getInstance()
                .getAPIManagerConfigurationService().getAPIManagerConfiguration()
                .getThrottleProperties();
        if (throttleProperties.isEnabled()) {
            return throttleProperties.isEnableUnlimitedTier();
        } else {
            APIManagerConfiguration config = ServiceReferenceHolder.getInstance().
                    getAPIManagerConfigurationService().getAPIManagerConfiguration();
            return JavaUtils.isTrueExplicitly(config.getFirstProperty(APIConstants.ENABLE_UNLIMITED_TIER));
        }
    }

    /**
     * Used to get subscription Spike arrest Enable
     *
     * @return condition of Subscription Spike arrest configuration
     */
    public static boolean isEnabledSubscriptionSpikeArrest() {
        ThrottleProperties throttleProperties = ServiceReferenceHolder.getInstance()
                .getAPIManagerConfigurationService().getAPIManagerConfiguration()
                .getThrottleProperties();
        return throttleProperties.isEnabledSubscriptionLevelSpikeArrest();
    }

    /**
     * This method is used to get the labels in a given tenant space
     *
     * @param tenantDomain tenant domain name
     * @return micro gateway labels in a given tenant space
     * @throws APIManagementException if failed to fetch micro gateway labels
     */
    public static List<Label> getAllLabels(String tenantDomain) throws APIManagementException {
        ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();
        return apiMgtDAO.getAllLabels(tenantDomain);
    }

    public static Map<String, Tier> getTiersFromPolicies(String policyLevel, int tenantId) throws APIManagementException {
        Map<String, Tier> tierMap = new HashMap<String, Tier>();
        ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();
        Policy[] policies;
        if (PolicyConstants.POLICY_LEVEL_SUB.equalsIgnoreCase(policyLevel)) {
            policies = apiMgtDAO.getSubscriptionPolicies(tenantId);
        } else if (PolicyConstants.POLICY_LEVEL_API.equalsIgnoreCase(policyLevel)) {
            policies = apiMgtDAO.getAPIPolicies(tenantId);
        } else if (PolicyConstants.POLICY_LEVEL_APP.equalsIgnoreCase(policyLevel)) {
            policies = apiMgtDAO.getApplicationPolicies(tenantId);
        } else {
            throw new APIManagementException("No such a policy type : " + policyLevel);
        }

        for (Policy policy : policies) {
            if (!APIConstants.UNLIMITED_TIER.equalsIgnoreCase(policy.getPolicyName())) {
                Tier tier = new Tier(policy.getPolicyName());
                tier.setDescription(policy.getDescription());
                tier.setDisplayName(policy.getDisplayName());
                Limit limit = policy.getDefaultQuotaPolicy().getLimit();
                tier.setTimeUnit(limit.getTimeUnit());
                tier.setUnitTime(limit.getUnitTime());

                //If the policy is a subscription policy
                if (policy instanceof SubscriptionPolicy) {
                    SubscriptionPolicy subscriptionPolicy = (SubscriptionPolicy) policy;
                    setBillingPlanAndCustomAttributesToTier(subscriptionPolicy, tier);
                    if(StringUtils.equals(subscriptionPolicy.getBillingPlan(),APIConstants.COMMERCIAL_TIER_PLAN)){
                        tier.setMonetizationAttributes(subscriptionPolicy.getMonetizationPlanProperties());
                    }
                }

                if (limit instanceof RequestCountLimit) {

                    RequestCountLimit countLimit = (RequestCountLimit) limit;
                    tier.setRequestsPerMin(countLimit.getRequestCount());
                    tier.setRequestCount(countLimit.getRequestCount());
                } else {
                    BandwidthLimit bandwidthLimit = (BandwidthLimit) limit;
                    tier.setRequestsPerMin(bandwidthLimit.getDataAmount());
                    tier.setRequestCount(bandwidthLimit.getDataAmount());
                }
                if (PolicyConstants.POLICY_LEVEL_SUB.equalsIgnoreCase(policyLevel)) {
                    tier.setTierPlan(((SubscriptionPolicy) policy).getBillingPlan());
                }
                tierMap.put(policy.getPolicyName(), tier);
            } else {
                if (APIUtil.isEnabledUnlimitedTier()) {
                    Tier tier = new Tier(policy.getPolicyName());
                    tier.setDescription(policy.getDescription());
                    tier.setDisplayName(policy.getDisplayName());
                    tier.setRequestsPerMin(Integer.MAX_VALUE);
                    tier.setRequestCount(Integer.MAX_VALUE);
                    if (isUnlimitedTierPaid(getTenantDomainFromTenantId(tenantId))) {
                        tier.setTierPlan(APIConstants.COMMERCIAL_TIER_PLAN);
                    } else {
                        tier.setTierPlan(APIConstants.BILLING_PLAN_FREE);
                    }

                    tierMap.put(policy.getPolicyName(), tier);
                }
            }
        }

        if (PolicyConstants.POLICY_LEVEL_SUB.equalsIgnoreCase(policyLevel)) {
            tierMap.remove(APIConstants.UNAUTHENTICATED_TIER);
        }
        return tierMap;
    }

    /**
     * Extract custom attributes and billing plan from subscription policy and set to tier.
     *
     * @param subscriptionPolicy - The SubscriptionPolicy object to extract details from
     * @param tier               - The Tier to set information into
     */
    public static void setBillingPlanAndCustomAttributesToTier(SubscriptionPolicy subscriptionPolicy, Tier tier) {

        //set the billing plan.
        tier.setTierPlan(subscriptionPolicy.getBillingPlan());

        //If the tier has custom attributes
        if (subscriptionPolicy.getCustomAttributes() != null &&
                subscriptionPolicy.getCustomAttributes().length > 0) {

            Map<String, Object> tierAttributes = new HashMap<String, Object>();
            try {
                String customAttr = new String(subscriptionPolicy.getCustomAttributes(), "UTF-8");
                JSONParser parser = new JSONParser();
                JSONArray jsonArr = (JSONArray) parser.parse(customAttr);
                Iterator jsonArrIterator = jsonArr.iterator();
                while (jsonArrIterator.hasNext()) {
                    JSONObject json = (JSONObject) jsonArrIterator.next();
                    tierAttributes.put(String.valueOf(json.get("name")), json.get("value"));
                }
                tier.setTierAttributes(tierAttributes);
            } catch (ParseException e) {
                log.error("Unable to convert String to Json", e);
                tier.setTierAttributes(null);
            } catch (UnsupportedEncodingException e) {
                log.error("Custom attribute byte array does not use UTF-8 character set", e);
                tier.setTierAttributes(null);
            }
        }
    }

    public static Set<Tier> getAvailableTiers(Map<String, Tier> definedTiers, String tiers, String apiName) {
        Set<Tier> availableTier = new HashSet<Tier>();
        if (tiers != null && !"".equals(tiers)) {
            String[] tierNames = tiers.split("\\|\\|");
            for (String tierName : tierNames) {
                Tier definedTier = definedTiers.get(tierName);
                if (definedTier != null) {
                    availableTier.add(definedTier);
                } else {
                    log.warn("Unknown tier: " + tierName + " found on API: " + apiName);
                }
            }
        }
        return availableTier;
    }

    public static byte[] toByteArray(InputStream is) throws IOException {
        return IOUtils.toByteArray(is);
    }

    public static long ipToLong(String ipAddress) {
        long result = 0;
        String[] ipAddressInArray = ipAddress.split("\\.");
        for (int i = 3; i >= 0; i--) {
            long ip = Long.parseLong(ipAddressInArray[3 - i]);
            //left shifting 24,16,8,0 and bitwise OR
            //1. 192 << 24
            //1. 168 << 16
            //1. 1   << 8
            //1. 2   << 0
            result |= ip << (i * 8);

        }
        return result;
    }

    public String getFullLifeCycleData(Registry registry) throws XMLStreamException, RegistryException {
        return CommonUtil.getLifecycleConfiguration(APIConstants.API_LIFE_CYCLE, registry);

    }

    /**
     * Composes OR based search criteria from provided array of values
     *
     * @param values
     * @return
     */
    public static String getORBasedSearchCriteria(String[] values) {
        String criteria = "(";
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                criteria = criteria + values[i];
                if (i != values.length - 1) {
                    criteria = criteria + " OR ";
                } else {
                    criteria = criteria + ")";
                }
            }
            return criteria;
        }
        return null;
    }

    /**
     * Generates solr compatible search criteria synatax from user entered query criteria.
     * Ex: From version:1.0.0, this returns version=*1.0.0*
     *
     * @param criteria
     * @return solar compatible criteria
     * @throws APIManagementException
     */
    public static String getSingleSearchCriteria(String criteria) throws APIManagementException {
        criteria = criteria.trim();
        String searchValue = criteria;
        String searchKey = APIConstants.NAME_TYPE_PREFIX;

        if (criteria.contains(":")) {
            if (criteria.split(":").length > 1) {
                String[] splitValues = criteria.split(":");
                searchKey = splitValues[0].trim();
                searchValue = splitValues[1];
                //if search key is 'tag' instead of 'tags', allow it as well since rest api document says query
                // param to use for tag search is 'tag'

                if (APIConstants.TAG_SEARCH_TYPE_PREFIX3.equals(searchKey)) {
                    searchKey = APIConstants.TAG_SEARCH_TYPE_PREFIX;
                    searchValue = searchValue.replace(" ", "\\ ");
                }

                if (!APIConstants.DOCUMENTATION_SEARCH_TYPE_PREFIX.equalsIgnoreCase(searchKey) &&
                        !APIConstants.TAG_SEARCH_TYPE_PREFIX.equalsIgnoreCase(searchKey)) {
                    if (!(searchValue.endsWith("\"") && searchValue.startsWith("\""))) {
                        if (!searchValue.endsWith("*")) {
                            searchValue = searchValue + "*";
                        }
                        if (!searchValue.startsWith("*")) {
                            searchValue = "*" + searchValue;
                        }
                    }
                }

            } else {
                throw new APIManagementException("Search term is missing. Try again with valid search query.");
            }
        } else {
            if (!(searchValue.endsWith("\"") && searchValue.startsWith("\""))) {
                if (!searchValue.endsWith("*")) {
                    searchValue = searchValue + "*";
                }
                if (!searchValue.startsWith("*")) {
                    searchValue = "*" + searchValue;
                }
            }
        }
        if (APIConstants.API_PROVIDER.equalsIgnoreCase(searchKey)) {
            searchValue = searchValue.replaceAll("@", "-AT-");
        }
        return searchKey + "=" + searchValue;
    }

    /**
     * return whether store forum feature is enabled
     *
     * @return true or false indicating enable or not
     */
    public static boolean isStoreForumEnabled() {
        APIManagerConfiguration config = ServiceReferenceHolder.getInstance().
                getAPIManagerConfigurationService().getAPIManagerConfiguration();
        String forumEnabled = config.getFirstProperty(APIConstants.API_STORE_FORUM_ENABLED);
        if (forumEnabled == null) {
            return true;
        }
        return Boolean.parseBoolean(forumEnabled);
    }

    /**
     * Returns a secured DocumentBuilderFactory instance
     *
     * @return DocumentBuilderFactory
     */
    public static DocumentBuilderFactory getSecuredDocumentBuilder() {

        org.apache.xerces.impl.Constants Constants = null;
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        try {
            dbf.setFeature(Constants.SAX_FEATURE_PREFIX + Constants.EXTERNAL_GENERAL_ENTITIES_FEATURE, false);
            dbf.setFeature(Constants.SAX_FEATURE_PREFIX + Constants.EXTERNAL_PARAMETER_ENTITIES_FEATURE, false);
            dbf.setFeature(Constants.XERCES_FEATURE_PREFIX + Constants.LOAD_EXTERNAL_DTD_FEATURE, false);
        } catch (ParserConfigurationException e) {
            log.error(
                    "Failed to load XML Processor Feature " + Constants.EXTERNAL_GENERAL_ENTITIES_FEATURE + " or " +
                            Constants.EXTERNAL_PARAMETER_ENTITIES_FEATURE + " or " + Constants.LOAD_EXTERNAL_DTD_FEATURE);
        }

        SecurityManager securityManager = new SecurityManager();
        securityManager.setEntityExpansionLimit(ENTITY_EXPANSION_LIMIT);
        dbf.setAttribute(Constants.XERCES_PROPERTY_PREFIX + Constants.SECURITY_MANAGER_PROPERTY, securityManager);

        return dbf;
    }

    /**
     * Logs an audit message on actions performed on entities (APIs, Applications, etc). The log is printed in the
     * following JSON format
     * {
     * "typ": "API",
     * "action": "update",
     * "performedBy": "admin@carbon.super",
     * "info": {
     * "name": "Twitter",
     * "context": "/twitter",
     * "version": "1.0.0",
     * "provider": "nuwan"
     * }
     * }
     *
     * @param entityType  - The entity type. Ex: API, Application
     * @param entityInfo  - The details of the entity. Ex: API Name, Context
     * @param action      - The type of action performed. Ex: Create, Update
     * @param performedBy - The user who performs the action.
     */
    public static void logAuditMessage(String entityType, String entityInfo, String action, String performedBy) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("typ", entityType);
        jsonObject.put("action", action);
        jsonObject.put("performedBy", performedBy);
        jsonObject.put("info", entityInfo);
        audit.info(jsonObject.toString());
    }

    public static int getPortOffset() {
        ServerConfiguration carbonConfig = ServerConfiguration.getInstance();
        String portOffset = System.getProperty(APIConstants.PORT_OFFSET_SYSTEM_VAR,
                carbonConfig.getFirstProperty(APIConstants.PORT_OFFSET_CONFIG));
        try {
            if ((portOffset != null)) {
                return Integer.parseInt(portOffset.trim());
            } else {
                return 0;
            }
        } catch (NumberFormatException e) {
            log.error("Invalid Port Offset: " + portOffset + ". Default value 0 will be used.", e);
            return 0;
        }
    }

    public static boolean isQueryParamDataPublishingEnabled() {
        return ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().getAPIManagerConfiguration().
                getThrottleProperties().isEnableQueryParamConditions();
    }

    public static boolean isHeaderDataPublishingEnabled() {
        return ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().getAPIManagerConfiguration().
                getThrottleProperties().isEnableHeaderConditions();
    }

    public static boolean isJwtTokenPublishingEnabled() {
        return ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().getAPIManagerConfiguration().
                getThrottleProperties().isEnableJwtConditions();
    }

    public static String getAnalyticsServerURL() {
        return ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().getAPIAnalyticsConfiguration().
                getDasServerUrl();
    }

    public static String getAnalyticsServerUserName() {
        return ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().getAPIAnalyticsConfiguration().
                getDasReceiverServerUser();
    }

    public static String getAnalyticsServerPassword() {
        return ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().getAPIAnalyticsConfiguration().
                getDasReceiverServerPassword();
    }

    /**
     * Create the Cache object from the given parameters
     *
     * @param cacheManagerName - Name of the Cache Manager
     * @param cacheName        - Name of the Cache
     * @param modifiedExp      - Value of the MODIFIED Expiry Type
     * @param accessExp        - Value of the ACCESSED Expiry Type
     * @return - The cache object
     */
    public static Cache getCache(final String cacheManagerName, final String cacheName, final long modifiedExp,
                                 final long accessExp) {

        return Caching.getCacheManager(
                cacheManagerName).createCacheBuilder(cacheName).
                setExpiry(CacheConfiguration.ExpiryType.MODIFIED, new CacheConfiguration.Duration(TimeUnit.SECONDS,
                        modifiedExp)).
                setExpiry(CacheConfiguration.ExpiryType.ACCESSED, new CacheConfiguration.Duration(TimeUnit.SECONDS,
                        accessExp)).setStoreByValue(false).build();
    }

    /**
     * This method is used to get the actual endpoint password of an API from the hidden property
     * in the case where the handler APIEndpointPasswordRegistryHandler is enabled in registry.xml
     *
     * @param api      The API
     * @param registry The registry object
     * @return The actual password of the endpoint if exists
     * @throws RegistryException Throws if the api resource doesn't exist
     */
    private static String getActualEpPswdFromHiddenProperty(API api, Registry registry) throws RegistryException {
        String apiPath = APIUtil.getAPIPath(api.getId());
        Resource apiResource = registry.get(apiPath);
        return apiResource.getProperty(APIConstants.REGISTRY_HIDDEN_ENDPOINT_PROPERTY);
    }

    /**
     * To check whether given role exist in the array of roles.
     *
     * @param userRoleList      Role list to check against.
     * @param accessControlRole Access Control Role.
     * @return true if the Array contains the role specified.
     */
    public static boolean compareRoleList(String[] userRoleList, String accessControlRole) {
        if (userRoleList != null) {
            for (String userRole : userRoleList) {
                if (userRole.equalsIgnoreCase(accessControlRole)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * To clear the publisherRoleCache for certain users.
     *
     * @param userName Names of the user.
     */
    public static void clearRoleCache(String userName) {
        if (isPublisherRoleCacheEnabled) {
            Caching.getCacheManager(APIConstants.API_MANAGER_CACHE_MANAGER).getCache(APIConstants
                    .API_PUBLISHER_ADMIN_PERMISSION_CACHE).remove(userName);
            Caching.getCacheManager(APIConstants.API_MANAGER_CACHE_MANAGER).getCache(APIConstants
                    .API_USER_ROLE_CACHE).remove(userName);
        }
    }

    /**
     * Used in application sharing to check if this featuer is enabled
     *
     * @return returns true if ENABLE_MULTIPLE_GROUPID is set to True
     */
    public static boolean isMultiGroupAppSharingEnabled() {

        if (multiGrpAppSharing == null) {

            APIManagerConfiguration config = ServiceReferenceHolder.getInstance().
                    getAPIManagerConfigurationService().getAPIManagerConfiguration();

            String groupIdExtractorClass = config.getFirstProperty(APIConstants
                    .API_STORE_GROUP_EXTRACTOR_IMPLEMENTATION);

            if (groupIdExtractorClass != null && !groupIdExtractorClass.isEmpty()) {
                try {

                    LoginPostExecutor groupingExtractor = (LoginPostExecutor) APIUtil.getClassForName
                            (groupIdExtractorClass).newInstance();

                    if (groupingExtractor instanceof NewPostLoginExecutor) {
                        multiGrpAppSharing = "true";
                    } else {
                        multiGrpAppSharing = "false";
                    }
                    // if there is a exception the default flow will work hence ingnoring the applications
                } catch (InstantiationException e) {
                    multiGrpAppSharing = "false";
                } catch (IllegalAccessException e) {
                    multiGrpAppSharing = "false";
                } catch (ClassNotFoundException e) {
                    multiGrpAppSharing = "false";
                }
            } else {
                multiGrpAppSharing = "false";
            }
        }
        return Boolean.valueOf(multiGrpAppSharing);
    }

    /**
     * Used to reconstruct the input search query as sub context and doc content doesn't support AND search
     *
     * @param query Input search query
     * @return Reconstructed new search query
     * @throws APIManagementException If there is an error in the search query
     */
    public static String constructNewSearchQuery(String query) throws APIManagementException {
        String newSearchQuery = "";
        String inputSearchQuery = query.trim();

        // sub context and doc content doesn't support AND search
        if (inputSearchQuery != null && inputSearchQuery.contains(" ") && !inputSearchQuery
                .contains(APIConstants.TAG_SEARCH_TYPE_PREFIX4) && !inputSearchQuery
                .contains(APIConstants.CONTENT_SEARCH_TYPE_PREFIX)) {
            if (inputSearchQuery.split(" ").length > 1) {
                String[] searchCriterias = inputSearchQuery.split(" ");
                for (int i = 0; i < searchCriterias.length; i++) {
                    if (searchCriterias[i].contains(":") && searchCriterias[i].split(":").length > 1) {
                        if (APIConstants.DOCUMENTATION_SEARCH_TYPE_PREFIX
                                .equalsIgnoreCase(searchCriterias[i].split(":")[0])
                                || APIConstants.SUBCONTEXT_SEARCH_TYPE_PREFIX
                                .equalsIgnoreCase(searchCriterias[i].split(":")[0])) {
                            throw new APIManagementException("Invalid query. AND based search is not supported for "
                                    + "doc and subcontext prefixes");
                        }
                    }
                    if (i == 0) {
                        newSearchQuery = APIUtil.getSingleSearchCriteria(searchCriterias[i]);
                    } else {
                        newSearchQuery = newSearchQuery + APIConstants.SEARCH_AND_TAG + APIUtil
                                .getSingleSearchCriteria(searchCriterias[i]);
                    }
                }
            }
        } else {
            newSearchQuery = APIUtil.getSingleSearchCriteria(inputSearchQuery);
        }
        return newSearchQuery;
    }

    /**
     * Removes x-mediation-scripts from swagger as they should not be provided to store consumers
     *
     * @param apiSwagger swagger definition of API
     * @return swagger which exclude x-mediation-script elements
     */
    public static String removeXMediationScriptsFromSwagger(String apiSwagger) {
        //removes x-mediation-script key:values
        String mediationScriptRegex = "\"x-mediation-script\":\".*?(?<!\\\\)\"";
        Pattern pattern = Pattern.compile("," + mediationScriptRegex);
        Matcher matcher = pattern.matcher(apiSwagger);
        while (matcher.find()) {
            apiSwagger = apiSwagger.replace(matcher.group(), "");
        }
        pattern = Pattern.compile(mediationScriptRegex + ",");
        matcher = pattern.matcher(apiSwagger);
        while (matcher.find()) {
            apiSwagger = apiSwagger.replace(matcher.group(), "");
        }
        return apiSwagger;
    }

    /**
     * Handle if any cross tenant access permission violations detected. Cross tenant resources (apis/apps) can be
     * retrieved only by super tenant admin user, only while a migration process(2.6.0 to 3.0.0). APIM server has to be
     * started with the system property 'migrationMode=true' if a migration related exports are to be done.
     *
     * @param targetTenantDomain Tenant domain of which resources are requested
     * @param username           Logged in user name
     * @throws APIMgtInternalException  When internal error occurred
     */
    public static boolean hasUserAccessToTenant(String username, String targetTenantDomain)
            throws APIMgtInternalException {
        String superAdminRole = null;
        
        //Accessing the same tenant as the user's tenant
        if (targetTenantDomain.equals(MultitenantUtils.getTenantDomain(username))) {
            return true;
        }

        try {
            superAdminRole = ServiceReferenceHolder.getInstance().getRealmService().
                    getTenantUserRealm(org.wso2.carbon.utils.multitenancy.MultitenantConstants.SUPER_TENANT_ID).getRealmConfiguration().getAdminRoleName();
        } catch (UserStoreException e) {
            handleInternalException("Error in getting super admin role name", e);
        }

        //check whether logged in user is a super tenant user
        String superTenantDomain = null;
        try {
            superTenantDomain = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().
                    getSuperTenantDomain();
        } catch (UserStoreException e) {
            handleInternalException("Error in getting the super tenant domain", e);
        }
        boolean isSuperTenantUser = MultitenantUtils.getTenantDomain(username).equals(superTenantDomain);
        if (!isSuperTenantUser) {
            return false;
        }

        //check whether the user has super tenant admin role
        boolean isSuperAdminRoleNameExistInUser = false;
        try {
            isSuperAdminRoleNameExistInUser = isUserInRole(username, superAdminRole);
        } catch (UserStoreException | APIManagementException e) {
            handleInternalException("Error in checking whether the user has admin role", e);
        }

        return isSuperAdminRoleNameExistInUser;
    }

    /**
     * To set the resource properties to the API.
     *
     * @param api          API that need to set the resource properties.
     * @param registry     Registry to get the resource from.
     * @param artifactPath Path of the API artifact.
     * @return Updated API.
     * @throws RegistryException Registry Exception.
     */
    private static API setResourceProperties(API api, Registry registry, String artifactPath) throws RegistryException {
        Resource apiResource = registry.get(artifactPath);
        Properties properties = apiResource.getProperties();
        if (properties != null) {
            Enumeration propertyNames = properties.propertyNames();
            while (propertyNames.hasMoreElements()) {
                String propertyName = (String) propertyNames.nextElement();
                if (log.isDebugEnabled()) {
                    log.debug("API '" + api.getId().toString() + "' " + "has the property " + propertyName);
                }
                if (propertyName.startsWith(APIConstants.API_RELATED_CUSTOM_PROPERTIES_PREFIX)) {
                    api.addProperty(propertyName.substring(APIConstants.API_RELATED_CUSTOM_PROPERTIES_PREFIX.length()),
                            apiResource.getProperty(propertyName));
                }
            }
        }
        api.setAccessControl(apiResource.getProperty(APIConstants.ACCESS_CONTROL));

        String accessControlRoles = null;

        String displayPublisherRoles = apiResource.getProperty(APIConstants.DISPLAY_PUBLISHER_ROLES);
        if (displayPublisherRoles == null) {

            String publisherRoles = apiResource.getProperty(APIConstants.PUBLISHER_ROLES);

            if (publisherRoles != null) {
                accessControlRoles = APIConstants.NULL_USER_ROLE_LIST.equals(
                        apiResource.getProperty(APIConstants.PUBLISHER_ROLES)) ?
                        null : apiResource.getProperty(APIConstants.PUBLISHER_ROLES);
            }
        } else {
            accessControlRoles = APIConstants.NULL_USER_ROLE_LIST.equals(displayPublisherRoles) ?
                    null : displayPublisherRoles;
        }

        api.setAccessControlRoles(accessControlRoles);
        return api;
    }

    /**
     * This method is used to get the authorization configurations from the tenant registry or from api-manager.xml if 
     * config is not available in tenant registry
     *
     * @param tenantId The Tenant ID
     * @param property The configuration to get from tenant registry or api-manager.xml
     * @return The configuration read from tenant registry or api-manager.xml
     * @throws APIManagementException Throws if the registry resource doesn't exist
     *                                or the content cannot be parsed to JSON
     */
    public static String getOAuthConfiguration(int tenantId, String property)
            throws APIManagementException {
        String authConfigValue = APIUtil
                .getOAuthConfigurationFromTenantRegistry(tenantId, property);
        if (StringUtils.isBlank(authConfigValue)) {
            authConfigValue = APIUtil.getOAuthConfigurationFromAPIMConfig(property);
        }
        return authConfigValue;
    }
    
    /**
     * This method is used to get the authorization configurations from the tenant registry
     *
     * @param tenantId The Tenant ID
     * @param property The configuration to get from tenant registry
     * @return The configuration read from tenant registry or else null
     * @throws APIManagementException Throws if the registry resource doesn't exist
     *                                or the content cannot be parsed to JSON
     */
    public static String getOAuthConfigurationFromTenantRegistry(int tenantId, String property)
            throws APIManagementException {
        try {
            Registry registryConfig = ServiceReferenceHolder.getInstance().getRegistryService()
                    .getConfigSystemRegistry(tenantId);

            if (registryConfig.resourceExists(APIConstants.API_TENANT_CONF_LOCATION)) {
                Resource resource = registryConfig.get(APIConstants.API_TENANT_CONF_LOCATION);
                String content = new String((byte[]) resource.getContent(), Charset.defaultCharset());
                if (content != null) {
                    JSONObject tenantConfig = (JSONObject) new JSONParser().parse(content);
                    //Read the configuration from the tenant registry
                    String oAuthConfiguration = "";
                    if (null != tenantConfig.get(property)) {
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append(tenantConfig.get(property));
                        oAuthConfiguration = stringBuilder.toString();
                    }

                    if (!StringUtils.isBlank(oAuthConfiguration)) {
                        return oAuthConfiguration;
                    }
                }
            }
        } catch (RegistryException e) {
            String msg = "Error while retrieving " + property + " from tenant registry.";
            throw new APIManagementException(msg, e);
        } catch (ParseException pe) {
            String msg = "Couldn't create json object from Swagger object for custom OAuth header.";
            throw new APIManagementException(msg, pe);
        }
        return null;
    }

    /**
     * This method is used to get the authorization configurations from the api manager configurations
     *
     * @param property The configuration to get from api-manager.xml
     * @return The configuration read from api-manager.xml or else null
     * @throws APIManagementException Throws if the registry resource doesn't exist
     *                                or the content cannot be parsed to JSON
     */
    public static String getOAuthConfigurationFromAPIMConfig(String property)
            throws APIManagementException {

            //If tenant registry doesn't have the configuration, then read it from api-manager.xml
            APIManagerConfiguration apimConfig = ServiceReferenceHolder.getInstance()
                    .getAPIManagerConfigurationService().getAPIManagerConfiguration();
            String oAuthConfiguration = apimConfig.getFirstProperty(APIConstants.OAUTH_CONFIGS + property);

            if (!StringUtils.isBlank(oAuthConfiguration)) {
                return oAuthConfiguration;
            }

        return null;
    }

    public static boolean isForgetPasswordConfigured() {
        AxisConfiguration axis2Config = ServiceReferenceHolder.getContextService().getServerConfigContext()
                .getAxisConfiguration();
        TransportOutDescription emailTransportSender = axis2Config.getTransportOut(APIConstants.EMAIL_TRANSPORT);
        if (emailTransportSender != null) {
            return true;
        }
        return false;
    }

    /**
     * Used to get the custom pagination limit for store
     *
     * @return returns the store pagination value from api-manager.xml
     */
    public static int getApisPerPageInStore() {
        String paginationLimit = ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService()
                .getAPIManagerConfiguration().getFirstProperty(APIConstants.API_STORE_APIS_PER_PAGE);
        if (paginationLimit != null) {
            return Integer.parseInt(paginationLimit);
        }
        return 0;
    }

    /**
     * Used to get the custom pagination limit for publisher
     *
     * @return returns the publisher pagination value from api-manager.xml
     */
    public static int getApisPerPageInPublisher() {
        String paginationLimit = ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService()
                .getAPIManagerConfiguration().getFirstProperty(APIConstants.API_PUBLISHER_APIS_PER_PAGE);
        if (paginationLimit != null) {
            return Integer.parseInt(paginationLimit);
        }
        return 0;
    }


    /**
     * This method is used to get application from client id.
     *
     * @param clientId  client id
     * @return application object.
     * @throws APIManagementException
     */
    public static Application getApplicationByClientId(String clientId) throws APIManagementException {
        ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();
        return apiMgtDAO.getApplicationByClientId(clientId);
    }

    public static List<ConditionDto> extractConditionDto(String base64EncodedString) throws ParseException {

        List<ConditionDto> conditionDtoList = new ArrayList<>();
        String base64Decoded = new String(Base64.decodeBase64(base64EncodedString));
        JSONArray conditionJsonArray = (JSONArray) new JSONParser().parse(base64Decoded);
        for (Object conditionJson : conditionJsonArray) {
            ConditionDto conditionDto = new ConditionDto();
            JSONObject conditionJsonObject = (JSONObject) conditionJson;
            if (conditionJsonObject.containsKey(PolicyConstants.IP_SPECIFIC_TYPE.toLowerCase())) {
                JSONObject ipSpecificCondition = (JSONObject) conditionJsonObject.get(PolicyConstants.IP_SPECIFIC_TYPE
                        .toLowerCase());
                ConditionDto.IPCondition ipCondition = new Gson().fromJson(ipSpecificCondition.toJSONString(),
                        ConditionDto.IPCondition.class);
                conditionDto.setIpCondition(ipCondition);
            } else if (conditionJsonObject.containsKey(PolicyConstants.IP_RANGE_TYPE.toLowerCase())) {
                JSONObject ipRangeCondition = (JSONObject) conditionJsonObject.get(PolicyConstants.IP_RANGE_TYPE
                        .toLowerCase());
                ConditionDto.IPCondition ipCondition = new Gson().fromJson(ipRangeCondition.toJSONString(),
                        ConditionDto.IPCondition.class);
                conditionDto.setIpRangeCondition(ipCondition);
            }
            if (conditionJsonObject.containsKey(PolicyConstants.JWT_CLAIMS_TYPE.toLowerCase())) {
                JSONObject jwtClaimConditions = (JSONObject) conditionJsonObject.get(PolicyConstants.JWT_CLAIMS_TYPE
                        .toLowerCase());
                ConditionDto.JWTClaimConditions jwtClaimCondition = new Gson().fromJson(jwtClaimConditions
                        .toJSONString(), ConditionDto.JWTClaimConditions.class);
                conditionDto.setJwtClaimConditions(jwtClaimCondition);
            }
            if (conditionJsonObject.containsKey(PolicyConstants.HEADER_TYPE.toLowerCase())) {
                JSONObject headerConditionJson = (JSONObject) conditionJsonObject.get(PolicyConstants.HEADER_TYPE
                        .toLowerCase());
                ConditionDto.HeaderConditions headerConditions = new Gson().fromJson(headerConditionJson
                        .toJSONString(), ConditionDto.HeaderConditions.class);
                conditionDto.setHeaderConditions(headerConditions);
            }

            if (conditionJsonObject.containsKey(PolicyConstants.QUERY_PARAMETER_TYPE.toLowerCase())) {
                JSONObject queryParamConditionJson = (JSONObject) conditionJsonObject.get(PolicyConstants
                        .QUERY_PARAMETER_TYPE.toLowerCase());
                ConditionDto.QueryParamConditions queryParamCondition = new Gson().fromJson(queryParamConditionJson
                        .toJSONString(), ConditionDto.QueryParamConditions.class);
                conditionDto.setQueryParameterConditions(queryParamCondition);
            }
            conditionDtoList.add(conditionDto);
        }
        conditionDtoList.sort(new Comparator<ConditionDto>() {
            @Override
            public int compare(ConditionDto o1, ConditionDto o2) {

                if (o1.getIpCondition() != null && o2.getIpCondition() == null) {
                    return -1;
                } else if (o1.getIpCondition() == null && o2.getIpCondition() != null) {
                    return 1;
                } else {
                    if (o1.getIpRangeCondition() != null && o2.getIpRangeCondition() == null) {
                        return -1;
                    } else if (o1.getIpRangeCondition() == null && o2.getIpRangeCondition() != null) {
                        return 1;
                    } else {
                        if (o1.getHeaderConditions() != null && o2.getHeaderConditions() == null) {
                            return -1;
                        } else if (o1.getHeaderConditions() == null && o2.getHeaderConditions() != null) {
                            return 1;
                        } else {
                            if (o1.getQueryParameterConditions() != null && o2.getQueryParameterConditions() == null) {
                                return -1;
                            } else if (o1.getQueryParameterConditions() == null && o2.getQueryParameterConditions()
                                    != null) {
                                return 1;
                            } else {
                                if (o1.getJwtClaimConditions() != null && o2.getJwtClaimConditions() == null) {
                                    return -1;
                                } else if (o1.getJwtClaimConditions() == null && o2.getJwtClaimConditions() != null) {
                                    return 1;
                                }
                            }
                        }
                    }
                }
                return 0;
            }
        });

        return conditionDtoList;
    }

    /**
     * Get if there any tenant-specific application configurations from the tenant
     * registry
     *
     * @param tenantId The Tenant Id
     * @return JSONObject The Application Attributes read from tenant registry or else null
     * @throws APIManagementException Throws if the registry resource doesn't exist
     * or the content cannot be parsed to JSON
     */
    public static JSONObject getAppAttributeKeysFromRegistry(int tenantId) throws APIManagementException {

        try {
            Registry registryConfig = ServiceReferenceHolder.getInstance().getRegistryService().getConfigSystemRegistry(tenantId);
            if (registryConfig.resourceExists(APIConstants.API_TENANT_CONF_LOCATION)) {
                Resource resource = registryConfig.get(APIConstants.API_TENANT_CONF_LOCATION);
                String content = new String((byte[]) resource.getContent(), Charset.defaultCharset());
                if (content != null) {
                    JSONObject tenantConfigs = (JSONObject) new JSONParser().parse(content);
                    String property = APIConstants.ApplicationAttributes.APPLICATION_CONFIGURATIONS;
                    if (tenantConfigs.keySet().contains(property)) {
                        return (JSONObject) tenantConfigs.get(APIConstants.ApplicationAttributes.APPLICATION_CONFIGURATIONS);
                    }
                }
            }
        } catch (RegistryException exception) {
            String msg = "Error while retrieving application attributes from tenant registry.";
            throw new APIManagementException(msg, exception);
        } catch (ParseException parseExceptione) {
            String msg = "Couldn't create json object from Swagger object for custom application attributes.";
            throw new APIManagementException(msg, parseExceptione);
        }
        return null;
    }

    /**
     * Validate the input file name for invalid path elements
     *
     * @param fileName
     */
    public static void validateFileName(String fileName) throws APIManagementException {
        if (!fileName.isEmpty() && (fileName.contains("../") || fileName.contains("..\\"))) {
            handleException("File name contains invalid path elements. " + fileName);
        }
    }

    /**
     * Convert special characters to encoded value.
     * 
     * @param role
     * @return encorded value
     */
    public static String sanitizeUserRole(String role) {
        if (role.contains("&")) {
            return role.replaceAll("&", "%26");
        } else {
            return role;
        }
    }

    /**
     * Util method to call SP rest api to invoke queries. 
     * 
     * @param appName SP app name that the query should run against
     * @param query query 
     * @return jsonObj JSONObject of the response
     * @throws APIManagementException
     */
    public static JSONObject executeQueryOnStreamProcessor(String appName, String query) throws APIManagementException {
        String spEndpoint = APIManagerAnalyticsConfiguration.getInstance().getDasServerUrl() + "/stores/query";
        String spUserName = APIManagerAnalyticsConfiguration.getInstance().getDasServerUser();
        String spPassword = APIManagerAnalyticsConfiguration.getInstance().getDasServerPassword();
        byte[] encodedAuth = Base64
                .encodeBase64((spUserName + ":" + spPassword).getBytes(Charset.forName("ISO-8859-1")));
        String authHeader = "Basic " + new String(encodedAuth);
        URL spURL;
        try {
            spURL = new URL(spEndpoint);

            HttpClient httpClient = APIUtil.getHttpClient(spURL.getPort(), spURL.getProtocol());
            HttpPost httpPost = new HttpPost(spEndpoint);

            httpPost.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
            JSONObject obj = new JSONObject();
            obj.put("appName", appName);
            obj.put("query", query);
            
            if (log.isDebugEnabled()) {
                log.debug("Request from SP: " + obj.toJSONString());
            }

            StringEntity requestEntity = new StringEntity(obj.toJSONString(), ContentType.APPLICATION_JSON);

            httpPost.setEntity(requestEntity);

            HttpResponse response;
            try {
                response = httpClient.execute(httpPost);
                HttpEntity entity = response.getEntity();
                if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                    String error = "Error while invoking SP rest api :  " + response.getStatusLine().getStatusCode()
                            + " " + response.getStatusLine().getReasonPhrase();
                    log.error(error);
                    throw new APIManagementException(error);
                }
                String responseStr = EntityUtils.toString(entity);
                if (log.isDebugEnabled()) {
                    log.debug("Response from SP: " + responseStr);
                }
                JSONParser parser = new JSONParser();
                return (JSONObject) parser.parse(responseStr);

            } catch (ClientProtocolException e) {
                handleException("Error while connecting to the server ", e);
            } catch (IOException e) {
                handleException("Error while connecting to the server ", e);
            } catch (ParseException e) {
                handleException("Error while parsing the response ", e);
            } finally {
                httpPost.reset();
            }

        } catch (MalformedURLException e) {
            handleException("Error while parsing the stream processor url", e);
        }

        return null;

    }

    public static boolean isDueToAuthorizationFailure(Throwable e) {
        Throwable rootCause = getPossibleErrorCause(e);
        return rootCause instanceof AuthorizationFailedException
                || rootCause instanceof APIMgtAuthorizationFailedException;
    }
    
    /**
     * Attempts to find the actual cause of the throwable 'e'
     *
     * @param e throwable
     * @return the root cause of 'e' if the root cause exists, otherwise returns 'e' itself
     */
    private static Throwable getPossibleErrorCause (Throwable e) {
        Throwable rootCause = ExceptionUtils.getRootCause(e);
        rootCause = rootCause == null ? e : rootCause;
        return rootCause;
    }

    /**
     * Notify document artifacts if an api state change occured. This change is required to re-trigger the document
     * indexer so that the documnet indexes will be updated with the new associated api status.
     * @param apiArtifact
     * @param registry
     * @throws RegistryException
     * @throws APIManagementException
     */
    public static void notifyAPIStateChangeToAssociatedDocuments(GenericArtifact apiArtifact, Registry registry)
            throws RegistryException, APIManagementException {
        Association[] docAssociations = registry
                .getAssociations(apiArtifact.getPath(), APIConstants.DOCUMENTATION_ASSOCIATION);
        for (Association association : docAssociations) {
            String documentResourcePath = association.getDestinationPath();
            Resource docResource = registry.get(documentResourcePath);
            String oldStateChangeIndicatorStatus = docResource.getProperty(APIConstants.API_STATE_CHANGE_INDICATOR);
            String newStateChangeIndicatorStatus = "false";
            if (oldStateChangeIndicatorStatus != null) {
                newStateChangeIndicatorStatus = String.valueOf(!Boolean.parseBoolean(oldStateChangeIndicatorStatus));
            }
            docResource.setProperty(APIConstants.API_STATE_CHANGE_INDICATOR, "false");
            registry.put(documentResourcePath, docResource);
        }
    }

    /**
     * This method is used to extact group ids from Extractor.
     *
     * @param response  login response String.
     * @param groupingExtractorClass    extractor class.
     * @return  group ids
     * @throws APIManagementException Throws is an error occured when stractoing group Ids
     */
    public static String[] getGroupIdsFromExtractor(String response, String groupingExtractorClass)
            throws APIManagementException {
        if (groupingExtractorClass != null) {
            try {
                LoginPostExecutor groupingExtractor = (LoginPostExecutor) APIUtil.getClassForName
                        (groupingExtractorClass).newInstance();
                //switching 2.1.0 and 2.2.0
                if (APIUtil.isMultiGroupAppSharingEnabled()) {
                    NewPostLoginExecutor newGroupIdListExtractor = (NewPostLoginExecutor) groupingExtractor;
                    return newGroupIdListExtractor.getGroupingIdentifierList(response);
                } else {
                    String groupId = groupingExtractor.getGroupingIdentifiers(response);
                    return new String[]{groupId};
                }

            } catch (ClassNotFoundException e) {
                String msg = groupingExtractorClass + " is not found in runtime";
                log.error(msg, e);
                throw new APIManagementException(msg, e);
            } catch (ClassCastException e) {
                String msg = "Cannot cast " + groupingExtractorClass + " NewPostLoginExecutor";
                log.error(msg, e);
                throw new APIManagementException(msg, e);
            } catch (IllegalAccessException e) {
                String msg = "Error occurred while invocation of getGroupingIdentifier method";
                log.error(msg, e);
                throw new APIManagementException(msg, e);
            } catch (InstantiationException e) {
                String msg = "Error occurred while instantiating " + groupingExtractorClass + " class";
                log.error(msg, e);
                throw new APIManagementException(msg, e);
            }
        }
        return null;
    }

    /**
     * This method is used to set environments values to api object.
     *
     * @param environments environments values in json format
     * @return set of environments that need to Publish
     */
    public static Set<String> extractEnvironmentsForAPI(List<String> environments) {

        Set<String> environmentStringSet = null;
        if (environments == null) {
            environmentStringSet = new HashSet<String>(
                    ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService()
                            .getAPIManagerConfiguration().getApiGatewayEnvironments().keySet());
        } else {
            //handle not to publish to any of the gateways
            if (environments.size() == 1 && APIConstants.API_GATEWAY_NONE.equals(environments.get(0))) {
                environmentStringSet = new HashSet<String>();
            }
            //handle to set published gateways into api object
            else if (environments.size() > 0) {
                environmentStringSet = new HashSet<String>(environments);
                environmentStringSet.remove(APIConstants.API_GATEWAY_NONE);
            }
            //handle to publish to any of the gateways when api creating stage
            else if (environments.size() == 0) {
                environmentStringSet = new HashSet<String>(
                        ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService()
                                .getAPIManagerConfiguration().getApiGatewayEnvironments().keySet());
            }
        }
        return environmentStringSet;
    }

    public static List<String> getGrantTypes() throws APIManagementException {
        IdentityConfigParser configParser;
        List<String> grantTypes = new ArrayList<>();
        configParser = IdentityConfigParser.getInstance();
        OMElement oauthElem = configParser.getConfigElement(CONFIG_ELEM_OAUTH);
        Iterator supportedGrantTypes = oauthElem.getFirstChildWithName(getQNameWithIdentityNS(
                "SupportedGrantTypes")).getChildElements();
        while (supportedGrantTypes.hasNext()) {
            grantTypes.add(StringUtils.substringBetween(supportedGrantTypes.next().toString(),
                    GRANT_TYPE_NAME, GRANT_TYPE_NAME));
        }
        return grantTypes;
    }

    public static Map<String, Environment> getEnvironments(){
        return ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService()
                        .getAPIManagerConfiguration().getApiGatewayEnvironments();
    }
    private static QName getQNameWithIdentityNS(String localPart) {
        return new QName(IdentityCoreConstants.IDENTITY_DEFAULT_NAMESPACE, localPart);
    }

    /**
     * Return the admin username read from the user-mgt.xml
     * @return
     * @throws APIMgtInternalException
     */
    public static String getAdminUsername () throws APIMgtInternalException {
        String adminName = "admin";
        try {
            String tenantDomain = CarbonContext.getThreadLocalCarbonContext().getTenantDomain();
            int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().
                    getTenantId(tenantDomain);

            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain);
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(tenantId);

            adminName = ServiceReferenceHolder.getInstance().getRealmService().getTenantUserRealm(tenantId)
                    .getRealmConfiguration().getAdminUserName();

        } catch (UserStoreException e) {
            handleInternalException("Error in getting admin username from user-mgt.xml", e);
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
        return adminName;
    }

    /**
     * Return the admin password read from the user-mgt.xml
     * @return
     * @throws APIMgtInternalException
     */
    public static String getAdminPassword () throws APIMgtInternalException {
        String adminPassword = "admin";
        try {
            String tenantDomain = CarbonContext.getThreadLocalCarbonContext().getTenantDomain();
            int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().
                    getTenantId(tenantDomain);

            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain);
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(tenantId);

            adminPassword = ServiceReferenceHolder.getInstance().getRealmService().getTenantUserRealm(tenantId)
                    .getRealmConfiguration().getAdminUserName();

        } catch (UserStoreException e) {
            handleInternalException("Error in getting admin password from user-mgt.xml", e);
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
        return adminPassword;
    }

    /**
     * This method returns the base64 encoded for the given username and password
     * @return base64 encoded username and password
     */
    public static String getBase64EncodedAdminCredentials() throws APIMgtInternalException {
        String credentials = getAdminUsername() + ":" + getAdminPassword();
        byte[] encodedCredentials = Base64.encodeBase64(
                credentials.getBytes(Charset.forName( "UTF-8")));
        return new String(encodedCredentials, Charset.forName("UTF-8"));
    }
}
