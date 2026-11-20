package org.poolc.api.gamification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.poolc.api.common.exception.ConflictException;
import org.poolc.api.auth.exception.UnauthorizedException;
import org.poolc.api.common.domain.YearSemester;
import org.poolc.api.gamification.domain.BallTransaction;
import org.poolc.api.gamification.domain.BallTransactionType;
import org.poolc.api.gamification.domain.BallType;
import org.poolc.api.gamification.domain.CollectionDraw;
import org.poolc.api.gamification.domain.AchievementProgress;
import org.poolc.api.gamification.domain.CollectibleCatalog;
import org.poolc.api.gamification.domain.CollectibleRarity;
import org.poolc.api.gamification.dto.DrawResponse;
import org.poolc.api.gamification.dto.GameSummaryResponse;
import org.poolc.api.gamification.dto.ShinyDrawStatus;
import org.poolc.api.gamification.dto.CollectionItemResponse;
import org.poolc.api.gamification.dto.AchievementResponse;
import org.poolc.api.gamification.dto.AdminBallGrantRequest;
import org.poolc.api.gamification.dto.AdminPokemonGrantRequest;
import org.poolc.api.gamification.repository.AchievementProgressRepository;
import org.poolc.api.gamification.repository.BallTransactionRepository;
import org.poolc.api.gamification.repository.CollectionDrawRepository;
import org.poolc.api.gamification.repository.CollectibleCatalogRepository;
import org.poolc.api.gamification.repository.MemberFeaturedCollectibleRepository;
import org.poolc.api.activity.repository.ActivityRepository;
import org.poolc.api.activity.repository.SessionRepository;
import org.poolc.api.member.domain.Member;
import org.poolc.api.member.domain.MemberRole;
import org.poolc.api.member.domain.MemberRoles;
import org.poolc.api.member.dto.MyActivitySummaryResponse;
import org.poolc.api.member.repository.MemberRepository;
import org.poolc.api.member.service.MemberService;
import org.poolc.api.project.repository.ProjectRepository;
import org.poolc.api.scrap.repository.ScrapRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GamificationServiceTest {
    @Mock private BallTransactionRepository ballTransactionRepository;
    @Mock private CollectionDrawRepository collectionDrawRepository;
    @Mock private CollectibleCatalogRepository collectibleCatalogRepository;
    @Mock private MemberFeaturedCollectibleRepository featuredCollectibleRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private MemberService memberService;
    @Mock private AchievementProgressRepository achievementProgressRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private ScrapRepository scrapRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private Member member;

    private GamificationService service;

    @BeforeEach
    void setUp() {
        service = new GamificationService(
                ballTransactionRepository, collectionDrawRepository, collectibleCatalogRepository,
                memberRepository, memberService, achievementProgressRepository, sessionRepository,
                activityRepository, scrapRepository, projectRepository, featuredCollectibleRepository);
        lenient().when(member.getUUID()).thenReturn("member-uuid");
        lenient().when(member.getLoginID()).thenReturn("member-login-id");
        lenient().when(memberRepository.findByUUID("member-uuid")).thenReturn(Optional.of(member));
        lenient().when(memberRepository.findByUUIDForUpdate("member-uuid")).thenReturn(Optional.of(member));
        lenient().when(sessionRepository.findAllWithActivityAndAttendanceInSemester(any(), any(), any())).thenReturn(Collections.emptyList());
        lenient().when(sessionRepository.findAll()).thenReturn(Collections.emptyList());
        lenient().when(sessionRepository.findAllRelevantToMember("member-login-id")).thenReturn(Collections.emptyList());
        lenient().when(activityRepository.findActivitiesByActivityMembers("member-login-id")).thenReturn(Collections.emptyList());
        lenient().when(activityRepository.findActivitiesByHost(member)).thenReturn(Collections.emptyList());
        lenient().when(scrapRepository.countByMemberIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq("member-login-id"), any(), any())).thenReturn(0L);
        lenient().when(projectRepository.findProjectsByProjectMembers("member-login-id")).thenReturn(Collections.emptyList());
        lenient().when(achievementProgressRepository.findAllByMemberUuidAndAchievementKey("member-uuid", "DAILY_ATTENDANCE"))
                .thenReturn(Collections.emptyList());
        lenient().when(achievementProgressRepository.findAllByMemberUuid("member-uuid")).thenReturn(Collections.emptyList());
    }

    @Test
    void adminWithoutTechnicianRoleCannotGrantPokemonOrBalls() {
        when(member.getRoles()).thenReturn(MemberRoles.getDefaultFor(MemberRole.ADMIN));

        assertThatThrownBy(() -> service.grantPokemon(member, pokemonGrantRequest("other-member")))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.grantBalls(member, ballGrantRequest("other-member")))
                .isInstanceOf(UnauthorizedException.class);
        verify(collectionDrawRepository, never()).save(any(CollectionDraw.class));
        verify(ballTransactionRepository, never()).save(any(BallTransaction.class));
    }

    @Test
    void technicianCannotGrantPokemonOrBallsToSelf() {
        when(member.getRoles()).thenReturn(MemberRoles.getDefaultFor(MemberRole.TECHNICIAN));
        when(memberRepository.findByLoginID("member-login-id")).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.grantPokemon(member, pokemonGrantRequest("member-login-id")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("자기 자신");
        assertThatThrownBy(() -> service.grantBalls(member, ballGrantRequest("member-login-id")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("자기 자신");
        verify(collectionDrawRepository, never()).save(any(CollectionDraw.class));
        verify(ballTransactionRepository, never()).save(any(BallTransaction.class));
    }

    @Test
    void technicianCanGrantBallsToAnotherMember() {
        Member recipient = org.mockito.Mockito.mock(Member.class);
        when(member.getRoles()).thenReturn(MemberRoles.getDefaultFor(MemberRole.TECHNICIAN));
        when(recipient.getUUID()).thenReturn("other-uuid");
        when(memberRepository.findByLoginID("other-member")).thenReturn(Optional.of(recipient));
        when(ballTransactionRepository.save(any(BallTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.grantBalls(member, ballGrantRequest("other-member"));

        ArgumentCaptor<BallTransaction> captor = ArgumentCaptor.forClass(BallTransaction.class);
        verify(ballTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getGrantedBy()).isSameAs(member);
        assertThat(captor.getValue().getMember()).isSameAs(recipient);
        assertThat(captor.getValue().getAmount()).isEqualTo(1);
    }

    private AdminPokemonGrantRequest pokemonGrantRequest(String loginId) {
        AdminPokemonGrantRequest request = new AdminPokemonGrantRequest();
        request.setLoginID(loginId);
        request.setCollectibleId(1L);
        return request;
    }

    private AdminBallGrantRequest ballGrantRequest(String loginId) {
        AdminBallGrantRequest request = new AdminBallGrantRequest();
        request.setLoginID(loginId);
        request.setBallType(BallType.NORMAL);
        request.setAmount(1);
        return request;
    }

    @Test
    void currentSemesterHoursGrantOnlyTheUnpaidWholeHours() {
        when(memberService.getMyActivitySummary(member)).thenReturn(activitySummary("3.7"));
        when(ballTransactionRepository.getAmountByMemberUuidAndTypeAndSource(
                "member-uuid", BallTransactionType.ACTIVITY_HOUR_REWARD, "ACTIVITY_HOURS", currentSemester()))
                .thenReturn(1L);
        when(ballTransactionRepository.getBalanceByMemberUuidAndBallType("member-uuid", BallType.NORMAL)).thenReturn(3L);
        when(collectionDrawRepository.findAllByMemberUuidWithCollectible("member-uuid")).thenReturn(Collections.emptyList());
        when(collectibleCatalogRepository.countByEnabledTrue()).thenReturn(0L);

        service.getSummary(member);

        ArgumentCaptor<BallTransaction> transactionCaptor = ArgumentCaptor.forClass(BallTransaction.class);
        verify(ballTransactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getAmount()).isEqualTo(2);
        assertThat(transactionCaptor.getValue().getType()).isEqualTo(BallTransactionType.ACTIVITY_HOUR_REWARD);
    }

    @Test
    void decreasedActivityHoursDoNotReverseGrantedBalls() {
        when(memberService.getMyActivitySummary(member)).thenReturn(activitySummary("3"));
        when(ballTransactionRepository.getAmountByMemberUuidAndTypeAndSource(
                "member-uuid", BallTransactionType.ACTIVITY_HOUR_REWARD, "ACTIVITY_HOURS", currentSemester()))
                .thenReturn(4L);
        when(ballTransactionRepository.getBalanceByMemberUuidAndBallType("member-uuid", BallType.NORMAL)).thenReturn(4L);
        when(collectionDrawRepository.findAllByMemberUuidWithCollectible("member-uuid")).thenReturn(Collections.emptyList());
        when(collectibleCatalogRepository.countByEnabledTrue()).thenReturn(0L);

        service.getSummary(member);

        verify(ballTransactionRepository, never()).save(any(BallTransaction.class));
    }

    @Test
    void clubWifiQuestAcceptsOnlyConfiguredClientIps() {
        ReflectionTestUtils.setField(service, "clubWifiAllowedIps", "203.0.113.10, 203.0.113.11");
        MockHttpServletRequest allowedRequest = new MockHttpServletRequest();
        allowedRequest.setRemoteAddr("203.0.113.10");
        MockHttpServletRequest proxiedAllowedRequest = new MockHttpServletRequest();
        proxiedAllowedRequest.setRemoteAddr("172.20.0.3");
        proxiedAllowedRequest.addHeader("X-Real-IP", "203.0.113.11");
        MockHttpServletRequest rejectedRequest = new MockHttpServletRequest();
        rejectedRequest.setRemoteAddr("203.0.113.12");

        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "isClubWifiRequest", allowedRequest)).isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "isClubWifiRequest", proxiedAllowedRequest)).isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "isClubWifiRequest", rejectedRequest)).isFalse();
    }

    @Test
    void drawWithNoBallsDoesNotConsumeOrCreateResult() {
        when(memberService.getMyActivitySummary(member)).thenReturn(activitySummary("0"));
        when(ballTransactionRepository.getAmountByMemberUuidAndTypeAndSource(
                eq("member-uuid"), eq(BallTransactionType.ACTIVITY_HOUR_REWARD), eq("ACTIVITY_HOURS"), eq(currentSemester())))
                .thenReturn(0L);
        when(ballTransactionRepository.getBalanceByMemberUuidAndBallType("member-uuid", BallType.NORMAL)).thenReturn(0L);

        assertThatThrownBy(() -> service.draw(member)).isInstanceOf(ConflictException.class);

        verify(collectionDrawRepository, never()).save(any(CollectionDraw.class));
        verify(ballTransactionRepository, never()).save(any(BallTransaction.class));
    }

    @Test
    void drawWithBallsButNoUncollectedCatalogDoesNotConsumeABall() {
        when(memberService.getMyActivitySummary(member)).thenReturn(activitySummary("0"));
        when(ballTransactionRepository.getAmountByMemberUuidAndTypeAndSource(
                eq("member-uuid"), eq(BallTransactionType.ACTIVITY_HOUR_REWARD), eq("ACTIVITY_HOURS"), eq(currentSemester())))
                .thenReturn(0L);
        when(ballTransactionRepository.getBalanceByMemberUuidAndBallType("member-uuid", BallType.NORMAL)).thenReturn(1L);
        for (CollectibleRarity rarity : CollectibleRarity.values()) {
            when(collectibleCatalogRepository.existsUncollectedVariantByMemberUuidAndRarity("member-uuid", rarity, false))
                    .thenReturn(false);
        }

        assertThatThrownBy(() -> service.draw(member)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("모든 일반 포켓몬을 수집했습니다");

        verify(collectionDrawRepository, never()).save(any(CollectionDraw.class));
        verify(ballTransactionRepository, never()).save(any(BallTransaction.class));
    }

    @Test
    void drawConsumesOneBallAndCreatesTheCollectionRecordTogether() {
        CollectibleCatalog collectible = org.mockito.Mockito.mock(CollectibleCatalog.class);
        CollectionDraw savedDraw = org.mockito.Mockito.mock(CollectionDraw.class);
        when(memberService.getMyActivitySummary(member)).thenReturn(activitySummary("0"));
        when(ballTransactionRepository.getAmountByMemberUuidAndTypeAndSource(
                eq("member-uuid"), eq(BallTransactionType.ACTIVITY_HOUR_REWARD), eq("ACTIVITY_HOURS"), eq(currentSemester())))
                .thenReturn(0L);
        when(ballTransactionRepository.getBalanceByMemberUuidAndBallType("member-uuid", BallType.NORMAL)).thenReturn(1L);
        when(collectibleCatalogRepository.existsUncollectedVariantByMemberUuidAndRarity("member-uuid", CollectibleRarity.COMMON, false))
                .thenReturn(true);
        when(collectibleCatalogRepository.findUncollectedVariantByMemberUuidAndRarity(eq("member-uuid"), eq(CollectibleRarity.COMMON), eq(false), any(Pageable.class)))
                .thenReturn(List.of(collectible));
        when(collectionDrawRepository.save(any(CollectionDraw.class))).thenReturn(savedDraw);
        when(savedDraw.getId()).thenReturn(1L);
        when(savedDraw.getCollectible()).thenReturn(collectible);
        when(savedDraw.getRarityAtDraw()).thenReturn(CollectibleRarity.COMMON);
        when(savedDraw.getDrawnAt()).thenReturn(LocalDateTime.now());
        when(collectible.getRarity()).thenReturn(CollectibleRarity.COMMON);

        DrawResponse response = service.draw(member);

        assertThat(response.getDrawId()).isEqualTo(1L);
        verify(collectionDrawRepository).save(any(CollectionDraw.class));
        ArgumentCaptor<BallTransaction> transactionCaptor = ArgumentCaptor.forClass(BallTransaction.class);
        verify(ballTransactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getAmount()).isEqualTo(-1);
        assertThat(transactionCaptor.getValue().getType()).isEqualTo(BallTransactionType.DRAW);
    }

    @Test
    void shinyDrawConsumesOneMasterBallAndCreatesAShinyCollectionRecord() {
        CollectibleCatalog collectible = org.mockito.Mockito.mock(CollectibleCatalog.class);
        CollectionDraw savedDraw = org.mockito.Mockito.mock(CollectionDraw.class);
        when(memberService.getMyActivitySummary(member)).thenReturn(activitySummary("0"));
        when(ballTransactionRepository.getAmountByMemberUuidAndTypeAndSource(
                eq("member-uuid"), eq(BallTransactionType.ACTIVITY_HOUR_REWARD), eq("ACTIVITY_HOURS"), eq(currentSemester())))
                .thenReturn(0L);
        when(ballTransactionRepository.getBalanceByMemberUuidAndBallType("member-uuid", BallType.MASTER)).thenReturn(1L);
        when(collectibleCatalogRepository.existsUncollectedVariantByMemberUuidAndRarity("member-uuid", CollectibleRarity.COMMON, true))
                .thenReturn(true);
        when(collectibleCatalogRepository.findUncollectedVariantByMemberUuidAndRarity(eq("member-uuid"), eq(CollectibleRarity.COMMON), eq(true), any(Pageable.class)))
                .thenReturn(List.of(collectible));
        when(collectionDrawRepository.save(any(CollectionDraw.class))).thenReturn(savedDraw);
        when(savedDraw.getId()).thenReturn(1L);
        when(savedDraw.getCollectible()).thenReturn(collectible);
        when(savedDraw.getRarityAtDraw()).thenReturn(CollectibleRarity.COMMON);
        when(savedDraw.getDrawnAt()).thenReturn(LocalDateTime.now());
        when(savedDraw.isShiny()).thenReturn(true);
        when(collectible.getRarity()).thenReturn(CollectibleRarity.COMMON);

        service.draw(member, true);

        ArgumentCaptor<CollectionDraw> drawCaptor = ArgumentCaptor.forClass(CollectionDraw.class);
        verify(collectionDrawRepository).save(drawCaptor.capture());
        assertThat(drawCaptor.getValue().isShiny()).isTrue();
        ArgumentCaptor<BallTransaction> transactionCaptor = ArgumentCaptor.forClass(BallTransaction.class);
        verify(ballTransactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getAmount()).isEqualTo(-1);
        assertThat(transactionCaptor.getValue().getBallType()).isEqualTo(BallType.MASTER);
    }

    @Test
    void summaryRequiresANormalCollectibleBeforeShinyDraw() {
        stubSummaryPrerequisites(Collections.emptyList());
        when(collectibleCatalogRepository.countByEnabledTrue()).thenReturn(1L);

        GameSummaryResponse response = service.getSummary(member);

        assertThat(response.getShinyDrawStatus()).isEqualTo(ShinyDrawStatus.NEEDS_NORMAL);
        assertThat(response.getTotalCatalogCount()).isEqualTo(1L);
        assertThat(response.getTotalVariantCount()).isEqualTo(2L);
    }

    @Test
    void summaryAllowsShinyDrawWhenAnOwnedNormalHasNoShinyVariant() {
        CollectionDraw normalDraw = org.mockito.Mockito.mock(CollectionDraw.class);
        CollectibleCatalog collectible = org.mockito.Mockito.mock(CollectibleCatalog.class);
        when(normalDraw.isShiny()).thenReturn(false);
        when(normalDraw.getCollectible()).thenReturn(collectible);
        when(collectible.getId()).thenReturn(1L);
        stubSummaryPrerequisites(List.of(normalDraw));
        when(collectibleCatalogRepository.countByEnabledTrue()).thenReturn(1L);
        when(collectibleCatalogRepository.existsUncollectedShinyVariantForMember("member-uuid")).thenReturn(true);

        GameSummaryResponse response = service.getSummary(member);

        assertThat(response.getShinyDrawStatus()).isEqualTo(ShinyDrawStatus.AVAILABLE);
        assertThat(response.getCollectedCatalogCount()).isEqualTo(1L);
        assertThat(response.getNormalCatalogCount()).isEqualTo(1L);
        assertThat(response.getCollectedVariantCount()).isEqualTo(1L);
    }

    @Test
    void summaryKeepsLegacySpeciesCountSeparateFromVariantCount() {
        CollectibleCatalog collectible = org.mockito.Mockito.mock(CollectibleCatalog.class);
        CollectionDraw normalDraw = org.mockito.Mockito.mock(CollectionDraw.class);
        CollectionDraw shinyDraw = org.mockito.Mockito.mock(CollectionDraw.class);
        when(normalDraw.isShiny()).thenReturn(false);
        when(normalDraw.getCollectible()).thenReturn(collectible);
        when(shinyDraw.isShiny()).thenReturn(true);
        when(shinyDraw.getCollectible()).thenReturn(collectible);
        when(collectible.getId()).thenReturn(1L);
        stubSummaryPrerequisites(List.of(normalDraw, shinyDraw));
        when(collectibleCatalogRepository.countByEnabledTrue()).thenReturn(1L);
        when(collectibleCatalogRepository.existsUncollectedShinyVariantForMember("member-uuid")).thenReturn(false);

        GameSummaryResponse response = service.getSummary(member);

        assertThat(response.getCollectedCatalogCount()).isEqualTo(1L);
        assertThat(response.getCollectedVariantCount()).isEqualTo(2L);
        assertThat(response.getShinyCatalogCount()).isEqualTo(1L);
    }

    @Test
    void collectionExposesNormalAndShinyOwnershipSeparately() {
        CollectibleCatalog collectible = org.mockito.Mockito.mock(CollectibleCatalog.class);
        CollectionDraw normalDraw = org.mockito.Mockito.mock(CollectionDraw.class);
        CollectionDraw shinyDraw = org.mockito.Mockito.mock(CollectionDraw.class);
        when(normalDraw.isShiny()).thenReturn(false);
        when(normalDraw.getCollectible()).thenReturn(collectible);
        when(shinyDraw.isShiny()).thenReturn(true);
        when(shinyDraw.getCollectible()).thenReturn(collectible);
        when(collectible.getId()).thenReturn(1L);
        when(collectible.isEnabled()).thenReturn(true);
        when(collectionDrawRepository.findAllByMemberUuidWithCollectible("member-uuid"))
                .thenReturn(List.of(normalDraw, shinyDraw));
        when(collectibleCatalogRepository.findAllByOrderByGenerationAscExternalIdAsc()).thenReturn(List.of(collectible));

        List<CollectionItemResponse> response = service.getCollection(member);

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.isNormalOwned()).isTrue();
            assertThat(item.isShinyOwned()).isTrue();
        });
    }

    @Test
    void summaryMarksShinyDrawCompleteWhenAllOwnedNormalsHaveShinyVariants() {
        CollectionDraw normalDraw = org.mockito.Mockito.mock(CollectionDraw.class);
        CollectibleCatalog collectible = org.mockito.Mockito.mock(CollectibleCatalog.class);
        when(normalDraw.isShiny()).thenReturn(false);
        when(normalDraw.getCollectible()).thenReturn(collectible);
        when(collectible.getId()).thenReturn(1L);
        stubSummaryPrerequisites(List.of(normalDraw));
        when(collectibleCatalogRepository.countByEnabledTrue()).thenReturn(1L);
        when(collectibleCatalogRepository.existsUncollectedShinyVariantForMember("member-uuid")).thenReturn(false);

        GameSummaryResponse response = service.getSummary(member);

        assertThat(response.getShinyDrawStatus()).isEqualTo(ShinyDrawStatus.COMPLETE);
    }

    @Test
    void shinyDrawRequiresANormalCollectible() {
        stubShinyDrawWithoutCandidates(Collections.emptyList());

        assertThatThrownBy(() -> service.draw(member, true))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("일반 포켓몬을 먼저 획득");
    }

    @Test
    void shinyDrawRejectsWhenEveryOwnedNormalAlreadyHasAShinyVariant() {
        CollectionDraw normalDraw = org.mockito.Mockito.mock(CollectionDraw.class);
        when(normalDraw.isShiny()).thenReturn(false);
        stubShinyDrawWithoutCandidates(List.of(normalDraw));
        when(collectibleCatalogRepository.existsUncollectedShinyVariantForMember("member-uuid")).thenReturn(false);

        assertThatThrownBy(() -> service.draw(member, true))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("이로치를 모두 수집했습니다");
    }

    @Test
    void seasonCollectionAchievementCountsNormalAndShinyAsOneSpecies() {
        CollectibleCatalog collectible = org.mockito.Mockito.mock(CollectibleCatalog.class);
        CollectionDraw normalDraw = org.mockito.Mockito.mock(CollectionDraw.class);
        CollectionDraw shinyDraw = org.mockito.Mockito.mock(CollectionDraw.class);
        when(collectible.getId()).thenReturn(1L);
        when(normalDraw.getCollectible()).thenReturn(collectible);
        when(normalDraw.getDrawnAt()).thenReturn(LocalDateTime.now());
        when(shinyDraw.getCollectible()).thenReturn(collectible);
        when(shinyDraw.getDrawnAt()).thenReturn(LocalDateTime.now());
        when(collectionDrawRepository.findAllByMemberUuidWithCollectible("member-uuid"))
                .thenReturn(List.of(normalDraw, shinyDraw));
        when(memberService.getMyActivitySummary(member)).thenReturn(activitySummary("0"));
        when(achievementProgressRepository.findByMemberUuidAndAchievementKeyAndPeriodKey(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(achievementProgressRepository.save(any(AchievementProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<AchievementResponse> responses = service.getAchievements(member);

        assertThat(responses)
                .filteredOn(response -> "SEASON_COLLECTION".equals(response.getKey()))
                .singleElement()
                .extracting(AchievementResponse::getProgress)
                .isEqualTo(1);
    }

    @Test
    void approvedMemberCanClaimTheJoinApprovalAchievement() {
        when(member.isMember()).thenReturn(true);
        when(memberService.getMyActivitySummary(member)).thenReturn(activitySummary("0"));
        when(achievementProgressRepository.findByMemberUuidAndAchievementKeyAndPeriodKey(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(achievementProgressRepository.save(any(AchievementProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<AchievementResponse> responses = service.getAchievements(member);

        assertThat(responses)
                .filteredOn(response -> "PERMANENT_MEMBER_APPROVED".equals(response.getKey()))
                .singleElement()
                .extracting(AchievementResponse::getProgress, AchievementResponse::getRewardAmount)
                .containsExactly(1, 30);
    }

    @Test
    void seasonRoleAchievementRemainsCompletedForTheCurrentSemesterAfterTheRoleChanges() {
        AchievementProgress previouslyCompleted = new AchievementProgress(
                member, "SEASON_ADMIN", currentSemester(), 1);
        when(member.getRole()).thenReturn("MEMBER");
        when(memberService.getMyActivitySummary(member)).thenReturn(activitySummary("0"));
        when(achievementProgressRepository.findAllByMemberUuid("member-uuid"))
                .thenReturn(List.of(previouslyCompleted));

        List<AchievementResponse> responses = service.getAchievements(member);

        assertThat(responses)
                .filteredOn(response -> "SEASON_ADMIN".equals(response.getKey()))
                .singleElement()
                .extracting(AchievementResponse::getProgress)
                .isEqualTo(1);
    }

    @Test
    void seasonAchievementKeepsItsHighestProgressForTheCurrentSemester() {
        AchievementProgress previouslyRecorded = new AchievementProgress(
                member, "SEASON_SCRAPS", currentSemester(), 5);
        when(memberService.getMyActivitySummary(member)).thenReturn(activitySummary("0"));
        when(achievementProgressRepository.findAllByMemberUuid("member-uuid"))
                .thenReturn(List.of(previouslyRecorded));

        List<AchievementResponse> responses = service.getAchievements(member);

        assertThat(responses)
                .filteredOn(response -> "SEASON_SCRAPS".equals(response.getKey()))
                .singleElement()
                .extracting(AchievementResponse::getProgress)
                .isEqualTo(5);
    }

    @Test
    void clubWifiAchievementProgressIsStoredForTheCurrentDay() {
        ReflectionTestUtils.setField(service, "clubWifiAllowedIps", "203.0.113.10");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        when(memberService.getMyActivitySummary(member)).thenReturn(activitySummary("0"));
        when(achievementProgressRepository.save(any(AchievementProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.getAchievements(member, request);

        ArgumentCaptor<AchievementProgress> progressCaptor = ArgumentCaptor.forClass(AchievementProgress.class);
        verify(achievementProgressRepository, org.mockito.Mockito.atLeastOnce()).save(progressCaptor.capture());
        assertThat(progressCaptor.getAllValues())
                .anySatisfy(progress -> {
                    assertThat(progress.getAchievementKey()).isEqualTo("DAILY_CLUB_WIFI");
                    assertThat(progress.getPeriodKey()).isEqualTo(LocalDate.now().toString());
                    assertThat(progress.getProgress()).isEqualTo(1);
                });
    }

    private MyActivitySummaryResponse activitySummary(String totalHours) {
        return MyActivitySummaryResponse.builder()
                .totalHours(new BigDecimal(totalHours))
                .seminarStudyHours(BigDecimal.ZERO)
                .officialActivityHours(BigDecimal.ZERO)
                .projectHours(BigDecimal.ZERO)
                .seminarStudyActivities(Collections.emptyList())
                .officialActivities(Collections.emptyList())
                .projectActivities(Collections.emptyList())
                .build();
    }

    private String currentSemester() {
        return YearSemester.of(LocalDate.now()).toString();
    }

    private void stubSummaryPrerequisites(List<CollectionDraw> draws) {
        when(memberService.getMyActivitySummary(member)).thenReturn(activitySummary("0"));
        when(ballTransactionRepository.getAmountByMemberUuidAndTypeAndSource(
                eq("member-uuid"), eq(BallTransactionType.ACTIVITY_HOUR_REWARD), eq("ACTIVITY_HOURS"), eq(currentSemester())))
                .thenReturn(0L);
        when(ballTransactionRepository.getBalanceByMemberUuidAndBallType("member-uuid", BallType.NORMAL)).thenReturn(0L);
        when(collectionDrawRepository.findAllByMemberUuidWithCollectible("member-uuid")).thenReturn(draws);
    }

    private void stubShinyDrawWithoutCandidates(List<CollectionDraw> draws) {
        when(memberService.getMyActivitySummary(member)).thenReturn(activitySummary("0"));
        when(ballTransactionRepository.getAmountByMemberUuidAndTypeAndSource(
                eq("member-uuid"), eq(BallTransactionType.ACTIVITY_HOUR_REWARD), eq("ACTIVITY_HOURS"), eq(currentSemester())))
                .thenReturn(0L);
        when(ballTransactionRepository.getBalanceByMemberUuidAndBallType("member-uuid", BallType.MASTER)).thenReturn(1L);
        when(collectionDrawRepository.findAllByMemberUuidWithCollectible("member-uuid")).thenReturn(draws);
        for (CollectibleRarity rarity : CollectibleRarity.values()) {
            when(collectibleCatalogRepository.existsUncollectedVariantByMemberUuidAndRarity("member-uuid", rarity, true))
                    .thenReturn(false);
        }
    }
}
