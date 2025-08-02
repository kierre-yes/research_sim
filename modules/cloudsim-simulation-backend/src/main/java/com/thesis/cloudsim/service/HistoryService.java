package com.thesis.cloudsim.service;

import com.thesis.cloudsim.dto.SimulationHistoryEntry;
import org.springframework.stereotype.Service;

import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

@Service
public class HistoryService {
    
    // Simple in-memory storage (can be replaced with database later)
    private final Deque<SimulationHistoryEntry> historyStore = new ConcurrentLinkedDeque<>();
    private static final int MAX_HISTORY_SIZE = 1000;
    
    public List<SimulationHistoryEntry> getHistory(int limit) {
        return historyStore.stream()
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    public SimulationHistoryEntry saveHistory(SimulationHistoryEntry entry) {
        // Add to the beginning of the deque (most recent first)
        historyStore.addFirst(entry);
        
        // Maintain size limit
        while (historyStore.size() > MAX_HISTORY_SIZE) {
            historyStore.removeLast();
        }
        
        return entry;
    }
    
    public void deleteHistory(String id) {
        historyStore.removeIf(entry -> entry.getId().equals(id));
    }
    
    public void clearHistory() {
        historyStore.clear();
    }
}
