# 命令码 codegen

[English](README.md) · **简体中文**

NativeRelay 用 `int` **command** 码路由请求。框架刻意不赋予它任何含义——含义由**你**定。在多端项目
里（C# + Java + Lua + Objective-C…）这意味着同一个命令码必须在每一端数值一致，而手工对齐 N 份正是
"编译全过、运行时派发到错分支" 这类 bug 的经典来源。

本工具用**单一真相源**（[`commands.json`](commands.json)）生成各端的枚举/常量——改一处、重新生成。

```
                        ┌──────────► generated/RelayCommand.cs    (C# enum)
commands.json ──gen.py──┤            generated/RelayCommand.java  (Java int 常量)
 (单一真相源)           ├──────────► generated/relay_command.lua  (Lua table)
                        └──────────► generated/RelayCommand.h     (Obj-C NS_ENUM)
```

> 这些是**占位**命令。你真实的、带业务命名的命令清单请放在你自己的私有工程里——本仓库只提供脚手架 + 一份示例。

## 使用

需要 Python 3.8+。

```bash
python gen.py            # (重新)生成到 ./generated/
python gen.py --check    # 在内存里生成；若 ./generated/ 缺失或过期则退出码 1
```

编辑 [`commands.json`](commands.json)，重跑 `gen.py`，把各生成文件拷到/指向对应工程。要加一种语言，
在 [`gen.py`](gen.py) 里加一个生成函数 + 在 `TARGETS` 里加一条即可。

## 它替你守的纪律（跨端命令码不容差错）

1. **数值显式写死，绝不自增。** 插入一个新命令不能让其他命令偏移——各端不会一起重排。`gen.py` 要求每条命令显式写 `value`。
2. **不许重名、不许复用数值。** `gen.py` 会**大声报错**（`error: value 1 reused by 'B' and 'A'`），而不是静默生成一个撞码。
3. **生成物标注 `AUTO-GENERATED — DO NOT EDIT`。** 改 JSON，别改生成结果。
4. **CI guard 防"改了 JSON 忘了重新生成"。** 在 CI 里跑 `--check`：

   ```yaml
   # 例如 GitHub Actions
   - run: python tools/codegen/gen.py --check
   ```

   只要有人改了 `commands.json` 却没提交重新生成的文件，构建就红。

> 要废弃某命令？把它的数值留作保留占位——**绝不要把数值回收**给新含义，否则新旧版本会对"这个 int 是什么"产生分歧。

## 许可

MIT © 2026 Likeon
