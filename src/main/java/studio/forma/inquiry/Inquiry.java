package studio.forma.inquiry;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import studio.forma.catalog.CatalogService.Estimate;
import studio.forma.catalog.ServiceCode;

@Entity
@Table(name = "inquiries")
public class Inquiry {
  @Id private UUID id;

  @Column(name = "client_name", nullable = false, length = 80)
  private String name;

  @Column(nullable = false, length = 254)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(name = "service_code", nullable = false, length = 24)
  private ServiceCode service;

  @Column(nullable = false)
  private int area;

  @Column(nullable = false, length = 2000)
  private String message;

  @Column(nullable = false)
  private long estimateMin;

  @Column(nullable = false)
  private long estimateMax;

  @Column(nullable = false, length = 24)
  private String pricingVersion;

  @Column(nullable = false)
  private Instant consentAt;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private InquiryStatus status;

  @Version private Long version;

  protected Inquiry() {}

  public Inquiry(
      UUID id, String name, String email, String message, Estimate estimate, Instant now) {
    this.id = id;
    this.name = name;
    this.email = email;
    this.message = message;
    this.service = estimate.service();
    this.area = estimate.area();
    this.estimateMin = estimate.min();
    this.estimateMax = estimate.max();
    this.pricingVersion = estimate.pricingVersion();
    this.consentAt = now;
    this.createdAt = now;
    this.updatedAt = now;
    this.status = InquiryStatus.NEW;
  }

  public void transitionTo(InquiryStatus next) {
    if (!status.canTransitionTo(next)) {
      throw new InquiryConflictException(
          "Этот переход статуса недоступен. Обновите список заявок.");
    }
    status = next;
    updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public ServiceCode getService() {
    return service;
  }

  public int getArea() {
    return area;
  }

  public String getMessage() {
    return message;
  }

  public long getEstimateMin() {
    return estimateMin;
  }

  public long getEstimateMax() {
    return estimateMax;
  }

  public String getPricingVersion() {
    return pricingVersion;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public InquiryStatus getStatus() {
    return status;
  }

  public long getVersion() {
    return version == null ? 0 : version;
  }
}
