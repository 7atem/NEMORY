from PIL import Image, ImageDraw, ImageFont
import os

# Load the app icon source
icon_path = r'D:\Vault Brain\store-assets\nemory-icon-source.png'
output_path = r'D:\Vault Brain\store-assets\nemory-feature-graphic.png'

icon = Image.open(icon_path).convert('RGBA')

# Feature graphic size
width, height = 1024, 500
img = Image.new('RGBA', (width, height))
draw = ImageDraw.Draw(img)

# Create gradient background (purple top to blue bottom)
for y in range(height):
    ratio = y / height
    r = int(123 + (0 - 123) * ratio)
    g = int(47 + (198 - 47) * ratio)
    b = int(247 + (255 - 247) * ratio)
    draw.line([(0, y), (width, y)], fill=(r, g, b, 255))

# Resize icon for feature graphic
icon_size = 220
icon = icon.resize((icon_size, icon_size), Image.Resampling.LANCZOS)

# Create a white rounded square behind icon with shadow
padding = 28
bg_size = icon_size + padding * 2
bg = Image.new('RGBA', (bg_size, bg_size), (255, 255, 255, 0))
bg_draw = ImageDraw.Draw(bg)
shadow_offset = 8
bg_draw.rounded_rectangle(
    [(shadow_offset, shadow_offset), (bg_size - shadow_offset, bg_size - shadow_offset)],
    radius=60,
    fill=(0, 0, 0, 60)
)
bg_draw.rounded_rectangle(
    [(0, 0), (bg_size - shadow_offset * 2, bg_size - shadow_offset * 2)],
    radius=60,
    fill=(255, 255, 255, 255)
)
center = (bg_size // 2, bg_size // 2)
icon_pos = (center[0] - icon_size // 2, center[1] - icon_size // 2)
bg.paste(icon, icon_pos, icon)

icon_x = 100
icon_y = (height - bg_size) // 2
img.paste(bg, (icon_x, icon_y), bg)

# Load fonts
def load_font(size):
    font_paths = [
        r'C:\Windows\Fonts\segoeuib.ttf',
        r'C:\Windows\Fonts\arialbd.ttf',
        r'C:\Windows\Fonts\arial.ttf',
    ]
    for path in font_paths:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except:
                pass
    return ImageFont.load_default()

title_font = load_font(90)
subtitle_font = load_font(40)

title = 'Nemory'
line1 = 'Save anything.'
line2 = 'Find it when you need it.'

text_x = icon_x + bg_size + 50
title_y = 155

# Draw title
draw.text((text_x, title_y), title, font=title_font, fill=(255, 255, 255, 255))

# Draw subtitle lines below title
draw.text((text_x, title_y + 110), line1, font=subtitle_font, fill=(255, 255, 255, 230))
draw.text((text_x, title_y + 165), line2, font=subtitle_font, fill=(255, 255, 255, 230))

img = img.convert('RGB')
img.save(output_path, 'PNG', quality=95)
print(f'Feature graphic saved to: {output_path}')
print(f'Size: {img.size}')
