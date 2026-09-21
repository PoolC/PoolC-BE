package org.poolc.api.activity.service;

import lombok.RequiredArgsConstructor;
import org.poolc.api.activity.domain.Activity;
import org.poolc.api.activity.domain.Session;
import org.poolc.api.activity.domain.SessionQrToken;
import org.poolc.api.activity.dto.SessionCheckInResponse;
import org.poolc.api.activity.dto.SessionQrResponse;
import org.poolc.api.activity.dto.SessionResponse;
import org.poolc.api.activity.exception.NotHostException;
import org.poolc.api.activity.repository.ActivityRepository;
import org.poolc.api.activity.repository.SessionRepository;
import org.poolc.api.activity.repository.SessionQrTokenRepository;
import org.poolc.api.activity.vo.AttendanceValues;
import org.poolc.api.activity.vo.SessionCreateValues;
import org.poolc.api.activity.vo.SessionUpdateValues;
import org.poolc.api.member.domain.Member;
import org.poolc.api.member.repository.MemberRepository;
import org.poolc.api.member.service.MemberService;
import org.poolc.api.common.exception.ConflictException;
import org.poolc.api.tool.domain.Qr;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.*;
import java.util.Base64;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SessionService {

    private final EntityManager em;
    private final SessionRepository sessionRepository;
    private final ActivityRepository activityRepository;
    private final MemberService memberService;
    private final MemberRepository memberRepository;
    private final SessionQrTokenRepository sessionQrTokenRepository;

    @Value("${activity.session.qr.base-url:https://poolc.org/activity/session/check-in}")
    private String checkInBaseUrl;


    @Transactional
    public Session createSession(Member member, SessionCreateValues sessionCreateValues) {
        Activity activity = activityRepository.findOneActivityWithHostAndTags(sessionCreateValues.getActivityID()).orElseThrow(() -> new NoSuchElementException("존재하지 않는 활동입니다"));
        if (!checkUserIsHost(activity.getHost().getUUID(), member.getUUID())) {
            throw new NotHostException("호스트가 아닌 사람은 세션 정보를 입력할수 없습니다");
        }
        Session session = new Session(activity, sessionCreateValues);
        sessionRepository.save(session);
        return session;
    }


    public List<Session> findSessionsByActivityID(Long id) {
        Activity activity = activityRepository.findById(id).orElseThrow(() -> new NoSuchElementException("존재하지 않는 활동입니다"));
        List<Session> sessionsByActivity = sessionRepository.findByActivity(activity);
        sessionsByActivity.sort(Comparator.comparing(Session::getSessionNumber));
        return sessionsByActivity;
    }

    public Session findOneSessionByID(Long id) {
        return sessionRepository.findById(id).orElseThrow(() -> new NoSuchElementException("존재하지 않는 회차입니다"));
    }

    @Transactional
    public void updateSession(Long id, SessionUpdateValues values) {
        Session session = sessionRepository.findById(id).orElseThrow(() -> new NoSuchElementException("존재하지 않는 회차입니다"));
        if (!checkUserIsHost(session.getActivity().getHost().getUUID(), values.getUuid())) {
            throw new NotHostException("호스트가 아닌 사람은 세션 정보를 업데이트 할수 없습니다");
        }
        session.update(values);
    }

    @Transactional
    public void attend(String uuid, AttendanceValues values) {
        Session session = sessionRepository.findById(values.getSessionID())
                .orElseThrow(() -> new NoSuchElementException("해당하는 세션이 없습니다"));
        Activity activity = session.getActivity();
        if (!checkUserIsHost(uuid, activity.getHost().getUUID())) {
            throw new NotHostException("호스트가 아니면 출석체크를 할수 없습니다");
        }
        if (session.isQrEnabled()) {
            throw new ConflictException("QR 출석이 켜져 있는 동안에는 수동 출석을 수정할 수 없습니다. 먼저 QR을 꺼주세요.");
        }
        checkMembersExist(values.getMemberLoginIDs());
        checkMembersExistInActivity(values.getMemberLoginIDs(), activity);
        session.clear();
        session.attend(values.getMemberLoginIDs());

    }

    @Transactional
    public SessionQrResponse generateQr(Member member, Long sessionId) {
        Session session = findSessionForManagement(member, sessionId);
        sessionQrTokenRepository.findBySessionId(sessionId).ifPresent(sessionQrTokenRepository::delete);
        String token = UUID.randomUUID().toString().replace("-", "");
        session.enableQr();
        sessionQrTokenRepository.save(new SessionQrToken(token, session));
        return qrResponseOf(token);
    }

    @Transactional(readOnly = true)
    public SessionQrResponse getQr(Member member, Long sessionId) {
        Session session = findSessionForManagement(member, sessionId);
        if (!session.isQrEnabled()) {
            throw new ConflictException("현재 출석 QR이 꺼져 있습니다.");
        }
        SessionQrToken qrToken = sessionQrTokenRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ConflictException("출석 QR을 찾을 수 없습니다."));
        return qrResponseOf(qrToken.getToken());
    }

    @Transactional
    public SessionResponse disableQr(Member member, Long sessionId) {
        Session session = findSessionForManagement(member, sessionId);
        session.disableQr();
        sessionQrTokenRepository.findBySessionId(sessionId).ifPresent(sessionQrTokenRepository::delete);
        return SessionResponse.of(session);
    }

    @Transactional
    public SessionCheckInResponse checkIn(String token, String memberLoginId) {
        SessionQrTokenRepository.CheckInToken qrToken = sessionQrTokenRepository.findCheckInTokenByToken(token)
                .orElseThrow(() -> new ConflictException("유효하지 않은 출석 QR입니다."));
        if (!qrToken.getQrEnabled()) {
            throw new ConflictException("현재 출석 QR이 꺼져 있습니다.");
        }
        if (!sessionRepository.isActivityMember(qrToken.getSessionId(), memberLoginId)) {
            throw new ConflictException("이 세미나·스터디의 신청자만 QR 출석할 수 있습니다.");
        }
        boolean alreadyCheckedIn = sessionRepository.insertAttendanceIfAbsent(qrToken.getSessionId(), memberLoginId) == 0;
        return new SessionCheckInResponse(alreadyCheckedIn);
    }

    public List<Map.Entry<Member, Boolean>> findActivityMembersWithAttendanceBySessionId(Long sessionId) {
        Session session = sessionRepository.findByIdWithAttendances(sessionId).orElseThrow(() -> new NoSuchElementException("해당하는 세션이 없습니다"));
        List<Member> members = memberRepository.findAllMembersByLoginIDList(session.getActivity().getMemberLoginIDs());

        //TODO: Refactor가 필요한 거 같다. 좀 더 세련되게 코드를 짤 수 있을 거 같다.
        Map<Member, Boolean> membersAttendanceMap = new HashMap<>();
        members.stream().forEach(member -> membersAttendanceMap.put(member, false));
        session.getAttendedMemberLoginIDs().forEach(memberLoginId ->
                membersAttendanceMap.put(findByMemberLoginId(memberLoginId), true));
        ArrayList<Map.Entry<Member, Boolean>> memberAttendanceList = new ArrayList<>(membersAttendanceMap.entrySet());
        memberAttendanceList.sort(new Comparator<Map.Entry<Member, Boolean>>() {
            @Override
            public int compare(Map.Entry<Member, Boolean> o1, Map.Entry<Member, Boolean> o2) {
                return o1.getKey().getName().compareTo(o2.getKey().getName());
            }
        });
        return memberAttendanceList;
    }

    private void checkMembersExistInActivity(List<String> memberLoginIDs, Activity activity) {
        memberLoginIDs.forEach((s) -> {
            if (!activity.getMemberLoginIDs().contains(s)) {
                throw new NoSuchElementException("해당하는 회원이 이 활동에 수강신청하지 않았습니다");
            }
        });
    }

    private void checkMembersExist(List<String> memberLoginIDs) {
        memberLoginIDs.forEach(memberService::getMemberByLoginID);
    }

    private boolean checkUserIsHost(String uuid, String userID) {
        if (!uuid.equals(userID)) {
            return false;
        } else {
            return true;
        }
    }

    private Member findByMemberLoginId(String memberLoginId) {
        return memberRepository.findByLoginID(memberLoginId).orElseThrow(() -> new NoSuchElementException("해당하는 세션이 없습니다"));
    }

    private Session findSessionForManagement(Member member, Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("해당하는 세션이 없습니다"));
        if (!member.isAdmin() && !checkUserIsHost(member.getUUID(), session.getActivity().getHost().getUUID())) {
            throw new NotHostException("호스트나 관리자만 출석 QR을 관리할 수 있습니다");
        }
        return session;
    }

    private SessionQrResponse qrResponseOf(String token) {
        String checkInUrl = checkInBaseUrl + "/" + token;
        String imageDataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(new Qr(checkInUrl).createQrImage());
        return new SessionQrResponse(checkInUrl, imageDataUrl);
    }
}
