package org.camunda.operate.util;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Random;


public abstract class DateUtil {

  private static final Random RANDOM = new Random();

  public static OffsetDateTime getRandomStartDate() {
    Instant now = Instant.now();
    now = now.minus((5 + RANDOM.nextInt(10)), ChronoUnit.DAYS);
    now = now.minus(RANDOM.nextInt(60*24), ChronoUnit.MINUTES);
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    return OffsetDateTime.now(clock);
  }

  public static OffsetDateTime getRandomEndDate() {
    return getRandomEndDate(false);
  }

  public static OffsetDateTime getRandomEndDate(boolean nullable) {
    if (nullable) {
      if (RANDOM.nextInt(10) % 3 == 1) {
        return null;
      }
    }
    Instant now = Instant.now();
    now = now.minus((1 + RANDOM.nextInt(4)), ChronoUnit.DAYS);
    now = now.minus(RANDOM.nextInt(60*24), ChronoUnit.MINUTES);
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    return OffsetDateTime.now(clock);
  }

}
