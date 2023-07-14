-- 模拟数据
INSERT INTO organization (id, num, name, description, create_user, update_user, create_time, update_time) VALUES ('organization_id_001', 100010, '测试日志组织', '测试日志的组织', 'admin', 'admin', unix_timestamp() * 1000, unix_timestamp() * 1000);

INSERT INTO project (id, num, organization_id, name, description, create_user, update_user, create_time, update_time) VALUES ('project_id_001', 100010, 'organization_id_001', '测试日志项目', '测试日志的项目', 'admin', 'admin', unix_timestamp() * 1000, unix_timestamp() * 1000);


-- 初始化日志记录
INSERT INTO operation_log(`id`, `project_id`, `organization_id`, `create_time`, `create_user`, `source_id`, `method`, `type`, `module`, `content`, `path`) VALUES (uuid(), 'system', '', 1689141859000, 'admin', '1', 'post', 'add', 'SYSTEM_PARAMETER_SETTING', '认证配置', '/system/authsource/add');
INSERT INTO operation_log(`id`, `project_id`, `organization_id`, `create_time`, `create_user`, `source_id`, `method`, `type`, `module`, `content`, `path`) VALUES (uuid(), '', 'organization_id_001', 1689141859000, 'admin', '1', 'post', 'add', 'SYSTEM_PARAMETER_SETTING', '认证配置', '/system/authsource/add');
INSERT INTO operation_log(`id`, `project_id`, `organization_id`, `create_time`, `create_user`, `source_id`, `method`, `type`, `module`, `content`, `path`) VALUES (uuid(), 'project_id_001', 'organization_id_001', 1689141859000, 'admin', '1', 'post', 'add', 'SYSTEM_PARAMETER_SETTING', '认证配置', '/system/authsource/add');

