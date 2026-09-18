import urllib.request
import os

os.makedirs('test_dataset', exist_ok=True)

images = {
    'receipt_1.jpg': 'https://upload.wikimedia.org/wikipedia/commons/0/0b/ReceiptSwiss.jpg',
    'business_card_1.jpg': 'https://upload.wikimedia.org/wikipedia/commons/8/87/Business_Card_-_G_D_Renshaw.jpg',
    'id_card_1.jpg': 'https://upload.wikimedia.org/wikipedia/commons/c/ca/National_ID_Card_of_Bangladesh_%28Front%29.jpg'
}

opener = urllib.request.build_opener()
opener.addheaders = [('User-agent', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)')]
urllib.request.install_opener(opener)

for filename, url in images.items():
    path = os.path.join('test_dataset', filename)
    try:
        urllib.request.urlretrieve(url, path)
        print(f"Downloaded {filename}")
    except Exception as e:
        print(f"Failed to download {filename}: {e}")
