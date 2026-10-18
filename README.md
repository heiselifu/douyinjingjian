# 抖音精简（DyClean）

一个 **LSPosed / libxposed 模块**，通过「规则集」隐藏抖音（`com.ss.android.ugc.aweme.mobile`）界面里的冗余元素：底部导航、顶部频道、首页挂件、侧边栏入口、消息页 Banner、个人主页各处等。

模块**不注入任何自绘控件**，只按规则把命中 View 设为 `GONE`（可回退），因此对目标进程侵入性很低，卸载/关闭模块即恢复原样。

> 本项目是对原 `com.dyclean`（v1.17）模块的重写实现：包名、规则语法、开关划分与原版保持兼容，代码全部重写并开源。

---

## 功能一览

| 分类 | 内容 |
| --- | --- |
| 底部导航 | 隐藏「加号 / 朋友 / 消息 / 我」任一槽位 |
| 顶部频道 | 隐藏「热点 / 精选 / 直播 / 团购 / 经验」，仅保留关注与推荐 |
| 首页 | 隐藏音乐挂件 / 拍同款条（`#videomusiccoverblock`） |
| 侧边栏 | 隐藏通知消息、转化记录、创作者中心、直播广场、管理助手、未成年人保护 |
| 消息页 | 隐藏顶部提醒 Banner、顶部限时日常 / 故事头像行 |
| 我页 | 标题栏、访客、背景图、头像、昵称、抖音号、数据栏、编辑主页、简介、功能入口、作品栏等逐项开关 |

界面上的每个开关与规则文件里的一行（或两行）规则**一一对应**：打开开关 = 该规则行写入规则文件，关闭 = 该行被移除/注释。

---

## 工作原理

```
设置页（Compose/View） ──保存──▶ 规则文本
                                   │
              ┌────────────────────┼────────────────────┐
              ▼                    ▼                    ▼
   模块 ContentProvider      抖音私有目录文件       LSPosed 远程 prefs
   content://…prefs/rules    dyclean_rules.txt     getRemotePreferences
              └────────────────────┴────────────────────┘
                                   ▼
                            目标进程内 Engine
```

规则加载优先级（逐级回退，全部失败则用内置默认规则）：

1. **模块 ContentProvider**（`content://<applicationId>.prefs/rules`）——保存在设置页即可生效，无需重启抖音；
2. **目标 App 私有目录规则文件** —— 由设置页「写入抖音目录」通过 root 直接落盘，适用于 LSPosed 通道不可用的场景；
3. **LSPosed 远程 SharedPreferences**；
4. **内置默认规则**（`DefaultRules.kt`）。

### Hook 点

| 位置 | 用途 |
| --- | --- |
| `Activity.onResume` | 页面进入时挂载扫描 |
| `Instrumentation.callActivityOnResume` | 抖音多数 Activity 覆写 `onResume` 不回调 super 时的兜底 |

挂载后：

- 监听 `ViewTreeObserver.OnGlobalLayoutListener`，布局变化驱动扫描；
- 兜底轮询（默认 800ms，可配）；
- 页面重建后进入一轮密集重扫（默认 120ms × 2.5s），应对 RecyclerView 复用；
- 每次扫描带最小间隔防抖（默认 60ms），单次扫描上限 `5000` 节点 / 深度 `64`。

### 隐藏动作

`action=` 可取 `GONE`（默认）、`INVISIBLE`、`ALPHA0`、`COLLAPSE`、`REMOVE_FROM_PARENT`。
除 `REMOVE_FROM_PARENT` 外全部可逆：恢复时按记录的原始 `LayoutParams` 还原，因此不会出现「关掉模块后界面错乱」。

---

## 规则语法

规则文件是一份 INI 风格文本，含 `[target]` 与 `[rules]` 两段（`[rules]` 可省略）。

```ini
[target]
package=com.ss.android.ugc.aweme.mobile
activity=com.ss.android.ugc.aweme.main.MainActivity
rescan=layout+fallback      # off / layout / fallback / layout+fallback
action=GONE                 # GONE / INVISIBLE / ALPHA0 / COLLAPSE / REMOVE_FROM_PARENT
# gap=60                    # 两次扫描最小间隔（ms）
# burst=120                 # 密集重扫间隔（ms）

[rules]
hide_id    videomusiccoverblock
tab_hide   热点
hide_up    uh5 1
hide_text_up 打开提醒 2
```

- 空行、`#` 或 `!` 开头的行忽略；
- 规则行格式：`关键字 参数 [上溯层数]`，**参数允许包含空格**；
- 末尾数字被解析为上溯层数，例如 `hide_up uh5 1` 表示「找到 `#uh5` 后，向上第 1 级祖先一起隐藏」。

