import requests
import json
import time
import sys
import os
from datetime import datetime
from typing import Dict, Any, List, Tuple

# Configuration
BASE_URL = "http://localhost:8081"
HEADERS = {'Content-Type': 'application/json'}

# Define expected metrics based on the codebase
EXPECTED_METRICS = ['makespan', 'responseTime', 'loadBalance', 'resourceUtilization', 
                    'totalCost', 'costEfficiency', 'energyConsumption', 'fitness']

class APITester:
    def __init__(self):
        self.results = []
        self.test_start_time = datetime.now()
        
    def log(self, message: str, level: str = "INFO"):
        """Log messages with timestamp"""
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print(f"[{timestamp}] [{level}] {message}")
        
    def validate_response(self, response: requests.Response, endpoint: str) -> Tuple[bool, Dict[str, Any]]:
        """Validate API response"""
        try:
            if response.status_code == 200:
                data = response.json()
                self.log(f"✓ {endpoint} returned status 200", "SUCCESS")
                return True, data
            else:
                self.log(f"✗ {endpoint} returned status {response.status_code}", "ERROR")
                return False, {"error": f"Status code: {response.status_code}", "content": response.text}
        except Exception as e:
            self.log(f"✗ {endpoint} failed to parse response: {str(e)}", "ERROR")
            return False, {"error": str(e)}
            
    def analyze_metrics(self, data: Dict[str, Any], algorithm: str) -> Dict[str, Any]:
        """Analyze and validate metrics from simulation results"""
        analysis = {
            "algorithm": algorithm,
            "metrics_found": {},
            "missing_metrics": [],
            "validation_errors": []
        }
        
        # Check if we have a summary in the response
        if 'summary' in data:
            summary = data['summary']
            
            # Check each expected metric
            for metric in EXPECTED_METRICS:
                if metric in summary:
                    value = summary[metric]
                    analysis["metrics_found"][metric] = value
                    
                    # Validate metric ranges
                    if value < 0:
                        analysis["validation_errors"].append(f"{metric} has negative value: {value}")
                    
                    # Specific validations
                    if metric == "loadBalance" and not (0 <= value <= 1):
                        analysis["validation_errors"].append(f"loadBalance should be between 0 and 1, got: {value}")
                    if metric == "resourceUtilization" and not (0 <= value <= 100):
                        analysis["validation_errors"].append(f"resourceUtilization should be between 0 and 100, got: {value}")
                else:
                    analysis["missing_metrics"].append(metric)
                    
        else:
            analysis["validation_errors"].append("No 'summary' field in response")
            
        return analysis
        
    def test_simulate_raw(self, algorithm: str, params: Dict[str, Any]) -> Dict[str, Any]:
        """Test /api/simulate/raw endpoint"""
        endpoint = f"{BASE_URL}/api/simulate/raw"
        self.log(f"Testing {endpoint} with {algorithm}...")
        
        try:
            response = requests.post(endpoint, headers=HEADERS, json=params, timeout=30)
            success, data = self.validate_response(response, endpoint)
            
            if success:
                analysis = self.analyze_metrics(data, algorithm)
                
                # Log key metrics
                if analysis["metrics_found"]:
                    self.log(f"Key metrics for {algorithm}:")
                    for metric, value in analysis["metrics_found"].items():
                        self.log(f"  - {metric}: {value:.4f}")
                        
                return {
                    "endpoint": endpoint,
                    "algorithm": algorithm,
                    "success": True,
                    "data": data,
                    "analysis": analysis
                }
            else:
                return {
                    "endpoint": endpoint,
                    "algorithm": algorithm,
                    "success": False,
                    "error": data
                }
                
        except Exception as e:
            self.log(f"Exception testing {endpoint}: {str(e)}", "ERROR")
            return {
                "endpoint": endpoint,
                "algorithm": algorithm,
                "success": False,
                "error": str(e)
            }
            
    def test_simulate_with_plots(self, algorithm: str, params: Dict[str, Any]) -> Dict[str, Any]:
        """Test /api/simulate/with-plots endpoint"""
        endpoint = f"{BASE_URL}/api/simulate/with-plots"
        self.log(f"Testing {endpoint} with {algorithm}...")
        
        try:
            response = requests.post(endpoint, headers=HEADERS, json=params, timeout=60)
            
            # Check if MATLAB is warming up
            if response.status_code == 202:
                self.log("MATLAB engine is warming up, retrying in 5 seconds...", "INFO")
                time.sleep(5)
                response = requests.post(endpoint, headers=HEADERS, json=params, timeout=60)
                
            success, data = self.validate_response(response, endpoint)
            
            if success:
                # Check for plots in response
                has_plots = 'plotPaths' in data if isinstance(data, dict) else False
                
                if has_plots:
                    self.log(f"Generated {len(data['plotPaths'])} plots")
                    
                return {
                    "endpoint": endpoint,
                    "algorithm": algorithm,
                    "success": True,
                    "has_plots": has_plots,
                    "data": data
                }
            else:
                return {
                    "endpoint": endpoint,
                    "algorithm": algorithm,
                    "success": False,
                    "error": data
                }
                
        except Exception as e:
            self.log(f"Exception testing {endpoint}: {str(e)}", "ERROR")
            return {
                "endpoint": endpoint,
                "algorithm": algorithm,
                "success": False,
                "error": str(e)
            }
            
    def test_api_run(self, algorithm: str, params: Dict[str, Any]) -> Dict[str, Any]:
        """Test /api/run endpoint"""
        endpoint = f"{BASE_URL}/api/run"
        self.log(f"Testing {endpoint} with {algorithm}...")
        
        try:
            response = requests.post(endpoint, headers=HEADERS, json=params, timeout=30)
            success, data = self.validate_response(response, endpoint)
            
            if success:
                analysis = self.analyze_metrics(data, algorithm)
                
                return {
                    "endpoint": endpoint,
                    "algorithm": algorithm,
                    "success": True,
                    "data": data,
                    "analysis": analysis
                }
            else:
                return {
                    "endpoint": endpoint,
                    "algorithm": algorithm,
                    "success": False,
                    "error": data
                }
                
        except Exception as e:
            self.log(f"Exception testing {endpoint}: {str(e)}", "ERROR")
            return {
                "endpoint": endpoint,
                "algorithm": algorithm,
                "success": False,
                "error": str(e)
            }
            
    def test_matlab_status(self) -> Dict[str, Any]:
        """Test /api/test/matlab-status endpoint"""
        endpoint = f"{BASE_URL}/api/test/matlab-status"
        self.log(f"Testing {endpoint}...")
        
        try:
            response = requests.get(endpoint, timeout=10)
            success, data = self.validate_response(response, endpoint)
            
            if success:
                self.log(f"MATLAB available: {data.get('matlabAvailable', False)}")
                if data.get('matlabAvailable'):
                    self.log(f"MATLAB engine ready: {data.get('engineReady', False)}")
                    
            return {
                "endpoint": endpoint,
                "success": success,
                "data": data if success else {"error": data}
            }
            
        except Exception as e:
            self.log(f"Exception testing {endpoint}: {str(e)}", "ERROR")
            return {
                "endpoint": endpoint,
                "success": False,
                "error": str(e)
            }
            
    def compare_algorithms(self, epso_results: Dict[str, Any], eaco_results: Dict[str, Any]):
        """Compare EPSO and EACO results"""
        self.log("\n" + "="*60)
        self.log("ALGORITHM COMPARISON")
        self.log("="*60)
        
        if not (epso_results.get("success") and eaco_results.get("success")):
            self.log("Cannot compare - one or both algorithms failed", "ERROR")
            return
            
        epso_metrics = epso_results.get("analysis", {}).get("metrics_found", {})
        eaco_metrics = eaco_results.get("analysis", {}).get("metrics_found", {})
        
        if not (epso_metrics and eaco_metrics):
            self.log("Cannot compare - missing metrics", "ERROR")
            return
            
        # Compare each metric
        comparison = []
        for metric in EXPECTED_METRICS:
            if metric in epso_metrics and metric in eaco_metrics:
                epso_val = epso_metrics[metric]
                eaco_val = eaco_metrics[metric]
                
                # Determine which is better based on metric type
                better_lower = ["makespan", "responseTime", "totalCost", "energyConsumption", "loadBalance"]
                better_higher = ["resourceUtilization", "costEfficiency", "fitness"]
                
                if metric in better_lower:
                    winner = "EPSO" if epso_val < eaco_val else "EACO"
                    improvement = abs(epso_val - eaco_val) / max(epso_val, eaco_val) * 100
                elif metric in better_higher:
                    winner = "EPSO" if epso_val > eaco_val else "EACO"
                    improvement = abs(epso_val - eaco_val) / max(epso_val, eaco_val) * 100
                else:
                    winner = "N/A"
                    improvement = 0
                    
                comparison.append({
                    "metric": metric,
                    "epso": epso_val,
                    "eaco": eaco_val,
                    "winner": winner,
                    "improvement": improvement
                })
                
        # Display comparison table
        self.log("\nMetric Comparison:")
        self.log(f"{'Metric':<20} {'EPSO':>12} {'EACO':>12} {'Winner':>8} {'Improvement':>12}")
        self.log("-" * 65)
        
        for comp in comparison:
            self.log(f"{comp['metric']:<20} {comp['epso']:>12.4f} {comp['eaco']:>12.4f} {comp['winner']:>8} {comp['improvement']:>11.2f}%")
            
    def run_comprehensive_tests(self):
        """Run all tests comprehensively"""
        self.log("Starting Comprehensive API Testing")
        self.log("="*60)
        
        # Test configurations
        test_configs = [
            {
                "name": "Small Configuration",
                "params": {
                    "optimizationAlgorithm": "EPSO",
                    "numHosts": 2,
                    "numVMs": 5,
                    "numCloudlets": 10,
                    "makespanWeight": 0.25,
                    "costWeight": 0.25,
                    "energyWeight": 0.25,
                    "loadBalanceWeight": 0.25
                }
            },
            {
                "name": "Medium Configuration",
                "params": {
                    "optimizationAlgorithm": "EPSO",
                    "numHosts": 5,
                    "numVMs": 20,
                    "numCloudlets": 50,
                    "makespanWeight": 0.25,
                    "costWeight": 0.25,
                    "energyWeight": 0.25,
                    "loadBalanceWeight": 0.25
                }
            },
            {
                "name": "Large Configuration",
                "params": {
                    "optimizationAlgorithm": "EPSO",
                    "numHosts": 10,
                    "numVMs": 50,
                    "numCloudlets": 200,
                    "makespanWeight": 0.25,
                    "costWeight": 0.25,
                    "energyWeight": 0.25,
                    "loadBalanceWeight": 0.25
                }
            }
        ]
        
        # Test MATLAB status first
        self.log("\n1. Testing MATLAB Status")
        matlab_result = self.test_matlab_status()
        self.results.append(matlab_result)
        
        # Test each configuration with both algorithms
        for config in test_configs:
            self.log(f"\n2. Testing {config['name']}")
            
            # Test EPSO
            epso_params = config["params"].copy()
            epso_params["optimizationAlgorithm"] = "EPSO"
            
            self.log("\n  2.1 Testing EPSO Algorithm")
            epso_raw = self.test_simulate_raw("EPSO", epso_params)
            self.results.append(epso_raw)
            
            epso_run = self.test_api_run("EPSO", epso_params)
            self.results.append(epso_run)
            
            # Test EACO
            eaco_params = config["params"].copy()
            eaco_params["optimizationAlgorithm"] = "EACO"
            
            self.log("\n  2.2 Testing EACO Algorithm")
            eaco_raw = self.test_simulate_raw("EACO", eaco_params)
            self.results.append(eaco_raw)
            
            eaco_run = self.test_api_run("EACO", eaco_params)
            self.results.append(eaco_run)
            
            # Compare algorithms for this configuration
            if epso_raw.get("success") and eaco_raw.get("success"):
                self.compare_algorithms(epso_raw, eaco_raw)
                
            # Test with plots if MATLAB is available
            if matlab_result.get("data", {}).get("engineReady"):
                self.log("\n  2.3 Testing with MATLAB plots")
                epso_plots = self.test_simulate_with_plots("EPSO", epso_params)
                self.results.append(epso_plots)
                
                eaco_plots = self.test_simulate_with_plots("EACO", eaco_params)
                self.results.append(eaco_plots)
                
    def generate_report(self):
        """Generate test report"""
        self.log("\n" + "="*60)
        self.log("TEST SUMMARY REPORT")
        self.log("="*60)
        
        total_tests = len(self.results)
        successful_tests = sum(1 for r in self.results if r.get("success"))
        failed_tests = total_tests - successful_tests
        
        self.log(f"Total Tests: {total_tests}")
        self.log(f"Successful: {successful_tests}")
        self.log(f"Failed: {failed_tests}")
        self.log(f"Success Rate: {(successful_tests/total_tests)*100:.2f}%")
        
        # List failed tests
        if failed_tests > 0:
            self.log("\nFailed Tests:")
            for result in self.results:
                if not result.get("success"):
                    self.log(f"  - {result.get('endpoint', 'Unknown')} ({result.get('algorithm', 'N/A')})")
                    self.log(f"    Error: {result.get('error', 'Unknown error')}")
                    
        # Save detailed report
        report_filename = f"test_report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        with open(report_filename, 'w') as f:
            json.dump({
                "test_start_time": self.test_start_time.isoformat(),
                "test_end_time": datetime.now().isoformat(),
                "summary": {
                    "total_tests": total_tests,
                    "successful": successful_tests,
                    "failed": failed_tests,
                    "success_rate": (successful_tests/total_tests)*100
                },
                "results": self.results
            }, f, indent=2)
            
        self.log(f"\nDetailed report saved to: {report_filename}")


def main():
    # Check if server is running
    try:
        response = requests.get(f"{BASE_URL}/api/test/matlab-status", timeout=5)
        if response.status_code != 200:
            print("ERROR: Backend server is not responding. Please ensure it's running on port 8081.")
            sys.exit(1)
    except requests.exceptions.RequestException:
        print("ERROR: Cannot connect to backend server at http://localhost:8081")
        print("Please ensure the backend is running with: mvn spring-boot:run")
        sys.exit(1)
        
    # Run tests
    tester = APITester()
    tester.run_comprehensive_tests()
    tester.generate_report()


if __name__ == "__main__":
    main()
