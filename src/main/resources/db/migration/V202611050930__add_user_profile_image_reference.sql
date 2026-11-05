ALTER TABLE users
    ADD COLUMN profile_image_id UUID;

-- 하나의 파일을 여러 사용자의 프로필 이미지로 공유하지 않는다.
ALTER TABLE users
    ADD CONSTRAINT uq_users_profile_image
        UNIQUE (profile_image_id);

-- 파일 정보가 존재하는 경우에만 사용자 프로필로 연결할 수 있다.
ALTER TABLE users
    ADD CONSTRAINT fk_users_profile_image
        FOREIGN KEY (profile_image_id)
        REFERENCES file_infos (id);

COMMENT ON COLUMN users.profile_image_id IS '프로필 이미지 파일 ID';