# Moxia 发版手册（双源：CNB 主源 + GitHub 兜底）

这份文档是发版的**唯一权威记录**。改流程、改流水线前先读这里；踩过的坑都在第 8、9 节，不要凭直觉"简化"。

---

## 1. 为什么是双源

| | CNB（主源） | GitHub（兜底） |
| --- | --- | --- |
| 仓库 | `nulxiel/moxia`（public） | `3546475/moxia`（fork） |
| 流水线 | `.cnb.yml`（`tag_push` 触发） | `.github/workflows/release-build.yml`（`v*` tag 触发） |
| Release | `https://cnb.cool/nulxiel/moxia/-/releases/download/vX.Y.Z/` | `https://github.com/3546475/moxia/releases/download/vX.Y.Z/` |
| 清单 | `https://cnb.cool/nulxiel/moxia/-/git/raw/main/update.json` | `https://raw.githubusercontent.com/3546475/moxia/main/update.json` |

两端**各自独立构建**，所以同一个版本的 APK 字节不同、`apkSha256` 不同。两份清单各自只描述自己那一份构建，**绝不混用字段**。

客户端默认按上面的顺序读两份清单，取 `versionCode` 最大的那份；`versionCode` 相同取先读到的那份（CNB 优先）。CNB 半发布（清单还是旧的）时，GitHub 那份会被自动选中——这是兜底的意义。

> `UpdateSource.GithubRelease`（GitHub Releases API）仍然存在，但**不是默认源**：官方 API 限 60 次/小时/IP，且没有摘要字段。

---

## 2. 仓库、分支、标签

| remote | 地址 | 作用 |
| --- | --- | --- |
| `fork` | `https://github.com/3546475/moxia.git` | GitHub 兜底端，推送目标 |
| `cnb` | `https://cnb.cool/nulxiel/moxia.git` | CNB 主源，推送目标 |
| `origin` | `git@github.com:yamada-sexta/memos-m.git` | **上游 release-only，永远不推** |

本地分支：

- `main` —— 两种设计语言（Material 3 / Miuix），跟随 `fork/main`。
- `small` —— `main` 去掉 Miuix（单主题），跟随 `fork/small`；与 `main` 的冲突一律以 `small` 的单主题代码为准。
- `cnb-main` —— 发布分支，跟随 `cnb/main`。CNB 侧的 `.cnb.yml` 在这里演进。

标签：`vX.Y.Z`。**两端同名标签指向同一个提交**，各端自己构建。

---

## 3. 版本号的唯一来源

版本号只有一处算法，三处实现必须一致：

```
v1.2.3  ->  1 * 1_000_000 + 2 * 1_000 + 3  =  1002003
v1.0.0  ->  1000000
v0.7.8  ->  7008
```

- `scripts/gen_update_json.py --tag vX.Y.Z --print-version-code` —— **唯一计算入口**。
- `app/build.gradle.kts` 的 `versionCode = requestedVersionCode ?: detectedVersionCode ?: 1`：`-PversionCode` 优先，其次从 git 描述推断。
- 两端流水线都用 `--print-version-code` 算出 `VC`，再显式传 `-PversionCode="$VC" -PversionName="${TAG#v}"`。
- GitHub 侧还用 `aapt2` 回读 APK 里的 `versionCode` 并断言等于 `VC`。

> **绝不能让 Gradle 自己从 git 推断版本号**：构建机的 tag 状态和流水线要发布的 tag 可能不是同一个，会导致包里的版本与清单里的版本对不上。

---

## 4. 清单契约

`update.json`：

```json
{
  "schemaVersion": 1,
  "channel": "stable",
  "versionCode": 1000000,
  "versionName": "1.0.0",
  "tag": "v1.0.0",
  "publishedAt": null,
  "releaseNotes": "...",
  "apkUrl":   { "arm64-v8a": "...", "armeabi-v7a": "...", "universal": "..." },
  "apkSha256": { "arm64-v8a": "...", "armeabi-v7a": "...", "universal": "..." },
  "apkSize":  { "arm64-v8a": 0, "armeabi-v7a": 0, "universal": 0 }
}
```

