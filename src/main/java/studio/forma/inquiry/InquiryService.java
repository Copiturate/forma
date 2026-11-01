package studio.forma.inquiry;

import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import studio.forma.catalog.CatalogService;
import studio.forma.web.ApiModels.*;

@Service
public class InquiryService {
  private final InquiryRepository repository;
  private final CatalogService catalog;

  public InquiryService(InquiryRepository repository, CatalogService catalog) {
    this.repository = repository;
    this.catalog = catalog;
  }

  // Repository saveAndFlush owns the transaction: a unique-key race can be read after rollback.
  public Receipt create(CreateInquiry request) {
    var existing = repository.findById(request.requestId());
    if (existing.isPresent()) return receiptForRetry(existing.get(), request);
    var estimate = catalog.estimate(request.service(), request.area());
    var inquiry =
        new Inquiry(
            request.requestId(),
            request.name().strip(),
            request.email().strip(),
            request.message().strip(),
            estimate,
            Instant.now());
    try {
      return Receipt.from(repository.saveAndFlush(inquiry));
    } catch (DataIntegrityViolationException exception) {
      var concurrent = repository.findById(request.requestId());
      if (concurrent.isPresent()) return receiptForRetry(concurrent.get(), request);
      throw exception;
    }
  }

  private Receipt receiptForRetry(Inquiry inquiry, CreateInquiry request) {
    if (!inquiry.getName().equals(request.name().strip())
        || !inquiry.getEmail().equals(request.email().strip())
        || inquiry.getService() != request.service()
        || inquiry.getArea() != request.area()
        || !inquiry.getMessage().equals(request.message().strip())) {
      throw new InquiryConflictException("Идентификатор уже использован для другой заявки.");
    }
    return Receipt.from(inquiry);
  }

  @Transactional(readOnly = true)
  public InquiryPage list(InquiryStatus status, int page) {
    Pageable pageable =
        PageRequest.of(page, 12, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    Page<Inquiry> result =
        status == null ? repository.findAll(pageable) : repository.findByStatus(status, pageable);
    return new InquiryPage(
        result.map(InquiryView::from).getContent(),
        page,
        result.getTotalPages(),
        result.getTotalElements());
  }

  @Transactional
  public InquiryView update(UUID id, ChangeStatus request) {
    Inquiry inquiry =
        repository
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заявка не найдена."));
    if (inquiry.getVersion() != request.version()) {
      throw new InquiryConflictException("Заявка уже изменена. Обновите список.");
    }
    inquiry.transitionTo(request.status());
    repository.flush();
    return InquiryView.from(inquiry);
  }
}
