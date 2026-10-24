CREATE INDEX "IDX_NOTIFICATIONS_RECEIVER_CREATED_ID"
    ON "notifications" ("receiver_id", "created_at", "id");
