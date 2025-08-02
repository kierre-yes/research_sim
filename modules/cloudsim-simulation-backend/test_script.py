import requests
import json

def test_api_run():
    url = "http://localhost:8081/api/run"
    headers = {'Content-Type': 'application/json'}
    payload = {
        "optimizationAlgorithm": "EPSO",
        "numHosts": 10,
        "numVMs": 20,
        "numCloudlets": 100,
        "makespanWeight": 0.25,
        "costWeight": 0.25,
        "energyWeight": 0.25,
        "loadBalanceWeight": 0.25
    }
    response = requests.post(url, headers=headers, data=json.dumps(payload))
    print("Response for /api/run:", response.json())


def test_file_upload():
    url = "http://localhost:8081/api/test-file-upload"
    files = {'file': ('test.txt', open('test.txt', 'rb'))}
    data = {'testParam': 'example'}
    response = requests.post(url, files=files, data=data)
    print("Response for /api/test-file-upload:", response.json())


def test_run_with_file():
    url = "http://localhost:8081/api/run-with-file"
    files = {'file': ('workload.csv', open('workload.csv', 'rb'))}
    params = {
        "optimizationAlgorithm": "EACO",
        "numHosts": "10",
        "numVMs": "20"
    }
    response = requests.post(url, files=files, data=params)
    print("Response for /api/run-with-file:", response.json())


def test_matlab_status():
    url = "http://localhost:8081/api/test/matlab-status"
    response = requests.get(url)
    print("Response for /api/test/matlab-status:", response.json())


def main():
    test_api_run()
    test_file_upload()
    test_run_with_file()
    test_matlab_status()


if __name__ == "__main__":
    main()
