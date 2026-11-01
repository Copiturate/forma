package studio.forma.web;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import studio.forma.catalog.ServiceCode;
import studio.forma.inquiry.*;

public final class ApiModels {
  private ApiModels() {}

  public record EstimateRequest(@NotNull ServiceCode service, @Min(20) @Max(2000) int area) {}

  public record CreateInquiry(
      @NotNull UUID requestId,
      @NotBlank
          @Size(min = 2, max = 80)
          @Pattern(regexp = "(?s).*\\S.*\\S.*", message = "Укажите имя минимум из двух символов")
          String name,
      @NotBlank @Email @Size(max = 254) String email,
      @NotNull ServiceCode service,
      @Min(20) @Max(2000) int area,
      @NotNull @Size(max = 2000) String message,
      @NotNull @AssertTrue Boolean consent) {}

  public record ChangeStatus(@NotNull InquiryStatus status, @NotNull @Min(0) Long version) {}

  public record Receipt(
      UUID id, Instant createdAt, long estimateMin, long estimateMax, String currency) {
    public static Receipt from(Inquiry inquiry) {
      return new Receipt(
          inquiry.getId(),
          inquiry.getCreatedAt(),
          inquiry.getEstimateMin(),
          inquiry.getEstimateMax(),
          "RUB");
    }
  }

  public record InquiryView(
      UUID id,
      String name,
      String email,
      ServiceCode service,
      int area,
      String message,
      long estimateMin,
      long estimateMax,
      String pricingVersion,
      InquiryStatus status,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    public static InquiryView from(Inquiry inquiry) {
      return new InquiryView(
          inquiry.getId(),
          inquiry.getName(),
          inquiry.getEmail(),
          inquiry.getService(),
          inquiry.getArea(),
          inquiry.getMessage(),
          inquiry.getEstimateMin(),
          inquiry.getEstimateMax(),
          inquiry.getPricingVersion(),
          inquiry.getStatus(),
          inquiry.getCreatedAt(),
          inquiry.getUpdatedAt(),
          inquiry.getVersion());
    }
  }

  public record InquiryPage(
      List<InquiryView> items, int page, int totalPages, long totalElements) {}
}
