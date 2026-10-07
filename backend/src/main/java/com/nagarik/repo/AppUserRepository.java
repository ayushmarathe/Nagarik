package com.nagarik.repo;

import com.nagarik.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /**
     * Email is stored lower-cased, so this is an exact match rather than an
     * ignore-case scan - which also means it can use the unique index.
     */
    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByDisplayNameIgnoreCase(String displayName);

    boolean existsByEmail(String email);

    boolean existsByDisplayNameIgnoreCase(String displayName);
}