规则：

1. 两份清单的 `versionCode` 必须相同，`apkSha256` 必须不同（独立构建的证据）。
2. CNB 清单的 `apkUrl` 全部指向 `cnb.cool/nulxiel/moxia/-/releases/download/...`；GitHub 清单全部指向 `github.com/3546475/moxia/releases/download/...`。
3. Release 资产名统一 `moxia-vX.Y.Z-{arm64-v8a,armeabi-v7a,universal}.apk`。
4. 清单**后于**安装包上线：先建 Release 并上传包，再回写清单。
5. 客户端只在本份清单内校验 `apkSha256`；`apkSha256` 缺失时跳过校验（不跨清单找）。

---

## 5. 凭据

### CNB

私有仓 `nulxiel/moxia-secrets`（private），文件 `moxia-release-env.yml`，格式 `KEY: value`：

| 键 | 内容 |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | 发布用 keystore 的 base64 |
| `SIGNING_STORE_PASSWORD` | keystore 口令 |
| `SIGNING_KEY_ALIAS` | 密钥别名 |
| `SIGNING_KEY_PASSWORD` | 密钥口令 |
| `REPO_TOKEN` | 有 `repo-release` 权限的令牌 |

`.cnb.yml` 用 `imports: https://cnb.cool/nulxiel/moxia-secrets/-/blob/main/moxia-release-env.yml` 注入；**env 优先于 imports**。

> 内置的 `CNB_TOKEN` **没有** repo-release 权限（403）。必须用 `$REPO_TOKEN`。
> `REPO_TOKEN` 会出现在构建日志的命令行里，CNB 私有仓的日志只有登录用户可见；轮换时同步改私有仓。

### GitHub

仓库 Secrets：`KEYSTORE`（base64）、`RELEASE_KEYSTORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`。

### 本地

`keystore.properties`（仓库根，已被 `.gitignore` 忽略）：`storeFile` / `storePassword` / `keyAlias` / `keyPassword`。`storeFile` 是相对仓库根的路径。

---

## 6. 发版流程

### 6.0 前提

- 代码在 `main`（或 `small`）上已经合并并推到 `fork`。
- `cnb-main` 与 `cnb/main` 同步（CI 每次发版会往 `cnb/main` 提交清单，所以推之前先 `git fetch cnb main`）。

### 6.1 CNB（主源）

```bash
git checkout cnb-main
git fetch cnb main && git rebase cnb/main        # 吸收 CI 回写的清单提交
# 改版本号 / 改代码，提交
git push cnb cnb-main:main
git tag -f vX.Y.Z cnb-main && git push cnb vX.Y.Z  # 触发 tag_push 流水线
```

流水线（`.cnb.yml`，8 个 stage）：

1. 准备 JDK 21（幂等，装在 `/root/.gradle/jdk21`）。
2. 校验 tag 是合法版本号（`--print-version-code`）。
3. 安装 SDK 平台（容错，缺失时交给 AGP 自己下载）。
4. 构建：`testDebugUnitTest` + `assembleRelease`（一次 gradlew 调用，后台跑 + 心跳）。
5. 重命名为 `moxia-${TAG}-<abi>.apk`。
6. 建 CNB Release：`POST https://api.cnb.cool/nulxiel/moxia/-/releases`，`Authorization: Bearer $REPO_TOKEN`、`Accept: application/vnd.cnb.api+json`、`Content-Type: application/json`，body `{tag_name, name, body, make_latest}`；**201/200/409 算成功**。
7. 上传附件：`image cnbcool/attachments:latest`。
8. 回写清单：clone `https://cnb:${REPO_TOKEN}@cnb.cool/nulxiel/moxia.git`（`main` 分支）→ 覆盖 `update.json` → commit → push。

### 6.2 GitHub（兜底）

```bash
git checkout main
git push fork main:main
git tag -f vX.Y.Z <同一个提交> && git push --force fork vX.Y.Z
```

