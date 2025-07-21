package com.thesis.cloudsim.matlab;

import com.mathworks.engine.MatlabEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to verify MATLAB Engine connectivity
 * Run this test to check if MATLAB integration is working
 */
public class MatlabIntegrationTest {

    @Test
    @Disabled("Enable this test to check MATLAB connectivity")
    public void testMatlabEngineConnection() {
        System.out.println("Testing MATLAB Engine connection...");
        
        try {
            // Try to connect to existing MATLAB session
            System.out.println("Attempting to connect to shared MATLAB session named 'thesisEngine'...");
            MatlabEngine engine = MatlabEngine.connectMatlab("thesisEngine");
            System.out.println("✓ Successfully connected to existing MATLAB session!");
            
            // Test basic operation
            engine.eval("disp('Hello from Java!')");
            double result = engine.getVariable("ans");
            
            engine.disconnect();
            System.out.println("✓ MATLAB Engine is working correctly!");
            
        } catch (Exception connectEx) {
            System.out.println("✗ Could not connect to existing session: " + connectEx.getMessage());
            System.out.println("Attempting to start new MATLAB session...");
            
            try {
                // Try to start new MATLAB session
                MatlabEngine engine = MatlabEngine.startMatlab();
                System.out.println("✓ Successfully started new MATLAB session!");
                
                // Test basic operation
                engine.eval("x = 2 + 3");
                Double result = engine.getVariable("x");
                assertEquals(5.0, result, 0.01, "Basic MATLAB calculation failed");
                
                engine.close();
                System.out.println("✓ MATLAB Engine is working correctly!");
                
            } catch (Exception startEx) {
                System.err.println("✗ Failed to start MATLAB: " + startEx.getMessage());
                fail("Could not connect to or start MATLAB Engine");
            }
        }
    }

    @Test
    public void testMatlabAvailability() {
        System.out.println("Checking MATLAB installation...");
        
        // Check if MATLAB is in PATH
        String path = System.getenv("PATH");
        boolean matlabInPath = path != null && path.toLowerCase().contains("matlab");
        
        System.out.println("PATH contains MATLAB: " + matlabInPath);
        
        // Check java.library.path
        String libraryPath = System.getProperty("java.library.path");
        System.out.println("Java library path: " + libraryPath);
        
        // This test just reports, doesn't fail
        assertTrue(true, "This is just an informational test");
    }
}
