import urllib.request, json, subprocess, sys

def get_token():
    proc = subprocess.run(['git', 'credential', 'fill'], input='protocol=https\nhost=github.com\n', capture_output=True, text=True)
    for line in proc.stdout.splitlines():
        if line.startswith('password='): return line.split('=', 1)[1].strip()
    return None

headers = {'Authorization': f'Bearer {get_token()}', 'Accept': 'application/vnd.github+json', 'User-Agent': 'Nemory'}

run_id = sys.argv[1] if len(sys.argv) > 1 else '39938280430'
req = urllib.request.Request(f'https://api.github.com/repos/7atem/NEMORY/actions/runs/{run_id}/jobs', headers=headers)
with urllib.request.urlopen(req) as r:
    data = json.load(r)
for job in data['jobs']:
    print(f"\nJob {job['name']}: {job['status']} ({job['conclusion']})")
    for s in job['steps']:
        print(f"   [{s['status']} | {s['conclusion']}] {s['name']}")

