import urllib.request, json, subprocess, time, sys

def get_token():
    proc = subprocess.run(['git', 'credential', 'fill'], input='protocol=https\nhost=github.com\n', capture_output=True, text=True)
    for line in proc.stdout.splitlines():
        if line.startswith('password='): return line.split('=', 1)[1].strip()
    return None

token = get_token()
headers = {'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json', 'User-Agent': 'Nemory-Monitor'}

# Find runs for latest commit
commit_proc = subprocess.run(['git', 'rev-parse', 'HEAD'], capture_output=True, text=True)
head_sha = commit_proc.stdout.strip()
print(f"Monitoring runs for commit {head_sha[:7]}...", flush=True)

ios_run_id = None
android_run_id = None

for retry in range(15):
    try:
        req = urllib.request.Request('https://api.github.com/repos/7atem/NEMORY/actions/runs?per_page=10', headers=headers)
        with urllib.request.urlopen(req, timeout=30) as r:
            runs_data = json.load(r)
        for r in runs_data.get('workflow_runs', []):
            if r.get('head_sha') == head_sha:
                name = r.get('name', '').lower()
                if 'ios' in name and ios_run_id is None:
                    ios_run_id = str(r['id'])
                elif ('android' in name or 'ci' in name) and android_run_id is None:
                    android_run_id = str(r['id'])
        if ios_run_id and android_run_id:
            break
    except Exception as e:
        print(f"Lookup error: {e}", flush=True)
    time.sleep(3)

print(f"Discovered runs: iOS={ios_run_id}, Android={android_run_id}", flush=True)

runs_to_track = []
if ios_run_id: runs_to_track.append((ios_run_id, "iOS"))
if android_run_id: runs_to_track.append((android_run_id, "Android"))

for iteration in range(120):
    all_done = True
    print(f"\n--- Check #{iteration + 1} at {time.strftime('%X')} ---", flush=True)
    for run_id, label in runs_to_track:
        try:
            req = urllib.request.Request(f'https://api.github.com/repos/7atem/NEMORY/actions/runs/{run_id}', headers=headers)
            with urllib.request.urlopen(req, timeout=30) as r:
                run_data = json.load(r)
            status = run_data.get('status')
            conclusion = run_data.get('conclusion')
            print(f"[{label}] Run {run_id}: status={status}, conclusion={conclusion}", flush=True)

            if status != 'completed':
                all_done = False
                j_req = urllib.request.Request(f'https://api.github.com/repos/7atem/NEMORY/actions/runs/{run_id}/jobs', headers=headers)
                with urllib.request.urlopen(j_req, timeout=30) as jr:
                    j_data = json.load(jr)
                for job in j_data.get('jobs', []):
                    current_step = next((s['name'] for s in job['steps'] if s['status'] == 'in_progress'), None)
                    completed_steps = [s['name'] for s in job['steps'] if s['status'] == 'completed']
                    last_step = completed_steps[-1] if completed_steps else 'None'
                    print(f"   Job {job['name']}: {job['status']} | Active: {current_step} | Completed: {last_step}", flush=True)
        except Exception as e:
            print(f"Error checking {label}: {e}", flush=True)
            all_done = False

    if all_done:
        print("\nAll workflow runs finished!", flush=True)
        break

    time.sleep(25)

if ios_run_id:
    print("\nFetching iOS artifacts...", flush=True)
    try:
        req = urllib.request.Request(f'https://api.github.com/repos/7atem/NEMORY/actions/runs/{ios_run_id}/artifacts', headers=headers)
        with urllib.request.urlopen(req, timeout=30) as r:
            art_data = json.load(r)
        for a in art_data.get('artifacts', []):
            print(f"Artifact: {a['name']} ({a['size_in_bytes'] / (1024*1024):.2f} MB) - ID {a['id']}", flush=True)
    except Exception as e:
        print(f"Artifact lookup error: {e}", flush=True)
