/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.sso;

import static io.camunda.operate.property.Auth0Properties.DEFAULT_ROLES_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static io.camunda.operate.util.CollectionUtil.asMap;
import static io.camunda.operate.webapp.security.OperateURIs.SSO_CALLBACK_URI;
import static io.camunda.operate.webapp.security.OperateURIs.LOGIN_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.LOGOUT_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.NO_PERMISSION;
import static io.camunda.operate.webapp.security.OperateURIs.ROOT;
import static io.camunda.operate.webapp.security.OperateProfileService.SSO_AUTH_PROFILE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.auth0.AuthenticationController;
import com.auth0.AuthorizeUrl;
import com.auth0.IdentityVerificationException;
import com.auth0.Tokens;
import io.camunda.operate.webapp.security.AuthenticationTestable;
import io.camunda.operate.webapp.security.Permission;
import io.camunda.operate.webapp.security.oauth2.CCSaaSJwtAuthenticationTokenValidator;
import io.camunda.operate.webapp.security.oauth2.Jwt2AuthenticationTokenConverter;
import io.camunda.operate.webapp.security.oauth2.OAuth2WebConfigurer;
import io.camunda.operate.webapp.security.OperateProfileService;
import io.camunda.operate.webapp.security.es.RolePermissionService;
import io.camunda.operate.webapp.security.sso.model.ClusterInfo;
import io.camunda.operate.webapp.security.sso.model.ClusterInfo.SalesPlan;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import io.camunda.operate.management.ElsIndicesCheck;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.rest.AuthenticationRestService;
import io.camunda.operate.webapp.security.OperateURIs;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(
    classes = {
        TestApplicationWithNoBeans.class,
        OAuth2WebConfigurer.class,
        Jwt2AuthenticationTokenConverter.class,
        CCSaaSJwtAuthenticationTokenValidator.class,
        SSOWebSecurityConfig.class,
        Auth0Service.class,
        SSOController.class,
        TokenAuthentication.class,
        SSOUserService.class,
        AuthenticationRestService.class,
        RolePermissionService.class,
        OperateURIs.class,
        OperateProperties.class,
        OperateProfileService.class
    },
    properties = {
        "server.servlet.context-path=" + AuthenticationIT.CONTEXT_PATH,
        "camunda.operate.auth0.clientId=1",
        "camunda.operate.auth0.clientSecret=2",
        "camunda.operate.cloud.organizationId=3",
        "camunda.operate.auth0.domain=domain",
        "camunda.operate.auth0.backendDomain=backendDomain",
        "camunda.operate.auth0.claimName=claimName",
        "camunda.operate.cloud.permissionaudience=audience",
        "camunda.operate.cloud.permissionurl=https://permissionurl"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles(SSO_AUTH_PROFILE)
public class AuthenticationIT implements AuthenticationTestable {

  public static final SalesPlan OPERATE_TEST_SALESPLAN = new SalesPlan("test");
  public static final List<String> OPERATE_TEST_ROLES = List.of("owner", "admin");

  public final static String CONTEXT_PATH = "/operate-test";
  @ClassRule
  public static final SpringClassRule SPRING_CLASS_RULE = new SpringClassRule();
  @Rule
  public final SpringMethodRule springMethodRule = new SpringMethodRule();

  @LocalServerPort
  private int randomServerPort;

  @Autowired
  private TestRestTemplate testRestTemplate;

  @Autowired
  private OperateProperties operateProperties;

  @MockBean
  private AuthenticationController authenticationController;

  @SpyBean
  private Auth0Service auth0Service;

  @Autowired
  private BeanFactory beanFactory;

  @MockBean
  @Qualifier("auth0_restTemplate")
  private RestTemplate restTemplate;

  @MockBean
  private ElsIndicesCheck probes;
  private final BiFunction<String, String, Tokens> orgExtractor = AuthenticationIT::tokensWithOrgAsMapFrom;

  @Before
  public void setUp() {
    // mock building authorizeUrl
    AuthorizeUrl mockedAuthorizedUrl = mock(AuthorizeUrl.class);
    given(authenticationController.buildAuthorizeUrl(isNotNull(), isNotNull(), isNotNull()))
        .willReturn(mockedAuthorizedUrl);
    given(mockedAuthorizedUrl.withAudience(isNotNull())).willReturn(mockedAuthorizedUrl);
    given(mockedAuthorizedUrl.withScope(isNotNull())).willReturn(mockedAuthorizedUrl);
    given(mockedAuthorizedUrl.build()).willReturn(
        "https://domain/authorize?redirect_uri=http://localhost:58117/sso-callback&client_id=1&audience=https://backendDomain/userinfo");

  }

  @Test
  public void testHandleInvalidRequestException() throws Exception {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response)).contains(
        operateProperties.getAuth0().getDomain(),
        SSO_CALLBACK_URI,
        operateProperties.getAuth0().getClientId(),
        operateProperties.getAuth0().getBackendDomain()
    );
    // Step 3 Call back uri with valid userinfos
    // mock building tokens
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(orgExtractor.apply(operateProperties.getAuth0().getClaimName(),
            operateProperties.getCloud().getOrganizationId()));
    doThrow(new Auth0ServiceException(new Exception("Invalid response code from the auth0-sandbox: HTTP 502.")))
        .when(auth0Service).authenticate(any(), any());
    response = get(SSO_CALLBACK_URI, cookies);

    assertThat(redirectLocationIn(response)).contains(
        operateProperties.getAuth0().getDomain(),
        "logout",
        operateProperties.getAuth0().getClientId(),
        urlFor(ROOT)
    );
  }

  @Test
  public void testLoginSuccess() throws Exception {
    mockPermissionAllowed();
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response)).contains(
        operateProperties.getAuth0().getDomain(),
        SSO_CALLBACK_URI,
        operateProperties.getAuth0().getClientId(),
        operateProperties.getAuth0().getBackendDomain()
    );
    // Step 3 Call back uri with valid userinfos
    // mock building tokens
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(orgExtractor.apply(operateProperties.getAuth0().getClaimName(),
            operateProperties.getCloud().getOrganizationId()));

    response = get(SSO_CALLBACK_URI, cookies);
    assertThatRequestIsRedirectedTo(response, urlFor(ROOT));

    response = get(ROOT, cookies);
    // Check if access to url possible
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  public void testLoginFailedWithNoReadPermissions() throws Exception {
    mockNoReadPermission();
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            operateProperties.getAuth0().getDomain(),
            SSO_CALLBACK_URI,
            operateProperties.getAuth0().getClientId(),
            operateProperties.getAuth0().getBackendDomain());
    // Step 3 Call back uri with valid userdata
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(
            orgExtractor.apply(
                operateProperties.getAuth0().getClaimName(),
                operateProperties.getCloud().getOrganizationId()));

    response = get(SSO_CALLBACK_URI, cookies);
    assertThat(redirectLocationIn(response)).contains(NO_PERMISSION);

    response = get(ROOT, cookies);
    // Check that access to url is not possible
    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));
  }

  @Test
  public void testLoginSucceedWithNoWritePermissions() throws Exception {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    mockNoWritePermission();
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            operateProperties.getAuth0().getDomain(),
            SSO_CALLBACK_URI,
            operateProperties.getAuth0().getClientId(),
            operateProperties.getAuth0().getBackendDomain());
    // Step 3 Call back uri with valid userdata
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(
            orgExtractor.apply(
                operateProperties.getAuth0().getClaimName(),
                operateProperties.getCloud().getOrganizationId()));

    get(SSO_CALLBACK_URI, cookies);

    final TokenAuthentication authentication = beanFactory.getBean(TokenAuthentication.class);
    assertThat(authentication.getPermissions().contains(Permission.WRITE)).isEqualTo(false);

    // successfully redirect to root even without write permission
    response = get(ROOT, cookies);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  public void testLoginFailedWithNoPermissions() throws Exception {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    mockNoReadPermission();
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response)).contains(
        operateProperties.getAuth0().getDomain(),
        SSO_CALLBACK_URI,
        operateProperties.getAuth0().getClientId(),
        operateProperties.getAuth0().getBackendDomain()
    );
    // Step 3 Call back uri with invalid userdata
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(
            orgExtractor.apply(operateProperties.getAuth0().getClaimName(), "wrong-organization"));

    response = get(SSO_CALLBACK_URI, cookies);
    assertThat(redirectLocationIn(response)).contains(NO_PERMISSION);

    response = get(ROOT, cookies);
    // Check that access to url is not possible
    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));
  }

  @Test
  public void testLoginFailedWithOtherException() throws Exception {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    mockPermissionAllowed();
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response)).contains(
        operateProperties.getAuth0().getDomain(),
        SSO_CALLBACK_URI,
        operateProperties.getAuth0().getClientId(),
        operateProperties.getAuth0().getBackendDomain()
    );
    // Step 3 Call back uri, but there is an IdentityVerificationException.
    doThrow(IdentityVerificationException.class).when(authenticationController)
        .handle(any(), any());

    response = get(SSO_CALLBACK_URI, cookies);
    assertThatRequestIsRedirectedTo(response, urlFor(NO_PERMISSION));
  }

  @Test
  public void testLogout() throws Throwable {
    // Step 1 Login
    mockPermissionAllowed();
    ResponseEntity<String> response = get(ROOT);
    HttpEntity<?> cookies = httpEntityWithCookie(response);
    response = get(LOGIN_RESOURCE, cookies);
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(orgExtractor.apply(operateProperties.getAuth0().getClaimName(),
            operateProperties.getCloud().getOrganizationId()));
    response = get(SSO_CALLBACK_URI, cookies);
    response = get(ROOT, cookies);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    // Step 2 logout
    response = get(LOGOUT_RESOURCE, cookies);
    assertThat(redirectLocationIn(response)).contains(
        operateProperties.getAuth0().getDomain(),
        "logout",
        operateProperties.getAuth0().getClientId(),
        urlFor(ROOT)
    );
    // Redirected to Login
    response = get(ROOT);
    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));
  }

  @Test
  public void testLoginToAPIResource() throws Exception {
    // Step 1 try to access user info
    String userInfoUrl = AuthenticationRestService.AUTHENTICATION_URL + "/user";
    ResponseEntity<String> response = get(userInfoUrl);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    HttpEntity<?> httpEntity = new HttpEntity<>(new HashMap<>(), response.getHeaders());
    // Step 2 Get Login provider url
    mockPermissionAllowed();
    response = get(LOGIN_RESOURCE, httpEntity);

    assertThat(redirectLocationIn(response)).contains(
        operateProperties.getAuth0().getDomain(),
        SSO_CALLBACK_URI,
        operateProperties.getAuth0().getClientId(),
        operateProperties.getAuth0().getBackendDomain()
    );
    // Step 3 Call back uri
    given(authenticationController.handle(isNotNull(), isNotNull())).willReturn(orgExtractor
        .apply(operateProperties.getAuth0().getClaimName(),
            operateProperties.getCloud().getOrganizationId()));

    response = get(SSO_CALLBACK_URI, httpEntity);
    httpEntity = httpEntityWithCookie(response);
    response = get(userInfoUrl, httpEntity);
    assertThat(response.getBody()).contains("\"displayName\":\"operate-testuser\"");
    assertThat(response.getBody()).contains("\"username\":\"operate-testuser\"");
    assertThat(response.getBody()).contains("\"salesPlanType\":\"test\"");
    assertThat(response.getBody()).contains("\"roles\":[\"owner\",\"admin\"]");
  }

  private HttpEntity<?> httpEntityWithCookie(ResponseEntity<String> response) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Cookie", response.getHeaders().get("Set-Cookie").get(0));
    return new HttpEntity<>(new HashMap<>(), headers);
  }

  @Test
  public void testAccessNoPermission() {
    ResponseEntity<String> response = get(NO_PERMISSION);
    assertThat(response.getBody()).contains("No permission for Operate");
  }

  protected void assertThatRequestIsRedirectedTo(ResponseEntity<?> response, String url) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(redirectLocationIn(response)).isEqualTo(url);
  }

  private ResponseEntity<String> get(String path, HttpEntity<?> requestEntity) {
    return testRestTemplate.exchange(path, HttpMethod.GET, requestEntity, String.class);
  }

  private String urlFor(String path) {
    return String.format("http://localhost:%d%s%s",randomServerPort, CONTEXT_PATH, path);
  }

  private static Tokens tokensWithOrgAsMapFrom(String claim, String organization) {
    String emptyJSONEncoded = toEncodedToken(Collections.EMPTY_MAP);
    long expiresInSeconds = System.currentTimeMillis() / 1000 + 10000; // now + 10 seconds
    Map<String, Object> orgMap = Map.of("id", organization);
    String accountData = toEncodedToken(asMap(
        claim, List.of(orgMap),
        "exp", expiresInSeconds,
        "name", "operate-testuser",
        DEFAULT_ROLES_KEY, OPERATE_TEST_ROLES
    ));
    return new Tokens("accessToken", emptyJSONEncoded + "." + accountData + "." + emptyJSONEncoded,
        "refreshToken", "type", 5L);
  }

  private static String toEncodedToken(Map map) {
    return toBase64(toJSON(map));
  }

  private static String toBase64(String input) {
    return new String(Base64.getEncoder().encode(input.getBytes()));
  }

  private static String toJSON(Map map) {
    return new JSONObject(map).toString();
  }

  @Override
  public TestRestTemplate getTestRestTemplate() {
    return testRestTemplate;
  }

  private void mockPermissionAllowed() {
    final ClusterInfo.OrgPermissions operate =
        new ClusterInfo.OrgPermissions(null, new ClusterInfo.Permission(true, true, true, true));

    final ClusterInfo.OrgPermissions cluster = new ClusterInfo.OrgPermissions(operate, null);
    final ClusterInfo clusterInfo = new ClusterInfo("Org Name", cluster, OPERATE_TEST_SALESPLAN);
    final ResponseEntity<ClusterInfo> clusterInfoResponseEntity =
        new ResponseEntity<>(clusterInfo, HttpStatus.OK);

    when(restTemplate.exchange(
        eq("https://permissionurl/3"), eq(HttpMethod.GET), (HttpEntity) any(), (Class) any()))
        .thenReturn(clusterInfoResponseEntity);
  }

  private void mockNoReadPermission() {
    final ClusterInfo.OrgPermissions operate =
        new ClusterInfo.OrgPermissions(null, new ClusterInfo.Permission(false, true, true, true));

    final ClusterInfo.OrgPermissions cluster = new ClusterInfo.OrgPermissions(operate, null);
    final ClusterInfo clusterInfo = new ClusterInfo("Org Name", cluster, OPERATE_TEST_SALESPLAN);
    final ResponseEntity<ClusterInfo> clusterInfoResponseEntity =
        new ResponseEntity<>(clusterInfo, HttpStatus.OK);

    when(restTemplate.exchange(
        eq("https://permissionurl/3"),
        eq(HttpMethod.GET),
        (HttpEntity) any(),
        (Class) any()))
        .thenReturn(clusterInfoResponseEntity);
  }

  private void mockNoWritePermission() {
    final ClusterInfo.OrgPermissions operate =
        new ClusterInfo.OrgPermissions(null, new ClusterInfo.Permission(true, false, false, false));

    final ClusterInfo.OrgPermissions cluster = new ClusterInfo.OrgPermissions(operate, null);
    final ClusterInfo clusterInfo = new ClusterInfo("Org Name", cluster, OPERATE_TEST_SALESPLAN);
    final ResponseEntity<ClusterInfo> clusterInfoResponseEntity =
        new ResponseEntity<>(clusterInfo, HttpStatus.OK);

    when(restTemplate.exchange(
        eq("https://permissionurl/3"),
        eq(HttpMethod.GET),
        (HttpEntity) any(),
        (Class) any()))
        .thenReturn(clusterInfoResponseEntity);
  }
}
