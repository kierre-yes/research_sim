package com.thesis.cloudsim.controller;

import com.thesis.cloudsim.dto.SimulationHistoryEntry;
import com.thesis.cloudsim.service.HistoryService;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public ResponseEntity<List<SimulationHistoryEntry>> getHistory(
            @RequestParam(defaultValue = "50") int limit) {
        List<SimulationHistoryEntry> history = historyService.getHistory(limit);
        return ResponseEntity.ok(history);
    }

    @PostMapping
    public ResponseEntity<SimulationHistoryEntry> saveHistory(
            @RequestBody SimulationHistoryEntry entry) {
        SimulationHistoryEntry saved = historyService.saveHistory(entry);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHistory(@PathVariable String id) {
        historyService.deleteHistory(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearHistory() {
        historyService.clearHistory();
        return ResponseEntity.noContent().build();
    }
}
