package org.camunda.optimize.service.security;

import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;


public class TokenVerifier {

  private Logger logger = LoggerFactory.getLogger(getClass());

  private Integer tokenLifeTimeInMin;
  private OffsetDateTime expiryDate;
  private JWTVerifier verifier;

  TokenVerifier(Integer tokenLifeTimeInMin, JWTVerifier verifier) {
    this.tokenLifeTimeInMin = tokenLifeTimeInMin;
    this.verifier = verifier;
    updateExpiryDate();
  }

  private void updateExpiryDate() {
    this.expiryDate = calculateExpiryDate();
  }

  private OffsetDateTime calculateExpiryDate() {
    return LocalDateUtil.getCurrentDateTime().plus(tokenLifeTimeInMin, ChronoUnit.MINUTES);
  }

  public boolean isTokenValid(String tokenKey) {
    try {
      verifier.verify(tokenKey);
    } catch (JWTVerificationException exception) {
      //Invalid signature/claims
      logger.debug("Error while validating authentication token [{}]. " +
        "Invalid signature or claims! Presumably, the user is already logged in somewhere else.",
        tokenKey
      );
      return false;
    }
    boolean isValid = !hasExpired(tokenKey);
    if (isValid) {
      updateExpiryDate();
    }
    return isValid;
  }

  private boolean hasExpired(String tokenKey) {
    if (expiryDate == null || LocalDateUtil.getCurrentDateTime().isAfter(expiryDate)) {
      logger.debug("Error while validating authentication token [{}]" +
        "Date has expired!", tokenKey);
      return true;
    }
    return false;
  }
}
