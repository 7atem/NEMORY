import urllib.request, json, subprocess, sys

def get_token():
    proc = subprocess.run(['git', 'credential', 'fill'], input='protocol=https\nhost=github.com\n', capture_output=True, text=True)
    for line in proc.stdout.splitlines():
        if line.startswith('password='): return line.split('=', 1)[1].strip()
    return None

token = get_token()
headers = {'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json', 'User-Agent': 'Nemory'}

# If run_id is supplied via argv, use it; otherwise fetch latest run
if len(sys.argv) > 1:
    run_id = sys.argv[1]
else:
    req = urllib.request.Request('https://api.github.com/repos/7atem/NEMORY/actions/runs?per_page=1', headers=headers)
    with urllib.request.urlopen(req) as r:
        data = json.load(r)
    run_id = data['workflow_runs'][0]['id']

print(f"Checking run ID: {run_id}")
req = urllib.request.Request(f'https://api.github.com/repos/7atem/NEMORY/actions/runs/{run_id}/artifacts', headers=headers)
with urllib.request.urlopen(req) as r:
    data = json.load(r)

print('Total artifacts:', data['total_count'])
for a in data['artifacts']:
    size_mb = a['size_in_bytes'] / (1024 * 1024)
    print(f"Artifact Name: {a['name']}")
    print(f"Size: {size_mb:.2f} MB")
    print(f"Download URL: {a['archive_download_url']}")
    print(f"Artifact ID: {a['id']}")
    print(f"Web URL: https://github.com/7atem/NEMORY/actions/runs/{run_id}")
