package com.tripnest.hotel;

import com.tripnest.shared.InternalAuth;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.annotation.Validated;

@RestController
@Validated
@RequestMapping("/api/v1/hotels")
public class HotelController {
  private final JdbcTemplate db;
  public HotelController(JdbcTemplate db) { this.db = db; }

  public record HotelWrite(@NotBlank @Size(max=160) String name, @NotBlank @Size(max=160) String location,
      @Size(max=2000) String description, @NotNull @PositiveOrZero BigDecimal nightlyPrice,
      @NotNull @PositiveOrZero Integer rooms) {}
  public record Reservation(long bookingId, long userId, long hotelId, LocalDate checkIn, LocalDate checkOut, int rooms) {}
  public record ReservationUpdate(LocalDate checkIn, LocalDate checkOut, int rooms) {}

  @GetMapping
  public List<Map<String,Object>> search(@RequestParam(required=false) String q,
      @RequestParam(required=false) String location, @RequestParam(required=false) LocalDate checkIn,
      @RequestParam(required=false) LocalDate checkOut, @Positive @RequestParam(defaultValue="1") int rooms) {
    validateDates(checkIn, checkOut);
    StringBuilder sql = new StringBuilder("select * from hotels where active=true");
    List<Object> args = new ArrayList<>();
    if (q != null && !q.isBlank()) { sql.append(" and (name ilike ? or location ilike ?)"); args.add("%"+q.trim()+"%"); args.add("%"+q.trim()+"%"); }
    if (location != null && !location.isBlank()) { sql.append(" and location ilike ?"); args.add("%"+location.trim()+"%"); }
    sql.append(" order by name limit 50");
    List<Map<String,Object>> results = new ArrayList<>();
    for (Map<String,Object> row : db.queryForList(sql.toString(), args.toArray())) {
      long id = ((Number)row.get("id")).longValue(); int available = availableRooms(id, checkIn, checkOut);
      if (available >= rooms) { Map<String,Object> item = new LinkedHashMap<>(row); item.put("availableRooms", available); results.add(item); }
    }
    return results;
  }

  @GetMapping("/{id}")
  public Map<String,Object> detail(@PathVariable long id, @RequestParam(required=false) LocalDate checkIn,
      @RequestParam(required=false) LocalDate checkOut, @Positive @RequestParam(defaultValue="1") int rooms) {
    validateDates(checkIn, checkOut);
    try { Map<String,Object> hotel = new LinkedHashMap<>(db.queryForMap("select * from hotels where id=? and active=true", id));
      int available = availableRooms(id, checkIn, checkOut); hotel.put("availableRooms", available);
      if (available < rooms) throw new ResponseStatusException(HttpStatus.CONFLICT, "Not enough rooms are available for those dates");
      return hotel;
    } catch (org.springframework.dao.EmptyResultDataAccessException ex) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel not found"); }
  }

  @PostMapping
  public Map<String,Object> create(@Valid @RequestBody HotelWrite h) {
    Long id = db.queryForObject("insert into hotels(name,location,description,nightly_price,rooms,active) values(?,?,?,?,?,true) returning id",
        Long.class, h.name().trim(), h.location().trim(), h.description(), h.nightlyPrice(), h.rooms());
    return detail(id, null, null, 1);
  }

