package org.camunda.optimize.service.alert;

import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class EmailNotificationService implements NotificationService {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private ConfigurationService configurationService;

  @Autowired
  public EmailNotificationService(final ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }

  @Override
  public void notifyRecipient(String text, String destination) {
    logger.debug("sending email [{}] to [{}]", text, destination);
    if (configurationService.isEmailEnabled()) {
      try {
        sendEmail(destination, text);
      } catch (EmailException e) {
        logger.error("Was not able to send email from [{}] to [{}]!",
            configurationService.getAlertEmailAddress(),
            destination,
            e);
      }
    } else {
      logger.warn("The email service is not enabled and thus no email could be send. " +
          "Please check the Optimize documentation on how to enable email notifications!");
    }
  }

  private void sendEmail(String to, String body) throws EmailException {

    Email email = new SimpleEmail();
    email.setHostName(configurationService.getAlertEmailHostname());
    email.setSmtpPort(configurationService.getAlertEmailPort());
    email.setAuthentication(
      configurationService.getAlertEmailUsername(),
      configurationService.getAlertEmailPassword()
    );
    email.setFrom(configurationService.getAlertEmailAddress());

    String securityProtocol = configurationService.getAlertEmailProtocol();
    if (securityProtocol.equals("STARTTLS")) {
      email.setStartTLSEnabled(true);
    } else if(securityProtocol.equals("SSL/TLS")) {
      email.setSSLOnConnect(true);
      email.setSslSmtpPort(configurationService.getAlertEmailPort().toString());
    }

    email.setCharset("utf-8");
    email.setSubject("[Camunda-Optimize] - Report status");
    email.setMsg(body);
    email.addTo(to);
    email.send();
  }
}
