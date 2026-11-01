package studio.forma.catalog;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CatalogServiceTest {
  private final CatalogService service = new CatalogService();

  @Test
  void calculatesInteriorInRubles() {
    var estimate = service.estimate(ServiceCode.INTERIOR, 80);
    assertThat(estimate.min()).isEqualTo(320000);
    assertThat(estimate.max()).isEqualTo(480000);
    assertThat(estimate.currency()).isEqualTo("RUB");
  }

  @Test
  void handlesBothAreaBoundariesForEveryService() {
    for (var offering : service.offerings()) {
      assertThat(service.estimate(offering.code(), 20).min()).isEqualTo(20L * offering.minRate());
      assertThat(service.estimate(offering.code(), 2000).max())
          .isEqualTo(2000L * offering.maxRate());
    }
  }

  @Test
  void rejectsInvalidAreaAndMissingService() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> service.estimate(ServiceCode.INTERIOR, 19));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> service.estimate(ServiceCode.INTERIOR, 2001));
    assertThatIllegalArgumentException().isThrownBy(() -> service.estimate(null, 80));
  }
}
