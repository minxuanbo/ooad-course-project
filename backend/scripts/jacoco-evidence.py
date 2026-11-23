"""保存双策略覆盖率证据，核对相同字节码后通过官方JaCoCo CLI合并。"""

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET


COUNTERS = {"INSTRUCTION", "BRANCH", "LINE", "COMPLEXITY", "METHOD", "CLASS"}


def class_hashes(classes):
    """包含所有生产class，不按包名、注解或策略排除。"""
    hashes = {
        path.relative_to(classes).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in sorted(classes.rglob("*.class"))
    }
    if not hashes:
        raise ValueError("生产class目录为空")
    return hashes


def snapshot(target, strategy, output):
    """只保存计数、摘要和执行数据，不复制可能带环境凭据的Surefire XML。"""
    totals = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
    suites = list((target / "surefire-reports").glob("TEST-*.xml"))
    for path in suites:
        suite = ET.parse(path).getroot()
        for key in totals:
            totals[key] += int(suite.get(key, "0"))
    if not totals["tests"] or any(totals[key] for key in ("failures", "errors", "skipped")):
        raise ValueError("测试必须实际执行，且失败、错误和跳过全部为零")
    hashes = class_hashes(target / "classes")
    execution = target / "jacoco.exec"
    if not execution.is_file() or not execution.stat().st_size:
        raise ValueError("缺少JaCoCo执行数据")
    output.mkdir(parents=True, exist_ok=False)
    shutil.copy2(execution, output / "jacoco.exec")
    metadata = {"strategy": strategy, "tests": totals, "classes": hashes,
                "executionSha256": hashlib.sha256(execution.read_bytes()).hexdigest()}
    (output / "snapshot.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    print(json.dumps({"strategy": strategy, **totals, "classFiles": len(hashes)}))


def validate_snapshot(path, strategy):
    """合并前拒绝错策略或执行数据被替换的快照。"""
    metadata = json.loads((path / "snapshot.json").read_text(encoding="utf-8"))
    execution = path / "jacoco.exec"
    totals = metadata["tests"]
    if (metadata["strategy"] != strategy or not totals["tests"]
            or any(totals[key] for key in ("failures", "errors", "skipped"))
            or hashlib.sha256(execution.read_bytes()).hexdigest() != metadata["executionSha256"]):
        raise ValueError("快照策略、测试结果或执行数据摘要不一致")
    return metadata


def merge(args):
    """官方CLI负责探针合并及报告，六项零遗漏与pom门槛一致。"""
    ddd = validate_snapshot(args.ddd, "ddd")
    structured = validate_snapshot(args.structured, "structured")
    if ddd["classes"] != structured["classes"] or ddd["classes"] != class_hashes(args.classes):
        raise ValueError("两次测试和当前生产class字节码不一致，禁止合并")
    args.output.mkdir(parents=True, exist_ok=False)
    command = [str(args.java), "-jar", str(args.cli)]
    subprocess.run(command + ["merge", str(args.ddd / "jacoco.exec"),
                             str(args.structured / "jacoco.exec"), "--destfile",
                             str(args.output / "jacoco.exec")], check=True)
    subprocess.run(command + ["report", str(args.output / "jacoco.exec"), "--classfiles",
                             str(args.classes), "--sourcefiles", str(args.sources), "--html",
                             str(args.output / "html"), "--xml", str(args.output / "jacoco.xml")], check=True)
    report = ET.parse(args.output / "jacoco.xml").getroot()
    counters = {entry.get("type"): {"missed": int(entry.get("missed")),
                                   "covered": int(entry.get("covered"))}
                for entry in report.findall("counter")}
    (args.output / "summary.json").write_text(json.dumps(counters, indent=2), encoding="utf-8")
    print(json.dumps(counters))
    if set(counters) != COUNTERS or any(entry["missed"] for entry in counters.values()):
        raise ValueError("合并报告未达到全部六项零遗漏")


def main():
    """所有路径显式输入；不创建数据库、不读凭据、不覆盖旧证据。"""
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    save = commands.add_parser("snapshot")
    save.add_argument("--target", type=Path, required=True)
    save.add_argument("--strategy", choices=("ddd", "structured"), required=True)
    save.add_argument("--output", type=Path, required=True)
    combine = commands.add_parser("merge")
    for name in ("ddd", "structured", "classes", "sources", "cli", "java", "output"):
        combine.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    if args.command == "snapshot":
        snapshot(args.target, args.strategy, args.output)
    else:
        merge(args)


if __name__ == "__main__":
    main()
