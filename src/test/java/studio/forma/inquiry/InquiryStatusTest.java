package studio.forma.inquiry;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InquiryStatusTest {
  @Test
  void onlyAllowsDocumentedTransitions() {
    assertThat(InquiryStatus.NEW.canTransitionTo(InquiryStatus.REVIEWING)).isTrue();
    assertThat(InquiryStatus.REVIEWING.canTransitionTo(InquiryStatus.CONTACTED)).isTrue();
    assertThat(InquiryStatus.CONTACTED.canTransitionTo(InquiryStatus.CLOSED)).isTrue();
    assertThat(InquiryStatus.NEW.canTransitionTo(InquiryStatus.CONTACTED)).isFalse();
    for (var status : InquiryStatus.values()) {
      assertThat(InquiryStatus.CLOSED.canTransitionTo(status)).isFalse();
      assertThat(status.canTransitionTo(status)).isFalse();
    }
  }
}
