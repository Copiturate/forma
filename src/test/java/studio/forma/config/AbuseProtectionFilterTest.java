package studio.forma.config;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class AbuseProtectionFilterTest {
  @Test
  void limitsPerAddressAndResetsAfterWindow() {
    var millis = new AtomicLong(0);
    Clock clock =
        new Clock() {
          public ZoneId getZone() {
            return ZoneOffset.UTC;
          }

          public Clock withZone(ZoneId zone) {
            return this;
          }

          public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
          }
        };
    var limiter = new AbuseProtectionFilter(clock);
    assertThat(limiter.allow("one", 2)).isTrue();
    assertThat(limiter.allow("one", 2)).isTrue();
    assertThat(limiter.allow("one", 2)).isFalse();
    assertThat(limiter.allow("two", 2)).isTrue();
    millis.set(60_000);
    assertThat(limiter.allow("one", 2)).isTrue();
  }
}
