"""runData 后回补用户对 generated lang 的手改（学派化文案等）。
用法：python _patch_lang.py   （在 runData 之后运行）
原理：对比 HEAD 版本与备份，找出用户手改过的 key，把值写回新生成的文件。
"""
import subprocess, json, io, shutil, sys

FILES = {
    "zh_cn": "src/generated/resources/assets/lolaccessories/lang/zh_cn.json",
    "en_us": "src/generated/resources/assets/lolaccessories/lang/en_us.json",
}
BACKUP = "_lang_backup"

def load_head(path):
    raw = subprocess.run(["git", "show", "HEAD:" + path],
                         capture_output=True).stdout.decode("utf-8", "replace")
    return json.loads(raw)

def load(path):
    return json.loads(io.open(path, encoding="utf-8").read())

# 1) 备份当前工作区 lang（含用户手改）
shutil.rmtree(BACKUP, ignore_errors=True)
import os
os.makedirs(BACKUP, exist_ok=True)
for lang, path in FILES.items():
    shutil.copy(path, f"{BACKUP}/{lang}.json")

# 2) 提取用户手改的 key（相对 HEAD 的差异，排除 HEAD 中不存在=本次新增的 key）
user_edits = {}
for lang, path in FILES.items():
    head = load_head(path)
    work = load(path)
    user_edits[lang] = {k: v for k, v in work.items() if k in head and head[k] != v}
    print(lang, "用户手改 key 数:", len(user_edits[lang]))

print("done-backup")
