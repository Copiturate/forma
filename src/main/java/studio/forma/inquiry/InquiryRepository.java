package studio.forma.inquiry;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRepository extends JpaRepository<Inquiry, UUID> {
  Page<Inquiry> findByStatus(InquiryStatus status, Pageable pageable);
}
