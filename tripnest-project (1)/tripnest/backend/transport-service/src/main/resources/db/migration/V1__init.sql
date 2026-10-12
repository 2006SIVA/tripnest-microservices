CREATE TABLE transport_options(id BIGSERIAL PRIMARY KEY,name VARCHAR(160) NOT NULL,origin VARCHAR(160) NOT NULL,destination VARCHAR(160) NOT NULL,mode VARCHAR(50) NOT NULL,price NUMERIC(12,2) NOT NULL CHECK(price>=0),available_seats INTEGER NOT NULL DEFAULT 20,active BOOLEAN NOT NULL DEFAULT true);
INSERT INTO transport_options(id,name,origin,destination,mode,price,available_seats) VALUES
 (1,'Airport to Ubud','DPS','Bali','Private transfer',38,20),
 (2,'Ubud village loop','Ubud','Bali','Local driver',54,12),
 (3,'Kansai Airport Express','KIX','Kyoto','Rail',22,60),
 (4,'Zurich to Interlaken','Zurich','Swiss Alps','Scenic rail',74,45),
 (5,'Historic Kyoto local route','Kyoto','Higashiyama','Local route',14,30);
SELECT setval(pg_get_serial_sequence('transport_options','id'),(SELECT max(id) FROM transport_options));
