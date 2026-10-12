package com.tripnest.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import com.tripnest.shared.TripNestPrincipal;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
  private final JdbcTemplate db;
  private final RestClient http = RestClient.create();
  private final PaymentProvider paymentProvider;
  @Value("${services.booking-url:http://booking-service:8080}") private String bookingUrl;
  public PaymentController(JdbcTemplate db, PaymentProvider paymentProvider) { this.db=db; this.paymentProvider=paymentProvider; }

  public record PaymentRequest(@NotNull Long bookingId, @NotBlank String outcome) {}
  @PostMapping
  @Transactional(noRollbackFor = ResponseStatusException.class)
  @SuppressWarnings("unchecked")
  public Map<String,Object> pay(@Valid @RequestBody PaymentRequest request,
      @RequestHeader("Authorization") String authorization,
      @AuthenticationPrincipal TripNestPrincipal principal) {
    Map<String,Object> booking;
    try { booking = http.get().uri(bookingUrl + "/api/v1/bookings/" + request.bookingId())
        .header("Authorization", authorization).retrieve().body(Map.class); }
    catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking not found or is not owned by this traveller"); }
    if (!principal.role().equals("ADMIN") && ((Number)booking.get("userId")).longValue() != principal.userId())
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This booking belongs to another traveller");
    db.update("insert into payment_booking_locks(booking_id) values(?) on conflict do nothing", request.bookingId());
    db.queryForObject("select booking_id from payment_booking_locks where booking_id=? for update", Long.class, request.bookingId());
    try { booking = http.get().uri(bookingUrl + "/api/v1/bookings/" + request.bookingId())
        .header("Authorization", authorization).retrieve().body(Map.class); }
    catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking not found or is not owned by this traveller"); }
    if (!principal.role().equals("ADMIN") && ((Number)booking.get("userId")).longValue() != principal.userId())
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This booking belongs to another traveller");
    List<Map<String,Object>> successful = db.queryForList("select id,amount,status,provider,reference from payments where booking_id=? and status='SUCCEEDED' order by created_at limit 1", request.bookingId());
    if (!successful.isEmpty()) {
      if (!List.of("PENDING_PAYMENT", "CONFIRMED").contains(booking.get("status")))
        throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking is no longer payable");
      Map<String,Object> prior=successful.getFirst();
      Map<String,Object> confirmation;
      try { confirmation=http.post().uri(bookingUrl+"/api/v1/bookings/internal/"+request.bookingId()+"/payment-result")
          .header("X-Service-Token",serviceSecret()).body(Map.of("success",true,"reference",prior.get("reference"))).retrieve().body(Map.class); }
      catch(Exception ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Successful payment is saved; confirmation can be retried",ex); }
      return paymentResponse(prior, request.bookingId(), confirmation);
    }
    if (!"PENDING_PAYMENT".equals(booking.get("status")))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a booking awaiting payment can be paid");
    BigDecimal amount = new BigDecimal(booking.get("total").toString());
    if (amount.signum() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking total must be positive");
    boolean success = paymentProvider.process(amount, request.outcome());
    String reference = "MOCK-" + UUID.randomUUID();
    db.update("insert into payments(booking_id,amount,status,provider,reference) values(?,?,?,?,?)",
        request.bookingId(), amount, success ? "SUCCEEDED" : "FAILED", paymentProvider.name(), reference);
    Map<String,Object> confirmation;
    String serviceSecret = serviceSecret();
    try {
      confirmation = http.post().uri(bookingUrl + "/api/v1/bookings/internal/" + request.bookingId() + "/payment-result")
          .header("X-Service-Token", serviceSecret).body(Map.of("success", success, "reference", reference))
          .retrieve().body(Map.class);
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Payment outcome was recorded; booking confirmation is temporarily unavailable");
    }
    Map<String,Object> saved=db.queryForMap("select id,amount,status,provider,reference from payments where reference=?",reference);
    return paymentResponse(saved,request.bookingId(),confirmation);
  }

  @GetMapping
  @SuppressWarnings("unchecked")
  public List<Map<String,Object>> history(@RequestParam long bookingId,
      @RequestHeader("Authorization") String authorization,
      @AuthenticationPrincipal TripNestPrincipal principal) {
    Map<String,Object> booking;
    try { booking=http.get().uri(bookingUrl+"/api/v1/bookings/"+bookingId).header("Authorization",authorization).retrieve().body(Map.class); }
    catch(Exception ex){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found");}
    if(!"ADMIN".equals(principal.role())&&((Number)booking.get("userId")).longValue()!=principal.userId())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Booking belongs to another traveller");
    return db.queryForList("select id,booking_id as \"bookingId\",amount,status,provider,reference,created_at as \"createdAt\" from payments where booking_id=? order by created_at desc",bookingId);
  }

  @GetMapping("/admin")
  public List<Map<String,Object>> allPayments(){return db.queryForList("select id,booking_id as \"bookingId\",amount,status,provider,reference,created_at as \"createdAt\" from payments order by created_at desc limit 500");}

  private String serviceSecret() {
    String secret=System.getenv("SERVICE_SECRET");
    if(secret==null||secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<32) throw new IllegalStateException("SERVICE_SECRET must be set to at least 32 bytes");
    return secret;
  }

  private Map<String,Object> paymentResponse(Map<String,Object> payment,long bookingId,Map<String,Object> confirmation) {
    Map<String,Object> response=new LinkedHashMap<>();response.put("paymentId",payment.get("id"));response.put("bookingId",bookingId);
    response.put("amount",payment.get("amount"));response.put("status",payment.get("status"));response.put("provider",payment.get("provider"));
    response.put("reference",payment.get("reference"));response.put("confirmationCode",confirmation.get("confirmationCode"));
    response.put("realCharge",false);return response;
  }
}
