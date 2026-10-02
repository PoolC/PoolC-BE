package org.poolc.api.member.service;

import lombok.RequiredArgsConstructor;
import net.bytebuddy.utility.RandomString;
import org.poolc.api.activity.dto.ActivityResponse;
import org.poolc.api.activity.service.ActivityService;
import org.poolc.api.auth.exception.UnauthorizedException;
import org.poolc.api.auth.exception.UnauthenticatedException;
import org.poolc.api.auth.infra.PasswordHashProvider;
import org.poolc.api.common.domain.YearSemester;
import org.poolc.api.common.exception.ConflictException;
import org.poolc.api.member.domain.Member;
import org.poolc.api.member.domain.MemberRole;
import org.poolc.api.member.domain.MemberRoles;
import org.poolc.api.member.dto.MemberResetRequest;
import org.poolc.api.member.dto.MemberResponse;
import org.poolc.api.member.dto.MemberResponseWithHour;
import org.poolc.api.member.dto.MyActivityDetailResponse;
import org.poolc.api.member.dto.MyActivitySummaryResponse;
import org.poolc.api.member.dto.UpdateMemberRequest;
import org.poolc.api.member.exception.DuplicateMemberException;
import org.poolc.api.member.exception.WrongPasswordException;
import org.poolc.api.member.repository.MemberQueryRepository;
import org.poolc.api.member.repository.MemberRepository;
import org.poolc.api.member.repository.RecognizedActivityHours;
import org.poolc.api.member.repository.RecognizedOfficialActivityHours;
import org.poolc.api.member.repository.RecognizedProjectHours;
import org.poolc.api.member.vo.MemberCreateValues;
import org.poolc.api.poolc.domain.Poolc;
import org.poolc.api.poolc.service.PoolcService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.mail.MessagingException;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final PasswordHashProvider passwordHashProvider;
    private final MemberQueryRepository memberQueryRepository;
    private final ActivityService activityService;
    private final MemberResponseAssembler memberResponseAssembler;
