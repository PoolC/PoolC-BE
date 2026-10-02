package org.poolc.api.member.domain;

import org.poolc.api.auth.exception.UnauthorizedException;
import org.poolc.api.common.exception.ConflictException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import javax.persistence.*;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;

@Embeddable
public class MemberRoles {
    private static final Set<MemberRole> ADDITIONAL_ROLES = EnumSet.of(
            MemberRole.TECHNICIAN,
            MemberRole.GRADUATED
    );
    private static final Set<MemberRole> AUTOMATICALLY_EXCEPTED_ROLES = EnumSet.of(
            MemberRole.SUPER_ADMIN,
            MemberRole.ADMIN,
            MemberRole.TECHNICIAN,
            MemberRole.GRADUATED_INACTIVE,
            MemberRole.GRADUATED,
            MemberRole.COMPLETE,
            MemberRole.INACTIVE
    );

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "roles", joinColumns = @JoinColumn(name = "member_uuid"))
    @Enumerated(EnumType.STRING)
    private Set<MemberRole> roles = new HashSet<>();

    protected MemberRoles() {
    }

    public MemberRoles(Set<MemberRole> roles) {
        this.roles = new HashSet<>(roles);

        checkRolesAreCorrect();
    }

    public static MemberRoles getDefaultFor(MemberRole role) {
        Set<MemberRole> returnRoles = new HashSet<>();

        returnRoles.add(role);
        returnRoles.addAll(role.getRequiredRoles());

        return new MemberRoles(returnRoles);
    }

    public boolean hasRole(MemberRole role) {
        return roles.contains(role);
    }

    public MemberRole getHighestRole() {
        return Stream.of(MemberRole.values())
                .filter(roles::contains)
                .findFirst()
                .orElse(MemberRole.PUBLIC);
    }

    public MemberRole getBaseRole() {
        return Stream.of(MemberRole.values())
                .filter(role -> !ADDITIONAL_ROLES.contains(role))
                .filter(roles::contains)
                .findFirst()
                .orElse(MemberRole.PUBLIC);
    }

    public Set<MemberRole> getAdditionalRoles() {
        return Stream.of(MemberRole.values())
                .filter(ADDITIONAL_ROLES::contains)
                .filter(roles::contains)
                .collect(Collectors.toSet());
    }

    public boolean isAcceptedMember() {
        return !roles.contains(MemberRole.UNACCEPTED);
    }

    public boolean isExpelled() {
        return !roles.contains(MemberRole.EXPELLED);
    }

    public boolean isMember() {
        return roles.contains(MemberRole.MEMBER);
    }

    public boolean isAdmin() {
        return roles.stream()
                .anyMatch(MemberRole::isAdmin);
    }

    public void changeRole(MemberRole role) {
        if (ADDITIONAL_ROLES.contains(role)) {
            toggleAdditionalRole(role, true);
            return;
        }
        if (role.equals(MemberRole.SUPER_ADMIN)) {
            throw new UnauthorizedException("Usage of super admin is prohibited");
        }

        Set<MemberRole> preservedAdditionalRoles = getAdditionalRoles();
        roles.clear();
        roles.add(role);
        roles.addAll(role.getRequiredRoles());
        if (role.isMember()) {
            roles.addAll(preservedAdditionalRoles);
        }
    }

    public void toggleAdditionalRole(MemberRole role, boolean enabled) {
        if (!ADDITIONAL_ROLES.contains(role)) {
            throw new IllegalArgumentException("Role is not an additional role: " + role.name());
        }
        if (!roles.contains(MemberRole.MEMBER)) {
            throw new ConflictException("Additional roles can only be assigned to members");
        }
        if (enabled) {
            roles.add(role);
        } else {
            roles.remove(role);
        }
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = roles.stream()
                .map(MemberRole::name)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role))
                .collect(Collectors.toSet());
        if (roles.contains(MemberRole.TECHNICIAN)) {
            authorities.add(new SimpleGrantedAuthority(MemberRole.ADMIN.name()));
        }
        return authorities;
    }

    public boolean isAdditionalRole(MemberRole role) {
        return ADDITIONAL_ROLES.contains(role);
    }

    public boolean checkIsExcepted() {
        return AUTOMATICALLY_EXCEPTED_ROLES.contains(getHighestRole());
    }

    private void checkRolesAreCorrect() {
        checkIsMemberButHasNonMemberRole();
        checkIsSpecialRoleButDoesNotHaveMemberRole();
    }

    private void checkIsMemberButHasNonMemberRole() {
        if (!roles.contains(MemberRole.MEMBER)) {
            return;
        }

        roles.stream()
                .filter(not(MemberRole::isMember))
                .findAny()
                .ifPresent(role -> {
                    throw new ConflictException("Member cannot have non-member role: " + role.name());
                });
    }

    private void checkIsSpecialRoleButDoesNotHaveMemberRole() {
        if (roles.contains(MemberRole.MEMBER)) {
            return;
        }

        roles.stream()
                .filter(not(MemberRole.MEMBER::equals))
                .filter(MemberRole::isMember)
                .findAny()
                .ifPresent(specialRole -> {
                    throw new ConflictException(
                            String.format("Special role %s also needs %s role",
                                    specialRole.name(), MemberRole.MEMBER));
                });
    }
}
