/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.webapp.security.oauth2;

import static io.camunda.operate.OperateProfileService.IDENTITY_AUTH_PROFILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.auth0.jwt.exceptions.InvalidClaimException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.Authentication;
import io.camunda.identity.sdk.impl.rest.exception.RestException;
import io.camunda.identity.sdk.tenants.Tenants;
import io.camunda.identity.sdk.tenants.dto.Tenant;
import io.camunda.operate.property.MultiTenancyProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.SpringContextHolder;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.security.tenant.OperateTenant;
import io.camunda.operate.webapp.security.tenant.TenantAwareAuthentication;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      IdentityJwt2AuthenticationTokenConverter.class,
      OperateProperties.class
    },
    properties = {OperateProperties.PREFIX + ".identity.issuerUrl = http://some.issuer.url"})
@ActiveProfiles({IDENTITY_AUTH_PROFILE, "test"})
public class IdentityJwt2AuthenticationTokenConverterIT {

  @Autowired @SpyBean private IdentityJwt2AuthenticationTokenConverter tokenConverter;

  @MockBean private Identity identity;

  @MockBean private Tenants tenants;

  @Mock private Authentication authentication;

  @SpyBean private OperateProperties operateProperties;

  @Autowired private ApplicationContext applicationContext;

  @Before
  public void setup() {
    new SpringContextHolder().setApplicationContext(applicationContext);
  }

  @Test(expected = InsufficientAuthenticationException.class)
  public void shouldFailIfClaimIsInvalid() {
    when(identity.authentication())
        .thenThrow(
            new InvalidClaimException(
                "The Claim 'aud' value doesn't contain the required audience."));
    final Jwt token = createJwtTokenWith();
    tokenConverter.convert(token);
  }

  @Test(expected = InsufficientAuthenticationException.class)
  public void shouldFailIfTokenVerificationFails() {
    when(identity.authentication())
        .thenThrow(new RuntimeException("Any exception during token verification"));
    final Jwt token = createJwtTokenWith();
    tokenConverter.convert(token);
  }

  @Test
  public void shouldConvert() {
    when(identity.authentication()).thenReturn(authentication);
    when(authentication.verifyToken(any())).thenReturn(null);
    final Jwt token = createJwtTokenWith();
    final AbstractAuthenticationToken authenticationToken = tokenConverter.convert(token);
    assertThat(authenticationToken).isInstanceOf(JwtAuthenticationToken.class);
    assertThat(authenticationToken.isAuthenticated()).isTrue();
  }

  @Test
  public void shouldReturnTenantsWhenMultiTenancyIsEnabled() throws IOException {
    when(identity.authentication()).thenReturn(authentication);
    when(authentication.verifyToken(any())).thenReturn(null);
    doReturn(tenants).when(identity).tenants();

    final var multiTenancyProperties = mock(MultiTenancyProperties.class);
    doReturn(multiTenancyProperties).when(operateProperties).getMultiTenancy();
    doReturn(true).when(multiTenancyProperties).isEnabled();

    final List<Tenant> tenants =
        new ObjectMapper()
            .readValue(
                this.getClass().getResource("/security/identity/tenants.json"),
                new TypeReference<>() {});
    doReturn(tenants).when(this.tenants).forToken(any());

    final Jwt token = createJwtTokenWith();
    final AbstractAuthenticationToken authenticationToken = tokenConverter.convert(token);
    assertThat(authenticationToken).isInstanceOf(TenantAwareAuthentication.class);
    final var tenantAwareAuth = (TenantAwareAuthentication) authenticationToken;

    //    //then tenants are properly converted and returned by tenant aware authentication
    final List<OperateTenant> returnedTenants = tenantAwareAuth.getTenants();

    assertThat(returnedTenants).hasSize(3);

    assertThat(returnedTenants)
        .filteredOn(t -> t.getTenantId().equals("<default>") && t.getName().equals("Default"))
        .hasSize(1);

    assertThat(returnedTenants)
        .filteredOn(t -> t.getTenantId().equals("tenant-a") && t.getName().equals("Tenant A"))
        .hasSize(1);

    assertThat(returnedTenants)
        .filteredOn(t -> t.getTenantId().equals("tenant-b") && t.getName().equals("Tenant B"))
        .hasSize(1);
  }

  @Test
  public void shouldReturnNullAsTenantsWhenMultiTenancyIsDisabled() {
    when(identity.authentication()).thenReturn(authentication);
    when(authentication.verifyToken(any())).thenReturn(null);
    doReturn(tenants).when(identity).tenants();

    final var multiTenancyProperties = mock(MultiTenancyProperties.class);
    doReturn(multiTenancyProperties).when(operateProperties).getMultiTenancy();
    doReturn(false).when(multiTenancyProperties).isEnabled();

    final Jwt token = createJwtTokenWith();
    final AbstractAuthenticationToken authenticationToken = tokenConverter.convert(token);
    assertThat(authenticationToken).isInstanceOf(TenantAwareAuthentication.class);
    final var tenantAwareAuth = (TenantAwareAuthentication) authenticationToken;

    // then no Identity is called
    assertThat(tenantAwareAuth.getTenants()).isNull();
    verifyNoInteractions(tenants);
  }

  @Test(expected = InsufficientAuthenticationException.class)
  public void shouldFailWhenGettingTenants() {
    when(identity.authentication()).thenReturn(authentication);
    when(authentication.verifyToken(any())).thenReturn(null);
    doReturn(tenants).when(identity).tenants();

    final var multiTenancyProperties = mock(MultiTenancyProperties.class);
    doReturn(multiTenancyProperties).when(operateProperties).getMultiTenancy();
    doReturn(true).when(multiTenancyProperties).isEnabled();

    final Jwt token = createJwtTokenWith();
    final AbstractAuthenticationToken authenticationToken = tokenConverter.convert(token);
    assertThat(authenticationToken).isInstanceOf(TenantAwareAuthentication.class);
    final var tenantAwareAuth = (TenantAwareAuthentication) authenticationToken;

    doThrow(new RestException("smth went wrong")).when(tenants).forToken(any());
    tenantAwareAuth.getTenants();
  }

  protected Jwt createJwtTokenWith() {
    return Jwt.withTokenValue("token")
        .audience(List.of("audience"))
        .header("alg", "HS256")
        .claim("foo", "bar")
        .build();
  }
}
