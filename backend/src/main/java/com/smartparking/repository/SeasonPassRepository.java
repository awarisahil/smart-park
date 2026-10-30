package com.smartparking.repository;

import com.smartparking.model.PassStatus;
import com.smartparking.model.SeasonPass;
import com.smartparking.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeasonPassRepository extends JpaRepository<SeasonPass, Long> {

    List<SeasonPass> findByUserOrderByCreatedAtDesc(User user);

    List<SeasonPass> findByUserAndStatus(User user, PassStatus status);
}
