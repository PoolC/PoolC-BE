package org.poolc.api.project.service;

import lombok.RequiredArgsConstructor;
import org.poolc.api.member.service.MemberService;
import org.poolc.api.project.domain.Project;
import org.poolc.api.project.domain.ProjectCategory;
import org.poolc.api.project.repository.ProjectRepository;
import org.poolc.api.project.vo.ProjectCreateValues;
import org.poolc.api.project.vo.ProjectUpdateValues;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final MemberService memberService;
    private final GameBuildUploadService gameBuildUploadService;

    @Transactional
    public void createProject(ProjectCreateValues values) {
        validateGameUrl(values.getGameURL());
        checkMembersExist(values.getMemberLoginIDs());

        Project project = new Project(values.getName(), values.getDescription(), values.getGenre(), values.getCategory(), values.getStartDate(), values.getEndDate(), values.getThumbnailURL(), values.getBody(), values.getGameURL());
        project.setMembers(values.getMemberLoginIDs());

        projectRepository.save(project);
    }

    public Project findOne(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException("해당하는 프로젝트가 없습니다."));
    }

    public List<Project> findProjects(ProjectCategory category) {
        return category == null ? projectRepository.findAllByOrderByCreatedAtDesc() : projectRepository.findByCategoryOrderByCreatedAtDesc(category);
    }

    public List<Project> findProjectsByProjectMembers(String loginId) {
        return projectRepository.findProjectsByProjectMembers(loginId);
    }

    @Transactional
    public void updateProject(ProjectUpdateValues values, Long projectID) {
        validateGameUrl(values.getGameURL());
        Project project = findOne(projectID);
        checkMembersExist(values.getMemberLoginIDs());
        project.update(values);
    }

    @Transactional
    public void deleteProject(Long projectId) {
        projectRepository.delete(findOne(projectId));
    }

    private void checkMembersExist(List<String> memberLoginIDs) {
        memberLoginIDs.forEach(memberService::getMemberByLoginID);
    }

    private void validateGameUrl(String gameURL) {
        if (!gameBuildUploadService.isAllowedGameUrl(gameURL)) {
            throw new IllegalArgumentException("게임 주소는 설정된 게임 도메인의 index.html이어야 합니다.");
        }
    }
}
