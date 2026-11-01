package studio.forma.inquiry;

import java.util.Set;

public enum InquiryStatus {
  NEW,
  REVIEWING,
  CONTACTED,
  CLOSED;

  public boolean canTransitionTo(InquiryStatus next) {
    return switch (this) {
      case NEW -> Set.of(REVIEWING, CLOSED).contains(next);
      case REVIEWING -> Set.of(CONTACTED, CLOSED).contains(next);
      case CONTACTED -> next == CLOSED;
      case CLOSED -> false;
    };
  }
}
