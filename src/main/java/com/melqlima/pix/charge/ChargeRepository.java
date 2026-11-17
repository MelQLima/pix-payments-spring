package com.melqlima.pix.charge;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargeRepository extends JpaRepository<Charge, UUID> {

    Optional<Charge> findByTxid(String txid);
}