### 关键字

| 关键字 | 参数 | 说明 |
| --- | --- | --- |
| `hide_id` | 资源 id 名（不含包名） | 按 id 命中即隐藏 |
| `hide_up` | id 名 + 上溯层数 | 命中后向上找 N 级祖先一起隐藏（用于「只要图标，不要整行」的场景） |
| `hide_text` | 文本 | 按文本命中即隐藏 |
| `hide_text_up` | 文本 + 上溯层数 | 文本命中后向上找 N 级祖先 |
| `tab_hide` | 频道标题 | 顶部频道栏内按 `#tv_toptab_title` 文本隐藏 |
| `tab_keep` | 频道标题 | 顶部频道栏只保留指定标题（白名单） |
| `bottom_tab_hide` | 槽位文字 | 底部导航按文字隐藏槽位 |
| `probe_id` | id 名 | 诊断：日志打印命中位置，不做隐藏 |
| `probe_text` | 文本 | 诊断：同上 |
| `click_text` | 文本 | 出现即点一次（一次性引导/弹窗） |

### 为什么很多规则锚定奇怪的名字（`uh5`、`se1`、`zlq`…）

抖音的资源 id 是混淆过的短串，且**同名 id 会在不同页面复用**（例如 `zkk` 同时出现在作品栏和评论区）。因此默认规则尽量选择「页面内唯一」的锚点，复用无法避免时改用 `hide_up` 从唯一子节点上溯到行容器，避免误伤。

---

## 共存与包名

- 源码 `namespace` 为 `com.dyclean`，`applicationId` 为 **`com.dyclean.rewrite`**，用于与原版 `com.dyclean` 同时安装、互不覆盖（两者作用域相同，但同开时会各自扫一遍；建议只启用一个）。
- ContentProvider authority 为 `<applicationId>.prefs`，同样不冲突。
- 若想完全取代原版，把 `app/build.gradle.kts` 的 `applicationId` 改回 `com.dyclean`，并同步 `core/RuleSource.kt` 中的 `PrefsContract.AUTHORITY`，卸载原版后安装即可。

---

## 构建

环境：JDK 17、Android SDK 34+、Gradle 8.x（仓库不含 wrapper）。

```bash
gradle assembleDebug --no-daemon -Dorg.gradle.vfs.watch=false
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

依赖 `app/libs/api-102.jar` 为 **libxposed Modern API 102** 的编译期接口（`compileOnly`，运行期由框架注入），来源：libxposed 官方发布。

安装后在 LSPosed 中启用模块、勾选作用域「抖音」，再重启抖音。

---

## 目录结构

```
app/src/main/java/com/dyclean/
├── ModuleMain.kt            # libxposed 入口，安装 Hook
├── DyPrefsProvider.kt       # 规则文本的只读 ContentProvider
├── core/
│   ├── Model.kt             # RuleKind / Rule / ActionKind / RescanPolicy / Ruleset
│   ├── RuleParser.kt        # 规则文件解析（含错误行定位）
│   ├── RuleSource.kt        # 四级加载链 + PrefsContract 常量
│   ├── DefaultRules.kt      # 内置默认规则集
│   ├── Engine.kt            # 扫描/匹配/隐藏/回退主引擎
│   ├── ViewUtils.kt         # View 树查找、上溯、可见性
│   ├── Actions.kt           # 可逆隐藏动作
│   └── Logger.kt            # 统一日志（tag: DyClean）
└── settings/
    ├── SettingsActivity.kt  # 设置界面
    ├── Toggles.kt           # 开关定义（与默认规则行一一对应）
    ├── RuleStore.kt         # 规则文本的本地读写与开关重写
    ├── TargetWriter.kt      # root 直写抖音目录（回退通道）
    └── RootShell.kt         # root 命令封装
```

---

## 已知限制

- 规则锚点依赖抖音当前版本的资源 id 与文案，**抖音升级后需要重新 dump View 树校准**；模块内置了 `probe_id` / `probe_text` 诊断规则用于定位。
- 仅在 `MainActivity` 主界面做过实测；二级页面（直播间、搜索页等）未逐页校准。
- 「隐藏底栏我」会使个人页无法进入；「隐藏作品栏」等激进开关默认关闭，请按需开启。
- 不修改网络请求、不拦截数据，只处理 View 层显示。

## 免责声明

仅供个人学习与自用。修改第三方 App 界面可能违反其用户协议，使用风险自负。
