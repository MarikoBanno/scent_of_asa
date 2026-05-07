CREATE TABLE IF NOT EXISTS store_holidays (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  holiday_date DATE NOT NULL,
  holiday_type VARCHAR(30) NOT NULL DEFAULT 'CLOSED',
  reason VARCHAR(255) NULL,
  applies_to_language VARCHAR(30) NULL,
  created_by_staff_id BIGINT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT uk_store_holidays_date_lang UNIQUE (holiday_date, applies_to_language)
);
