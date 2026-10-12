package com.tripnest.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BookingBuilderTest {
  @Test
  void calculatesPackageHotelAndTransportTotalFromGuestCountAndNights() {
    BookingBuilder builder = new BookingBuilder(41, 7, "Example")
        .packageItem(11, "Package", new BigDecimal("100.00"))
        .hotel(12, "Hotel", new BigDecimal("50.00"))
        .transport(13, "Train", new BigDecimal("20.00"))
        .dates(LocalDate.of(2027, 4, 1), LocalDate.of(2027, 4, 4), 3);

    builder.validate();
    // 3 guests × $100 package + 3 nights × 2 rooms × $50 + 3 guests × $20.
    assertEquals(new BigDecimal("660.00"), builder.total());
  }

  @Test
  void requiresAtLeastOneBookableComponent() {
    BookingBuilder builder = new BookingBuilder(41, 7, "Example")
        .dates(LocalDate.of(2027, 4, 1), LocalDate.of(2027, 4, 2), 1);
    assertThrows(IllegalArgumentException.class, builder::validate);
  }

  @Test
  void pricesDestinationOnlyBookingsFromTheServerManagedBasePrice() {
    BookingBuilder builder = new BookingBuilder(41, 7, "Example")
        .destinationBasePrice(new BigDecimal("125.00"))
        .dates(LocalDate.of(2027, 4, 1), LocalDate.of(2027, 4, 2), 2);
    builder.validate();
    assertEquals(new BigDecimal("250.00"), builder.total());
  }

  @Test
  void rejectsDepartureOnOrBeforeArrival() {
    BookingBuilder builder = new BookingBuilder(41, 7, "Example")
        .packageItem(11, "Package", new BigDecimal("100.00"))
        .dates(LocalDate.of(2027, 4, 2), LocalDate.of(2027, 4, 2), 1);
    assertThrows(IllegalArgumentException.class, builder::validate);
  }

  @Test
  void factoryCreatesOnlySelectedOptionalServices() {
    assertEquals(2, TravelServiceFactory.create(4L, 9L).size());
    assertEquals("HOTEL", TravelServiceFactory.create(4L, null).getFirst().type());
    assertEquals(0, TravelServiceFactory.create(null, null).size());
  }
}
