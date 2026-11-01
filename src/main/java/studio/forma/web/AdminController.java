package studio.forma.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import studio.forma.inquiry.*;
import studio.forma.web.ApiModels.*;

@RestController
@RequestMapping("/api/admin/inquiries")
@Validated
public class AdminController {
  private final InquiryService inquiries;

  public AdminController(InquiryService inquiries) {
    this.inquiries = inquiries;
  }

  @GetMapping
  public InquiryPage list(
      @RequestParam(required = false) InquiryStatus status,
      @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page) {
    return inquiries.list(status, page);
  }

  @PatchMapping(value = "/{id}/status", consumes = "application/json")
  public InquiryView update(@PathVariable UUID id, @Valid @RequestBody ChangeStatus request) {
    return inquiries.update(id, request);
  }
}
