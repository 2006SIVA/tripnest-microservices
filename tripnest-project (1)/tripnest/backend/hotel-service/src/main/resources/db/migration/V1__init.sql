CREATE TABLE hotels(id BIGSERIAL PRIMARY KEY,name VARCHAR(160) NOT NULL,location VARCHAR(160) NOT NULL,description TEXT,nightly_price NUMERIC(12,2) NOT NULL CHECK(nightly_price>=0),rooms INTEGER NOT NULL DEFAULT 1 CHECK(rooms>=0),active BOOLEAN NOT NULL DEFAULT true);
INSERT INTO hotels(id,name,location,description,nightly_price,rooms) VALUES
 (1,'Sayan Valley House','Ubud, Bali','A small garden retreat above the Ayung valley.',135,12),
 (2,'The Canopy Ubud','Ubud, Bali','A leafy boutique stay a short walk from the old town.',168,10),
 (3,'Machiya Kiyomizu','Kyoto, Japan','A restored wooden townhouse near the eastern lanes.',172,8),
 (4,'Haus am See','Interlaken, Switzerland','A lakeside mountain lodge with trail access.',210,9),
 (5,'Higashiyama Townhouse','Higashiyama, Japan','A restored townhouse near the pagoda lanes.',186,6);
SELECT setval(pg_get_serial_sequence('hotels','id'),(SELECT max(id) FROM hotels));
