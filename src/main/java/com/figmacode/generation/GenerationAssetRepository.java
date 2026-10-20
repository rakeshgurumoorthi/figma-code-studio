package com.figmacode.generation;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface GenerationAssetRepository extends JpaRepository<GenerationAsset, Long> {

    List<GenerationAsset> findByGenerationId(String generationId);

    Optional<GenerationAsset> findByGenerationIdAndFileName(String generationId, String fileName);

    @Transactional
    void deleteByGenerationId(String generationId);
}
