# GitHub应用更新

## 使用

默认更新源为[yuhang-wang-xjtu/diet](https://github.com/yuhang-wang-xjtu/diet)。设置 → 应用更新可修改公开仓库URL或`owner/repo`。保存空白可禁用更新源，取消当前更新下载，不影响健康记录。

点“检查更新 · GitHub”请求`https://api.github.com/repos/{owner}/{repo}/releases/latest`，只接受最新正式版；比较数字版本标签（例如`v0.1.8`、`0.1.8`）与已安装版本。0.1.10正确高于0.1.9，0.1.7.0等于0.1.7；不能识别的版本格式显示错误。没有正式Release、私有/不存在的仓库、限流、连接超时和仓库重定向分别提示。不会将源码压缩包视作APK。

发现更新后展示发布说明。一个可用APK可直接下载；多个APK由用户选择符合设备的附件。只接受配置仓库的GitHub HTTPS Release附件，大小在1–200MiB，若有digest必须为完整SHA-256。没有可用APK时可打开Release页面。

用户点击下载后交给系统DownloadManager，可在后台继续；返回应用查询状态，下载失败可“清除更新下载 / 重试”。不会从后台弹出安装器。完成后复制到应用私有缓存，再检查实际大小、GitHub提供的SHA-256（如有）、包名`cn.qingheng.app`、严格递增versionCode、最低Android版本、与已安装包完全相同的当前签名集合。签名轮换当前不支持。

通过后请求系统安装，使用FileProvider仅授权读取私有更新APK。Android要求允许轻衡“安装未知应用”，安装器最终仍由用户确认，不能静默覆盖安装。取消后可点“下载状态 / 安装更新”重试；允许安装权限返回后继续。升级成功首次启动清除该次下载；健康数据库和设置保留。不要先卸载旧版。

## 如何发布可更新的版本

1. 使用公开GitHub仓库，创建正式Release，标签如`v0.1.8`，不要标成Draft或Pre-release。
2. 正式版当前为0.1.7 / versionCode 10，后续例如0.1.8 / 11。版本名按0.x递增，同时保证Android内部versionCode严格增加。此前开发版使用1.x名称，其旧更新器会认为0.1.7更旧，需要手动下载0.1.7覆盖一次；内部版本号递增且签名一致，记录仍可保留。
3. 构建完整可独立安装的APK，作为Release附件上传，例如`qingheng-0.1.8.apk`。本项目未拆分ABI，APK为完整安装包。仅上传源码不会提供更新。
4. 所有版本必须使用相同Android签名密钥。0.1.7附件为debug构建，后续包需沿用相同签名才能覆盖安装。签名密钥及密码不能提交到公开仓库。
5. 在轻衡中填写该仓库后检查更新。新包仍需高于当前安装版本，不能用当前同版本APK演示升级。

当前不支持私有仓库Token、镜像下载、静默安装、定时后台检查或应用商店更新。GitHub在部分网络下可能无法连接，会显示错误并保留已有健康记录。

## 数据与权限

新增INTERNET仅用于主动检查和下载，GitHub及其下载服务接收常规网络请求信息；不发送饮食、体重、步数或数据库。检查请求不含账号Token。安装需要REQUEST_INSTALL_PACKAGES及系统安装授权；下载至应用目录，不请求共享存储权限。未点击更新不会定期联网检查。

正式发布版本及附件见[GitHub Releases](https://github.com/yuhang-wang-xjtu/diet/releases)。

依据：[GitHub最新Release API](https://docs.github.com/en/rest/releases/releases#get-the-latest-release)、[Android安装来源授权](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls())、[Android安全分享文件](https://developer.android.com/training/secure-file-sharing)。
