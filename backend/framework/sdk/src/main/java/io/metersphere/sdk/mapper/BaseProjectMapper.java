package io.metersphere.sdk.mapper;

import io.metersphere.project.domain.Project;

import java.util.List;

public interface BaseProjectMapper {
    Project selectOne();

    List<Project> selectProjectByIdList(List<String> projectIds);
}
