import requests
import json

# Test a simple API call with minimal parameters
def test_simple_api_call():
    url = "http://localhost:8081/api/run"
    headers = {'Content-Type': 'application/json'}
    
    # Use the example parameters from the codebase
    payload = {
        "optimizationAlgorithm": "EPSO",
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
        "vmScheduler": "TimeShared",
        "numCloudlets": 5,
        "workloadType": "Random",
        "useDefaultWorkload": True,
        "makespanWeight": 0.25,
        "costWeight": 0.25,
        "energyWeight": 0.25,
        "loadBalanceWeight": 0.25
    }
    
    print("Sending request to:", url)
    print("Request payload:", json.dumps(payload, indent=2))
    
    try:
        response = requests.post(url, headers=headers, json=payload, timeout=30)
        print(f"\nResponse Status Code: {response.status_code}")
        print(f"Response Headers: {dict(response.headers)}")
        
        if response.status_code == 200:
            data = response.json()
            print("\nResponse Data:")
            print(json.dumps(data, indent=2))
        else:
            print("\nError Response:")
            print(response.text)
            
    except Exception as e:
        print(f"\nException occurred: {type(e).__name__}: {str(e)}")

if __name__ == "__main__":
    test_simple_api_call()