//    private final MailService mailService;
    private final PoolcService poolcService;

    public void create(MemberCreateValues values) {
        boolean hasDuplicate = memberRepository.existsByLoginIDOrEmailOrPhoneNumberOrStudentID(values.getLoginID(),
                values.getEmail(), values.getPhoneNumber(), values.getStudentID());

        if (hasDuplicate) {
            throw new DuplicateMemberException("There are one or more duplicates of the following: loginID, email, phone number or studentID");
        }

        memberRepository.save(
                Member.builder()
                        .UUID(UUID.randomUUID().toString())
                        .loginID(values.getLoginID())
                        .passwordHash(passwordHashProvider.encodePassword(values.getPassword()))
                        .email(values.getEmail())
                        .phoneNumber(values.getPhoneNumber())
                        .name(values.getName())
                        .department(values.getDepartment())
                        .studentID(values.getStudentID())
                        .passwordResetToken(null)
                        .passwordResetTokenValidUntil(null)
                        .introduction(values.getIntroduction())
                        .isExcepted(false)
                        .roles(MemberRoles.getDefaultFor(MemberRole.UNACCEPTED))
                        .build());
    }

    public void checkMe(Member loginMember) {
        if (loginMember == null) {
            throw new UnauthenticatedException("로그인이 필요합니다.");
        }
        Poolc poolc = poolcService.get();
        if (!poolc.checkSubscriptionPeriod() && !loginMember.isAcceptedMember()) {
            throw new UnauthorizedException("인증받지 않은 회원입니다.");
        }
    }

    public List<MemberResponse> getAllMembersResponse(Member loginMember) {
        List<Member> members = getAllMembers();
        List<Member> visibleMembers = members.stream()
                .filter(responseMember -> (!Optional.ofNullable(loginMember).isEmpty() && loginMember.isAdmin() || !responseMember.shouldHide()))
                .collect(Collectors.toList());
        return loginMember != null && loginMember.isAdmin()
                ? memberResponseAssembler.ofAllForAdmin(visibleMembers)
                : memberResponseAssembler.ofAll(visibleMembers);
    }

    @Transactional
    public void updateAdminRemarks(Member admin, String loginID, String remarks) {
        if (admin == null || !admin.isAdmin()) {
            throw new UnauthorizedException("Only admins can update member remarks");
        }
        if (remarks != null && remarks.length() > 1000) {
            throw new IllegalArgumentException("비고는 1000자 이내로 입력해주세요.");
        }
        Member targetMember = getMemberByLoginID(loginID);
        targetMember.updateAdminRemarks(remarks == null || remarks.trim().isEmpty() ? null : remarks.trim());
    }

    @Transactional
    public void updateAdditionalRole(Member admin, String loginID, MemberRole role, boolean enabled) {
        if (admin == null || !admin.isAdmin()) {
            throw new UnauthorizedException("Only admins can update member roles");
        }
        if (role == null) {
            throw new IllegalArgumentException("추가 역할을 선택해주세요.");
        }
        Member targetMember = getMemberByLoginID(loginID);
        if (MemberRole.SUPER_ADMIN.name().equals(targetMember.getRole())) {
            throw new UnauthorizedException("Usage of super admin is prohibited");
        }
        targetMember.toggleAdditionalRole(role, enabled);
        memberRepository.saveAndFlush(targetMember);
    }

    @Transactional
    public void updateMyAdditionalRole(Member member, MemberRole role, boolean enabled) {
        if (member == null) {
            throw new UnauthenticatedException("로그인이 필요합니다.");
        }
        if (role == null) {
            throw new IllegalArgumentException("추가 역할을 선택해주세요.");
        }
        if (!role.isSelfToggleable()) {
            throw new UnauthorizedException("이 역할은 본인이 변경할 수 없습니다.");
        }
        member.toggleAdditionalRole(role, enabled);
        memberRepository.saveAndFlush(member);
    }

    public List<MemberResponse> getAllMembersResponseByName(String name) {
        return memberRepository.findByName(name)
                .stream()
                .map(memberResponseAssembler::of)
                .collect(Collectors.toList());
    }

    public List<ActivityResponse> getMemberActivityResponses(String loginID) {
        return activityService.findActivitiesByMemberLoginId(loginID)
                .stream().map(ActivityResponse::of)
                .collect(Collectors.toList());
    }

    public List<ActivityResponse> getHostActivityResponses(Member findMember) {
        return activityService.findActivitiesByHost(findMember)
                .stream().map(ActivityResponse::of)
                .collect(Collectors.toList());
    }

    public List<Member> findMembers(List<String> members) {
        return memberRepository.findAllMembersByLoginIDList(members);
    }

    public boolean checkMemberExistsByLoginID(String loginID) {
        return !memberRepository.existsByLoginID(loginID);
    }

    public void checkGetRoles(Member loginMember) {
        if (loginMember == null) {
            throw new UnauthenticatedException("로그인이 필요합니다.");
        }
        Poolc poolc = poolcService.get();
        if (!poolc.checkSubscriptionPeriod() && !loginMember.isAcceptedMember()) {
            throw new UnauthorizedException("인증받지 않은 회원입니다.");
        }
    }

    public void throwIfNotMember(Member member) {
        if(member == null || !member.isMember()) {
            throw new UnauthorizedException("회원이 아닙니다");
        }
    }

    public Member getMemberByLoginID(String loginID) {
        return memberRepository.findByLoginID(loginID)
                .orElseThrow(() -> new NoSuchElementException("No user found with given loginID"));
    }
    public Member getMemberIfRegistered(String loginID, String password) {
        return memberRepository.findByLoginID(loginID)
                .filter(member -> passwordHashProvider.matches(password, member.getPasswordHash()))
                .orElseThrow(() -> new WrongPasswordException("아이디와 비밀번호를 확인해주세요."));
    }
    // TODO: 이부분 좀 더 깨끗하게 Refactoring해야할 거 같다.

    @Transactional(readOnly = true)
    public List<MemberResponseWithHour> getHoursWithMembers() {
        YearSemester yearSemester = YearSemester.of(LocalDate.now());
        List<Member> members = getAllMembers();
        Map<String, BigDecimal> recognizedHoursByLoginId = memberQueryRepository.getRecognizedHours(
                yearSemester.getFirstDateFromYearSemester(), yearSemester.getLastDateFromYearSemester(), LocalDate.now());

        return members.stream()
                .map(member -> MemberResponseWithHour.of(member,
                        recognizedHoursByLoginId.getOrDefault(member.getLoginID(), BigDecimal.ZERO),
                        Boolean.TRUE.equals(member.getIsExcepted()) || member.getRoles().checkIsExcepted()))
                .sorted(Comparator.comparing(response -> response.getMember().getName()))
                .collect(Collectors.toList());
    }

    public Long getMyHour(Member member){
        YearSemester yearSemester = YearSemester.of(LocalDate.now());
        LocalDate startDate = yearSemester.getFirstDateFromYearSemester();
        LocalDate endDate = yearSemester.getLastDateFromYearSemester();
        return memberQueryRepository.getMyHour(member, startDate, endDate);
    }

    @Transactional(readOnly = true)
    public MyActivitySummaryResponse getMyActivitySummary(Member member) {
        YearSemester yearSemester = YearSemester.of(LocalDate.now());
        LocalDate semesterStartDate = yearSemester.getFirstDateFromYearSemester();
        LocalDate semesterEndDate = yearSemester.getLastDateFromYearSemester();
        List<RecognizedActivityHours> recognizedActivityHours = memberQueryRepository.getRecognizedActivityHours(
                member.getLoginID(), semesterStartDate, semesterEndDate, LocalDate.now());
        List<RecognizedProjectHours> recognizedProjectHours = memberQueryRepository.getRecognizedProjectHours(
                member.getLoginID(), semesterStartDate, semesterEndDate);
        List<RecognizedOfficialActivityHours> recognizedOfficialActivityHours = memberQueryRepository.getRecognizedOfficialActivityHours(
                member.getLoginID(), semesterStartDate, semesterEndDate);

        BigDecimal seminarStudyHours = recognizedActivityHours.stream()
                .map(RecognizedActivityHours::getHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<MyActivityDetailResponse> seminarStudyActivities = recognizedActivityHours.stream()
                .map(activity -> MyActivityDetailResponse.builder()
                        .activityId(activity.getActivityId())
                        .title(activity.getTitle())
                        .recognizedHours(activity.getHours())
                        .hosted(activity.isHosted())
                        .build())
                .sorted(Comparator.comparing(MyActivityDetailResponse::getTitle))
                .collect(Collectors.toList());

        List<MyActivityDetailResponse> projectActivities = recognizedProjectHours.stream()
                .map(project -> MyActivityDetailResponse.builder()
                        .activityId(project.getProjectId())
                        .title(project.getTitle())
                        .recognizedHours(project.getHours())
                        .hosted(false)
                        .build())
                .sorted(Comparator.comparing(MyActivityDetailResponse::getTitle))
                .collect(Collectors.toList());
        BigDecimal projectHours = recognizedProjectHours.stream()
                .map(RecognizedProjectHours::getHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<MyActivityDetailResponse> officialActivities = recognizedOfficialActivityHours.stream()
                .map(activity -> MyActivityDetailResponse.builder()
                        .activityId(activity.getActivityId())
                        .title(activity.getTitle())
                        .recognizedHours(activity.getHours())
                        .hosted(false)
                        .build())
                .collect(Collectors.toList());
        BigDecimal officialActivityHours = recognizedOfficialActivityHours.stream()
                .map(RecognizedOfficialActivityHours::getHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return MyActivitySummaryResponse.builder()
                .totalHours(seminarStudyHours.add(projectHours).add(officialActivityHours))
                .seminarStudyHours(seminarStudyHours)
                .officialActivityHours(officialActivityHours)
                .projectHours(projectHours)
                .seminarStudyActivities(seminarStudyActivities)
                .officialActivities(officialActivities)
                .projectActivities(projectActivities)
                .build();
    }

    public void authorizeMember(String loginID) {
        Member findMember = getMemberByLoginID(loginID);
        findMember.acceptMember();
        findMember.updateIsExcepted();
        memberRepository.saveAndFlush(findMember);
    }

    public void toggleAdmin(Member admin, String loginID) {
        Member targetMember = getMemberByLoginID(loginID);
        admin.changeRole(targetMember, targetMember.getRoles().hasRole(MemberRole.ADMIN) ?
                MemberRole.MEMBER :
                MemberRole.ADMIN);
        targetMember.updateIsExcepted();
        memberRepository.saveAndFlush(targetMember);
    }

    public void selfChangeToRole(Member member, MemberRole role) {
        member.selfChangeRole(role);
        member.updateIsExcepted();
        memberRepository.saveAndFlush(member);
    }

    public void changeToRole(Member admin, String targetMemberLoginID, MemberRole role) {
        Member targetMember = getMemberByLoginID(targetMemberLoginID);
        admin.changeRole(targetMember, role);
        targetMember.updateIsExcepted();
        memberRepository.saveAndFlush(targetMember);
    }

    public void toggleIsExcepted(Member member, String targetLoginID) {
        Member targetMember = getMemberByLoginID(targetLoginID);
        member.toggleExcept(targetMember);
        memberRepository.saveAndFlush(targetMember);
    }

    public void sendResetPasswordMail(MemberResetRequest request) throws MessagingException {
        String email = request.getEmail();
        String resetPasswordToken = resetMemberPasswordToken(email);

//        mailService.sendEmailPasswordResetToken(email, resetPasswordToken);
    }

    public void resetPassword(MemberResetRequest request) {
        String passwordResetToken = request.getPasswordResetToken();
        String newPassword = request.getNewPassword();

        updateMemberPassword(passwordResetToken, newPassword);
    }

    public void updateMember(Member member, UpdateMemberRequest updateMemberRequest) {
        String encodePassword = passwordHashProvider.encodePassword(updateMemberRequest.getPassword());
        member.updateMemberInfo(updateMemberRequest, encodePassword);
        memberRepository.saveAndFlush(member);
    }

    public void deleteMember(String loginID) {
        Member member = getMemberByLoginID(loginID);
        if (member.isAcceptedMember()) {
            throw new ConflictException("승인 완료 회원은 삭제할 수 없습니다.");
        }
        memberRepository.delete(member);
    }

    private List<Member> getAllMembers() {
        List<Member> members = memberRepository.findAll();
        members.sort(Comparator.comparing(Member::getName));
        return members;
    }

    public String findNameByLoginID(String loginID) {
        Member member = memberRepository.findByLoginID(loginID)
                .orElseThrow(() -> new NoSuchElementException("No member with given login ID."));
        return member.getName();
    }

    private String resetMemberPasswordToken(String email) {
        Member resetMember = memberRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("No user found with given email"));
        String resetPasswordToken = RandomString.make(40);
        resetMember.setPasswordResetToken(resetPasswordToken);
        memberRepository.saveAndFlush(resetMember);
        return resetPasswordToken;
    }

    private void updateMemberPassword(String passwordResetToken, String newPassword) {
        Member resetMember = memberRepository.findByPasswordResetToken(passwordResetToken)
                .orElseThrow(() -> new NoSuchElementException("No user found with given passwordResetToken"));
        String newPasswordHash = passwordHashProvider.encodePassword(newPassword);
        resetMember.updatePassword(newPasswordHash);
        memberRepository.saveAndFlush(resetMember);
    }

    public void deleteUnacceptedMembers() {
        getAllMembers().stream().filter(Predicate.not(Member::isEnabled))
                .forEach(memberRepository::delete);
    }
}
