ALTER TABLE session
    ADD COLUMN IF NOT EXISTS qr_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS session_qr_token (
    token VARCHAR(64) PRIMARY KEY,
    session_id BIGINT NOT NULL UNIQUE,
    CONSTRAINT fk_session_qr_token_session
        FOREIGN KEY (session_id) REFERENCES session(id) ON DELETE CASCADE
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM attendance
        GROUP BY session_id, member_loginid
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot add session attendance uniqueness: duplicate rows exist.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'attendance'::regclass
          AND contype = 'u'
          AND pg_get_constraintdef(oid) = 'UNIQUE (session_id, member_loginid)'
    ) THEN
        ALTER TABLE attendance
            ADD CONSTRAINT uk_session_attendance UNIQUE (session_id, member_loginid);
    END IF;
END $$;
