package com.tripnest.booking;

import java.util.ArrayList;
import java.util.List;

public final class TravelServiceFactory {
  private TravelServiceFactory() {}
  public record BookingItem(String type, long serviceItemId) {}

  public static List<BookingItem> create(Long hotelId, Long transportId) {
    List<BookingItem> items = new ArrayList<>();
    if (hotelId != null) items.add(new BookingItem("HOTEL", hotelId));
    if (transportId != null) items.add(new BookingItem("TRANSPORT", transportId));
    return List.copyOf(items);
  }
}
