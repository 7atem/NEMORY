$content = Get-Content "d:\Vault Brain\core\ai\llm\src\main\java\com\vaultbrain\core\ai\llm\CaptureEnrichmentResult.kt" -Raw
$content = $content.Replace("val start = raw.indexOf('{')", "val cleanRaw = raw.replace("`json", "", ignoreCase = true).replace("` ", "").trim()
        val start = cleanRaw.indexOf('{')")
$content = $content.Replace("val end = raw.lastIndexOf('}')", "val end = cleanRaw.lastIndexOf('}')")
$content = $content.Replace("raw.substring(start, end + 1)", "cleanRaw.substring(start, end + 1)")
Set-Content "d:\Vault Brain\core\ai\llm\src\main\java\com\vaultbrain\core\ai\llm\CaptureEnrichmentResult.kt" -Value $content -Encoding UTF8
