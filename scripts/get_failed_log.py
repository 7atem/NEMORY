import urllib.request, json, subprocess, time

def get_token():
    proc = subprocess.run(['git', 'credential', 'fill'], input='protocol=https\nhost=github.com\n', capture_output=True, text=True)
    for line in proc.stdout.splitlines():
        if line.startswith('password='): return line.split('=', 1)[1].strip()
    return None

token = get_token()
headers = {'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json', 'User-Agent': 'Nemory'}

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

req = urllib.request.Request('https://api.github.com/repos/7atem/NEMORY/actions/runs/39831334098/jobs', headers=headers)
with urllib.request.urlopen(req) as r:
    data = json.load(r)
job_id = data['jobs'][0]['id']

found = False
for attempt in range(10):
    try:
        req2 = urllib.request.Request(f'https://api.github.com/repos/7atem/NEMORY/actions/jobs/{job_id}/logs', headers=headers)
        opener = urllib.request.build_opener(NoRedirect)
        try:
            opener.open(req2)
        except urllib.error.HTTPError as err:
            if err.code == 302:
                loc = err.headers['Location']
                req3 = urllib.request.Request(loc)
                with urllib.request.urlopen(req3) as resp:
                    lines = resp.read().decode('utf-8', errors='replace').splitlines()
                    err_lines = [l for l in lines if ': error:' in l]
                    if err_lines:
                        for el in err_lines:
                            print(el)
                    else:
                        for l in lines[-40:]:
                            print(l)
                    found = True
                    break
    except Exception as e:
        print(f"Waiting for log blob (attempt {attempt+1})... {e}")
        time.sleep(3)

if not found:
    print("Could not retrieve logs")
