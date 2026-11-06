-- Optional title is separate from the guest's given name. Existing profiles remain unspecified.
ALTER TABLE guest
  ADD COLUMN title VARCHAR(10) NULL AFTER guest_id,
  ADD CONSTRAINT ck_guest_title CHECK (title IS NULL OR title IN ('นาย','นาง','นางสาว'));
