import urllib.request
import json
import subprocess
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

def fetch_log(job_id):
    req = urllib.request.Request(f"https://api.github.com/repos/7atem/NEMORY/actions/jobs/{job_id}/logs", headers=headers)
    opener = urllib.request.build_opener(NoRedirect)
    try:
        opener.open(req)
        return ""
    except urllib.error.HTTPError as error:
        if error.code != 302:
            raise
        location = error.headers['Location']
        # Fetch from redirected Azure URL without GitHub token
        with urllib.request.urlopen(location) as resp:
            return resp.read().decode('utf-8', errors='replace')

def check_run(run_id, name):
    print(f"\n==================== RUN {run_id} ({name}) ====================")
    req = urllib.request.Request(f'https://api.github.com/repos/7atem/NEMORY/actions/runs/{run_id}/jobs', headers=headers)
    with urllib.request.urlopen(req) as resp:
        jobs = json.load(resp)['jobs']
    for job in jobs:
        print(f"\nJob: {job['name']} (ID: {job['id']}) - Status: {job['status']}, Conclusion: {job['conclusion']}")
        for step in job['steps']:
            if step['conclusion'] == 'failure':
                print(f"  FAILED STEP: {step['name']}")
                
        if job['conclusion'] == 'failure':
            try:
                log_text = fetch_log(job['id'])
                lines = log_text.splitlines()
                err_lines = []
                for i, l in enumerate(lines):
                    lower = l.lower()
                    if any(w in lower for w in ['error:', 'fatal:', 'failed', 'failure', 'exception', 'what went wrong']):
                        start = max(0, i - 2)
                        end = min(len(lines), i + 8)
                        for idx in range(start, end):
                            err_lines.append(f"{idx+1}: {lines[idx]}")
                print(f"Total lines in log: {len(lines)}")
                print("\n--- ERROR LOG EXCERPT ---")
                seen = set()
                filtered = []
                for el in err_lines:
                    if el not in seen:
                        seen.add(el)
                        filtered.append(el)
                print('\n'.join(filtered[-60:]))
            except Exception as e:
                print(f"Could not load log: {e}")

check_run('39771048460', 'iOS validation')
check_run('39771048380', 'Android validation')
