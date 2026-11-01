package studio.forma.catalog;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
  public static final String PRICING_VERSION = "2026-01";
  private final List<Offering> offerings =
      List.of(
          new Offering(
              ServiceCode.ARCHITECTURE, "Архитектурная концепция", 2500, 3500, "4–8 недель"),
          new Offering(ServiceCode.INTERIOR, "Дизайн интерьера", 4000, 6000, "6–12 недель"),
          new Offering(
              ServiceCode.SUPERVISION,
              "Авторское сопровождение",
              1000,
              1500,
              "на период реализации"));

  public List<Offering> offerings() {
    return offerings;
  }

  public Estimate estimate(ServiceCode service, int area) {
    if (service == null || area < 20 || area > 2000) {
      throw new IllegalArgumentException("Выберите услугу и площадь от 20 до 2000 м².");
    }
    Offering offering =
        offerings.stream().filter(item -> item.code() == service).findFirst().orElseThrow();
    return new Estimate(
        service,
        area,
        (long) area * offering.minRate(),
        (long) area * offering.maxRate(),
        "RUB",
        PRICING_VERSION);
  }

  public record Offering(
      ServiceCode code, String title, int minRate, int maxRate, String duration) {}

  public record Estimate(
      ServiceCode service, int area, long min, long max, String currency, String pricingVersion) {}
}
