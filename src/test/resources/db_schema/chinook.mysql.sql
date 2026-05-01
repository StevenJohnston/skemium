CREATE TABLE album
(
    album_id  INT NOT NULL AUTO_INCREMENT,
    title     VARCHAR(160) NOT NULL,
    artist_id INT          NOT NULL,
    PRIMARY KEY (album_id)
);

CREATE TABLE artist
(
    artist_id INT NOT NULL AUTO_INCREMENT,
    name      VARCHAR(120),
    PRIMARY KEY (artist_id)
);

CREATE TABLE customer
(
    customer_id    INT         NOT NULL AUTO_INCREMENT,
    first_name     VARCHAR(40) NOT NULL,
    last_name      VARCHAR(20) NOT NULL,
    company        VARCHAR(80),
    address        VARCHAR(70),
    city           VARCHAR(40),
    state          VARCHAR(40),
    country        VARCHAR(40),
    postal_code    VARCHAR(10),
    phone          VARCHAR(24),
    fax            VARCHAR(24),
    email          VARCHAR(60) NOT NULL,
    support_rep_id INT,
    PRIMARY KEY (customer_id)
);

CREATE TABLE employee
(
    employee_id INT         NOT NULL AUTO_INCREMENT,
    last_name   VARCHAR(20) NOT NULL,
    first_name  VARCHAR(20) NOT NULL,
    title       VARCHAR(30),
    reports_to  INT,
    birth_date  DATETIME,
    hire_date   DATETIME,
    address     VARCHAR(70),
    city        VARCHAR(40),
    state       VARCHAR(40),
    country     VARCHAR(40),
    postal_code VARCHAR(10),
    phone       VARCHAR(24),
    fax         VARCHAR(24),
    email       VARCHAR(60),
    PRIMARY KEY (employee_id)
);

CREATE TABLE genre
(
    genre_id INT NOT NULL AUTO_INCREMENT,
    name     VARCHAR(120),
    PRIMARY KEY (genre_id)
);

CREATE TABLE invoice
(
    invoice_id          INT            NOT NULL AUTO_INCREMENT,
    customer_id         INT            NOT NULL,
    invoice_date        DATETIME       NOT NULL,
    billing_address     VARCHAR(70),
    billing_city        VARCHAR(40),
    billing_state       VARCHAR(40),
    billing_country     VARCHAR(40),
    billing_postal_code VARCHAR(10),
    total               DECIMAL(10, 2) NOT NULL,
    PRIMARY KEY (invoice_id)
);

CREATE TABLE playlist
(
    playlist_id INT NOT NULL AUTO_INCREMENT,
    name        VARCHAR(120),
    PRIMARY KEY (playlist_id)
);

CREATE TABLE track
(
    track_id      INT            NOT NULL AUTO_INCREMENT,
    name          VARCHAR(200)   NOT NULL,
    album_id      INT,
    media_type_id INT            NOT NULL,
    genre_id      INT,
    composer      VARCHAR(220),
    milliseconds  INT            NOT NULL,
    bytes         INT,
    unit_price    DECIMAL(10, 2) NOT NULL,
    PRIMARY KEY (track_id)
);
