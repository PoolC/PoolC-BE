package org.poolc.api.gamification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.poolc.api.common.domain.YearSemester;
import org.poolc.api.common.exception.ConflictException;
import org.poolc.api.gamification.domain.BallTransaction;
import org.poolc.api.gamification.domain.BallTransactionType;
import org.poolc.api.gamification.domain.BallType;
import org.poolc.api.gamification.domain.CollectionDraw;
import org.poolc.api.gamification.domain.CollectibleCatalog;
import org.poolc.api.gamification.domain.CollectibleRarity;
import org.poolc.api.gamification.domain.AchievementProgress;
import org.poolc.api.gamification.repository.MemberFeaturedCollectibleRepository;
import org.poolc.api.gamification.dto.CollectionItemResponse;
import org.poolc.api.gamification.dto.AchievementResponse;
import org.poolc.api.gamification.dto.BallBalancesResponse;
import org.poolc.api.gamification.dto.DrawResponse;
import org.poolc.api.gamification.dto.GameSummaryResponse;
import org.poolc.api.gamification.dto.AdminPokemonGrantRequest;
import org.poolc.api.gamification.dto.AdminPokemonGrantResponse;
import org.poolc.api.gamification.dto.AdminBallGrantRequest;
import org.poolc.api.gamification.dto.AdminBallGrantResponse;
import org.poolc.api.gamification.dto.ShinyDrawStatus;
import org.poolc.api.gamification.repository.BallTransactionRepository;
import org.poolc.api.gamification.repository.CollectionDrawRepository;
import org.poolc.api.gamification.repository.CollectibleCatalogRepository;
import org.poolc.api.gamification.repository.AchievementProgressRepository;
import org.poolc.api.activity.domain.Session;
import org.poolc.api.activity.repository.SessionRepository;
import org.poolc.api.activity.repository.ActivityRepository;
import org.poolc.api.member.domain.Member;
import org.poolc.api.member.domain.MemberRole;
import org.poolc.api.auth.exception.UnauthorizedException;
import org.poolc.api.member.dto.MyActivitySummaryResponse;
import org.poolc.api.member.repository.MemberRepository;
import org.poolc.api.member.service.MemberService;
import org.poolc.api.scrap.repository.ScrapRepository;
import org.poolc.api.project.repository.ProjectRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.EnumSet;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GamificationService {
    private static final String ACTIVITY_HOURS = "ACTIVITY_HOURS";
    private static final LocalDateTime HIDDEN_INITIAL_BULK_GRANT_AT =
            LocalDateTime.of(2026, 9, 8, 1, 12, 42, 360_542_000);
    private static final List<String> ADMIN_BALL_GRANT_SOURCES = List.of(
            "ADMIN_BALL_GRANT", "BULK_GRANT", "MANUAL_GRANT", "ADMIN_MANUAL_GRANT",
            "POOLC_ADMIN_GRANT", "MANUAL_ADMIN", "ADMIN_GRANT", "ADMIN_MANUAL", "PROD_MANUAL_GRANT");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BallTransactionRepository ballTransactionRepository;
    private final CollectionDrawRepository collectionDrawRepository;
    private final CollectibleCatalogRepository collectibleCatalogRepository;
    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final AchievementProgressRepository achievementProgressRepository;
    private final SessionRepository sessionRepository;
    private final ActivityRepository activityRepository;
    private final ScrapRepository scrapRepository;
    private final ProjectRepository projectRepository;
    private final MemberFeaturedCollectibleRepository featuredCollectibleRepository;

    @Value("${gamification.club-wifi.allowed-ips:127.0.0.1}")
    private String clubWifiAllowedIps;

    private static final List<AchievementDefinition> ACHIEVEMENTS = List.of(
            new AchievementDefinition("DAILY_ATTENDANCE", "DAILY", "홈페이지에 로그인하기", "오늘 홈페이지에 로그인하세요.", 1, BallType.NORMAL, 1),
            new AchievementDefinition("DAILY_CLUB_WIFI", "DAILY", "동아리방에서 출석하기", "동아리방 Wi-Fi에서 홈페이지에 접속하세요.", 1, BallType.NORMAL, 3),
            new AchievementDefinition("DAILY_DRAW", "DAILY", "오늘 포켓몬 만나기", "오늘 포켓몬을 한 마리 뽑아보세요.", 1, BallType.NORMAL, 2),
            new AchievementDefinition("SEASON_ACTIVITY_5", "SEASON", "이번 학기 활동 5시간", "이번 학기 활동 시간을 5시간 채우세요.", 5, BallType.NORMAL, 5),
            new AchievementDefinition("SEASON_ACTIVITY_10", "SEASON", "이번 학기 활동 10시간", "이번 학기 활동 시간을 10시간 채우세요.", 10, BallType.NORMAL, 10),
            new AchievementDefinition("SEASON_ACTIVITY_15", "SEASON", "이번 학기 활동 15시간", "이번 학기 활동 시간을 15시간 채우세요.", 15, BallType.NORMAL, 15),
            new AchievementDefinition("SEASON_ACTIVITY_20", "SEASON", "이번 학기 활동 20시간", "이번 학기 활동 시간을 20시간 채우세요.", 20, BallType.NORMAL, 20),
            new AchievementDefinition("SEASON_ACTIVITY_25", "SEASON", "이번 학기 활동 25시간", "이번 학기 활동 시간을 25시간 채우세요.", 25, BallType.NORMAL, 25),
            new AchievementDefinition("SEASON_ACTIVITY_30", "SEASON", "이번 학기 활동 30시간", "이번 학기 활동 시간을 30시간 채우세요.", 30, BallType.NORMAL, 30),
            new AchievementDefinition("SEASON_ATTENDANCE_2", "SEASON", "이번 학기 로그인 2회", "이번 학기 홈페이지에 2회 로그인하세요.", 2, BallType.NORMAL, 5),
            new AchievementDefinition("SEASON_ATTENDANCE_4", "SEASON", "이번 학기 로그인 4회", "이번 학기 홈페이지에 4회 로그인하세요.", 4, BallType.NORMAL, 5),
            new AchievementDefinition("SEASON_ATTENDANCE_6", "SEASON", "이번 학기 로그인 6회", "이번 학기 홈페이지에 6회 로그인하세요.", 6, BallType.NORMAL, 5),
            new AchievementDefinition("SEASON_ATTENDANCE_8", "SEASON", "이번 학기 로그인 8회", "이번 학기 홈페이지에 8회 로그인하세요.", 8, BallType.NORMAL, 5),
            new AchievementDefinition("SEASON_ATTENDANCE_10", "SEASON", "이번 학기 로그인 10회", "이번 학기 홈페이지에 10회 로그인하세요.", 10, BallType.NORMAL, 10),
            new AchievementDefinition("SEASON_PARTICIPATION", "SEASON", "이번 학기 활동 참여", "이번 학기 세미나/스터디에 참여하세요.", 1, BallType.NORMAL, 5),
            new AchievementDefinition("SEASON_SCRAPS", "SEASON", "이번 학기 스크랩 5개", "이번 학기에 게시글 5개를 스크랩하세요.", 5, BallType.NORMAL, 5),
            new AchievementDefinition("SEASON_COLLECTION", "SEASON", "이번 학기 포켓몬 5종 수집", "이번 학기에 포켓몬 5종을 수집하세요.", 5, BallType.NORMAL, 10),
            new AchievementDefinition("SEASON_ADMIN", "SEASON", "임원진 되기", "이번 학기에 임원진 역할을 획득하세요.", 1, BallType.NORMAL, 30),
            new AchievementDefinition("SEASON_TECHNICIAN", "SEASON", "기여자 되기", "이번 학기에 기여자 역할을 획득하세요.", 1, BallType.NORMAL, 30),
            new AchievementDefinition("PERMANENT_MEMBER_APPROVED", "PERMANENT", "가입 승인 완료", "PoolC 회원으로 승인되었어요.", 1, BallType.NORMAL, 30),
            new AchievementDefinition("PERMANENT_PROFILE", "PERMANENT", "프로필 완성", "프로필 정보를 완성하세요.", 1, BallType.NORMAL, 5),
            new AchievementDefinition("PERMANENT_ATTENDANCE", "PERMANENT", "첫 활동 출석", "첫 활동에 출석하세요.", 1, BallType.NORMAL, 5),
            new AchievementDefinition("PERMANENT_ACTIVITY_PARTICIPATION", "PERMANENT", "첫 활동 참여", "첫 세미나/스터디에 참여하세요.", 1, BallType.NORMAL, 5),
            new AchievementDefinition("PERMANENT_PROJECT", "PERMANENT", "첫 프로젝트 참여", "첫 프로젝트에 참여하세요.", 1, BallType.NORMAL, 10),
            new AchievementDefinition("PERMANENT_COLLECTION", "PERMANENT", "첫 포켓몬 획득", "첫 포켓몬을 획득하세요.", 1, BallType.NORMAL, 5),
            new AchievementDefinition("PERMANENT_SHINY", "PERMANENT", "첫 이로치 획득", "첫 이로치 포켓몬을 획득하세요.", 1, BallType.NORMAL, 10),
            new AchievementDefinition("PERMANENT_REGULATION", "PERMANENT", "회칙 읽어보기", "PoolC 동아리 회칙을 확인하세요.", 1, BallType.NORMAL, 10),
            new AchievementDefinition("PERMANENT_GITHUB", "PERMANENT", "GitHub 접속하기", "PoolC GitHub 조직을 방문하세요.", 1, BallType.NORMAL, 10),
            new AchievementDefinition("PERMANENT_FEATURED_COLLECTIBLE", "PERMANENT", "포켓볼 대표 캐릭 설정하기", "대표 포켓몬을 지정하세요.", 1, BallType.NORMAL, 10),
            new AchievementDefinition("PERMANENT_HOST_3", "PERMANENT", "세미나 3회 개최", "세미나를 3회 개최하세요.", 3, BallType.NORMAL, 15),
            new AchievementDefinition("PERMANENT_HOST_6", "PERMANENT", "세미나 6회 개최", "세미나를 6회 개최하세요.", 6, BallType.NORMAL, 20),
            new AchievementDefinition("PERMANENT_HOST_10", "PERMANENT", "세미나 10회 개최", "세미나를 10회 개최하세요.", 10, BallType.NORMAL, 35),
            new AchievementDefinition("PERMANENT_HOURS_30", "PERMANENT", "총 활동 시간 30시간", "총 활동 시간을 30시간 채우세요.", 30, BallType.NORMAL, 5),
            new AchievementDefinition("PERMANENT_HOURS_40", "PERMANENT", "총 활동 시간 40시간", "총 활동 시간을 40시간 채우세요.", 40, BallType.NORMAL, 10),
            new AchievementDefinition("PERMANENT_HOURS_50", "PERMANENT", "총 활동 시간 50시간", "총 활동 시간을 50시간 채우세요.", 50, BallType.NORMAL, 10),
            new AchievementDefinition("PERMANENT_HOURS_75", "PERMANENT", "총 활동 시간 75시간", "총 활동 시간을 75시간 채우세요.", 75, BallType.NORMAL, 15),
            new AchievementDefinition("PERMANENT_HOURS_100", "PERMANENT", "총 활동 시간 100시간", "총 활동 시간을 100시간 채우세요.", 100, BallType.NORMAL, 20)
    );

    @Transactional
    public List<AchievementResponse> getAchievements(Member authenticatedMember) {
        return getAchievements(authenticatedMember, null);
    }

    @Transactional
    public List<AchievementResponse> getAchievements(Member authenticatedMember, HttpServletRequest request) {
        Member member = memberRepository.findByUUID(authenticatedMember.getUUID())
                .orElseThrow(() -> new NoSuchElementException("회원을 찾을 수 없습니다."));
        LocalDate today = LocalDate.now();
        ensureDailyAttendance(member, today);
        Map<String, Integer> progress = calculateAchievementProgress(member, today, request);
        Map<String, AchievementProgress> savedProgressByKey = achievementProgressRepository.findAllByMemberUuid(member.getUUID())
                .stream()
                .collect(Collectors.toMap(saved -> saved.getAchievementKey() + "|" + saved.getPeriodKey(), saved -> saved));
        preserveAchievementProgress(member, today, progress, savedProgressByKey);
        return ACHIEVEMENTS.stream().map(definition -> {
            String periodKey = periodKey(definition.type, today);
            AchievementProgress saved = savedProgressByKey.get(definition.key + "|" + periodKey);
            int storedProgress = saved == null ? 0 : saved.getProgress();
            return new AchievementResponse(definition.key, definition.type, definition.title, definition.description,
                    definition.target, Math.max(progress.get(definition.key), storedProgress), definition.rewardBallType.name(), definition.rewardAmount,
                    saved != null && saved.isClaimed(), saved == null ? 0 : saved.getClaimedCount());
        }).collect(Collectors.toList());
    }

    private void preserveAchievementProgress(Member member, LocalDate today, Map<String, Integer> progress,
                                             Map<String, AchievementProgress> savedProgressByKey) {
        ACHIEVEMENTS.forEach(definition -> {
            String periodKey = periodKey(definition.type, today);
            String progressKey = definition.key + "|" + periodKey;
            int currentProgress = progress.get(definition.key);
            AchievementProgress saved = savedProgressByKey.get(progressKey);

            if (saved == null && currentProgress > 0) {
                saved = achievementProgressRepository.save(new AchievementProgress(
                        member, definition.key, periodKey, currentProgress));
                savedProgressByKey.put(progressKey, saved);
            } else if (saved != null && currentProgress > saved.getProgress()) {
                saved.updateProgress(currentProgress);
                achievementProgressRepository.save(saved);
            }
        });
    }

    private void ensureDailyAttendance(Member member, LocalDate today) {
        achievementProgressRepository.findByMemberUuidAndAchievementKeyAndPeriodKey(member.getUUID(), "DAILY_ATTENDANCE", today.toString())
                .orElseGet(() -> achievementProgressRepository.save(new AchievementProgress(member, "DAILY_ATTENDANCE", today.toString(), 1)));
    }

    @Transactional
    public BallBalancesResponse claimAchievement(Member authenticatedMember, String achievementKey) {
        return claimAchievement(authenticatedMember, achievementKey, null);
    }

    @Transactional
    public BallBalancesResponse claimAchievement(Member authenticatedMember, String achievementKey, HttpServletRequest request) {
        Member member = memberRepository.findByUUIDForUpdate(authenticatedMember.getUUID())
                .orElseThrow(() -> new NoSuchElementException("회원을 찾을 수 없습니다."));
        AchievementDefinition definition = ACHIEVEMENTS.stream()
                .filter(item -> item.key.equals(achievementKey))
                .findFirst()
                .orElseThrow(() -> new ConflictException("존재하지 않는 업적입니다."));
        LocalDate today = LocalDate.now();
        String periodKey = periodKey(definition.type, today);
        Map<String, Integer> achievementProgress = calculateAchievementProgress(member, today, request);
        AchievementProgress progress = achievementProgressRepository
                .findByMemberUuidAndAchievementKeyAndPeriodKey(member.getUUID(), definition.key, periodKey)
                .orElseGet(() -> achievementProgressRepository.save(new AchievementProgress(
                    member, definition.key, periodKey, achievementProgress.get(definition.key))));
        progress.updateProgress(achievementProgress.get(definition.key));
        if (progress.getProgress() < definition.target || progress.isClaimed()) {
            if (!"REPEATABLE".equals(definition.type)) {
                throw new ConflictException(progress.isClaimed() ? "이미 보상을 받았습니다." : "아직 달성하지 못한 업적입니다.");
            }
        }
        if ("REPEATABLE".equals(definition.type)) {
            int completedRewardCount = progress.getProgress() / definition.target;
            int claimableCount = completedRewardCount - progress.getClaimedCount();
            if (claimableCount <= 0) {
                throw new ConflictException("받을 수 있는 보상이 없습니다.");
            }
            progress.claim(claimableCount);
            achievementProgressRepository.save(progress);
            ballTransactionRepository.save(new BallTransaction(member, definition.rewardAmount * claimableCount,
                    BallTransactionType.ADMIN_GRANT, definition.rewardBallType, "ACHIEVEMENT", definition.key + "_" + periodKey + "_" + progress.getClaimedCount()));
            return getBallBalances(member);
        }
        progress.claim();
        achievementProgressRepository.save(progress);
        ballTransactionRepository.save(new BallTransaction(member, definition.rewardAmount,
                BallTransactionType.ADMIN_GRANT, definition.rewardBallType, "ACHIEVEMENT", definition.key + "_" + periodKey));
        return getBallBalances(member);
    }

    @Transactional
    public void recordPermanentAchievementEvent(Member authenticatedMember, String achievementKey) {
        if (!"PERMANENT_REGULATION".equals(achievementKey) && !"PERMANENT_GITHUB".equals(achievementKey)) {
            throw new ConflictException("기록할 수 없는 업적입니다.");
        }
        Member member = memberRepository.findByUUIDForUpdate(authenticatedMember.getUUID())
                .orElseThrow(() -> new NoSuchElementException("회원을 찾을 수 없습니다."));
        achievementProgressRepository.findByMemberUuidAndAchievementKeyAndPeriodKey(member.getUUID(), achievementKey, "PERMANENT")
                .orElseGet(() -> achievementProgressRepository.save(new AchievementProgress(member, achievementKey, "PERMANENT", 1)));
    }

    private Map<String, Integer> calculateAchievementProgress(Member member, LocalDate today, HttpServletRequest request) {
        int daily = 1;
        int dailyDraws = 0;
        List<CollectionDraw> draws = collectionDrawRepository.findAllByMemberUuidWithCollectible(member.getUUID());
        Set<Long> seasonCollection = new java.util.HashSet<>();
        YearSemester currentSemester = YearSemester.of(today);
        for (CollectionDraw draw : draws) {
            LocalDate drawnDate = draw.getDrawnAt().toLocalDate();
            if (today.equals(drawnDate)) {
                dailyDraws++;
            }
            if (!drawnDate.isBefore(currentSemester.getFirstDateFromYearSemester())
                    && !drawnDate.isAfter(currentSemester.getLastDateFromYearSemester())) {
                seasonCollection.add(draw.getCollectible().getId());
            }
        }
        LocalDateTime seasonStart = currentSemester.getFirstDateFromYearSemester().atStartOfDay();
        LocalDateTime seasonEnd = currentSemester.getLastDateFromYearSemester().plusDays(1).atStartOfDay();
        int seasonScraps = Math.toIntExact(scrapRepository.countByMemberIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(member.getLoginID(), seasonStart, seasonEnd));
        List<org.poolc.api.activity.domain.Activity> participatedActivities = activityRepository.findActivitiesByActivityMembers(member.getLoginID());
        int seasonParticipation = participatedActivities.stream()
                .filter(activity -> activity.getStartDate() != null
                        && !activity.getStartDate().isBefore(currentSemester.getFirstDateFromYearSemester())
                        && !activity.getStartDate().isAfter(currentSemester.getLastDateFromYearSemester()))
                .mapToInt(activity -> 1)
                .sum();
        int seasonLogins = (int) achievementProgressRepository
                .findAllByMemberUuidAndAchievementKey(member.getUUID(), "DAILY_ATTENDANCE")
                .stream()
                .filter(progressRecord -> isDateInRange(progressRecord.getPeriodKey(), currentSemester.getFirstDateFromYearSemester(), currentSemester.getLastDateFromYearSemester()))
                .count();
        int seasonHours = memberService.getMyActivitySummary(member).getTotalHours()
                .setScale(0, RoundingMode.FLOOR).intValue();
        List<Session> relevantSessions = sessionRepository.findAllRelevantToMember(member.getLoginID());
        int allAttendance = (int) relevantSessions.stream()
                .filter(session -> session.getAttendedMemberLoginIDs().contains(member.getLoginID()))
                .count();
        int allActivityParticipation = participatedActivities.size();
        List<org.poolc.api.project.domain.Project> projects = projectRepository.findProjectsByProjectMembers(member.getLoginID());
        int allProjects = projects.size();
        int allHours = totalRecognizedHours(member, relevantSessions, allProjects);
        int memberApproved = member.isMember() ? 1 : 0;
        int firstProfile = hasCompleteProfile(member) ? 1 : 0;
        int firstCollection = draws.isEmpty() ? 0 : 1;
        int firstShiny = draws.stream().anyMatch(CollectionDraw::isShiny) ? 1 : 0;
        int firstAdmin = "ADMIN".equals(member.getRole()) || "SUPER_ADMIN".equals(member.getRole()) ? 1 : 0;
        int firstTechnician = "TECHNICIAN".equals(member.getRole()) ? 1 : 0;
        int regulationRead = recordedPermanentEvent(member.getUUID(), "PERMANENT_REGULATION");
        int githubVisited = recordedPermanentEvent(member.getUUID(), "PERMANENT_GITHUB");
        int featuredCollectible = featuredCollectibleRepository.findByMemberUuid(member.getUUID()).isPresent() ? 1 : 0;
        int hostedSeminars = (int) activityRepository.findActivitiesByHost(member).stream()
                .filter(activity -> Boolean.TRUE.equals(activity.getIsSeminar()))
                .count();
        Map<String, Integer> result = new HashMap<>();
        result.put("DAILY_ATTENDANCE", daily);
        result.put("DAILY_CLUB_WIFI", isClubWifiRequest(request) ? 1 : 0);
        result.put("DAILY_DRAW", dailyDraws);
        result.put("SEASON_ACTIVITY_5", seasonHours);
        result.put("SEASON_ACTIVITY_10", seasonHours);
        result.put("SEASON_ACTIVITY_15", seasonHours);
        result.put("SEASON_ACTIVITY_20", seasonHours);
        result.put("SEASON_ACTIVITY_25", seasonHours);
        result.put("SEASON_ACTIVITY_30", seasonHours);
        result.put("SEASON_ATTENDANCE_2", seasonLogins);
        result.put("SEASON_ATTENDANCE_4", seasonLogins);
        result.put("SEASON_ATTENDANCE_6", seasonLogins);
        result.put("SEASON_ATTENDANCE_8", seasonLogins);
        result.put("SEASON_ATTENDANCE_10", seasonLogins);
        result.put("SEASON_PARTICIPATION", seasonParticipation);
        result.put("SEASON_SCRAPS", seasonScraps);
        result.put("SEASON_COLLECTION", seasonCollection.size());
        result.put("SEASON_ADMIN", firstAdmin);
        result.put("SEASON_TECHNICIAN", firstTechnician);
        result.put("PERMANENT_MEMBER_APPROVED", memberApproved);
        result.put("PERMANENT_PROFILE", firstProfile);
        result.put("PERMANENT_ATTENDANCE", allAttendance > 0 ? 1 : 0);
        result.put("PERMANENT_ACTIVITY_PARTICIPATION", allActivityParticipation > 0 ? 1 : 0);
        result.put("PERMANENT_PROJECT", allProjects > 0 ? 1 : 0);
        result.put("PERMANENT_COLLECTION", firstCollection);
        result.put("PERMANENT_SHINY", firstShiny);
        result.put("PERMANENT_REGULATION", regulationRead);
        result.put("PERMANENT_GITHUB", githubVisited);
        result.put("PERMANENT_FEATURED_COLLECTIBLE", featuredCollectible);
        result.put("PERMANENT_HOST_3", hostedSeminars);
        result.put("PERMANENT_HOST_6", hostedSeminars);
        result.put("PERMANENT_HOST_10", hostedSeminars);
        result.put("PERMANENT_HOURS_30", allHours);
        result.put("PERMANENT_HOURS_40", allHours);
        result.put("PERMANENT_HOURS_50", allHours);
        result.put("PERMANENT_HOURS_75", allHours);
        result.put("PERMANENT_HOURS_100", allHours);
        return result;
    }

    private int recordedPermanentEvent(String memberUuid, String achievementKey) {
        return achievementProgressRepository
                .findByMemberUuidAndAchievementKeyAndPeriodKey(memberUuid, achievementKey, "PERMANENT")
                .map(AchievementProgress::getProgress)
                .orElse(0);
    }

    private boolean isDateInRange(String value, LocalDate start, LocalDate end) {
        try {
            LocalDate date = LocalDate.parse(value);
            return !date.isBefore(start) && !date.isAfter(end);
        } catch (java.time.format.DateTimeParseException ignored) {
            return false;
        }
    }

    private boolean hasCompleteProfile(Member member) {
        return java.util.stream.Stream.of(member.getName(), member.getEmail(), member.getPhoneNumber(),
                        member.getDepartment(), member.getStudentID())
                .allMatch(value -> value != null && !value.isBlank());
    }

    private int totalRecognizedHours(Member member, List<Session> relevantSessions, int projectCount) {
        java.math.BigDecimal hours = java.math.BigDecimal.ZERO;
        for (Session session : relevantSessions) {
            String loginId = member.getLoginID();
            boolean hosted = session.getActivity().getHost().getLoginID().equals(loginId);
            boolean attended = session.getAttendedMemberLoginIDs().contains(loginId);
            if (hosted) {
                hours = hours.add(java.math.BigDecimal.valueOf(session.getHour()).multiply(new java.math.BigDecimal("2.5")));
            } else if (attended) {
                hours = hours.add(java.math.BigDecimal.valueOf(session.getHour()));
            }
        }
        hours = hours.add(java.math.BigDecimal.TEN.multiply(java.math.BigDecimal.valueOf(projectCount)));
        return hours.setScale(0, RoundingMode.FLOOR).intValue();
    }

    private boolean isClubWifiRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String remoteAddress = clientIp(request);
        return java.util.Arrays.stream(clubWifiAllowedIps.split(","))
                .map(String::trim)
                .anyMatch(remoteAddress::equals);
    }

    private String clientIp(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        return realIp != null && !realIp.isBlank() ? realIp.trim() : request.getRemoteAddr();
    }

    private String periodKey(String type, LocalDate date) {
        if ("DAILY".equals(type)) return date.toString();
        if ("REPEATABLE".equals(type)) return "REPEATABLE-" + YearSemester.of(date);
        if ("PERMANENT".equals(type)) return "PERMANENT";
        return YearSemester.of(date).toString();
    }

    @Transactional
    public GameSummaryResponse getSummary(Member authenticatedMember) {
        Member member = memberRepository.findByUUIDForUpdate(authenticatedMember.getUUID())
                .orElseThrow(() -> new NoSuchElementException("회원을 찾을 수 없습니다."));
        synchronizeActivityHourReward(member);

        List<CollectionDraw> draws = collectionDrawRepository.findAllByMemberUuidWithCollectible(member.getUUID());
        Set<Long> collected = draws.stream().map(draw -> draw.getCollectible().getId()).collect(Collectors.toSet());
        Set<Long> normalCollected = draws.stream().filter(draw -> !draw.isShiny())
                .map(draw -> draw.getCollectible().getId()).collect(Collectors.toSet());
        Set<Long> shinyCollected = draws.stream().filter(CollectionDraw::isShiny)
                .map(draw -> draw.getCollectible().getId()).collect(Collectors.toSet());
        long catalogCount = collectibleCatalogRepository.countByEnabledTrue();
        ShinyDrawStatus shinyDrawStatus = shinyDrawStatus(member.getUUID(), normalCollected);
        return new GameSummaryResponse(
                getBallBalances(member),
                catalogCount,
                collected.size(),
                shinyCollected.size(),
                normalCollected.size(),
                catalogCount * 2,
                normalCollected.size() + shinyCollected.size(),
                shinyDrawStatus);
    }

    public List<CollectionItemResponse> getCollection(Member member) {
        Map<Long, List<CollectionDraw>> drawsByCollectibleId = collectionDrawRepository.findAllByMemberUuidWithCollectible(member.getUUID())
                .stream()
                .collect(Collectors.groupingBy(draw -> draw.getCollectible().getId()));
        return collectibleCatalogRepository.findAllByOrderByGenerationAscExternalIdAsc().stream()
                .filter(CollectibleCatalog::isEnabled)
                .map(collectible -> {
                    List<CollectionDraw> draws = drawsByCollectibleId.getOrDefault(collectible.getId(), Collections.emptyList());
                    return new CollectionItemResponse(
                            collectible,
                            draws.stream().anyMatch(draw -> !draw.isShiny()),
                            draws.stream().anyMatch(CollectionDraw::isShiny));
                })
                .collect(Collectors.toList());
    }

    public List<CollectionItemResponse> getAdminCatalog() {
        return collectibleCatalogRepository.findAllByOrderByGenerationAscExternalIdAsc().stream()
                .filter(CollectibleCatalog::isEnabled)
                .map(collectible -> new CollectionItemResponse(collectible, false, false))
                .collect(Collectors.toList());
    }

    @Transactional
    public AdminPokemonGrantResponse grantPokemon(Member admin, AdminPokemonGrantRequest request) {
        requireTechnician(admin);
        if (request == null || request.getLoginID() == null || request.getLoginID().isBlank() || request.getCollectibleId() == null) {
            throw new ConflictException("지급할 회원과 포켓몬을 선택해 주세요.");
        }
        String grantReason = validateGrantReason(request.getReason());
        Member recipient = memberRepository.findByLoginID(request.getLoginID())
                .orElseThrow(() -> new NoSuchElementException("회원을 찾을 수 없습니다."));
        requireOtherMember(admin, recipient);
        CollectibleCatalog collectible = collectibleCatalogRepository.findById(request.getCollectibleId())
                .filter(CollectibleCatalog::isEnabled)
                .orElseThrow(() -> new NoSuchElementException("포켓몬을 찾을 수 없습니다."));
        if (collectionDrawRepository.existsByMemberUuidAndCollectibleIdAndShiny(
                recipient.getUUID(), collectible.getId(), request.isShiny())) {
            throw new ConflictException("이미 보유한 포켓몬입니다.");
        }
        CollectionDraw grant = collectionDrawRepository.save(
                new CollectionDraw(recipient, collectible, request.isShiny(), admin, grantReason));
        return new AdminPokemonGrantResponse(grant, false);
    }

    public List<AdminPokemonGrantResponse> getAdminGrantHistory() {
        Set<String> drawTransactionIds = new java.util.HashSet<>(ballTransactionRepository.findDrawSourceIds(
                BallTransactionType.DRAW, List.of("DRAW", "SHINY_DRAW")));
        return collectionDrawRepository.findAllForAdminHistory().stream()
                .filter(draw -> draw.getGrantedBy() != null || !drawTransactionIds.contains(draw.getId().toString()))
                .map(draw -> new AdminPokemonGrantResponse(draw,
                        draw.getGrantedBy() == null && !drawTransactionIds.contains(draw.getId().toString())))
                .limit(100)
                .collect(Collectors.toList());
    }

    @Transactional
    public AdminBallGrantResponse grantBalls(Member admin, AdminBallGrantRequest request) {
        requireTechnician(admin);
        if (request == null || request.getLoginID() == null || request.getLoginID().isBlank()
                || request.getBallType() == null || request.getAmount() == null || request.getAmount() <= 0) {
            throw new ConflictException("회원, 포켓볼 종류와 양수를 입력해 주세요.");
        }
        String grantReason = validateGrantReason(request.getReason());
        if (request.getBallType() != BallType.NORMAL && request.getBallType() != BallType.MASTER) {
            throw new ConflictException("지급할 수 없는 포켓볼 종류입니다.");
        }
        Member recipient = memberRepository.findByLoginID(request.getLoginID())
                .orElseThrow(() -> new NoSuchElementException("회원을 찾을 수 없습니다."));
        requireOtherMember(admin, recipient);
        BallTransaction transaction = ballTransactionRepository.save(new BallTransaction(
                recipient,
                request.getAmount(),
                BallTransactionType.ADMIN_GRANT,
                request.getBallType(),
                "ADMIN_BALL_GRANT",
                java.util.UUID.randomUUID().toString(),
                admin,
                grantReason));
        return new AdminBallGrantResponse(transaction);
    }

    public List<AdminBallGrantResponse> getAdminBallGrantHistory() {
        return ballTransactionRepository.findAdminBallGrants(BallTransactionType.ADMIN_GRANT, ADMIN_BALL_GRANT_SOURCES).stream()
                .filter(transaction -> !("BULK_GRANT".equals(transaction.getSourceType())
                        && transaction.getBallType() == BallType.NORMAL
                        && transaction.getAmount() == 30
                        && HIDDEN_INITIAL_BULK_GRANT_AT.equals(transaction.getCreatedAt())))
                .map(AdminBallGrantResponse::new)
                .collect(Collectors.toList());
    }

    private void requireTechnician(Member member) {
        if (member == null || !member.getRoles().hasRole(MemberRole.TECHNICIAN)) {
            throw new UnauthorizedException("기여자 권한이 필요합니다.");
        }
    }

    private String validateGrantReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new ConflictException("지급 사유를 입력해 주세요.");
        }
        String normalizedReason = reason.trim();
        if (normalizedReason.length() > 500) {
            throw new ConflictException("지급 사유는 500자 이하로 입력해 주세요.");
        }
        return normalizedReason;
    }

    private void requireOtherMember(Member giver, Member recipient) {
        if (giver.getUUID().equals(recipient.getUUID())) {
            throw new ConflictException("자기 자신에게는 지급할 수 없습니다.");
        }
    }

    public List<DrawResponse> getDraws(Member member) {
        return collectionDrawRepository.findRecentByMemberUuidWithCollectible(member.getUUID(), PageRequest.of(0, 5)).stream()
                .map(DrawResponse::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public DrawResponse draw(Member authenticatedMember) {
        return draw(authenticatedMember, false);
    }

    @Transactional
    public DrawResponse draw(Member authenticatedMember, boolean shiny) {
        Member member = memberRepository.findByUUIDForUpdate(authenticatedMember.getUUID())
                .orElseThrow(() -> new NoSuchElementException("회원을 찾을 수 없습니다."));
        synchronizeActivityHourReward(member);
        return drawForMember(member, shiny);
    }

    @Transactional
    public BallBalancesResponse exchangeMasterBall(Member authenticatedMember) {
        Member member = memberRepository.findByUUIDForUpdate(authenticatedMember.getUUID())
                .orElseThrow(() -> new NoSuchElementException("회원을 찾을 수 없습니다."));
        synchronizeActivityHourReward(member);
        if (ballTransactionRepository.getBalanceByMemberUuidAndBallType(member.getUUID(), BallType.NORMAL) < 20) {
            throw new ConflictException("마스터볼 교환에는 포켓볼 20개가 필요합니다.");
        }
        String exchangeId = "MASTER_BALL_" + java.util.UUID.randomUUID();
        ballTransactionRepository.save(new BallTransaction(member, -20, BallTransactionType.EXCHANGE, BallType.NORMAL, "MASTER_BALL_EXCHANGE", exchangeId));
        ballTransactionRepository.save(new BallTransaction(member, 1, BallTransactionType.EXCHANGE, BallType.MASTER, "MASTER_BALL_EXCHANGE", exchangeId));
        return getBallBalances(member);
    }

    private DrawResponse drawForMember(Member member, boolean shiny) {
        int drawCost = 1;
        BallType ballType = shiny ? BallType.MASTER : BallType.NORMAL;
        long ballCount = ballTransactionRepository.getBalanceByMemberUuidAndBallType(member.getUUID(), ballType);
        if (ballCount < drawCost) {
            throw new ConflictException(shiny ? "이로치 뽑기에는 마스터볼 1개가 필요합니다." : "사용할 포켓볼이 부족합니다.");
        }

        EnumSet<CollectibleRarity> availableCandidateRarities = EnumSet.noneOf(CollectibleRarity.class);
        for (CollectibleRarity rarity : availableRarities(ballType)) {
            if (collectibleCatalogRepository.existsUncollectedVariantByMemberUuidAndRarity(member.getUUID(), rarity, shiny)) {
                availableCandidateRarities.add(rarity);
            }
        }
        if (availableCandidateRarities.isEmpty()) {
            if (shiny) {
                ShinyDrawStatus shinyDrawStatus = shinyDrawStatus(member.getUUID(), null);
                throw new ConflictException(ShinyDrawStatus.NEEDS_NORMAL == shinyDrawStatus
                        ? "이로치 뽑기는 일반 포켓몬을 먼저 획득한 뒤 이용할 수 있습니다."
                        : "획득한 포켓몬의 이로치를 모두 수집했습니다.");
            }
            throw new ConflictException("모든 일반 포켓몬을 수집했습니다.");
        }

        CollectibleRarity rarity = selectRarity(availableCandidateRarities);
        List<CollectibleCatalog> candidates = collectibleCatalogRepository
                .findUncollectedVariantByMemberUuidAndRarity(member.getUUID(), rarity, shiny, PageRequest.of(0, 1));
        CollectibleCatalog collectible = candidates.get(0);
        CollectionDraw draw = collectionDrawRepository.save(new CollectionDraw(member, collectible, shiny));
        ballTransactionRepository.save(new BallTransaction(member, -drawCost, BallTransactionType.DRAW, ballType,
                shiny ? "SHINY_DRAW" : "DRAW", draw.getId().toString()));
        return new DrawResponse(draw, getBallBalances(member));
    }

    private ShinyDrawStatus shinyDrawStatus(String memberUuid, Set<Long> normalCollected) {
        boolean hasNormalCollectible = normalCollected != null
                ? !normalCollected.isEmpty()
                : collectionDrawRepository.findAllByMemberUuidWithCollectible(memberUuid).stream()
                        .anyMatch(draw -> !draw.isShiny());
        if (!hasNormalCollectible) {
            return ShinyDrawStatus.NEEDS_NORMAL;
        }
        return collectibleCatalogRepository.existsUncollectedShinyVariantForMember(memberUuid)
                ? ShinyDrawStatus.AVAILABLE
                : ShinyDrawStatus.COMPLETE;
    }

    private void synchronizeActivityHourReward(Member member) {
        MyActivitySummaryResponse activitySummary = memberService.getMyActivitySummary(member);
        int rewardableHours = activitySummary.getTotalHours().setScale(0, RoundingMode.FLOOR).intValue();
        String semester = YearSemester.of(LocalDate.now()).toString();
        long grantedHours = ballTransactionRepository.getAmountByMemberUuidAndTypeAndSource(
                member.getUUID(), BallTransactionType.ACTIVITY_HOUR_REWARD, ACTIVITY_HOURS, semester);

        if (rewardableHours > grantedHours) {
            ballTransactionRepository.save(new BallTransaction(
                    member,
                    Math.toIntExact(rewardableHours - grantedHours),
                    BallTransactionType.ACTIVITY_HOUR_REWARD,
                    BallType.NORMAL,
                    ACTIVITY_HOURS,
                    semester));
        }
    }

    private CollectibleRarity selectRarity(Set<CollectibleRarity> availableRarities) {
        int totalWeight = availableRarities.stream().mapToInt(this::rarityWeight).sum();
        int roll = RANDOM.nextInt(totalWeight);
        for (CollectibleRarity rarity : CollectibleRarity.values()) {
            if (!availableRarities.contains(rarity)) {
                continue;
            }
            roll -= rarityWeight(rarity);
            if (roll < 0) {
                return rarity;
            }
        }
        throw new IllegalStateException("뽑기 등급을 선택하지 못했습니다.");
    }

    private int rarityWeight(CollectibleRarity rarity) {
        switch (rarity) {
            case COMMON: return 70;
            case RARE: return 24;
            case EPIC: return 5;
            case LEGENDARY: return 1;
            default: throw new IllegalArgumentException("알 수 없는 포켓몬 등급입니다.");
        }
    }

    private EnumSet<CollectibleRarity> availableRarities(BallType ballType) {
        switch (ballType) {
            case NORMAL: return EnumSet.allOf(CollectibleRarity.class);
            case MASTER: return EnumSet.allOf(CollectibleRarity.class);
            case RARE: return EnumSet.of(CollectibleRarity.RARE, CollectibleRarity.EPIC, CollectibleRarity.LEGENDARY);
            case EPIC: return EnumSet.of(CollectibleRarity.EPIC, CollectibleRarity.LEGENDARY);
            case LEGENDARY: return EnumSet.of(CollectibleRarity.LEGENDARY);
            default: throw new IllegalArgumentException("알 수 없는 포켓볼입니다.");
        }
    }

    private BallBalancesResponse getBallBalances(Member member) {
        return new BallBalancesResponse(
                ballTransactionRepository.getBalanceByMemberUuidAndBallType(member.getUUID(), BallType.NORMAL),
                ballTransactionRepository.getBalanceByMemberUuidAndBallType(member.getUUID(), BallType.MASTER));
    }

    private static class AchievementDefinition {
        private final String key;
        private final String type;
        private final String title;
        private final String description;
        private final int target;
        private final BallType rewardBallType;
        private final int rewardAmount;

        private AchievementDefinition(String key, String type, String title, String description,
                                      int target, BallType rewardBallType, int rewardAmount) {
            this.key = key;
            this.type = type;
            this.title = title;
            this.description = description;
            this.target = target;
            this.rewardBallType = rewardBallType;
            this.rewardAmount = rewardAmount;
        }
    }
}
