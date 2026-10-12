package com.tripnest.booking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class BookingBuilder {
  private final long userId;
  private final long destinationId;
  private final String destinationName;
  private Long packageId;
  private String packageName;
  private Long hotelId;
  private String hotelName;
  private Long transportId;
  private String transportName;
  private LocalDate startDate;
  private LocalDate endDate;
  private int guests = 1;
  private BigDecimal packageUnitPrice = BigDecimal.ZERO;
  private BigDecimal destinationUnitPrice = BigDecimal.ZERO;
  private BigDecimal hotelNightlyPrice = BigDecimal.ZERO;
  private BigDecimal transportUnitPrice = BigDecimal.ZERO;

  public BookingBuilder(long userId, long destinationId, String destinationName) {
    this.userId = userId; this.destinationId = destinationId; this.destinationName = destinationName;
  }
  public BookingBuilder packageItem(long id, String name, BigDecimal price) { packageId=id; packageName=name; packageUnitPrice=price; return this; }
  public BookingBuilder destinationBasePrice(BigDecimal price) { destinationUnitPrice=price; return this; }
  public BookingBuilder hotel(long id, String name, BigDecimal nightlyPrice) { hotelId=id; hotelName=name; hotelNightlyPrice=nightlyPrice; return this; }
  public BookingBuilder transport(long id, String name, BigDecimal unitPrice) { transportId=id; transportName=name; transportUnitPrice=unitPrice; return this; }
  public BookingBuilder dates(LocalDate start, LocalDate end, int count) { startDate=start; endDate=end; guests=count; return this; }

  public long userId() { return userId; }
  public long destinationId() { return destinationId; }
  public String destinationName() { return destinationName; }
  public Long packageId() { return packageId; }
  public String packageName() { return packageName; }
  public Long hotelId() { return hotelId; }
  public String hotelName() { return hotelName; }
  public Long transportId() { return transportId; }
  public String transportName() { return transportName; }
  public LocalDate startDate() { return startDate; }
  public LocalDate endDate() { return endDate; }
  public int guests() { return guests; }
  public BigDecimal packageUnitPrice() { return packageUnitPrice; }
  public BigDecimal destinationUnitPrice() { return destinationUnitPrice; }
  public BigDecimal hotelNightlyPrice() { return hotelNightlyPrice; }
  public BigDecimal transportUnitPrice() { return transportUnitPrice; }
  public BigDecimal total() {
    long nights = ChronoUnit.DAYS.between(startDate, endDate);
    int rooms = (guests + 1) / 2;
    BigDecimal extras=packageUnitPrice.multiply(BigDecimal.valueOf(guests))
        .add(hotelNightlyPrice.multiply(BigDecimal.valueOf(nights * rooms)))
        .add(transportUnitPrice.multiply(BigDecimal.valueOf(guests)));
    if(packageId==null&&hotelId==null&&transportId==null) extras=destinationUnitPrice.multiply(BigDecimal.valueOf(guests));
    return extras.setScale(2, java.math.RoundingMode.HALF_UP);
  }
  public void validate() {
    if (startDate == null || endDate == null || !endDate.isAfter(startDate)) throw new IllegalArgumentException("Travel dates are required and check-out must follow check-in");
    if (guests < 1 || guests > 20) throw new IllegalArgumentException("Guest count must be between 1 and 20");
    if (packageId == null && hotelId == null && transportId == null && destinationUnitPrice.signum() <= 0)
      throw new IllegalArgumentException("The destination has no bookable starting price");
    if (total().signum() < 0) throw new IllegalArgumentException("Booking total must not be negative");
  }
}
