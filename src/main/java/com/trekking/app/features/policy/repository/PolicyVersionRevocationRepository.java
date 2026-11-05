package com.trekking.app.features.policy.repository;

import com.trekking.app.features.policy.entity.PolicyVersionRevocation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyVersionRevocationRepository extends JpaRepository<PolicyVersionRevocation, Long> {}