流水线（`.github/workflows/release-build.yml`）：校验 tag → 算 `VC` → 单测 → `assembleRelease -PversionName -PversionCode` → `aapt2` 回读校验 → `softprops/action-gh-release@v2` 发布（同名 tag 会**替换**已有资产）→ 回写清单到 `main`。

> GitHub 的 `v1.0.0` 已经存在且指向旧提交，所以同名标签必须 `--force`。这是有意的：两端同名标签指向同一提交，各自构建。

### 6.3 顺序

先 CNB、后 GitHub。两端都完成后客户端就同时能看到两个源。哪端先都行，但**不要只发一端就宣布完成**——单源发布等于没有兜底。

---

## 7. 客户端行为

- 默认源：`Manifest(CNB raw)` → `Manifest(GitHub raw)`，见 `data/update/UpdateSource.kt`。
- `UpdateRepository.check()` 遍历**所有**源收集应答，取 `versionCode` 最大的一份评估；所有源都失败才报"无法连接更新源"，且**不写节流时间戳**（下次冷启动会重试）。
- 渠道：`debug` 构建可以消费 `stable` 清单（`channelMatches` 放行），反之不行。
- `knownUpdate` 只保留一份（最优的那份）。

---

## 8. 不要改回去的地方

每一条都是踩过坑之后加的，改流水线前先确认坑还在不在。

| # | 位置 | 做法 | 去掉会怎样 |
| --- | --- | --- | --- |
| 1 | `.cnb.yml` Build stage | gradlew 放后台跑，主循环每 20 秒打印一次心跳，超过 45 分钟才 kill | runner 的 **10 分钟静默看门狗**会把"正在解析依赖"的构建杀掉，日志末尾是"任务超过(10m0s)没有任何输出而被强制关闭"，看起来像编译失败其实不是 |
| 2 | `settings.gradle.kts` | jitpack 用 `exclusiveContent` 独占 `com.github.gregcockroft` / `com.github.jitpack` | 仓库顺序让 Gradle 先问 Maven Central，Central 对该 group 回 **429**，而 429 是致命错误、不再回退到 jitpack → 依赖解析失败 |
| 3 | `.cnb.yml` + `app/build.gradle.kts` | `-PexcludeDockerTests=true` 过滤 `MemosApiIntegrationTest` / `ContainerTest` | 这两个类用 Testcontainers，runner 里没有 Docker daemon → 61 个用例失败，整条流水线挂掉 |
| 4 | `.github/workflows/release-build.yml` 清单步骤 | 先 `git add update.json` 再 `git diff --cached --quiet` 判断 | 未跟踪的文件 `git diff` 看不见 → "manifest already current" 永远成立 → **清单永远没被提交**，兜底源 404 |
| 5 | `.cnb.yml` SDK stage | `sdkmanager` 不可用时只提示、不 `exit 1` | 镜像里没有可用的 `sdkmanager`，硬校验会让流水线 1 秒内失败；平台交给 AGP 自己下载 |
| 6 | 两端流水线 | 显式传 `-PversionCode` / `-PversionName`，并用 `aapt2` 回读校验 | Gradle 从 git 推断的版本可能与流水线发布的 tag 不一致 → 包与清单版本对不上 |
| 7 | `.cnb.yml` 清单步骤 | clone 后覆盖文件、`git add`、`git commit`、`git push` 全都显式写 | 只 clone 不提交，清单不会出现在 `main` 上 |

---

## 9. 故障排查

