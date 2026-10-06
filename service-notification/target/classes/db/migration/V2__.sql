-- Welcome notifications for the demo seed users.
INSERT INTO `notification` (`user_guid`, `type`, `subject`, `body`, `read_flag`, `related_entity_type`, `related_entity_id`, `dispatch_status`, `created_at`) VALUES
  ('00000000-0000-0000-0000-000000000002', 'RICH_INBOX', 'Welcome to Community Store', 'Browse products and campus activities from verified members of your community.', b'0', 'ACCOUNT', '00000000-0000-0000-0000-000000000002', 'DELIVERED_INBOX', NOW()),
  ('00000000-0000-0000-0000-000000000003', 'RICH_INBOX', 'You''re verified!', 'Your vendor account has been verified. You can now list products.', b'0', 'ACCOUNT', '00000000-0000-0000-0000-000000000003', 'DELIVERED_INBOX', NOW()),
  ('00000000-0000-0000-0000-000000000004', 'RICH_INBOX', 'You''re verified!', 'Your community leader account has been verified. You can now post activities.', b'0', 'ACCOUNT', '00000000-0000-0000-0000-000000000004', 'DELIVERED_INBOX', NOW()),
  ('00000000-0000-0000-0000-000000000005', 'RICH_INBOX', 'You''re verified!', 'Your faculty account has been verified. You can list products and post activities.', b'0', 'ACCOUNT', '00000000-0000-0000-0000-000000000005', 'DELIVERED_INBOX', NOW());
