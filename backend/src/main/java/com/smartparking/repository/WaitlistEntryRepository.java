package com.smartparking.repository;

import com.smartparking.model.User;
import com.smartparking.model.WaitlistEntry;
import com.smartparking.model.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {

    List<WaitlistEntry> findByStatusOrderByCreatedAtAsc(WaitlistStatus status);

    boolean existsByUserAndStatus(User user, WaitlistStatus status);

    long countByStatus(WaitlistStatus status);
}
