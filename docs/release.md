# 发布流程

仓库建议命名为 `dingtalk-checkin-reminder`。

本项目不使用 GitHub Actions 构建 APK。Release 附件中的 APK 由本机 release 构建生成，确认后手动上传。

## 发布前检查

发布前用本地关键词扫描源码，确认没有个人地点、私有路径、设备序列号或临时设计文件。

## 本机构建

```powershell
$env:DINGTALK_REMINDER_SIGNING_PROPERTIES="C:\path\to\signing.properties"
.\gradlew.bat assembleRelease
```

构建产物：

```text
app\build\outputs\apk\release\app-release.apk
```

## GitHub Release

登录 GitHub CLI 后，可以用脚本创建仓库、推送源码、推送标签并上传 APK：

```powershell
.\scripts\publish-github.ps1 -RepoName dingtalk-checkin-reminder -Visibility public
```

等价手动命令：

```powershell
git tag v0.2.6
git push origin main
git push origin v0.2.6
gh release create v0.2.6 app\build\outputs\apk\release\app-release.apk `
  --title "v0.2.6" `
  --notes-file CHANGELOG.md
```

如果没有安装 GitHub CLI，也可以在 GitHub 网页端创建 Release，然后手动上传 `app-release.apk`。
