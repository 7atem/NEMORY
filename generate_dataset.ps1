Add-Type -AssemblyName System.Drawing

$outDir = "d:\Vault Brain\test_dataset"
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }

function Create-TestImage {
    param([string]$filename, [string]$text)
    
    $bitmap = New-Object System.Drawing.Bitmap(600, 800)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.Clear([System.Drawing.Color]::White)
    
    $font = New-Object System.Drawing.Font("Consolas", 24)
    $brush = [System.Drawing.Brushes]::Black
    $rect = New-Object System.Drawing.RectangleF(20, 20, 560, 760)
    
    $graphics.DrawString($text, $font, $brush, $rect)
    
    $path = Join-Path $outDir $filename
    $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Jpeg)
    
    $graphics.Dispose()
    $bitmap.Dispose()
    Write-Host "Created $filename"
}

Create-TestImage -filename "1_receipt.jpg" -text "TARGET STORE #1234
123 Main St
Date: 2026-08-23

1x Apples     $2.99
1x Milk       $3.49

TAX:          $0.51
TOTAL:        $6.99
Visa ending in 4242"
Create-TestImage -filename "2_invoice.jpg" -text "INVOICE #9988
Services Rendered

Amount Due: $150.00
Due Date: 2026-09-01"
Create-TestImage -filename "3_prescription.jpg" -text "Dr. Smith Clinic
Patient: Jane Doe

Rx: Amoxicillin 500mg
Take 1 capsule twice daily
Dosage: 500mg
Refills: 3"
Create-TestImage -filename "4_boarding_pass.jpg" -text "BOARDING PASS
Passenger: Doe/John
Flight: BA123
Date: 2026-10-15
Depart: 14:30
Gate: A12
Seat: 14B"
Create-TestImage -filename "5_passport.jpg" -text "PASSPORT
Type: P
Country Code: USA
Passport No: 123456789
Date of Birth: 1980-05-15
Expiry Date: 2030-01-01"
Create-TestImage -filename "6_business_card.jpg" -text "John Doe
Software Engineer

john.doe@example.com
+1 555-0198
https://johndoe.dev"
Create-TestImage -filename "7_warranty_card.jpg" -text "WARRANTY REGISTRATION
Model Number: AC-9000X
Serial No: SN-8849201
Warranty Expiry: 2028-12-31"
Create-TestImage -filename "8_license_plate.jpg" -text "FLORIDA
PLATE: ABC-1234
SUNSHINE STATE"
Create-TestImage -filename "9_lab_result.jpg" -text "CLINICAL LABS INC
Patient: Jane Doe

Cholesterol: 180
Reference Range: 120-200"

Write-Host "Dataset generated successfully!"
