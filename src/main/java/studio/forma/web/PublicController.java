package studio.forma.web;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import studio.forma.catalog.CatalogService;
import studio.forma.inquiry.InquiryService;
import studio.forma.web.ApiModels.*;

@RestController
@RequestMapping("/api")
public class PublicController {
  private final CatalogService catalog;
  private final InquiryService inquiries;

  public PublicController(CatalogService catalog, InquiryService inquiries) {
    this.catalog = catalog;
    this.inquiries = inquiries;
  }

  @GetMapping("/csrf")
  public CsrfResponse csrf(CsrfToken token) {
    return new CsrfResponse(token.getHeaderName(), token.getToken());
  }

  @GetMapping("/services")
  public List<CatalogService.Offering> services() {
    return catalog.offerings();
  }

  @PostMapping(value = "/estimates", consumes = MediaType.APPLICATION_JSON_VALUE)
  public CatalogService.Estimate estimate(@Valid @RequestBody EstimateRequest request) {
    return catalog.estimate(request.service(), request.area());
  }

  @PostMapping(value = "/inquiries", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public Receipt create(@Valid @RequestBody CreateInquiry request) {
    return inquiries.create(request);
  }

  public record CsrfResponse(String headerName, String token) {}
}
