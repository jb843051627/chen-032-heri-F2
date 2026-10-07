-- H2(MySQL 兼容模式) 测试库：只建签发作业链路触及的六张表，列与实体逐列对齐
CREATE TABLE IF NOT EXISTS t_heri_job (
  id BIGINT NOT NULL,
  job_no VARCHAR(32),
  job_name VARCHAR(64),
  status INT,
  total_rows INT,
  done_rows INT,
  fail_rows INT,
  eff_start DATETIME,
  eff_end DATETIME,
  version VARCHAR(32),
  row_no INT,
  bl_no VARCHAR(64),
  amount DECIMAL(14,2),
  job_qty INT,
  version_status INT,
  writeback_flag INT,
  compensate_flag INT,
  compensated INT,
  compensate_cnt INT,
  fail_reason VARCHAR(255),
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id),
  CONSTRAINT uk_job_head UNIQUE (job_no, row_no)
);

CREATE TABLE IF NOT EXISTS t_heri_job_row (
  id BIGINT NOT NULL,
  job_no VARCHAR(32),
  row_no INT,
  bl_no VARCHAR(64),
  amount DECIMAL(14,2),
  job_qty INT,
  version VARCHAR(32),
  version_status INT,
  writeback_flag INT,
  compensate_flag INT,
  compensated INT,
  compensate_cnt INT,
  fail_reason VARCHAR(255),
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id),
  CONSTRAINT uk_job_row UNIQUE (job_no, row_no)
);

CREATE TABLE IF NOT EXISTS t_heri_del_row (
  id BIGINT NOT NULL,
  item_no VARCHAR(64),
  due_at DATETIME,
  amount DECIMAL(14,2),
  notify_type VARCHAR(128),
  ship_name VARCHAR(128),
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_heri_adv_row (
  id BIGINT NOT NULL,
  item_no VARCHAR(64),
  biz_no VARCHAR(64),
  amount DECIMAL(14,2),
  settle_type VARCHAR(128),
  ship_name VARCHAR(128),
  stage INT,
  content VARCHAR(255),
  last_action VARCHAR(32),
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_sys_oper_log (
  id BIGINT NOT NULL,
  title VARCHAR(255),
  method VARCHAR(255),
  oper_name VARCHAR(255),
  oper_url VARCHAR(255),
  oper_param VARCHAR(255),
  error_msg VARCHAR(255),
  oper_time DATETIME,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_heri_bl_rule (
  id BIGINT NOT NULL,
  rule_code VARCHAR(32),
  rule_name VARCHAR(64),
  th1_max DECIMAL(14,2),
  th2_max DECIMAL(14,2),
  th3_max DECIMAL(14,2),
  eff_start DATETIME,
  eff_end DATETIME,
  priority INT,
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id)
);

-- 整串进门并列路：串头 / 册上行 / 门口拦回 / 箱型校验规则
CREATE TABLE IF NOT EXISTS t_heri_cus_book (
  id BIGINT NOT NULL,
  book_no VARCHAR(32),
  declared_rows INT,
  received_rows INT,
  signed_rows INT,
  rejected_rows INT,
  blocked_rows INT,
  last_row_no INT,
  status INT,
  empty_book INT DEFAULT 0,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id),
  CONSTRAINT uk_cus_book_no UNIQUE (book_no)
);

CREATE TABLE IF NOT EXISTS t_heri_cus_row (
  id BIGINT NOT NULL,
  book_no VARCHAR(32),
  row_no INT,
  bl_no VARCHAR(64),
  declare_qty INT,
  signed_qty INT,
  device_kind VARCHAR(32),
  status INT,
  reject_reason VARCHAR(255),
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id),
  CONSTRAINT uk_cus_row UNIQUE (book_no, row_no)
);

CREATE TABLE IF NOT EXISTS t_heri_cus_block (
  id BIGINT NOT NULL,
  book_no VARCHAR(32),
  block_no INT,
  bl_no VARCHAR(64),
  declare_qty INT,
  signed_qty INT,
  device_kind VARCHAR(32),
  reason VARCHAR(255),
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_heri_dev_kind_rule (
  id BIGINT NOT NULL,
  device_kind VARCHAR(32),
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id),
  CONSTRAINT uk_device_kind UNIQUE (device_kind)
);
