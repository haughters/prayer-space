package com.prayerlink.common.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class VersionController {

    private final BuildProperties buildProperties;

    public VersionController(@Autowired(required = false) BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    @GetMapping("/version")
    public ResponseEntity<Map<String, String>> getVersion() {
        Map<String, String> info = new HashMap<>();
        
        if (buildProperties != null) {
            info.put("service", buildProperties.getArtifact());
            info.put("version", buildProperties.getVersion());
            info.put("builtAt", buildProperties.getTime().toString());
        } else {
            info.put("service", "unknown");
            info.put("version", "development");
            info.put("builtAt", "unknown");
        }
        
        String commitSha = System.getenv("GIT_COMMIT_SHA");
        info.put("commit", commitSha != null ? commitSha : "local");
        
        return ResponseEntity.ok(info);
    }
}
