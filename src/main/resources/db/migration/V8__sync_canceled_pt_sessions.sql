UPDATE pt_sessions
SET phase = 'CANCELED'
WHERE phase <> 'CANCELED'
  AND EXISTS (
    SELECT 1
    FROM sessions s
    WHERE s.id = pt_sessions.session_id
      AND s.status = 'CANCELED'
);