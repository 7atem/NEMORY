Add-Type -AssemblyName System.Drawing

$outDir = "d:\Vault Brain\test_dataset_extended"
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }

function Create-TestImage {
    param([string]$filename, [string]$text)
    $bitmap = New-Object System.Drawing.Bitmap(650, 800)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.Clear([System.Drawing.Color]::White)
    $font = New-Object System.Drawing.Font("Consolas", 20)
    $brush = [System.Drawing.Brushes]::Black
    $rect = New-Object System.Drawing.RectangleF(20, 20, 610, 760)
    $graphics.DrawString($text, $font, $brush, $rect)
    $path = Join-Path $outDir $filename
    $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Jpeg)
    $graphics.Dispose(); $bitmap.Dispose()
}

# --- MONEY ---
Create-TestImage "money_receipt_us.jpg" "WALMART STORE #502
Date: 2026-08-23

Eggs       $4.99
Bread      $2.49

TAX:       $0.60
TOTAL:     $8.08
Amex ending in 1002"
Create-TestImage "money_receipt_eu.jpg" "TESCO SUPERMARKET
24.08.2026

Milk       1.20 EUR
Tea        3.50 EUR

VAT        0.94 EUR
TOTAL      4.70 EUR
Card 5555"
Create-TestImage "money_invoice_freelance.jpg" "INVOICE #2026-A
Web Development

Amount Due: $1200.00
Due Date: 2026-09-15"
Create-TestImage "money_invoice_utility.jpg" "CITY WATER & POWER
Account: 0987654

Total Due: $85.50
Due by: 10/01/2026"

# --- HEALTH ---
Create-TestImage "health_prescription_1.jpg" "DR. HOUSE CLINIC
Patient: Gregory House

Rx: Vicodin 5mg
Take 1 tablet every 6 hours
Refills: 5"
Create-TestImage "health_prescription_2.jpg" "CITY PHARMACY

Lisinopril 10mg
Once daily
Qty: 30"
Create-TestImage "health_lab_lipid.jpg" "QUEST DIAGNOSTICS
Lipid Panel

Cholesterol: 195
Triglycerides: 110
Ref Range: <200"
Create-TestImage "health_lab_blood.jpg" "BLOOD WORK

Hemoglobin: 14.5
Reference: 13.8 - 17.2"

# --- TRAVEL ---
Create-TestImage "travel_flight_pass.jpg" "DELTA AIRLINES
JFK to LAX
Flight: DL 404
Date: 2026-11-20
Depart: 08:15
Gate: B22
Seat: 12F"
Create-TestImage "travel_train_ticket.jpg" "AMTRAK ACELA
NYP to WAS
Train: 2153
Date: 12/05/2026
Time: 10:00
Seat: 5A"
Create-TestImage "travel_hotel_booking.jpg" "MARRIOTT DOWNTOWN
Confirmation: 88XY92
Check-in: 2026-10-01
Check-out: 2026-10-05"

# --- BUREAUCRACY ---
Create-TestImage "bureaucracy_passport_us.jpg" "PASSPORT
USA
Passport No: 987654321
DOB: 1990-07-22
Expiry Date: 2032-07-21"
Create-TestImage "bureaucracy_id_card.jpg" "CALIFORNIA DRIVER LICENSE
DL: N1234567
DOB: 05/15/1985
EXP: 05/15/2028"
Create-TestImage "bureaucracy_visa.jpg" "SCHENGEN VISA
Valid for: SCHENGEN STATEN
From: 10-09-2026
Until: 10-12-2026"

# --- PRODUCTIVITY ---
Create-TestImage "productivity_bizcard_1.jpg" "Sarah Connor
Security Consultant

s.connor@skynet.com
555-0199"
Create-TestImage "productivity_bizcard_2.jpg" "Gordon Freeman
Research Scientist
Black Mesa Facility
g.freeman@blackmesa.gov"

# --- HOME ---
Create-TestImage "home_warranty.jpg" "SAMSUNG ELECTRONICS
Warranty Certificate
Model No: QN90B
Serial No: S/N 89347209
Valid until: 2027-01-01"
Create-TestImage "home_appliance_plate.jpg" "WHIRLPOOL REFRIGERATOR
Model Number: WRX735SDHZ
S/N: VS932847
115V 60Hz"

# --- CAR ---
Create-TestImage "car_license_plate.jpg" "NEW YORK
PLATE: EXCELSIOR
EMPIRE STATE"
Create-TestImage "car_insurance.jpg" "GEICO AUTO INSURANCE
Policy Number: 99887766
Vehicle: 2020 Honda Civic
Expiry: 2026-12-31"

Write-Host "Extended dataset of 20 images generated successfully!"
