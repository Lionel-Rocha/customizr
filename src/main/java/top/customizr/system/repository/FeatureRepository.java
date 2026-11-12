package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.customizr.system.model.Feature;

import java.util.UUID;

public interface FeatureRepository extends JpaRepository<Feature, UUID> {}
