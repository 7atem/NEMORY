from PIL import Image
import os
import re

source_dir = r'D:\Vault Brain\store-assets\tablet'
output_dir = r'D:\Vault Brain\store-assets\tablet-ready'
os.makedirs(output_dir, exist_ok=True)

# Find screenshot files (ignore (101) duplicates)
pattern = re.compile(r'^Screenshot_.*\.png$')
files = [f for f in os.listdir(source_dir) if pattern.match(f) and '(101)' not in f]
files.sort()

print(f'Found {len(files)} unique tablet screenshots:')
for f in files:
    print(f'  {f}')

for idx, filename in enumerate(files, start=1):
    path = os.path.join(source_dir, filename)
    img = Image.open(path)
    w, h = img.size
    print(f'Processing {filename}: {w}x{h}')
    
    # 2560x1600 is already valid for Google Play tablet screenshots
    # Aspect ratio is 16:10 which is between 16:9 and 9:16
    # Ensure dimensions are within limits
    final_w, final_h = img.size
    if final_w > 3840:
        final_h = int(final_h * 3840 / final_w)
        final_w = 3840
        img = img.resize((final_w, final_h), Image.Resampling.LANCZOS)
    if final_h > 3840:
        final_w = int(final_w * 3840 / final_h)
        final_h = 3840
        img = img.resize((final_w, final_h), Image.Resampling.LANCZOS)
    
    out_path = os.path.join(output_dir, f'nemory-tablet-{idx:02d}.png')
    img.save(out_path, 'PNG', optimize=True)
    size_kb = os.path.getsize(out_path) / 1024
    print(f'  -> {out_path}: {img.size}, {size_kb:.1f} KB')

print('Done. Tablet screenshots ready for upload.')
