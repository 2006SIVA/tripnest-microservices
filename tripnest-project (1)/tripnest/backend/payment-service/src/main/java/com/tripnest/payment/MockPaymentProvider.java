package com.tripnest.payment;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentProvider implements PaymentProvider {
  @Override
  public boolean process(BigDecimal amount, String outcome) {
    if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("A positive server-calculated amount is required");
    if (!"SUCCESS".equals(outcome) && !"FAILURE".equals(outcome)) throw new IllegalArgumentException("Outcome must be SUCCESS or FAILURE");
    return "SUCCESS".equals(outcome);
  }

  @Override
  public String name() { return "MOCK"; }
}
