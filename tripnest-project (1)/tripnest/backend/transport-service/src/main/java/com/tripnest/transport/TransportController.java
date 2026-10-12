package com.tripnest.transport;

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
@RequestMapping("/api/v1/transport")
public class TransportController {
  private final JdbcTemplate db;
  public TransportController(JdbcTemplate db) { this.db = db; }

  public record TransportWrite(@NotBlank @Size(max=160) String name, @NotBlank @Size(max=160) String origin,
      @NotBlank @Size(max=160) String destination, @NotBlank @Size(max=50) String mode,
      @NotNull @PositiveOrZero BigDecimal price, @NotNull @PositiveOrZero Integer availableSeats) {}
  public record Reservation(long bookingId, long userId, long transportId, LocalDate travelDate, int seats) {}
  public record ReservationUpdate(@NotNull LocalDate travelDate, int seats) {}

  @GetMapping
  public List<Map<String,Object>> search(@RequestParam(required=false) String q,
      @RequestParam(required=false) LocalDate travelDate, @Positive @RequestParam(defaultValue="1") int seats) {
    StringBuilder sql = new StringBuilder("select * from transport_options where active=true");
    List<Object> args = new ArrayList<>();
    if (q != null && !q.isBlank()) { sql.append(" and (name ilike ? or origin ilike ? or destination ilike ? or mode ilike ?)"); for (int i=0;i<4;i++) args.add("%"+q.trim()+"%"); }
    sql.append(" order by name limit 50");
    List<Map<String,Object>> results = new ArrayList<>();
    for (Map<String,Object> row : db.queryForList(sql.toString(), args.toArray())) {
      long id = ((Number)row.get("id")).longValue(); int available = availableSeats(id, travelDate);
      if (available >= seats) { Map<String,Object> item=new LinkedHashMap<>(row);item.put("availableSeats",available);results.add(item); }
    }
    return results;
  }

  @GetMapping("/{id}")
  public Map<String,Object> detail(@PathVariable long id, @RequestParam(required=false) LocalDate travelDate,
      @Positive @RequestParam(defaultValue="1") int seats) {
    try { Map<String,Object> item = new LinkedHashMap<>(db.queryForMap("select * from transport_options where id=? and active=true", id));
      int available=availableSeats(id,travelDate);item.put("availableSeats",available);
      if (available < seats) throw new ResponseStatusException(HttpStatus.CONFLICT,"Not enough seats are available for that date"); return item;
    } catch (org.springframework.dao.EmptyResultDataAccessException ex) { throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Transport option not found"); }
  }

  @PostMapping
  public Map<String,Object> create(@Valid @RequestBody TransportWrite t) {
    Long id=db.queryForObject("insert into transport_options(name,origin,destination,mode,price,available_seats,active) values(?,?,?,?,?,?,true) returning id",
        Long.class,t.name().trim(),t.origin().trim(),t.destination().trim(),t.mode().trim(),t.price(),t.availableSeats());
    return detail(id,null,1);
  }

  @PutMapping("/{id}")
  public Map<String,Object> update(@PathVariable long id,@Valid @RequestBody TransportWrite t) {
    int n=db.update("update transport_options set name=?,origin=?,destination=?,mode=?,price=?,available_seats=? where id=? and active=true",
        t.name().trim(),t.origin().trim(),t.destination().trim(),t.mode().trim(),t.price(),t.availableSeats(),id);
    if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Transport option not found");return detail(id,null,1);
  }

  @DeleteMapping("/{id}")
  public Map<String,String> remove(@PathVariable long id) {
    if(db.update("update transport_options set active=false where id=? and active=true",id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Transport option not found");
    return Map.of("status","REMOVED");
  }

  @PostMapping("/internal/reservations")
  @Transactional
  public Map<String,Object> reserve(@RequestHeader("X-Service-Token") String token,@Valid @RequestBody Reservation r) {
    InternalAuth.requireServiceToken(token);
    if(r.seats()<1)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"At least one seat is required");
    var existing=db.queryForList("select * from transport_reservations where booking_id=? and status='ACTIVE'",r.bookingId());if(!existing.isEmpty())return existing.getFirst();
    try{db.queryForMap("select id from transport_options where id=? and active=true for update",r.transportId());}
    catch(org.springframework.dao.EmptyResultDataAccessException ex){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Transport option not found");}
    if(availableSeats(r.transportId(),r.travelDate())<r.seats())throw new ResponseStatusException(HttpStatus.CONFLICT,"Not enough seats are available for that date");
    Long id=db.queryForObject("insert into transport_reservations(booking_id,user_id,transport_id,travel_date,seats,status) values(?,?,?,?,?,'ACTIVE') returning id",
        Long.class,r.bookingId(),r.userId(),r.transportId(),r.travelDate(),r.seats());return db.queryForMap("select * from transport_reservations where id=?",id);
  }

  @DeleteMapping("/internal/reservations/{bookingId}")
  public Map<String,String> release(@RequestHeader("X-Service-Token") String token,@PathVariable long bookingId) {
    InternalAuth.requireServiceToken(token);db.update("update transport_reservations set status='CANCELLED' where booking_id=? and status='ACTIVE'",bookingId);return Map.of("status","RELEASED");
  }

  @PutMapping("/internal/reservations/{bookingId}")
  @Transactional
  public Map<String,Object> updateReservation(@RequestHeader("X-Service-Token") String token,@PathVariable long bookingId,
      @Valid @RequestBody ReservationUpdate update) {
    InternalAuth.requireServiceToken(token);if(update.seats()<1)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"At least one seat is required");
    Map<String,Object> existing;
    try{existing=db.queryForMap("select * from transport_reservations where booking_id=? and status='ACTIVE' for update",bookingId);}
    catch(org.springframework.dao.EmptyResultDataAccessException ex){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Active transport reservation not found");}
    long transportId=((Number)existing.get("transport_id")).longValue();long reservationId=((Number)existing.get("id")).longValue();
    try{db.queryForMap("select id from transport_options where id=? and active=true for update",transportId);}
    catch(org.springframework.dao.EmptyResultDataAccessException ex){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Transport option not found");}
    Integer booked=db.queryForObject("select coalesce(sum(seats),0) from transport_reservations where transport_id=? and id<>? and travel_date=? and status='ACTIVE'",Integer.class,transportId,reservationId,update.travelDate());
    Integer capacity=db.queryForObject("select available_seats from transport_options where id=?",Integer.class,transportId);
    if(capacity-(booked==null?0:booked)<update.seats())throw new ResponseStatusException(HttpStatus.CONFLICT,"Not enough seats are available for that date");
    db.update("update transport_reservations set travel_date=?,seats=? where id=?",update.travelDate(),update.seats(),reservationId);
    return db.queryForMap("select * from transport_reservations where id=?",reservationId);
  }

  private int availableSeats(long transportId,LocalDate date) {
    Integer capacity=db.queryForObject("select available_seats from transport_options where id=? and active=true",Integer.class,transportId);
    if(date==null)return capacity;Integer booked=db.queryForObject("select coalesce(sum(seats),0) from transport_reservations where transport_id=? and travel_date=? and status='ACTIVE'",Integer.class,transportId,date);
    return capacity-(booked==null?0:booked);
  }
}
