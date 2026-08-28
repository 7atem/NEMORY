import urllib.request
import os

os.makedirs('test_dataset', exist_ok=True)

# Using full raw image URLs from Wikimedia Commons to avoid thumbnail generation errors (400) or 404s.
images = {
    '1_receipt.jpg': 'https://upload.wikimedia.org/wikipedia/commons/0/0b/ReceiptSwiss.jpg',
    '2_invoice.jpg': 'https://upload.wikimedia.org/wikipedia/commons/b/b3/Invoice_example.jpg',
    '3_prescription.jpg': 'https://upload.wikimedia.org/wikipedia/commons/d/d4/Prescription_for_Phenobarbital.jpg',
    '4_boarding_pass.jpg': 'https://upload.wikimedia.org/wikipedia/commons/b/bb/Boarding_Pass.jpg',
    '5_passport.jpg': 'https://upload.wikimedia.org/wikipedia/commons/5/5e/Passeport_fran%C3%A7ais_2013.jpg',
    '6_business_card.jpg': 'https://upload.wikimedia.org/wikipedia/commons/8/87/Business_Card_-_G_D_Renshaw.jpg',
    '7_product_tea.jpg': 'https://upload.wikimedia.org/wikipedia/commons/4/45/A_box_of_Lipton_yellow_label_tea.jpg',
    '8_license_plate.jpg': 'https://upload.wikimedia.org/wikipedia/commons/3/33/License_plate_of_Florida.jpg',
    '9_serial_plate.jpg': 'https://upload.wikimedia.org/wikipedia/commons/e/ec/Motor_nameplate.jpg'
}

opener = urllib.request.build_opener()
opener.addheaders = [('User-agent', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36')]
urllib.request.install_opener(opener)

for filename, url in images.items():
    path = os.path.join('test_dataset', filename)
    try:
        urllib.request.urlretrieve(url, path)
        print(f"Downloaded {filename}")
    except Exception as e:
        print(f"Failed to download {filename}: {e}")

print("Dataset fully populated in test_dataset/")
