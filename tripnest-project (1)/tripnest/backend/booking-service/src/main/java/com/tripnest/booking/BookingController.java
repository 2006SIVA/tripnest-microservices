package com.tripnest.booking;

import com.tripnest.shared.InternalAuth;
import com.tripnest.shared.TripNestPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {
  private final JdbcTemplate db;
  private final RestClient http = RestClient.create();
  @Value("${services.travel-url:http://travel-service:8080}") private String travelUrl;
  @Value("${services.hotel-url:http://hotel-service:8080}") private String hotelUrl;
  @Value("${services.transport-url:http://transport-service:8080}") private String transportUrl;
  public BookingController(JdbcTemplate db) { this.db = db; }

  public record CreateRequest(@NotNull Long destinationId, Long packageId, Long hotelId, Long transportId,
      @NotNull LocalDate startDate, @NotNull LocalDate endDate, @Min(1) @Max(20) int guests) {}
  public record PaymentResult(boolean success, @NotNull String reference) {}
  public record AdminStatus(@NotNull String status) {}
  public record UpdateRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate, @Min(1) @Max(20) int guests) {}

  @PostMapping
  public Map<String,Object> create(@AuthenticationPrincipal TripNestPrincipal principal, @Valid @RequestBody CreateRequest request) {
    BookingBuilder booking = buildFromRequest(principal.userId(), request);
    Long id = db.queryForObject("insert into bookings(user_id,destination_id,destination_name,package_id,package_name,hotel_id,hotel_name,transport_id,transport_name,start_date,end_date,status,total,guests,package_unit_price,hotel_nightly_price,transport_unit_price,destination_unit_price) values(?,?,?,?,?,?,?,?,?,?,?,'PENDING_PAYMENT',?,?,?,?,?,?) returning id",
        Long.class, booking.userId(), booking.destinationId(), booking.destinationName(), booking.packageId(), booking.packageName(), booking.hotelId(), booking.hotelName(), booking.transportId(), booking.transportName(), booking.startDate(), booking.endDate(), booking.total(), booking.guests(), booking.packageUnitPrice(), booking.hotelNightlyPrice(), booking.transportUnitPrice(), booking.destinationUnitPrice());
    try {
      reserveComponents(id, booking);
    } catch (RuntimeException reservationError) {
      try {
        releaseComponents(id);
        db.update("update bookings set status='CANCELLED',payment_status='FAILED' where id=?", id);
      } catch (RuntimeException cleanupError) {
        db.update("update bookings set status='CANCELLED',payment_status='FAILED',inventory_cleanup_required=true where id=?", id);
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Booking failed and inventory release needs an administrator retry", cleanupError);
      }
      throw reservationError;
    }
    return detail(id);
  }

  @GetMapping
  public List<Map<String,Object>> history(@AuthenticationPrincipal TripNestPrincipal principal) {
    return views(db.queryForList("select * from bookings where user_id=? order by created_at desc limit 200", principal.userId()));
  }

  @GetMapping("/{id}")
  public Map<String,Object> get(@PathVariable long id, @AuthenticationPrincipal TripNestPrincipal principal) {
    Map<String,Object> row = row(id);
    if (!"ADMIN".equals(principal.role()) && ((Number)row.get("user_id")).longValue() != principal.userId())
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This booking belongs to another traveller");
    return toView(row);
  }

  @DeleteMapping("/{id}")
  @Transactional
  public Map<String,Object> cancel(@PathVariable long id, @AuthenticationPrincipal TripNestPrincipal principal) {
    get(id, principal);
    Map<String,Object> row = rowForUpdate(id);
    String status = String.valueOf(row.get("status"));
    if (!"PENDING_PAYMENT".equals(status) && !"CONFIRMED".equals(status))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking can no longer be cancelled");
    if (!localDate(row.get("end_date")).isAfter(LocalDate.now()))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "A trip that has already ended cannot be cancelled");
    releaseComponents(id);
    db.update("update bookings set status='CANCELLED',inventory_cleanup_required=false where id=?", id);
    return detail(id);
  }

  @PutMapping("/{id}")
  @Transactional(noRollbackFor = ResponseStatusException.class)
  public Map<String,Object> update(@PathVariable long id, @AuthenticationPrincipal TripNestPrincipal principal,
      @Valid @RequestBody UpdateRequest request) {
    get(id,principal);
    Map<String,Object> current=rowForUpdate(id);
    if (!"PENDING_PAYMENT".equals(current.get("status")))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Only an unpaid booking can be changed; confirmed bookings require a separate rebooking/refund flow");
    if (!request.endDate().isAfter(request.startDate()) || request.startDate().isBefore(LocalDate.now()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a future arrival date and a later departure date");
    BookingBuilder revised=new BookingBuilder(principal.userId(),((Number)current.get("destination_id")).longValue(),String.valueOf(current.get("destination_name")))
        .dates(request.startDate(),request.endDate(),request.guests());
    revised.destinationBasePrice(decimal(current.get("destination_unit_price")));
    if(current.get("package_id")!=null) revised.packageItem(((Number)current.get("package_id")).longValue(),String.valueOf(current.get("package_name")),decimal(current.get("package_unit_price")));
    if(current.get("hotel_id")!=null) revised.hotel(((Number)current.get("hotel_id")).longValue(),String.valueOf(current.get("hotel_name")),decimal(current.get("hotel_nightly_price")));
    if(current.get("transport_id")!=null) revised.transport(((Number)current.get("transport_id")).longValue(),String.valueOf(current.get("transport_name")),decimal(current.get("transport_unit_price")));
    try { revised.validate(); } catch(IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,ex.getMessage()); }
    LocalDate oldStart=localDate(current.get("start_date")),oldEnd=localDate(current.get("end_date"));int oldGuests=((Number)current.get("guests")).intValue();
    boolean hotelMoved=false,transportMoved=false;
    String secret=serviceSecret();
    try {
      if(current.get("hotel_id")!=null){putInternal(hotelUrl+"/api/v1/hotels/internal/reservations/"+id,Map.of("checkIn",request.startDate().toString(),"checkOut",request.endDate().toString(),"rooms",(request.guests()+1)/2),secret);hotelMoved=true;}
      if(current.get("transport_id")!=null){putInternal(transportUrl+"/api/v1/transport/internal/reservations/"+id,Map.of("travelDate",request.startDate().toString(),"seats",request.guests()),secret);transportMoved=true;}
      db.update("update bookings set start_date=?,end_date=?,guests=?,total=?,status='PENDING_PAYMENT',payment_status='UNPAID',payment_reference=NULL,confirmation_code=NULL where id=?",
          request.startDate(),request.endDate(),request.guests(),revised.total(),id);
    } catch(RuntimeException ex) {
      boolean rollbackFailed=false;
      try { if(hotelMoved)putInternal(hotelUrl+"/api/v1/hotels/internal/reservations/"+id,Map.of("checkIn",oldStart.toString(),"checkOut",oldEnd.toString(),"rooms",(oldGuests+1)/2),secret); } catch(RuntimeException rollbackError) { rollbackFailed=true; }
      try { if(transportMoved)putInternal(transportUrl+"/api/v1/transport/internal/reservations/"+id,Map.of("travelDate",oldStart.toString(),"seats",oldGuests),secret); } catch(RuntimeException rollbackError) { rollbackFailed=true; }
      if(rollbackFailed) {
        db.update("update bookings set status='CANCELLED',payment_status='FAILED',inventory_cleanup_required=true where id=?",id);
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Inventory could not be restored; an administrator must retry release",ex);
      }
      throw ex;
    }
    return detail(id);
  }

  @GetMapping("/admin")
  public List<Map<String,Object>> allBookings() {
    return views(db.queryForList("select * from bookings order by created_at desc limit 500"));
  }

  @PatchMapping("/admin/{id}/status")
  @Transactional
  public Map<String,Object> adminStatus(@PathVariable long id, @Valid @RequestBody AdminStatus update) {
    if (!List.of("PENDING_PAYMENT", "CONFIRMED", "CANCELLED").contains(update.status()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported booking status");
    Map<String,Object> old = rowForUpdate(id);
    String oldStatus=String.valueOf(old.get("status"));
    if(!oldStatus.equals(update.status())&&!("PENDING_PAYMENT".equals(oldStatus)&&"CONFIRMED".equals(update.status()))&&!("PENDING_PAYMENT".equals(oldStatus)&&"CANCELLED".equals(update.status()))&&!("CONFIRMED".equals(oldStatus)&&"CANCELLED".equals(update.status())))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"This booking status transition is not allowed");
    if ("CONFIRMED".equals(update.status()) && !"SUCCEEDED".equals(old.get("payment_status")))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "A booking can only be confirmed after a successful payment");
    if ("CANCELLED".equals(update.status())) releaseComponents(id);
    db.update("update bookings set status=?,inventory_cleanup_required=false where id=?", update.status(), id);
    return detail(id);
  }

  @PostMapping("/internal/{id}/payment-result")
  @Transactional
  public Map<String,Object> paymentResult(@PathVariable long id, @RequestHeader("X-Service-Token") String token,
      @Valid @RequestBody PaymentResult result) {
    InternalAuth.requireServiceToken(token);
    Map<String,Object> old = rowForUpdate(id);
    if ("CONFIRMED".equals(old.get("status")) && result.success()) return detail(id);
    if (!"PENDING_PAYMENT".equals(old.get("status")))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking is not awaiting payment");
    String confirmation = result.success() ? "TN-" + UUID.randomUUID().toString().substring(0,8).toUpperCase() : null;
    db.update("update bookings set payment_status=?,status=?,confirmation_code=?,payment_reference=? where id=?",
        result.success() ? "SUCCEEDED" : "FAILED", result.success() ? "CONFIRMED" : "PENDING_PAYMENT", confirmation, result.reference(), id);
    return detail(id);
  }

  private BookingBuilder buildFromRequest(long userId, CreateRequest r) {
    if (!r.endDate().isAfter(r.startDate())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Check-out must be after check-in");
    Map<String,Object> destination = getMap(travelUrl + "/api/v1/destinations/" + r.destinationId());
    BookingBuilder b = new BookingBuilder(userId, r.destinationId(), String.valueOf(destination.get("name")))
        .destinationBasePrice(decimal(destination.get("starting_price"))).dates(r.startDate(),r.endDate(),r.guests());
    if (r.packageId() != null) {
      Map<String,Object> item = getMap(travelUrl + "/api/v1/packages/" + r.packageId());
      if (((Number)item.get("destination_id")).longValue() != r.destinationId()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Package does not belong to the selected destination");
      b.packageItem(r.packageId(),String.valueOf(item.get("name")),decimal(item.get("price")));
    }
    if (r.hotelId() != null) {
      int rooms=(r.guests()+1)/2;
      Map<String,Object> item=getMap(hotelUrl+"/api/v1/hotels/"+r.hotelId()+"?checkIn="+r.startDate()+"&checkOut="+r.endDate()+"&rooms="+rooms);
      b.hotel(r.hotelId(),String.valueOf(item.get("name")),decimal(item.get("nightly_price")));
    }
    if (r.transportId() != null) {
      Map<String,Object> item=getMap(transportUrl+"/api/v1/transport/"+r.transportId()+"?travelDate="+r.startDate()+"&seats="+r.guests());
      b.transport(r.transportId(),String.valueOf(item.get("name")),decimal(item.get("price")));
    }
    try { b.validate(); } catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,ex.getMessage()); }
    return b;
  }

  private void reserveComponents(long bookingId, BookingBuilder b) {
    String secret=serviceSecret();
    for (TravelServiceFactory.BookingItem item : TravelServiceFactory.create(b.hotelId(), b.transportId())) {
      if ("HOTEL".equals(item.type())) postInternal(hotelUrl+"/api/v1/hotels/internal/reservations",Map.of("bookingId",bookingId,"userId",b.userId(),"hotelId",item.serviceItemId(),"checkIn",b.startDate().toString(),"checkOut",b.endDate().toString(),"rooms",(b.guests()+1)/2),secret);
      else if ("TRANSPORT".equals(item.type())) postInternal(transportUrl+"/api/v1/transport/internal/reservations",Map.of("bookingId",bookingId,"userId",b.userId(),"transportId",item.serviceItemId(),"travelDate",b.startDate().toString(),"seats",b.guests()),secret);
    }
  }

  private void releaseComponents(long bookingId) {
    Map<String,Object> row=row(bookingId);String secret=serviceSecret();
    try { if(row.get("hotel_id")!=null) http.delete().uri(hotelUrl+"/api/v1/hotels/internal/reservations/"+bookingId).header("X-Service-Token",secret).retrieve().toBodilessEntity(); }
    catch(Exception ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Hotel reservation release failed; retry the cancellation",ex); }
    try { if(row.get("transport_id")!=null) http.delete().uri(transportUrl+"/api/v1/transport/internal/reservations/"+bookingId).header("X-Service-Token",secret).retrieve().toBodilessEntity(); }
    catch(Exception ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Transport reservation release failed; retry the cancellation",ex); }
  }

  private String serviceSecret() {
    String secret=System.getenv("SERVICE_SECRET");
    if(secret==null||secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<32) throw new IllegalStateException("SERVICE_SECRET must be set to at least 32 bytes");
    return secret;
  }

  @SuppressWarnings("unchecked") private Map<String,Object> getMap(String url) {
    try { return http.get().uri(url).retrieve().body(Map.class); }
    catch(Exception ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"A catalogue or availability service could not complete the request"); }
  }
  private void postInternal(String url,Map<String,Object> body,String secret) {
    try { http.post().uri(url).header("X-Service-Token",secret).body(body).retrieve().toBodilessEntity(); }
    catch(Exception ex) { throw new ResponseStatusException(HttpStatus.CONFLICT,"Selected inventory could not be reserved; the booking was not confirmed"); }
  }
  private void putInternal(String url,Map<String,Object> body,String secret) {
    try { http.put().uri(url).header("X-Service-Token",secret).body(body).retrieve().toBodilessEntity(); }
    catch(Exception ex) { throw new ResponseStatusException(HttpStatus.CONFLICT,"Requested dates or guest count are no longer available"); }
  }
  private static BigDecimal decimal(Object value) { return value instanceof BigDecimal b ? b : new BigDecimal(value.toString()); }
  private Map<String,Object> row(long id) {
    try { return db.queryForMap("select * from bookings where id=?",id); }
    catch(org.springframework.dao.EmptyResultDataAccessException ex) { throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found"); }
  }
  private Map<String,Object> rowForUpdate(long id) {
    try { return db.queryForMap("select * from bookings where id=? for update",id); }
    catch(org.springframework.dao.EmptyResultDataAccessException ex) { throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found"); }
  }
  private static LocalDate localDate(Object value) {
    if(value instanceof LocalDate date)return date;
    if(value instanceof java.sql.Date date)return date.toLocalDate();
    return LocalDate.parse(String.valueOf(value));
  }
  private Map<String,Object> detail(long id) { return toView(row(id)); }
  private List<Map<String,Object>> views(List<Map<String,Object>> rows) { List<Map<String,Object>> out=new ArrayList<>();for(var row:rows)out.add(toView(row));return out; }
  private Map<String,Object> toView(Map<String,Object> r) {
    Map<String,Object> v=new LinkedHashMap<>();
    v.put("id",r.get("id"));v.put("confirmationCode",r.get("confirmation_code"));v.put("userId",r.get("user_id"));
    v.put("destinationId",r.get("destination_id"));v.put("destination",r.get("destination_name"));v.put("packageId",r.get("package_id"));
    v.put("packageName",r.get("package_name"));v.put("hotelId",r.get("hotel_id"));v.put("hotel",r.get("hotel_name"));
    v.put("transportId",r.get("transport_id"));v.put("transport",r.get("transport_name"));v.put("startDate",r.get("start_date"));
    v.put("endDate",r.get("end_date"));v.put("guests",r.get("guests"));v.put("total",r.get("total"));
    v.put("status",r.get("status"));v.put("paymentStatus",r.get("payment_status"));v.put("paymentReference",r.get("payment_reference"));v.put("createdAt",r.get("created_at"));
    v.put("inventoryCleanupRequired",r.get("inventory_cleanup_required"));
    return v;
  }
}
