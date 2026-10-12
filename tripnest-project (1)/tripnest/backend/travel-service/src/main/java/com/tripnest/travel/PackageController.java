package com.tripnest.travel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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
@RequestMapping("/api/v1/packages")
public class PackageController {
  private final JdbcTemplate db;
  public PackageController(JdbcTemplate db) { this.db = db; }

  public record PackageWrite(@NotNull Long destinationId, @NotBlank @Size(max=160) String name,
      @Size(max=2000) String description, @Size(max=2000) String inclusions,
      @NotNull @PositiveOrZero BigDecimal price) {}

  @GetMapping
  public List<Map<String,Object>> search(@RequestParam(required=false) Long destinationId,
                                         @RequestParam(required=false) String q) {
    if (destinationId != null && q != null && !q.isBlank())
      return db.queryForList("select * from packages where active=true and destination_id=? and name ilike ? order by name limit 50", destinationId, "%"+q.trim()+"%");
    if (destinationId != null)
      return db.queryForList("select * from packages where active=true and destination_id=? order by name limit 50", destinationId);
    if (q != null && !q.isBlank())
      return db.queryForList("select * from packages where active=true and name ilike ? order by name limit 50", "%"+q.trim()+"%");
    return db.queryForList("select * from packages where active=true order by name limit 50");
  }

  @GetMapping("/{id}")
  public Map<String,Object> detail(@PathVariable long id) {
    try { return db.queryForMap("select * from packages where id=? and active=true", id); }
    catch (org.springframework.dao.EmptyResultDataAccessException ex) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Package not found"); }
  }

  @PostMapping
  public Map<String,Object> create(@Valid @RequestBody PackageWrite p) {
    Long exists = db.queryForObject("select id from destinations where id=? and active=true", Long.class, p.destinationId());
    if (exists == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Active destination not found");
    Long id = db.queryForObject("insert into packages(destination_id,name,description,inclusions,price,active) values(?,?,?,?,?,true) returning id",
        Long.class, p.destinationId(), p.name().trim(), p.description(), p.inclusions(), p.price());
    return detail(id);
  }

  @PutMapping("/{id}")
  public Map<String,Object> update(@PathVariable long id, @Valid @RequestBody PackageWrite p) {
    if (db.queryForList("select id from destinations where id=? and active=true", Long.class, p.destinationId()).isEmpty())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Active destination not found");
    int changed = db.update("update packages set destination_id=?,name=?,description=?,inclusions=?,price=? where id=? and active=true",
        p.destinationId(), p.name().trim(), p.description(), p.inclusions(), p.price(), id);
    if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Package not found");
    return detail(id);
  }

  @DeleteMapping("/{id}")
  public Map<String,String> remove(@PathVariable long id) {
    int changed = db.update("update packages set active=false where id=? and active=true", id);
    if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Package not found");
    return Map.of("status", "REMOVED");
  }
}
