import requests
import json
import time
import sys
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
        
    def create_complete_params(self, algorithm: str, size: str = "small") -> Dict[str, Any]:
        """Create complete parameters with all required fields"""
        base_params = {
            "optimizationAlgorithm": algorithm,
            "vmScheduler": "TimeShared",
            "workloadType": "Random",
            "useDefaultWorkload": True,
            "makespanWeight": 0.25,
            "costWeight": 0.25,
            "energyWeight": 0.25,
            "loadBalanceWeight": 0.25
        }
        
        if size == "small":
            base_params.update({
                "numHosts": 2,
                "numVMs": 4,
                "numPesPerHost": 2,
                "peMips": 1000,
                "ramPerHost": 4096,
                "bwPerHost": 10000,
                "storagePerHost": 100000,
                "vmMips": 500,
                "vmPes": 1,
                "vmRam": 1024,
                "vmBw": 1000,
                "vmSize": 10000,
                "numCloudlets": 5
            })
        elif size == "medium":
            base_params.update({
                "numHosts": 5,
                "numVMs": 20,
                "numPesPerHost": 4,
                "peMips": 2000,
                "ramPerHost": 8192,
                "bwPerHost": 100000,
                "storagePerHost": 1000000,
                "vmMips": 1000,
                "vmPes": 2,
                "vmRam": 2048,
                "vmBw": 10000,
                "vmSize": 50000,
                "numCloudlets": 50
            })
        elif size == "large":
            base_params.update({
                "numHosts": 10,
                "numVMs": 50,
                "numPesPerHost": 8,
                "peMips": 3000,
                "ramPerHost": 16384,
                "bwPerHost": 1000000,
                "storagePerHost": 10000000,
                "vmMips": 2000,
                "vmPes": 4,
                "vmRam": 4096,
                "vmBw": 100000,
                "vmSize": 100000,
                "numCloudlets": 200
            })
            
        return base_params
        
    def test_api_run(self, algorithm: str, size: str) -> Dict[str, Any]:
        """Test /api/run endpoint"""
        endpoint = f"{BASE_URL}/api/run"
        self.log(f"Testing {endpoint} with {algorithm} ({size} configuration)...")
        
        params = self.create_complete_params(algorithm, size)
        
        try:
            response = requests.post(endpoint, headers=HEADERS, json=params, timeout=60)
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
                    "size": size,
                    "success": True,
                    "data": data,
                    "analysis": analysis
                }
            else:
                return {
                    "endpoint": endpoint,
                    "algorithm": algorithm,
                    "size": size,
                    "success": False,
                    "error": data
                }
                
        except Exception as e:
            self.log(f"Exception testing {endpoint}: {str(e)}", "ERROR")
            return {
                "endpoint": endpoint,
                "algorithm": algorithm,
                "size": size,
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
                    improvement = abs(epso_val - eaco_val) / max(epso_val, eaco_val, 0.001) * 100
                elif metric in better_higher:
                    winner = "EPSO" if epso_val > eaco_val else "EACO"
                    improvement = abs(epso_val - eaco_val) / max(epso_val, eaco_val, 0.001) * 100
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
        
        configurations = ["small", "medium", "large"]
        
        for config_size in configurations:
            self.log(f"\nTesting {config_size.upper()} Configuration")
            self.log("-"*40)
            
            # Test EPSO
            epso_result = self.test_api_run("EPSO", config_size)
            self.results.append(epso_result)
            
            # Add delay between tests
            time.sleep(1)
            
            # Test EACO
            eaco_result = self.test_api_run("EACO", config_size)
            self.results.append(eaco_result)
            
            # Compare algorithms for this configuration
            if epso_result.get("success") and eaco_result.get("success"):
                self.compare_algorithms(epso_result, eaco_result)
                
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
        
        # Summary by configuration size
        self.log("\nResults by Configuration Size:")
        for size in ["small", "medium", "large"]:
            size_results = [r for r in self.results if r.get("size") == size]
            size_success = sum(1 for r in size_results if r.get("success"))
            self.log(f"  {size.upper()}: {size_success}/{len(size_results)} successful")
        
        # List failed tests
        if failed_tests > 0:
            self.log("\nFailed Tests:")
            for result in self.results:
                if not result.get("success"):
                    self.log(f"  - {result.get('endpoint', 'Unknown')} ({result.get('algorithm', 'N/A')}, {result.get('size', 'N/A')})")
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
                    "success_rate": (successful_tests/total_tests)*100 if total_tests > 0 else 0
                },
                "results": self.results
            }, f, indent=2)
            
        self.log(f"\nDetailed report saved to: {report_filename}")
        
        # Display overall performance comparison
        self.log("\n" + "="*60)
        self.log("OVERALL PERFORMANCE SUMMARY")
        self.log("="*60)
        
        # Calculate average metrics for each algorithm
        epso_results = [r for r in self.results if r.get("algorithm") == "EPSO" and r.get("success")]
        eaco_results = [r for r in self.results if r.get("algorithm") == "EACO" and r.get("success")]
        
        if epso_results and eaco_results:
            self.log("\nAverage Metrics Across All Configurations:")
            self.log(f"{'Metric':<20} {'EPSO Avg':>12} {'EACO Avg':>12}")
            self.log("-" * 45)
            
            for metric in EXPECTED_METRICS:
                epso_values = [r["analysis"]["metrics_found"].get(metric, 0) 
                             for r in epso_results 
                             if metric in r["analysis"]["metrics_found"]]
                eaco_values = [r["analysis"]["metrics_found"].get(metric, 0) 
                             for r in eaco_results 
                             if metric in r["analysis"]["metrics_found"]]
                
                if epso_values and eaco_values:
                    epso_avg = sum(epso_values) / len(epso_values)
                    eaco_avg = sum(eaco_values) / len(eaco_values)
                    self.log(f"{metric:<20} {epso_avg:>12.4f} {eaco_avg:>12.4f}")


def main():
    # Check if server is running
    try:
        response = requests.get(f"{BASE_URL}/api/test/matlab-status", timeout=5)
        if response.status_code == 200:
            data = response.json()
            print(f"✓ Backend server is running")
            print(f"✓ MATLAB available: {data.get('matlabAvailable', False)}")
            print(f"✓ MATLAB engine ready: {data.get('engineReady', False)}")
            print()
        else:
            print("ERROR: Backend server is not responding properly.")
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
