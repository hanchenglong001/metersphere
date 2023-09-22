package io.metersphere.project.service;

import io.metersphere.plugin.platform.api.AbstractPlatformPlugin;
import io.metersphere.project.domain.ProjectApplication;
import io.metersphere.project.domain.ProjectApplicationExample;
import io.metersphere.project.job.CleanUpReportJob;
import io.metersphere.project.job.IssueSyncJob;
import io.metersphere.project.mapper.ExtProjectMapper;
import io.metersphere.project.mapper.ExtProjectUserRoleMapper;
import io.metersphere.project.mapper.ProjectApplicationMapper;
import io.metersphere.project.request.ProjectApplicationRequest;
import io.metersphere.sdk.constants.*;
import io.metersphere.sdk.dto.LogDTO;
import io.metersphere.sdk.dto.OptionDTO;
import io.metersphere.sdk.dto.SessionUser;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.domain.*;
import io.metersphere.system.log.constants.OperationLogModule;
import io.metersphere.system.log.constants.OperationLogType;
import io.metersphere.system.mapper.ExtPluginMapper;
import io.metersphere.system.mapper.PluginMapper;
import io.metersphere.system.mapper.ServiceIntegrationMapper;
import io.metersphere.system.sechedule.ScheduleService;
import io.metersphere.system.service.PluginLoadService;
import io.metersphere.system.utils.ServiceUtils;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProjectApplicationService {
    @Resource
    private ProjectApplicationMapper projectApplicationMapper;

    @Resource
    private ScheduleService scheduleService;

    @Resource
    private ExtProjectUserRoleMapper extProjectUserRoleMapper;

    @Resource
    private ServiceIntegrationMapper serviceIntegrationMapper;
    @Resource
    private ExtPluginMapper extPluginMapper;

    @Resource
    private PluginMapper pluginMapper;

    @Resource
    private PluginLoadService pluginLoadService;

    @Resource
    private ExtProjectMapper extProjectMapper;

    /**
     * 更新配置信息
     *
     * @param applications
     * @return
     */
    public void update(List<ProjectApplication> applications, String currentUser) {
        applications.forEach(application -> {
            //定时任务配置，检查是否存在定时任务配置，存在则更新，不存在则新增
            this.doBeforeUpdate(application, currentUser);
            //配置信息入库
            this.createOrUpdateConfig(application);
        });
    }

    private void createOrUpdateConfig(ProjectApplication application) {
        String type = application.getType();
        String projectId = application.getProjectId();
        ProjectApplicationExample example = new ProjectApplicationExample();
        example.createCriteria().andProjectIdEqualTo(projectId).andTypeEqualTo(type);
        if (projectApplicationMapper.countByExample(example) > 0) {
            example.clear();
            example.createCriteria().andProjectIdEqualTo(projectId).andTypeEqualTo(type);
            projectApplicationMapper.updateByExample(application, example);
        } else {
            projectApplicationMapper.insertSelective(application);
        }
    }

    private void doBeforeUpdate(ProjectApplication application, String currentUser) {
        String type = application.getType();
        //TODO 自定义id配置 &其他配置
        if (StringUtils.equals(type, ProjectApplicationType.TEST_PLAN.TEST_PLAN_CLEAN_REPORT.name())
                || StringUtils.equals(type, ProjectApplicationType.UI.UI_CLEAN_REPORT.name())
                || StringUtils.equals(type, ProjectApplicationType.PERFORMANCE_TEST.PERFORMANCE_TEST_CLEAN_REPORT.name())
                || StringUtils.equals(type, ProjectApplicationType.API.API_CLEAN_REPORT.name())) {
            //清除 测试计划/UI测试/性能测试/接口测试 报告 定时任务
            this.doHandleSchedule(application, currentUser);
        }
    }

    private void doHandleSchedule(ProjectApplication application, String currentUser) {
        String typeValue = application.getTypeValue();
        String projectId = application.getProjectId();
        Boolean enable = BooleanUtils.isTrue(Boolean.valueOf(typeValue));
        Schedule schedule = scheduleService.getScheduleByResource(application.getProjectId(), CleanUpReportJob.class.getName());
        Optional<Schedule> optional = Optional.ofNullable(schedule);
        optional.ifPresentOrElse(s -> {
            s.setEnable(enable);
            s.setCreateUser(currentUser);
            scheduleService.editSchedule(s);
            scheduleService.addOrUpdateCronJob(s,
                    CleanUpReportJob.getJobKey(projectId),
                    CleanUpReportJob.getTriggerKey(projectId),
                    CleanUpReportJob.class);
        }, () -> {
            Schedule request = new Schedule();
            request.setName("Clean Report Job");
            request.setResourceId(projectId);
            request.setKey(projectId);
            request.setProjectId(projectId);
            request.setEnable(enable);
            request.setCreateUser(currentUser);
            request.setType(ScheduleType.CRON.name());
            // 每天凌晨2点执行清理任务
            request.setValue("0 0 2 * * ?");
            request.setJob(CleanUpReportJob.class.getName());
            scheduleService.addSchedule(request);
            scheduleService.addOrUpdateCronJob(request,
                    CleanUpReportJob.getJobKey(projectId),
                    CleanUpReportJob.getTriggerKey(projectId),
                    CleanUpReportJob.class);
        });

    }


    /**
     * 获取配置信息
     *
     * @param request
     * @return
     */
    public List<ProjectApplication> get(ProjectApplicationRequest request, List<String> types) {
        ProjectApplicationExample projectApplicationExample = new ProjectApplicationExample();
        projectApplicationExample.createCriteria().andProjectIdEqualTo(request.getProjectId()).andTypeIn(types);
        List<ProjectApplication> applicationList = projectApplicationMapper.selectByExample(projectApplicationExample);
        if (CollectionUtils.isNotEmpty(applicationList)) {
            return applicationList;
        }
        return new ArrayList<ProjectApplication>();
    }


    /**
     * 获取 项目成员（脚本审核人）
     *
     * @param projectId
     * @return
     */
    public List<User> getProjectUserList(String projectId) {
        return extProjectUserRoleMapper.getProjectUserList(projectId);
    }


    /**
     * 获取平台列表
     *
     * @return
     */
    public List<OptionDTO> getPlatformOptions(String organizationId) {
        ServiceIntegrationExample example = new ServiceIntegrationExample();
        example.createCriteria().andOrganizationIdEqualTo(organizationId).andEnableEqualTo(true);
        List<ServiceIntegration> serviceIntegrations = serviceIntegrationMapper.selectByExample(example);
        List<OptionDTO> options = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(serviceIntegrations)) {
            List<String> pluginIds = serviceIntegrations.stream().map(ServiceIntegration::getPluginId).collect(Collectors.toList());
            options = extPluginMapper.selectPluginOptions(pluginIds);
            return options;
        }
        return options;
    }

    public Object getPluginScript(String pluginId) {
        this.checkResourceExist(pluginId);
        AbstractPlatformPlugin platformPlugin = (AbstractPlatformPlugin) pluginLoadService.getMsPluginManager().getPlugin(pluginId).getPlugin();
        return pluginLoadService.getPluginScriptContent(pluginId, platformPlugin.getProjectScriptId());
    }

    private Plugin checkResourceExist(String id) {
        return ServiceUtils.checkResourceExist(pluginMapper.selectByPrimaryKey(id), "permission.system_plugin.name");
    }


    /**
     * 获取所有配置信息
     *
     * @param user
     * @return
     */
    public List<ProjectApplication> getAllConfigs(SessionUser user, String projectId) {
        List<ProjectApplication> list = new ArrayList<>();
        Boolean flag = checkAdmin(user);
        ProjectApplicationExample example = new ProjectApplicationExample();
        ProjectApplicationExample.Criteria criteria = example.createCriteria();
        criteria.andProjectIdEqualTo(projectId);
        if (flag) {
            list = projectApplicationMapper.selectByExample(example);
        }
        List<String> types = checkPermission(user);
        if (CollectionUtils.isNotEmpty(types)) {
            criteria.andTypeIn(types);
            list = projectApplicationMapper.selectByExample(example);
        }
        return list;
    }

    private List<String> checkPermission(SessionUser user) {
        List<UserRolePermission> permissions = new ArrayList<>();
        user.getUserRolePermissions().forEach(g -> {
            permissions.addAll(g.getUserRolePermissions());
        });
        List<String> permissionIds = permissions.stream().map(UserRolePermission::getPermissionId).collect(Collectors.toList());

        List<String> types = new ArrayList<>();
        permissionIds.forEach(permissionId -> {
            switch (permissionId) {
                case PermissionConstants.PROJECT_APPLICATION_WORKSTATION_READ ->
                        types.addAll(Arrays.asList(ProjectApplicationType.WORKSTATION.values()).stream().map(ProjectApplicationType.WORKSTATION::name).collect(Collectors.toList()));
                case PermissionConstants.PROJECT_APPLICATION_TEST_PLAN_READ ->
                        types.addAll(Arrays.asList(ProjectApplicationType.TEST_PLAN.values()).stream().map(ProjectApplicationType.TEST_PLAN::name).collect(Collectors.toList()));
                case PermissionConstants.PROJECT_APPLICATION_ISSUE_READ ->
                        types.addAll(Arrays.asList(ProjectApplicationType.ISSUE.values()).stream().map(ProjectApplicationType.ISSUE::name).collect(Collectors.toList()));
                case PermissionConstants.PROJECT_APPLICATION_CASE_READ ->
                        types.addAll(Arrays.asList(ProjectApplicationType.CASE.values()).stream().map(ProjectApplicationType.CASE::name).collect(Collectors.toList()));
                case PermissionConstants.PROJECT_APPLICATION_API_READ ->
                        types.addAll(Arrays.asList(ProjectApplicationType.API.values()).stream().map(ProjectApplicationType.API::name).collect(Collectors.toList()));
                case PermissionConstants.PROJECT_APPLICATION_UI_READ ->
                        types.addAll(Arrays.asList(ProjectApplicationType.UI.values()).stream().map(ProjectApplicationType.UI::name).collect(Collectors.toList()));
                case PermissionConstants.PROJECT_APPLICATION_PERFORMANCE_TEST_READ ->
                        types.addAll(Arrays.asList(ProjectApplicationType.PERFORMANCE_TEST.values()).stream().map(ProjectApplicationType.PERFORMANCE_TEST::name).collect(Collectors.toList()));
                default -> {
                }
            }
        });
        return types;
    }

    private Boolean checkAdmin(SessionUser user) {
        long count = user.getUserRoles()
                .stream()
                .filter(g -> StringUtils.equalsIgnoreCase(g.getId(), InternalUserRole.ADMIN.getValue()))
                .count();

        if (count > 0) {
            return true;
        }
        return false;
    }


    /**
     * 同步缺陷配置
     *
     * @param projectId
     * @param configs
     */
    public void syncIssueConfig(String projectId, Map<String, String> configs) {
        List<ProjectApplication> issueSyncConfigs = configs.entrySet().stream().map(config -> new ProjectApplication(projectId, ProjectApplicationType.ISSUE.ISSUE_SYNC.name() + "_" + config.getKey().toUpperCase(), config.getValue())).collect(Collectors.toList());
        //处理同步缺陷定时任务配置
        doSaveOrUpdateSchedule(issueSyncConfigs, projectId);
        ProjectApplicationExample example = new ProjectApplicationExample();
        example.createCriteria().andProjectIdEqualTo(projectId).andTypeLike(ProjectApplicationType.ISSUE.ISSUE_SYNC.name() + "%");
        if (projectApplicationMapper.countByExample(example) > 0) {
            example.clear();
            example.createCriteria().andTypeLike(ProjectApplicationType.ISSUE.ISSUE_SYNC.name() + "%");
            projectApplicationMapper.deleteByExample(example);
            projectApplicationMapper.batchInsert(issueSyncConfigs);
        } else {
            projectApplicationMapper.batchInsert(issueSyncConfigs);
        }
    }

    private void doSaveOrUpdateSchedule(List<ProjectApplication> issueSyncConfigs, String projectId) {
        List<ProjectApplication> syncCron = issueSyncConfigs.stream().filter(config -> config.getType().equals(ProjectApplicationType.ISSUE.ISSUE_SYNC.name() + "_" + ProjectApplicationType.ISSUE_SYNC_CONFIG.CRON_EXPRESSION.name())).collect(Collectors.toList());
        List<ProjectApplication> syncEnable = issueSyncConfigs.stream().filter(config -> config.getType().equals(ProjectApplicationType.ISSUE.ISSUE_SYNC.name() + "_" + ProjectApplicationType.ISSUE_SYNC_CONFIG.ENABLE.name())).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(syncCron)) {
            Boolean enable = Boolean.valueOf(syncEnable.get(0).getTypeValue());
            String typeValue = syncCron.get(0).getTypeValue();
            Schedule schedule = scheduleService.getScheduleByResource(projectId, IssueSyncJob.class.getName());
            Optional<Schedule> optional = Optional.ofNullable(schedule);
            optional.ifPresentOrElse(s -> {
                s.setEnable(enable);
                s.setValue(typeValue);
                scheduleService.editSchedule(s);
                scheduleService.addOrUpdateCronJob(s,
                        IssueSyncJob.getJobKey(projectId),
                        IssueSyncJob.getTriggerKey(projectId),
                        IssueSyncJob.class);
            }, () -> {
                Schedule request = new Schedule();
                request.setName("Issue Sync Job");
                request.setResourceId(projectId);
                request.setKey(projectId);
                request.setProjectId(projectId);
                request.setEnable(enable);
                request.setCreateUser(SessionUtils.getUserId());
                request.setType(ScheduleType.CRON.name());
                // 每天凌晨2点执行清理任务
                request.setValue(typeValue);
                request.setJob(IssueSyncJob.class.getName());
                scheduleService.addSchedule(request);
                scheduleService.addOrUpdateCronJob(request,
                        IssueSyncJob.getJobKey(projectId),
                        IssueSyncJob.getTriggerKey(projectId),
                        IssueSyncJob.class);
            });
        }
    }


    /**
     * 获取同步缺陷配置
     *
     * @param projectId
     * @return
     */
    public Map<String, String> getIssueConfigInfo(String projectId) {
        ProjectApplicationExample example = new ProjectApplicationExample();
        example.createCriteria().andProjectIdEqualTo(projectId).andTypeLike(ProjectApplicationType.ISSUE.ISSUE_SYNC.name() + "_%");
        List<ProjectApplication> list = projectApplicationMapper.selectByExample(example);
        Map<String, String> collect = new HashMap<>();
        if (CollectionUtils.isNotEmpty(list)) {
            list.stream().forEach(config -> {
                collect.put(config.getType().replace(ProjectApplicationType.ISSUE.ISSUE_SYNC.name() + "_", "").toLowerCase(), config.getTypeValue());
            });
        }
        return collect;
    }


    /**
     * 测试计划 日志
     *
     * @param applications
     * @return
     */
    public List<LogDTO> updateTestPlanLog(List<ProjectApplication> applications) {
        return delLog(applications, OperationLogModule.PROJECT_PROJECT_MANAGER, "测试计划配置");
    }


    /**
     * UI 日志
     *
     * @param applications
     * @return
     */
    public List<LogDTO> updateUiLog(List<ProjectApplication> applications) {
        return delLog(applications, OperationLogModule.PROJECT_PROJECT_MANAGER, "UI配置");
    }

    /**
     * 性能测试 日志
     *
     * @param applications
     * @return
     */
    public List<LogDTO> updatePerformanceLog(List<ProjectApplication> applications) {
        return delLog(applications, OperationLogModule.PROJECT_PROJECT_MANAGER, "性能测试配置");
    }

    /**
     * 接口测试 日志
     *
     * @param applications
     * @return
     */
    public List<LogDTO> updateApiLog(List<ProjectApplication> applications) {
        return delLog(applications, OperationLogModule.PROJECT_PROJECT_MANAGER, "接口测试配置");
    }


    /**
     * 用例管理 日志
     *
     * @param applications
     * @return
     */
    public List<LogDTO> updateCaseLog(List<ProjectApplication> applications) {
        return delLog(applications, OperationLogModule.PROJECT_PROJECT_MANAGER, "用例管理配置");
    }

    /**
     * 工作台 日志
     *
     * @param applications
     * @return
     */
    public List<LogDTO> updateWorkstationLog(List<ProjectApplication> applications) {
        return delLog(applications, OperationLogModule.PROJECT_PROJECT_MANAGER, "工作台配置");
    }

    private List<LogDTO> delLog(List<ProjectApplication> applications, String module, String content) {
        List<LogDTO> logs = new ArrayList<>();
        applications.forEach(application -> {
            ProjectApplicationExample example = new ProjectApplicationExample();
            example.createCriteria().andTypeEqualTo(application.getType()).andProjectIdEqualTo(application.getProjectId());
            List<ProjectApplication> list = projectApplicationMapper.selectByExample(example);
            LogDTO dto = new LogDTO(
                    application.getProjectId(),
                    "",
                    OperationLogConstants.SYSTEM,
                    null,
                    OperationLogType.UPDATE.name(),
                    module,
                    content);
            dto.setOriginalValue(JSON.toJSONBytes(list));
            logs.add(dto);
        });
        return logs;
    }


    /**
     * 缺陷同步配置 日志
     *
     * @param projectId
     * @param configs
     * @return
     */
    public LogDTO updateIssueSyncLog(String projectId, Map<String, String> configs) {
        Map<String, String> originConfig = getIssueConfigInfo(projectId);
        LogDTO dto = new LogDTO(
                projectId,
                "",
                OperationLogConstants.SYSTEM,
                null,
                OperationLogType.UPDATE.name(),
                OperationLogModule.PROJECT_PROJECT_MANAGER,
                "同步缺陷");
        dto.setModifiedValue(JSON.toJSONBytes(configs));
        dto.setOriginalValue(JSON.toJSONBytes(originConfig));
        return dto;
    }


    /**
     * 关联需求配置 日志
     * @param projectId
     * @param configs
     * @return
     */
    public LogDTO updateRelatedRequirementsLog(String projectId, Map<String, String> configs) {
        Map<String, String> originConfig = getRelatedConfigInfo(projectId);
        LogDTO dto = new LogDTO(
                projectId,
                "",
                OperationLogConstants.SYSTEM,
                null,
                OperationLogType.UPDATE.name(),
                OperationLogModule.PROJECT_PROJECT_MANAGER,
                "关联需求");
        dto.setModifiedValue(JSON.toJSONBytes(configs));
        dto.setOriginalValue(JSON.toJSONBytes(originConfig));
        return dto;
    }



    /**
     * 获取菜单列表
     *
     * @param projectId
     * @return
     */
    public Map<String, Boolean> getModuleSetting(String projectId) {
        String moduleSetting = extProjectMapper.getModuleSetting(projectId);
        Map<String, Boolean> moudleMap = new HashMap<>();
        if (StringUtils.isNotEmpty(moduleSetting)) {
            ProjectApplicationExample example = new ProjectApplicationExample();
            JSON.parseArray(moduleSetting).forEach(module -> {
                example.clear();
                example.createCriteria().andTypeEqualTo(String.valueOf(module)).andProjectIdEqualTo(projectId);
                List<ProjectApplication> applications = projectApplicationMapper.selectByExample(example);
                if (CollectionUtils.isNotEmpty(applications)) {
                    moudleMap.put(String.valueOf(module), Boolean.valueOf(applications.get(0).getTypeValue()));
                } else {
                    moudleMap.put(String.valueOf(module), Boolean.TRUE);
                }
            });
        }
        return moudleMap;
    }


    /**
     * 用例关联需求配置
     *
     * @param projectId
     * @param configs
     */
    public void updateRelated(String projectId, Map<String, String> configs) {
        List<ProjectApplication> relatedConfigs = configs.entrySet().stream().map(config -> new ProjectApplication(projectId, ProjectApplicationType.CASE_RELATED_CONFIG.CASE_RELATED.name() + "_" + config.getKey().toUpperCase(), config.getValue())).collect(Collectors.toList());
        ProjectApplicationExample example = new ProjectApplicationExample();
        example.createCriteria().andProjectIdEqualTo(projectId).andTypeLike(ProjectApplicationType.CASE_RELATED_CONFIG.CASE_RELATED.name() + "%");
        if (projectApplicationMapper.countByExample(example) > 0) {
            example.clear();
            example.createCriteria().andTypeLike(ProjectApplicationType.CASE_RELATED_CONFIG.CASE_RELATED.name() + "%");
            projectApplicationMapper.deleteByExample(example);
            projectApplicationMapper.batchInsert(relatedConfigs);
        } else {
            projectApplicationMapper.batchInsert(relatedConfigs);
        }
    }

    /**
     * 获取用例关联需求配置
     *
     * @param projectId
     * @return
     */
    public Map<String, String> getRelatedConfigInfo(String projectId) {
        ProjectApplicationExample example = new ProjectApplicationExample();
        example.createCriteria().andProjectIdEqualTo(projectId).andTypeLike(ProjectApplicationType.CASE_RELATED_CONFIG.CASE_RELATED.name() + "_%");
        List<ProjectApplication> list = projectApplicationMapper.selectByExample(example);
        Map<String, String> collect = new HashMap<>();
        if (CollectionUtils.isNotEmpty(list)) {
            list.stream().forEach(config -> {
                collect.put(config.getType().replace(ProjectApplicationType.CASE_RELATED_CONFIG.CASE_RELATED.name() + "_", "").toLowerCase(), config.getTypeValue());
            });
        }
        return collect;
    }
}
