package org.poolc.api.auth.service;

import lombok.RequiredArgsConstructor;
import org.poolc.api.auth.exception.UnactivatedException;
import org.poolc.api.auth.infra.JwtTokenProvider;
import org.poolc.api.member.domain.Member;
import org.poolc.api.member.domain.MemberRole;
import org.poolc.api.member.service.MemberService;
import org.poolc.api.poolc.domain.Poolc;
import org.poolc.api.poolc.service.PoolcService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final JwtTokenProvider jwtTokenProvider;
    private final MemberService memberService;
    private final PoolcService poolcService;

    public String createAccessToken(String loginID, String password) {
        Member member = memberService.getMemberIfRegistered(loginID, password);
        checkLoginEligibility(member, poolcService.get());
        return jwtTokenProvider.createToken(member);
    }

    public String reIssueAccessToken(Member member){
        checkLoginEligibility(member, poolcService.get());
        return jwtTokenProvider.createToken(member);
    }

    private void checkLoginEligibility(Member member, Poolc poolc) {
        if (member.getRoles().hasRole(MemberRole.EXPELLED) || member.getRoles().hasRole(MemberRole.QUIT)) {
            throw new UnactivatedException(String.format(
                    "로그인할 수 없는 계정입니다. 회장 %s(%s)에게 문의해주세요.",
                    poolc.getPresidentName(),
                    poolc.getPhoneNumber()
            ));
        }
        if (!poolc.checkSubscriptionPeriod() && !member.isAcceptedMember() && !member.isMember()) {
            throw new UnactivatedException("관리자 승인 전에는 로그인이 불가능합니다.");
        }
    }
}
