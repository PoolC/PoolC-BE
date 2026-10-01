ALTER TABLE member
    ADD COLUMN IF NOT EXISTS admin_remarks varchar(1000);
