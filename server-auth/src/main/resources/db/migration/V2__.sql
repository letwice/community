-- Demo seed credentials. Every seeded account uses the test password documented in the README.
-- User rows with the same guids are seeded by service-user.
INSERT INTO `auth_credential` (`user_guid`, `password_hash`, `failed_login_attempts`) VALUES
  ('00000000-0000-0000-0000-000000000001', '$2a$10$Lei4oe8GH55cdyNWEp7FReNvJZ7ElFrBwrfuSc5nvKWkNxhBeHzqu', 0),
  ('00000000-0000-0000-0000-000000000002', '$2a$10$Lei4oe8GH55cdyNWEp7FReNvJZ7ElFrBwrfuSc5nvKWkNxhBeHzqu', 0),
  ('00000000-0000-0000-0000-000000000003', '$2a$10$Lei4oe8GH55cdyNWEp7FReNvJZ7ElFrBwrfuSc5nvKWkNxhBeHzqu', 0),
  ('00000000-0000-0000-0000-000000000004', '$2a$10$Lei4oe8GH55cdyNWEp7FReNvJZ7ElFrBwrfuSc5nvKWkNxhBeHzqu', 0),
  ('00000000-0000-0000-0000-000000000005', '$2a$10$Lei4oe8GH55cdyNWEp7FReNvJZ7ElFrBwrfuSc5nvKWkNxhBeHzqu', 0),
  ('00000000-0000-0000-0000-000000000006', '$2a$10$Lei4oe8GH55cdyNWEp7FReNvJZ7ElFrBwrfuSc5nvKWkNxhBeHzqu', 0);
