from PIL import Image
import os
import re

source_dir = r'D:\Vault Brain\store-assets'
output_dir = r'D:\Vault Brain\store-assets\screenshots-ready'
os.makedirs(output_dir, exist_ok=True)

# Find screenshot files (ignore files with "(101)" duplicates and existing assets)
pattern = re.compile(r'^Screenshot_.*\.png$')
files = [f for f in os.listdir(source_dir) if pattern.match(f) and '(101)' not in f]
files.sort()

print(f'Found {len(files)} unique screenshots:')
for f in files:
    print(f'  {f}')

for idx, filename in enumerate(files, start=1):
    path = os.path.join(source_dir, filename)
    img = Image.open(path)
    w, h = img.size
    print(f'Processing {filename}: {w}x{h}')
    
    # Google Play wants aspect ratio between 16:9 and 9:16
    # Crop from top if too tall (> 16:9)
    target_h = int(w * 16 / 9)
    if h > target_h:
        # Crop from top
        img = img.crop((0, 0, w, target_h))
    elif h < int(w * 9 / 16):
        # Crop width to match height if too wide
        target_w = int(h * 9 / 16)
        left = (w - target_w) // 2
        img = img.crop((left, 0, left + target_w, h))
    
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
    
    # Save as PNG, compress if needed
    out_path = os.path.join(output_dir, f'nemory-screenshot-{idx:02d}.png')
    img.save(out_path, 'PNG', optimize=True)
    size_kb = os.path.getsize(out_path) / 1024
    print(f'  -> {out_path}: {img.size}, {size_kb:.1f} KB')

print('Done. Screenshots ready for upload.')
