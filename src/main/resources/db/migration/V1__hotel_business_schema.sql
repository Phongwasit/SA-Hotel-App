-- Approved 2026-11-04 model: 16 business tables / 83 stored fields.

-- Flyway's own schema-history table is technical migration metadata.

-- Cross-row full-night/method sums require Controller transaction validation.

CREATE TABLE employee (
  employee_id INTEGER NOT NULL AUTO_INCREMENT,
  first_name VARCHAR(100) NOT NULL,
  last_name VARCHAR(100) NOT NULL,
  position VARCHAR(50) NOT NULL,
  username VARCHAR(50) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  PRIMARY KEY (employee_id),
  CONSTRAINT uq_employee_1 UNIQUE (username),
  CONSTRAINT ck_employee_1 CHECK (CHAR_LENGTH(TRIM(username)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE room_type (
  type_name VARCHAR(50) NOT NULL,
  standard_nightly_rate DECIMAL(12,2) NOT NULL,
  amenities TEXT NULL,
  PRIMARY KEY (type_name),
  CONSTRAINT ck_room_type_1 CHECK (standard_nightly_rate > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE guest (
  guest_id INTEGER NOT NULL AUTO_INCREMENT,
  first_name VARCHAR(100) NOT NULL,
  last_name VARCHAR(100) NOT NULL,
  card_type VARCHAR(20) NOT NULL,
  official_id VARCHAR(50) NOT NULL,
  phone VARCHAR(30) NULL,
  PRIMARY KEY (guest_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE document_recipient (
  recipient_id INTEGER NOT NULL AUTO_INCREMENT,
  recipient_type VARCHAR(20) NOT NULL,
  recipient_name VARCHAR(255) NOT NULL,
  tax_id VARCHAR(30) NULL,
  address TEXT NULL,
  PRIMARY KEY (recipient_id),
  CONSTRAINT ck_document_recipient_1 CHECK (recipient_type IN ('PERSON','ORGANIZATION')),
  CONSTRAINT ck_document_recipient_2 CHECK (CHAR_LENGTH(TRIM(recipient_name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE booking (
  booking_id INTEGER NOT NULL AUTO_INCREMENT,
  booker_name VARCHAR(200) NOT NULL,
  phone VARCHAR(30) NOT NULL,
  staff_id INTEGER NOT NULL,
  created_at TIMESTAMP NOT NULL,
  PRIMARY KEY (booking_id),
  CONSTRAINT fk_booking_staff_id FOREIGN KEY (staff_id) REFERENCES employee(employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE room (
  room_id INTEGER NOT NULL AUTO_INCREMENT,
  room_number VARCHAR(20) NOT NULL,
  type_name VARCHAR(50) NOT NULL,
  bed_type VARCHAR(50) NOT NULL,
  occupancy VARCHAR(20) NOT NULL,
  cleanliness VARCHAR(20) NOT NULL,
  usability VARCHAR(20) NOT NULL,
  condition_reason VARCHAR(500) NULL,
  PRIMARY KEY (room_id),
  CONSTRAINT uq_room_1 UNIQUE (room_number),
  CONSTRAINT fk_room_type_name FOREIGN KEY (type_name) REFERENCES room_type(type_name),
  CONSTRAINT ck_room_1 CHECK (bed_type IN ('DOUBLE','TWIN')),
  CONSTRAINT ck_room_2 CHECK (occupancy IN ('VACANT','OCCUPIED')),
  CONSTRAINT ck_room_3 CHECK (cleanliness IN ('CLEAN','DIRTY')),
  CONSTRAINT ck_room_4 CHECK (usability IN ('USABLE','OUT_OF_ORDER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE receipt_reference (
  reference_id INTEGER NOT NULL AUTO_INCREMENT,
  recipient_id INTEGER NOT NULL,
  book_number VARCHAR(30) NOT NULL,
  receipt_number VARCHAR(30) NOT NULL,
  receipt_date DATE NOT NULL,
  PRIMARY KEY (reference_id),
  CONSTRAINT uq_receipt_reference_1 UNIQUE (book_number,receipt_number),
  CONSTRAINT fk_receipt_reference_recipient_id FOREIGN KEY (recipient_id) REFERENCES document_recipient(recipient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payment (
  payment_id INTEGER NOT NULL AUTO_INCREMENT,
  staff_id INTEGER NOT NULL,
  received_at TIMESTAMP NOT NULL,
  evidence TEXT NULL,
  PRIMARY KEY (payment_id),
  CONSTRAINT fk_payment_staff_id FOREIGN KEY (staff_id) REFERENCES employee(employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE booking_history (
  history_id INTEGER NOT NULL AUTO_INCREMENT,
  booking_id INTEGER NOT NULL,
  changed_at TIMESTAMP NOT NULL,
  action VARCHAR(20) NOT NULL,
  previous_details TEXT NOT NULL,
  PRIMARY KEY (history_id),
  CONSTRAINT fk_booking_history_booking_id FOREIGN KEY (booking_id) REFERENCES booking(booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE booking_item (
  booking_item_id INTEGER NOT NULL AUTO_INCREMENT,
  booking_id INTEGER NOT NULL,
  room_id INTEGER NOT NULL,
  check_in_date DATE NOT NULL,
  check_out_date DATE NOT NULL,
  status VARCHAR(20) NOT NULL,
  PRIMARY KEY (booking_item_id),
  CONSTRAINT fk_booking_item_booking_id FOREIGN KEY (booking_id) REFERENCES booking(booking_id),
  CONSTRAINT fk_booking_item_room_id FOREIGN KEY (room_id) REFERENCES room(room_id),
  CONSTRAINT ck_booking_item_1 CHECK (check_in_date < check_out_date),
  CONSTRAINT ck_booking_item_2 CHECK (status IN ('BOOKED','CANCELLED','CHECKED_IN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payment_method (
  payment_method_id INTEGER NOT NULL AUTO_INCREMENT,
  payment_id INTEGER NOT NULL,
  method VARCHAR(20) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  PRIMARY KEY (payment_method_id),
  CONSTRAINT uq_payment_method_1 UNIQUE (payment_method_id,payment_id),
  CONSTRAINT fk_payment_method_payment_id FOREIGN KEY (payment_id) REFERENCES payment(payment_id),
  CONSTRAINT ck_payment_method_1 CHECK (amount > 0),
  CONSTRAINT ck_payment_method_2 CHECK (method IN ('CASH','TRANSFER','CARD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE receipt_payment (
  reference_id INTEGER NOT NULL,
  payment_id INTEGER NOT NULL,
  PRIMARY KEY (reference_id,payment_id),
  CONSTRAINT fk_receipt_payment_reference_id FOREIGN KEY (reference_id) REFERENCES receipt_reference(reference_id),
  CONSTRAINT fk_receipt_payment_payment_id FOREIGN KEY (payment_id) REFERENCES payment(payment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE stay (
  stay_id INTEGER NOT NULL AUTO_INCREMENT,
  room_id INTEGER NOT NULL,
  guest_id INTEGER NOT NULL,
  staff_id INTEGER NOT NULL,
  booking_item_id INTEGER NULL,
  default_recipient_id INTEGER NULL,
  check_in_date DATE NOT NULL,
  expected_check_out_date DATE NOT NULL,
  actual_check_in TIMESTAMP NOT NULL,
  actual_check_out TIMESTAMP NULL,
  key_count INTEGER NOT NULL,
  status VARCHAR(20) NOT NULL,
  PRIMARY KEY (stay_id),
  CONSTRAINT uq_stay_1 UNIQUE (booking_item_id),
  CONSTRAINT fk_stay_room_id FOREIGN KEY (room_id) REFERENCES room(room_id),
  CONSTRAINT fk_stay_guest_id FOREIGN KEY (guest_id) REFERENCES guest(guest_id),
  CONSTRAINT fk_stay_staff_id FOREIGN KEY (staff_id) REFERENCES employee(employee_id),
  CONSTRAINT fk_stay_booking_item_id FOREIGN KEY (booking_item_id) REFERENCES booking_item(booking_item_id),
  CONSTRAINT fk_stay_default_recipient_id FOREIGN KEY (default_recipient_id) REFERENCES document_recipient(recipient_id),
  CONSTRAINT ck_stay_1 CHECK (check_in_date < expected_check_out_date),
  CONSTRAINT ck_stay_2 CHECK (key_count BETWEEN 1 AND 2),
  CONSTRAINT ck_stay_3 CHECK (status IN ('ACTIVE','CHECKED_OUT')),
  CONSTRAINT ck_stay_4 CHECK (actual_check_out IS NULL OR actual_check_out >= actual_check_in),
  CONSTRAINT ck_stay_5 CHECK ((status='ACTIVE' AND actual_check_out IS NULL) OR (status='CHECKED_OUT' AND actual_check_out IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE stay_charge (
  charge_id INTEGER NOT NULL AUTO_INCREMENT,
  stay_id INTEGER NOT NULL,
  charge_date DATE NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  PRIMARY KEY (charge_id),
  CONSTRAINT uq_stay_charge_1 UNIQUE (stay_id,charge_date),
  CONSTRAINT uq_stay_charge_2 UNIQUE (charge_id,stay_id),
  CONSTRAINT fk_stay_charge_stay_id FOREIGN KEY (stay_id) REFERENCES stay(stay_id),
  CONSTRAINT ck_stay_charge_1 CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE receipt_stay (
  reference_id INTEGER NOT NULL,
  stay_id INTEGER NOT NULL,
  PRIMARY KEY (reference_id,stay_id),
  CONSTRAINT fk_receipt_stay_reference_id FOREIGN KEY (reference_id) REFERENCES receipt_reference(reference_id),
  CONSTRAINT fk_receipt_stay_stay_id FOREIGN KEY (stay_id) REFERENCES stay(stay_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payment_allocation (
  allocation_id INTEGER NOT NULL AUTO_INCREMENT,
  payment_id INTEGER NOT NULL,
  stay_id INTEGER NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  charge_id INTEGER NOT NULL,
  payment_method_id INTEGER NOT NULL,
  PRIMARY KEY (allocation_id),
  CONSTRAINT uq_payment_allocation_1 UNIQUE (charge_id),
  CONSTRAINT fk_payment_allocation_payment_id FOREIGN KEY (payment_id) REFERENCES payment(payment_id),
  CONSTRAINT fk_payment_allocation_stay_id FOREIGN KEY (stay_id) REFERENCES stay(stay_id),
  CONSTRAINT fk_payment_allocation_paired_1 FOREIGN KEY (charge_id,stay_id) REFERENCES stay_charge(charge_id,stay_id),
  CONSTRAINT fk_payment_allocation_paired_2 FOREIGN KEY (payment_method_id,payment_id) REFERENCES payment_method(payment_method_id,payment_id),
  CONSTRAINT ck_payment_allocation_1 CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_booking_item_availability ON booking_item(room_id,status,check_in_date,check_out_date);

CREATE INDEX ix_stay_availability ON stay(room_id,status,check_in_date,expected_check_out_date);
