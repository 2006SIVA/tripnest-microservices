CREATE TABLE destinations(id BIGSERIAL PRIMARY KEY,name VARCHAR(160) NOT NULL,location VARCHAR(160) NOT NULL,category VARCHAR(80),description TEXT,starting_price NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK(starting_price>=0),active BOOLEAN NOT NULL DEFAULT true);
CREATE TABLE packages(id BIGSERIAL PRIMARY KEY,destination_id BIGINT NOT NULL REFERENCES destinations(id),name VARCHAR(160) NOT NULL,description TEXT,inclusions TEXT,price NUMERIC(12,2) NOT NULL CHECK(price>=0),active BOOLEAN NOT NULL DEFAULT true);
INSERT INTO destinations(id,name,location,category,description,starting_price) VALUES
 (1,'Bali','Indonesia','Island','Green ridgelines, temple courtyards and long afternoons by the water.',420),
 (2,'Kyoto','Japan','Culture','Lantern-lit lanes, quiet gardens and the old town.',510),
 (3,'Swiss Alps','Switzerland','Nature','Clear lakes, high trails and mountain villages linked by scenic rail.',860),
 (4,'Higashiyama','Japan','Culture','Wooden streets and pagodas in Kyoto’s historic hills.',510);
SELECT setval(pg_get_serial_sequence('destinations','id'),(SELECT max(id) FROM destinations));
INSERT INTO packages(id,destination_id,name,description,inclusions,price) VALUES
 (1,1,'The slower side of Bali','Six nights in and around Ubud','Ubud rice terrace walk; temple visit with local guide; daily breakfast',420.00),
 (2,1,'Ubud to the coast','Eight nights between Ubud and Uluwatu','Ubud and Uluwatu stays; private airport transfer; sunset coastal walk',680.00),
 (3,2,'Kyoto, one lane at a time','Five nights exploring historic Kyoto','Higashiyama walking tour; tea room experience; rail pass',510.00),
 (4,3,'The alpine rail week','Seven nights among lakes and peaks','Scenic rail pass; lakeside village guide; daily breakfast',860.00),
 (5,4,'Higashiyama after hours','Five nights in Kyoto’s historic eastern hills','Evening walking tour; tea room experience; temple entry',495.00);
SELECT setval(pg_get_serial_sequence('packages','id'),(SELECT max(id) FROM packages));