  @PutMapping("/{id}")
  public Map<String,Object> update(@PathVariable long id, @Valid @RequestBody HotelWrite h) {
    int n = db.update("update hotels set name=?,location=?,description=?,nightly_price=?,rooms=? where id=? and active=true",
        h.name().trim(), h.location().trim(), h.description(), h.nightlyPrice(), h.rooms(), id);
    if (n == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel not found");
    return detail(id, null, null, 1);
  }

  @DeleteMapping("/{id}")
  public Map<String,String> remove(@PathVariable long id) {
    if (db.update("update hotels set active=false where id=? and active=true", id) == 0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel not found");
    return Map.of("status", "REMOVED");
  }

  @PostMapping("/internal/reservations")
  @Transactional
  public Map<String,Object> reserve(@RequestHeader("X-Service-Token") String token, @Valid @RequestBody Reservation r) {
    InternalAuth.requireServiceToken(token); validateDates(r.checkIn(), r.checkOut());
    if (r.rooms() < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one room is required");
    var existing = db.queryForList("select * from hotel_reservations where booking_id=? and status='ACTIVE'", r.bookingId());
    if (!existing.isEmpty()) return existing.getFirst();
    try { db.queryForMap("select id from hotels where id=? and active=true for update", r.hotelId()); }
    catch (org.springframework.dao.EmptyResultDataAccessException ex) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel not found"); }
    if (availableRooms(r.hotelId(), r.checkIn(), r.checkOut()) < r.rooms())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Not enough rooms are available for those dates");
    Long id = db.queryForObject("insert into hotel_reservations(booking_id,user_id,hotel_id,check_in,check_out,rooms,status) values(?,?,?,?,?,?,'ACTIVE') returning id",
        Long.class, r.bookingId(), r.userId(), r.hotelId(), r.checkIn(), r.checkOut(), r.rooms());
    return db.queryForMap("select * from hotel_reservations where id=?", id);
  }

  @DeleteMapping("/internal/reservations/{bookingId}")
  public Map<String,String> release(@RequestHeader("X-Service-Token") String token, @PathVariable long bookingId) {
    InternalAuth.requireServiceToken(token); db.update("update hotel_reservations set status='CANCELLED' where booking_id=? and status='ACTIVE'", bookingId);
    return Map.of("status", "RELEASED");
  }

  @PutMapping("/internal/reservations/{bookingId}")
  @Transactional
  public Map<String,Object> updateReservation(@RequestHeader("X-Service-Token") String token,@PathVariable long bookingId,
      @Valid @RequestBody ReservationUpdate update) {
    InternalAuth.requireServiceToken(token);validateDates(update.checkIn(),update.checkOut());
    if(update.rooms()<1)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"At least one room is required");
    Map<String,Object> existing;
    try{existing=db.queryForMap("select * from hotel_reservations where booking_id=? and status='ACTIVE' for update",bookingId);}
    catch(org.springframework.dao.EmptyResultDataAccessException ex){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Active hotel reservation not found");}
    long hotelId=((Number)existing.get("hotel_id")).longValue();long reservationId=((Number)existing.get("id")).longValue();
    try{db.queryForMap("select id from hotels where id=? and active=true for update",hotelId);}
    catch(org.springframework.dao.EmptyResultDataAccessException ex){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Hotel not found");}
    Integer booked=db.queryForObject("select coalesce(sum(rooms),0) from hotel_reservations where hotel_id=? and id<>? and status='ACTIVE' and check_in < ? and check_out > ?",Integer.class,hotelId,reservationId,update.checkOut(),update.checkIn());
    Integer capacity=db.queryForObject("select rooms from hotels where id=?",Integer.class,hotelId);
    if(capacity-(booked==null?0:booked)<update.rooms())throw new ResponseStatusException(HttpStatus.CONFLICT,"Not enough rooms are available for those dates");
    db.update("update hotel_reservations set check_in=?,check_out=?,rooms=? where id=?",update.checkIn(),update.checkOut(),update.rooms(),reservationId);
    return db.queryForMap("select * from hotel_reservations where id=?",reservationId);
  }

  private int availableRooms(long hotelId, LocalDate start, LocalDate end) {
    int rooms = db.queryForObject("select rooms from hotels where id=? and active=true", Integer.class, hotelId);
    if (start == null) return rooms;
    Integer booked = db.queryForObject("select coalesce(sum(rooms),0) from hotel_reservations where hotel_id=? and status='ACTIVE' and check_in < ? and check_out > ?",
        Integer.class, hotelId, end, start);
    return rooms - (booked == null ? 0 : booked);
  }

  private static void validateDates(LocalDate start, LocalDate end) {
    if ((start == null) != (end == null) || (start != null && !end.isAfter(start)))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide both dates and ensure check-out is after check-in");
  }
}
