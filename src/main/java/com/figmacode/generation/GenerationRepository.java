package com.figmacode.generation;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GenerationRepository extends JpaRepository<Generation, String> {

    List<Generation> findTop20ByOwnerOrderByCreatedAtDesc(String owner);

    Optional<Generation> findByIdAndOwner(String id, String owner);
}
