import urllib.request
import json
import subprocess
import time
import sys

def get_token():
    proc = subprocess.run(['git', 'credential', 'fill'], input='protocol=https\nhost=github.com\n', capture_output=True, text=True)
    for line in proc.stdout.splitlines():
        if line.startswith('password='):
            return line.split('=', 1)[1].strip()
    return None

token = get_token()
headers = {
    'Authorization': f'Bearer {token}',
    'Accept': 'application/vnd.github+json',
    'User-Agent': 'Nemory-CI-Monitor'
}

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

def api_get(url):
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.load(resp)

def fetch_log(job_id):
    req = urllib.request.Request(f"https://api.github.com/repos/7atem/NEMORY/actions/jobs/{job_id}/logs", headers=headers)
    opener = urllib.request.build_opener(NoRedirect)
    try:
        opener.open(req)
        return ""
    except urllib.error.HTTPError as error:
        if error.code != 302:
            return ""
        location = error.headers['Location']
        with urllib.request.urlopen(location, timeout=60) as resp:
            return resp.read().decode('utf-8', errors='replace')

# Fetch latest runs for branch 'main'
runs_data = api_get("https://api.github.com/repos/7atem/NEMORY/actions/runs?branch=main&per_page=5")
latest_runs = runs_data['workflow_runs'][:2]
target_runs = [r['id'] for r in latest_runs]
print("Monitoring latest runs:", [(r['id'], r['name']) for r in latest_runs])

ios_run_id = next((r['id'] for r in latest_runs if 'iOS' in r['name']), target_runs[0])

for attempt in range(40):
    all_done = True
    print(f"\n--- Poll {attempt+1}/40 at {time.strftime('%H:%M:%S')} ---")
    for run_id in target_runs:
        run = api_get(f"https://api.github.com/repos/7atem/NEMORY/actions/runs/{run_id}")
        status = run['status']
        conclusion = run['conclusion']
        print(f"Run {run_id} ({run['name']}): status={status}, conclusion={conclusion}")
        if status != 'completed':
            all_done = False
        else:
            # Check jobs
            jobs_data = api_get(f"https://api.github.com/repos/7atem/NEMORY/actions/runs/{run_id}/jobs")
            for job in jobs_data['jobs']:
                print(f"  Job {job['name']}: {job['status']} ({job['conclusion']})")
                if job['conclusion'] == 'failure':
                    for step in job['steps']:
                        if step['conclusion'] == 'failure':
                            print(f"    Failed Step: {step['name']}")
                    try:
                        logs = fetch_log(job['id'])
                        lines = logs.splitlines()
                        errs = [l for l in lines if any(w in l.lower() for w in ['error:', 'fatal:', 'failed', 'failure:'])]
                        print("    Last 5 error lines:")
                        for el in errs[-5:]:
                            print(f"      {el[:150]}")
                    except Exception as e:
                        print(f"    (Log fetch error: {e})")
    
    if all_done:
        print("\nAll target runs completed!")
        break
    time.sleep(20)

# List artifacts for iOS run
try:
    artifacts = api_get(f"https://api.github.com/repos/7atem/NEMORY/actions/runs/{ios_run_id}/artifacts")
    print(f"\nArtifacts for iOS run {ios_run_id} (Total {artifacts['total_count']}):")
    for a in artifacts['artifacts']:
        print(f"  - {a['name']} ({a['size_in_bytes']} bytes), download URL: {a['archive_download_url']}")
except Exception as e:
    print(f"Could not list artifacts: {e}")
