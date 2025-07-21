package com.thesis.cloudsim.matlab;

import com.mathworks.engine.MatlabEngine;

/**
 * Standalone test to quickly verify MATLAB Engine connectivity
 * Run this with: mvn compile exec:java -Dexec.mainClass="com.thesis.cloudsim.matlab.MatlabQuickTest"
 */
public class MatlabQuickTest {
    
    public static void main(String[] args) {
        System.out.println("=== MATLAB Engine Quick Test ===\n");
        
        // Check environment
        System.out.println("1. Checking environment variables:");
        System.out.println("   PATH: " + (System.getenv("PATH").contains("MATLAB") ? "✓ Contains MATLAB" : "✗ Does not contain MATLAB"));
        System.out.println("   java.library.path: " + System.getProperty("java.library.path"));
        
        // Test MATLAB Engine
        System.out.println("\n2. Testing MATLAB Engine connection:");
        
        try {
            // First, try to connect to a shared session
            System.out.println("   Attempting to connect to shared MATLAB session 'thesisEngine'...");
            MatlabEngine engine = MatlabEngine.connectMatlab("thesisEngine");
            System.out.println("   ✓ Connected to existing MATLAB session!");
            testEngine(engine);
            engine.disconnect();
            
        } catch (Exception e1) {
            System.out.println("   ✗ Could not connect to shared session: " + e1.getMessage());
            
            try {
                // Try to start a new MATLAB session
                System.out.println("\n   Attempting to start new MATLAB session...");
                System.out.println("   (This may take 30-60 seconds...)");
                MatlabEngine engine = MatlabEngine.startMatlab();
                System.out.println("   ✓ Started new MATLAB session!");
                testEngine(engine);
                engine.close();
                
            } catch (Exception e2) {
                System.err.println("   ✗ Failed to start MATLAB: " + e2.getMessage());
                System.err.println("\nPossible solutions:");
                System.err.println("1. Ensure MATLAB is installed");
                System.err.println("2. Add MATLAB to your PATH");
                System.err.println("3. Start MATLAB manually and run: matlab.engine.shareEngine('thesisEngine')");
            }
        }
    }
    
    private static void testEngine(MatlabEngine engine) throws Exception {
        System.out.println("\n3. Running MATLAB commands:");
        
        // Basic calculation
        engine.eval("x = 5 + 3;");
        Double result = engine.getVariable("x");
        System.out.println("   5 + 3 = " + result + " ✓");
        
        // Array operation
        engine.eval("arr = [1, 2, 3, 4, 5];");
        engine.eval("avg = mean(arr);");
        Double avg = engine.getVariable("avg");
        System.out.println("   mean([1,2,3,4,5]) = " + avg + " ✓");
        
        // Test plot generation (without displaying)
        engine.eval("figure('Visible', 'off');");
        engine.eval("plot([1,2,3,4,5], [1,4,9,16,25]);");
        engine.eval("title('Test Plot');");
        System.out.println("   Plot generation test ✓");
        
        System.out.println("\n✓ All MATLAB Engine tests passed!");
    }
}
