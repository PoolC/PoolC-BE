package org.poolc.api.member.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import org.poolc.api.activity.dto.ActivityResponse;
import org.poolc.api.badge.domain.Badge;
import org.poolc.api.member.domain.Member;
import org.poolc.api.member.domain.MemberRole;
import org.poolc.api.project.dto.ProjectResponse;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class MemberResponse implements Serializable {
    private final String loginID;
    private final String email;
    private final String phoneNumber;
    private final String name;
    private final String department;
    private final String studentID;
    private final String profileImageURL;
    private final String introduction;
    private final String adminRemarks;
    private final String baseRole;
    private final List<String> additionalRoles;
    private final Boolean isActivated;
    private final Boolean isAdmin;
    private final Boolean isExcepted;
    private final List<ActivityResponse> hostActivities;
    private final List<ActivityResponse> participantActivities;
    private final List<ProjectResponse> projects;
    private String role;
    private final Badge badge;

    @JsonCreator
    public MemberResponse(String loginID, String email, String phoneNumber, String name, String department, String studentID, String profileImageURL, String introduction, Boolean isActivated, Boolean isAdmin, Boolean isExcepted, List<ActivityResponse> hostActivities, List<ActivityResponse> participantActivities, List<ProjectResponse> projects, String role, Badge badge, String adminRemarks, String baseRole, List<String> additionalRoles) {
        this.loginID = loginID;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.name = name;
        this.department = department;
        this.studentID = studentID;
        this.profileImageURL = profileImageURL;
        this.introduction = introduction;
        this.adminRemarks = adminRemarks;
        this.baseRole = baseRole;
        this.additionalRoles = additionalRoles;
        this.isActivated = isActivated;
        this.isAdmin = isAdmin;
        this.isExcepted = isExcepted;
        this.hostActivities = hostActivities;
        this.participantActivities = participantActivities;
        this.projects = projects;
        this.role = role;
        this.badge = badge;
    }

    public MemberResponse(Member member, boolean isAdmin) {
        if (isAdmin) {
            this.loginID = member.getLoginID();
            this.email = null;
            this.phoneNumber = member.getPhoneNumber();
            this.name = member.getName();
            this.department = member.getDepartment();
            this.studentID = member.getStudentID();
            this.profileImageURL = null;
            this.introduction = null;
            this.adminRemarks = null;
            this.baseRole = member.getBaseRole();
            this.additionalRoles = getAdditionalRoleNames(member.getAdditionalRoles());
            this.isActivated = null;
            this.isAdmin = null;
            this.isExcepted = null;
            this.hostActivities = null;
            this.participantActivities = null;
            this.projects = null;
            this.role = null;
            this.badge = member.getBadge();
        } else {
            this.loginID = member.getLoginID();
            this.email = null;
            this.phoneNumber = null;
            this.name = null;
            this.department = null;
            this.studentID = null;
            this.profileImageURL = null;
            this.introduction = null;
            this.adminRemarks = null;
            this.baseRole = member.getBaseRole();
            this.additionalRoles = getAdditionalRoleNames(member.getAdditionalRoles());
            this.isActivated = null;
            this.isAdmin = null;
            this.isExcepted = null;
            this.hostActivities = null;
            this.participantActivities = null;
            this.projects = null;
            this.role = null;
            this.badge = null;
        }

    }

    public static MemberResponse of(Member member) {
        return new MemberResponse(member.getLoginID(), member.getEmail(), member.getPhoneNumber(), member.getName(), member.getDepartment(), member.getStudentID(), null, member.getIntroduction(), member.isMember(), member.isAdmin(), member.getIsExcepted(), null, null, null, member.getRole(), member.getBadge(), null, member.getBaseRole(), getAdditionalRoleNames(member.getAdditionalRoles()));
    }

    public static MemberResponse of(Member member, String profileImageURL) {
        return new MemberResponse(member.getLoginID(), member.getEmail(), member.getPhoneNumber(), member.getName(), member.getDepartment(), member.getStudentID(), profileImageURL, member.getIntroduction(), member.isMember(), member.isAdmin(), member.getIsExcepted(), null, null, null, member.getRole(), member.getBadge(), null, member.getBaseRole(), getAdditionalRoleNames(member.getAdditionalRoles()));
    }

    public static MemberResponse ofAdminList(Member member, String profileImageURL) {
        return new MemberResponse(member.getLoginID(), member.getEmail(), member.getPhoneNumber(), member.getName(), member.getDepartment(), member.getStudentID(), profileImageURL, member.getIntroduction(), member.isMember(), member.isAdmin(), member.getIsExcepted(), null, null, null, member.getRole(), member.getBadge(), member.getAdminRemarks(), member.getBaseRole(), getAdditionalRoleNames(member.getAdditionalRoles()));
    }

    public static MemberResponse of(Member findMember, Member loginMember,
                                    List<ActivityResponse> hostActivities,
                                    List<ActivityResponse> participantActivities,
                                    List<ProjectResponse> projects) {
        return of(findMember, loginMember, hostActivities, participantActivities, projects, null);
    }

    public static MemberResponse of(Member findMember, Member loginMember,
                                    List<ActivityResponse> hostActivities,
                                    List<ActivityResponse> participantActivities,
                                    List<ProjectResponse> projects,
                                    String profileImageURL) {
        if(findMember.equals(loginMember)) {
            return new MemberResponse(findMember.getLoginID(), findMember.getEmail(), findMember.getPhoneNumber(), findMember.getName(), findMember.getDepartment(), findMember.getStudentID(), profileImageURL, findMember.getIntroduction(), findMember.isMember(), findMember.isAdmin(), findMember.getIsExcepted(), hostActivities, participantActivities, projects, findMember.getRole(),  findMember.getBadge(), null, findMember.getBaseRole(), getAdditionalRoleNames(findMember.getAdditionalRoles()));
        }else{
            return new MemberResponse(findMember.getLoginID(), null, null, findMember.getName(), findMember.getDepartment(), null, profileImageURL, findMember.getIntroduction(), findMember.isMember(), findMember.isAdmin(), findMember.getIsExcepted(), hostActivities, participantActivities, projects, findMember.getRole(),  findMember.getBadge(), null, findMember.getBaseRole(), getAdditionalRoleNames(findMember.getAdditionalRoles()));
        }
    }

    private static List<String> getAdditionalRoleNames(Set<MemberRole> roles) {
        return java.util.Arrays.stream(MemberRole.values())
                .filter(roles::contains)
                .map(Enum::name)
                .collect(Collectors.toList());
    }
}
