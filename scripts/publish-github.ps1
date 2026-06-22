param(
    [string]$RepoName = "dingtalk-checkin-reminder",
    [ValidateSet("public", "private")]
    [string]$Visibility = "public",
    [string]$Tag = "v0.2.8",
    [string]$ApkPath = "app\build\outputs\apk\release\app-release.apk"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI 'gh' is required. Install it or add the portable gh.exe directory to PATH."
}

gh auth status | Out-Host

if (-not (Test-Path $ApkPath)) {
    throw "APK not found: $ApkPath. Build it locally before publishing."
}

if (-not (git remote get-url origin 2>$null)) {
    if ($Visibility -eq "public") {
        gh repo create $RepoName --public --source . --remote origin --push
    } else {
        gh repo create $RepoName --private --source . --remote origin --push
    }
} else {
    git push origin main
}

git push origin $Tag

gh release create $Tag $ApkPath `
    --title $Tag `
    --notes-file CHANGELOG.md
