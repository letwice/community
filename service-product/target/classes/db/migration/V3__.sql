-- Community events now need admin approval before they reach the bulletin.
ALTER TABLE `community_activity`
  MODIFY COLUMN `status` ENUM('DRAFT','PENDING_APPROVAL','PUBLISHED','REJECTED','CANCELLED','COMPLETED') NOT NULL;
