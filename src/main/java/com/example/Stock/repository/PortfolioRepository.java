package com.example.Stock.repository;

import com.example.Stock.domain.Portfolio;
import com.example.Stock.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    Optional<Portfolio> findByUser(User user);

    boolean existsByUser(User user);
}
