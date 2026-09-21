package org.poolc.api.activity.repository;

import org.poolc.api.activity.domain.Activity;
import org.poolc.api.activity.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

public interface SessionRepository extends JpaRepository<Session, Long> {

    List<Session> findByActivity(Activity activity);

    @Query(value = "select distinct s from Session s left join fetch s.attendedMemberLoginIDs where s.id=:id")
    Optional<Session> findByIdWithAttendances(@Param("id") Long id);

    @Query("select distinct s from Session s join fetch s.activity a join fetch a.host left join fetch s.attendedMemberLoginIDs where a.startDate between :semesterStartDate and :semesterEndDate and s.date <= :asOfDate")
    List<Session> findAllWithActivityAndAttendanceInSemester(
            @Param("semesterStartDate") LocalDate semesterStartDate,
            @Param("semesterEndDate") LocalDate semesterEndDate,
            @Param("asOfDate") LocalDate asOfDate
    );

    @Query("select distinct s from Session s join fetch s.activity a join fetch a.host "
            + "left join fetch s.attendedMemberLoginIDs attendance "
            + "where a.host.loginID = :loginId or attendance = :loginId")
    List<Session> findAllRelevantToMember(@Param("loginId") String loginId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM session session "
            + "JOIN activity_members member ON member.activity_id = session.activity_id "
            + "WHERE session.id = :sessionId AND member.member_login_id = :memberLoginId)", nativeQuery = true)
    boolean isActivityMember(@Param("sessionId") Long sessionId, @Param("memberLoginId") String memberLoginId);

    @Modifying
    @Query(value = "INSERT INTO attendance (session_id, member_loginid) VALUES (:sessionId, :memberLoginId) "
            + "ON CONFLICT (session_id, member_loginid) DO NOTHING", nativeQuery = true)
    int insertAttendanceIfAbsent(@Param("sessionId") Long sessionId, @Param("memberLoginId") String memberLoginId);


}