| 症状 | 原因 | 处理 |
| --- | --- | --- |
| 流水线 1 秒内失败，日志停在 "Install the SDK platform" | `sdkmanager` 不可用且脚本硬失败 | 第 8 节 #5 |
| 日志 "任务超过(10m0s)没有任何输出而被强制关闭" | 静默看门狗 | 第 8 节 #1 |
| `Could not get resource '.../gradle-simple-1.0.pom'` + `429` | jitpack 的传递依赖被 Central 拦下 | 第 8 节 #2 |
| `:app:testDebugUnitTest` 报 "Could not find a valid Docker environment" | runner 无 Docker | 第 8节 #3 |
| Release 建好但清单 404 | 清单没提交到 `main` | 第 8 节 #4、#7 |
| `git push cnb cnb-main:main` 被拒（non-fast-forward） | CI 往 `cnb/main` 提交过清单 | `git fetch cnb main && git rebase cnb/main` 后再推 |
| 建 Release 报 403 | 用了内置 `CNB_TOKEN` | 换 `$REPO_TOKEN` |
| 建 Release 报 406 | 缺 `Accept: application/vnd.cnb.api+json` | 补上请求头 |
| tag 推上去没触发流水线 | 被打 tag 的提交里没有 `.cnb.yml` | `.cnb.yml` 必须在被打 tag 的那个提交里 |
| Release API 静默成功但没有包 | `curl ... \|\| true` 吞掉了错误 | 去掉 `\|\| true`，失败必须可见 |
| 中文/反引号方法名让构建崩 | 容器 locale 不是 UTF-8 | `LANG`/`LC_ALL` 必须设成 `C.UTF-8` |

---

## 10. 验收清单

发版后逐条核对：

1. CNB 的 `main` 分支上有 `.cnb.yml` 和 `main/update.json`。
2. GitHub 的 `main` 分支上有 `update.json`。
3. 两份清单的 `versionCode` 相同、`apkSha256` 不同。
4. CNB 清单的 `apkUrl` 全部指向 `cnb.cool/...`，GitHub 清单全部指向 `github.com/...`。
5. 两边 Release 都有 `moxia-vX.Y.Z-{arm64-v8a,armeabi-v7a,universal}.apk`。
6. 客户端「检查更新」能读到新版本并下载成功。
7. 人为把 CNB 清单的 `versionCode` 改小，客户端仍能拿到 GitHub 那份新版本。
8. 两份清单都拿不到时，显示「无法连接更新源」，且下次冷启动会重试。

---

## 11. 读 CNB 构建日志

网页日志页需要登录，API 可以直接读：

```bash
# 阶段状态
curl -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.cnb.api+json" \
  https://api.cnb.cool/nulxiel/moxia/-/build/status/{sn}

# 阶段日志
curl -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.cnb.api+json" \
  https://api.cnb.cool/nulxiel/moxia/-/build/logs/stage/{sn}/{sn}-001/stage-3
```

- `{sn}` 从 commit 页 `https://cnb.cool/nulxiel/moxia/-/commit/{sha}` 的 `__NEXT_DATA__.commit_statuses[].target_url` 里取。
- 阶段日志返回 `{"duration": ms, "content": ["...行...", ...]}`。
- 全部接口：`https://api.cnb.cool/swagger.json`。

---

## 12. 发布记录

### v1.0.0（2026-11-12）

- 发布提交：CNB `47a1c611`，GitHub `e588020c`（同一份发布内容，两端各自提交）。
- CNB 构建：`cnb-pcl-1k777hskj`，8 个 stage 全绿，构建耗时 10m42s。
- 包尺寸（两端相同）：arm64-v8a 23339790 / armeabi-v7a 19140424 / universal 64947573。
- 摘要（两端不同，证明独立构建）：
  - arm64-v8a：GitHub `2a63474d0dcd10024487857f9fa0168af3801c317a8a582962363e3adfba0673` / CNB `28dae80d32ff66efef4e9062fce6e5e0c2dfa5c7b06d1d78aae281942a5d77b0`
  - armeabi-v7a：GitHub `afdfbc2d7c0a0fbbd6b226e85aa445e91f84dd2a32fb8dbfb23eb12b9c4168d7` / CNB `76f2ba9a60b45d888415e8ddfa2b22f2ef9fba697af66c4a7b1be92c39175418`
  - universal：GitHub `8bb7fb9b2909585e910483758c1948f5a7eff30a38d0563e658452c604d19018` / CNB `3392f51b45aa050aa709f19d62ec14619e94118ec1ebaa144be21c5ed28f3d4c`
- 清单提交：CNB `main` = `2ffe06a8`（CI 回写）。
- 过程中修掉的根因见第 8 节 #1–#7。
