-- Demo seed users (credentials are seeded by server-auth with the same guids).
INSERT INTO `user` (`guid`, `email`, `first_name`, `last_name`, `user_type`, `status`, `verification_status`, `created_at`, `updated_at`, `version`) VALUES
  ('00000000-0000-0000-0000-000000000001', 'admin@community.store', 'Ada', 'Admin', NULL, 'ACTIVE', 'VERIFIED', NOW(), NOW(), 0),
  ('00000000-0000-0000-0000-000000000002', 'student@uni.ac.za', 'Sipho', 'Student', 'STUDENT', 'ACTIVE', 'VERIFIED', NOW(), NOW(), 0),
  ('00000000-0000-0000-0000-000000000003', 'vendor@community.store', 'Vusi', 'Vendor', 'VENDOR', 'ACTIVE', 'VERIFIED', NOW(), NOW(), 0),
  ('00000000-0000-0000-0000-000000000004', 'leader@community.store', 'Lerato', 'Leader', 'COMMUNITY_LEADER', 'ACTIVE', 'VERIFIED', NOW(), NOW(), 0),
  ('00000000-0000-0000-0000-000000000005', 'faculty@uni.ac.za', 'Thandi', 'Nkosi', 'FACULTY', 'ACTIVE', 'VERIFIED', NOW(), NOW(), 0),
  ('00000000-0000-0000-0000-000000000006', 'pending.vendor@community.store', 'Johan', 'Dlamini', 'VENDOR', 'ACTIVE', 'PENDING_REVIEW', NOW(), NOW(), 0);

INSERT INTO `user_role` (`user_guid`, `role`, `created_at`) VALUES
  ('00000000-0000-0000-0000-000000000001', 'ADMIN', NOW()),
  ('00000000-0000-0000-0000-000000000002', 'STUDENT', NOW()),
  ('00000000-0000-0000-0000-000000000003', 'VENDOR', NOW()),
  ('00000000-0000-0000-0000-000000000004', 'COMMUNITY_LEADER', NOW()),
  ('00000000-0000-0000-0000-000000000005', 'FACULTY', NOW()),
  ('00000000-0000-0000-0000-000000000006', 'VENDOR', NOW());

-- Account numbers are AES-256-GCM encrypted with the PoC development key in application.yml.
INSERT INTO `user_banking_detail` (`user_guid`, `account_holder_name`, `bank_name`, `account_number_encrypted`, `branch_code`, `account_type`, `verified`) VALUES
  ('00000000-0000-0000-0000-000000000003', 'Vusi Vendor', 'First National Bank', 'mh/e4UsNPJjiL5c/w+Jk0KS4KVJUKEJr1DfIqyXoxt0f+HktEJWo', '250655', 'CHEQUE', b'1'),
  ('00000000-0000-0000-0000-000000000005', 'Thandi Nkosi', 'Standard Bank', '/vrQ7X7/UhNwEY9YEqLHLlgxirmt+zj0M3zKdZhLlt3xKe3XF84s', '051001', 'SAVINGS', b'1');

-- Matches the two seeded orders in service-product (one FUNDS_RELEASED R150, one SHIPPED R300).
INSERT INTO `user_payout_summary` (`user_guid`, `total_sales_count`, `total_sales_amount`, `total_paid_out_amount`, `total_pending_payout_amount`, `last_payout_at`) VALUES
  ('00000000-0000-0000-0000-000000000003', 2, 450.00, 150.00, 300.00, NOW());
