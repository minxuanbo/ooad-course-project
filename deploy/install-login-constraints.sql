-- Run as DBA on the project database before starting the backend.
-- Application accounts need no SUPER privilege. Never disable unique constraints.
CREATE TABLE IF NOT EXISTS login_identifiers (
  identifier VARCHAR(100) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_login_identifier_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
DELIMITER $$
CREATE TRIGGER IF NOT EXISTS users_identifier_insert AFTER INSERT ON users FOR EACH ROW BEGIN
  INSERT INTO login_identifiers(identifier,user_id) VALUES(NEW.username,NEW.id),(NEW.employee_no,NEW.id)
  ON DUPLICATE KEY UPDATE user_id=IF(user_id=NEW.id,user_id,NULL);
  IF NEW.phone IS NOT NULL THEN
    INSERT INTO login_identifiers(identifier,user_id) VALUES(NEW.phone,NEW.id)
    ON DUPLICATE KEY UPDATE user_id=IF(user_id=NEW.id,user_id,NULL);
  END IF;
END$$
CREATE TRIGGER IF NOT EXISTS users_identifier_update AFTER UPDATE ON users FOR EACH ROW BEGIN
  IF NOT(OLD.username<=>NEW.username) OR NOT(OLD.employee_no<=>NEW.employee_no) OR NOT(OLD.phone<=>NEW.phone) THEN
    DELETE FROM login_identifiers WHERE user_id=NEW.id;
    INSERT INTO login_identifiers(identifier,user_id) VALUES(NEW.username,NEW.id),(NEW.employee_no,NEW.id)
    ON DUPLICATE KEY UPDATE user_id=IF(user_id=NEW.id,user_id,NULL);
    IF NEW.phone IS NOT NULL THEN
      INSERT INTO login_identifiers(identifier,user_id) VALUES(NEW.phone,NEW.id)
      ON DUPLICATE KEY UPDATE user_id=IF(user_id=NEW.id,user_id,NULL);
    END IF;
  END IF;
END$$
DELIMITER ;
