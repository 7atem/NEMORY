Add-Type -AssemblyName System.Drawing
$img = [System.Drawing.Image]::FromFile("D:\Vault Brain\test_photos\Grocery-Sample-Receipts-2-7447c4126c5783734dbf76b8d64dda6d.png")
Write-Output "Image size: $($img.Width)x$($img.Height)"
