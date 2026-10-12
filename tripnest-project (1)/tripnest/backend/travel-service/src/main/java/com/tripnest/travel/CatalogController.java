package com.tripnest.travel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/destinations")
public class CatalogController {
  private final JdbcTemplate db;
  public CatalogController(JdbcTemplate db) { this.db = db; }

  public record DestinationWrite(@NotBlank @Size(max=160) String name,
                                 @NotBlank @Size(max=160) String location,
                                 @Size(max=80) String category,
                                 @Size(max=2000) String description,
                                 @NotNull @PositiveOrZero BigDecimal startingPrice) {}

  @GetMapping
  public List<Map<String,Object>> search(@RequestParam(required=false) String q,
      @RequestParam(required=false) String location, @RequestParam(required=false) String category,
      @RequestParam(defaultValue="0") int offset) {
    StringBuilder sql = new StringBuilder("select * from destinations where active=true");
    java.util.ArrayList<Object> args = new java.util.ArrayList<>();
    if (q != null && !q.isBlank()) { sql.append(" and (name ilike ? or location ilike ? or description ilike ?)"); String like="%"+q.trim()+"%"; args.add(like);args.add(like);args.add(like); }
    if (location != null && !location.isBlank()) { sql.append(" and location ilike ?");args.add("%"+location.trim()+"%"); }
    if (category != null && !category.isBlank()) { sql.append(" and category ilike ?");args.add(category.trim()); }
    sql.append(" order by name limit 50 offset ?");args.add(Math.max(0, Math.min(offset, 10000)));
    return db.queryForList(sql.toString(), args.toArray());
  }

  @GetMapping("/{id}")
  public Map<String,Object> detail(@PathVariable long id) {
    Map<String,Object> result;
    try { result = new LinkedHashMap<>(db.queryForMap("select * from destinations where id=? and active=true", id)); }
    catch (org.springframework.dao.EmptyResultDataAccessException ex) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Destination not found"); }
    result.put("packages", db.queryForList("select * from packages where destination_id=? and active=true order by name", id));
    return result;
  }

  @PostMapping
  public Map<String,Object> create(@Valid @RequestBody DestinationWrite d) {
    Long id = db.queryForObject("insert into destinations(name,location,category,description,starting_price,active) values(?,?,?,?,?,true) returning id",
        Long.class, d.name().trim(), d.location().trim(), d.category(), d.description(), d.startingPrice());
    return detail(id);
  }

  @PutMapping("/{id}")
  public Map<String,Object> update(@PathVariable long id, @Valid @RequestBody DestinationWrite d) {
    int changed = db.update("update destinations set name=?,location=?,category=?,description=?,starting_price=? where id=? and active=true",
        d.name().trim(), d.location().trim(), d.category(), d.description(), d.startingPrice(), id);
    if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Destination not found");
    return detail(id);
  }

  @DeleteMapping("/{id}")
  public Map<String,String> remove(@PathVariable long id) {
    int changed = db.update("update destinations set active=false where id=? and active=true", id);
    if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Destination not found");
    db.update("update packages set active=false where destination_id=?", id);
    return Map.of("status", "REMOVED");
  }
}
