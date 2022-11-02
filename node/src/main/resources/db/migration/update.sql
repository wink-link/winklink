DROP TABLE IF EXISTS `heads`;
CREATE TABLE `heads`  (
  `id` INT UNSIGNED AUTO_INCREMENT,
  `address` varchar(128) NOT NULL,
  `hash` varchar(255) NOT NULL ,
  `number` BIGINT NOT NULL,
  `parent_hash` varchar(255) NOT NULL,
  `block_timestamp` BIGINT NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  index `idx_heads_created_at` (`created_at`),
  unique index `heads_address_key` (`address`),
  index `idx_heads_address` (`address`)
) ENGINE = InnoDB default charset=utf8;


ALTER TABLE `txes` ADD `task_run_id` varchar(36) AFTER `id`;
ALTER TABLE `job_runs` MODIFY `result` text;
ALTER TABLE `task_runs` MODIFY `result` text;
ALTER TABLE `txes` MODIFY `confirmed` tinyint DEFAULT 0
COMMENT '0:init 101:unstarted 102:inprogress 103:fatalerror 104:outofenergy 105:confirmed';
ALTER TABLE `txes` ADD INDEX `idx_confirmed` (`confirmed`);
ALTER TABLE `txes` ADD INDEX `idx_sent_at` (`sent_at`);

CREATE TABLE `demo`  (
  `id` INT UNSIGNED AUTO_INCREMENT,
  `key` varchar(255) NOT NULL ,
  `value` varchar(255) NOT NULL ,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE = InnoDB default charset=utf8;