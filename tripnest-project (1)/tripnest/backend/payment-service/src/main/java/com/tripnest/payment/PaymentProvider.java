package com.tripnest.payment;

import java.math.BigDecimal;

public interface PaymentProvider {
  boolean process(BigDecimal serverCalculatedAmount, String requestedOutcome);
  String name();
}
