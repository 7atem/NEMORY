import urllib.request
import json
import subprocess
import sys
import time

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

def api_get(url, retries=3):
    for i in range(retries):
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, timeout=30) as resp:
                return json.load(resp)
        except Exception as e:
            if i == retries - 1:
                raise e
            time.sleep(2)

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
        with urllib.request.urlopen(location, timeout=60) as resp:
            return resp.read().decode('utf-8', errors='replace')

def check_latest():
    data = api_get('https://api.github.com/repos/7atem/NEMORY/actions/runs?per_page=4')
    for run in data['workflow_runs']:
        print(f"\n==================== RUN {run['id']}: {run['name']} ====================")
        print(f"Commit: {run['head_sha'][:7]} | Branch: {run['head_branch']} | Status: {run['status']} | Conclusion: {run['conclusion']}")
        print(f"URL: {run['html_url']}")
        
        jobs_data = api_get(f"https://api.github.com/repos/7atem/NEMORY/actions/runs/{run['id']}/jobs")
        for job in jobs_data['jobs']:
            print(f"  Job: {job['name']} -> {job['status']} ({job['conclusion']})")
            for step in job['steps']:
                if step['conclusion'] == 'failure':
                    print(f"    FAILED STEP: {step['name']}")
            
            if job['conclusion'] == 'failure':
                try:
                    log_text = fetch_log(job['id'])
                    lines = log_text.splitlines()
                    err_lines = [l for l in lines if any(w in l.lower() for w in ['error:', 'fatal:', 'failed', 'failure:'])]
                    print("    Recent Errors:")
                    for el in err_lines[-10:]:
                        print(f"      {el[:150]}")
                except Exception as e:
                    print(f"    (Could not fetch log: {e})")

if __name__ == '__main__':
    check_latest()
