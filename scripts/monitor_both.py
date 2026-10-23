import urllib.request, json, subprocess, time, sys

def get_token():
    proc = subprocess.run(['git', 'credential', 'fill'], input='protocol=https\nhost=github.com\n', capture_output=True, text=True)
    for line in proc.stdout.splitlines():
        if line.startswith('password='): return line.split('=', 1)[1].strip()
    return None

token = get_token()
headers = {'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json', 'User-Agent': 'Nemory-Monitor'}

ios_run_id = '39961456272'
android_run_id = '39961456312'

for iteration in range(100):
    all_done = True
    print(f"\n--- Check #{iteration + 1} at {time.strftime('%X')} ---", flush=True)
    for run_id, label in [(ios_run_id, "iOS"), (android_run_id, "Android")]:
        try:
            req = urllib.request.Request(f'https://api.github.com/repos/7atem/NEMORY/actions/runs/{run_id}', headers=headers)
            with urllib.request.urlopen(req, timeout=30) as r:
                run_data = json.load(r)
            status = run_data.get('status')
            conclusion = run_data.get('conclusion')
            print(f"[{label}] Run {run_id}: status={status}, conclusion={conclusion}", flush=True)

            if status != 'completed':
                all_done = False
                # fetch current step
                j_req = urllib.request.Request(f'https://api.github.com/repos/7atem/NEMORY/actions/runs/{run_id}/jobs', headers=headers)
                with urllib.request.urlopen(j_req, timeout=30) as jr:
                    j_data = json.load(jr)
                for job in j_data.get('jobs', []):
                    current_step = next((s['name'] for s in job['steps'] if s['status'] == 'in_progress'), None)
                    last_step = [s['name'] for s in job['steps'] if s['status'] == 'completed'][-1:] or ['None']
                    print(f"   Job {job['name']}: {job['status']} | Active: {current_step} | Completed: {last_step[0]}", flush=True)
        except Exception as e:
            print(f"Error checking {label}: {e}", flush=True)
            all_done = False

    if all_done:
        print("\nAll workflow runs finished!", flush=True)
        break

    time.sleep(25)

# Check artifacts for iOS
print("\nFetching iOS artifacts...", flush=True)
try:
    req = urllib.request.Request(f'https://api.github.com/repos/7atem/NEMORY/actions/runs/{ios_run_id}/artifacts', headers=headers)
    with urllib.request.urlopen(req, timeout=30) as r:
        art_data = json.load(r)
    print(f"Total artifacts: {art_data['total_count']}", flush=True)
    for a in art_data['artifacts']:
        size_mb = a['size_in_bytes'] / (1024 * 1024)
        print(f"-> {a['name']} ({size_mb:.2f} MB)")
        print(f"   Download URL: {a['archive_download_url']}")
        print(f"   Artifact ID: {a['id']}")
except Exception as e:
    print(f"Error fetching artifacts: {e}", flush=True)
