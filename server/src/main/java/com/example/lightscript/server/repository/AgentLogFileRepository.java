package com.example.lightscript.server.repository;

import com.example.lightscript.server.entity.AgentLogFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentLogFileRepository extends JpaRepository<AgentLogFile, Long> {
    List<AgentLogFile> findByCollectionIdOrderByFileNameAsc(Long collectionId);
    long countByCollectionId(Long collectionId);
    long countByCollectionIdAndUploadStatus(Long collectionId, String uploadStatus);
}
