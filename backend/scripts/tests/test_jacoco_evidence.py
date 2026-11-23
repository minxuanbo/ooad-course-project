"""R22覆盖率证据工具拒绝失败测试、字节码不一致和执行数据替换。"""

import argparse
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("jacoco_evidence", Path(__file__).resolve().parents[1] / "jacoco-evidence.py")
EVIDENCE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(EVIDENCE)


class R22CoverageEvidenceTest(unittest.TestCase):
    """只验证证据保护，不用伪造探针数据声称验证了Java覆盖率。"""

    def prepare_target(self, root, failures=0, skipped=0):
        """生成工具输入的最小文件，不包含真实凭据或业务库。"""
        target = root / "target"
        (target / "classes").mkdir(parents=True)
        (target / "classes" / "Example.class").write_bytes(b"unit-only-not-java")
        (target / "surefire-reports").mkdir()
        (target / "surefire-reports" / "TEST-example.xml").write_text(
            f'<testsuite tests="1" failures="{failures}" errors="0" skipped="{skipped}">'
            '<properties><property name="private" value="never-copy"/></properties></testsuite>', encoding="utf-8")
        (target / "jacoco.exec").write_bytes(b"unit-only-not-probes")
        return target

    def test_r22_snapshot_omits_private_xml_and_preserves_existing_output(self):
        """快照只留结果和摘要，已有证据不能被覆盖。"""
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = self.prepare_target(root)
            output = root / "ddd"
            EVIDENCE.snapshot(target, "ddd", output)
            self.assertEqual({"snapshot.json", "jacoco.exec"}, {path.name for path in output.iterdir()})
            metadata = json.loads((output / "snapshot.json").read_text(encoding="utf-8"))
            self.assertEqual(1, metadata["tests"]["tests"])
            self.assertNotIn("never-copy", (output / "snapshot.json").read_text(encoding="utf-8"))
            with self.assertRaises(FileExistsError):
                EVIDENCE.snapshot(target, "ddd", output)

    def test_r22_failed_or_skipped_tests_cannot_be_successful_evidence(self):
        """失败或跳过不能成为有效完整回归快照。"""
        for failures, skipped in ((1, 0), (0, 1)):
            with self.subTest(failures=failures, skipped=skipped), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                target = self.prepare_target(root, failures, skipped)
                with self.assertRaisesRegex(ValueError, "全部为零"):
                    EVIDENCE.snapshot(target, "ddd", root / "ddd")
                self.assertFalse((root / "ddd").exists())

    def test_r22_changed_bytecode_is_rejected_before_cli(self):
        """不同字节码的两次执行不能相加，错误路径不调用CLI或创建合并目录。"""
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = self.prepare_target(root)
            EVIDENCE.snapshot(target, "ddd", root / "ddd")
            (target / "classes" / "Example.class").write_bytes(b"changed-bytecode")
            EVIDENCE.snapshot(target, "structured", root / "structured")
            args = argparse.Namespace(ddd=root / "ddd", structured=root / "structured", classes=target / "classes",
                                      output=root / "combined")
            with patch.object(EVIDENCE.subprocess, "run") as cli, self.assertRaisesRegex(ValueError, "字节码不一致"):
                EVIDENCE.merge(args)
            cli.assert_not_called()
            self.assertFalse(args.output.exists())

    def test_r22_replaced_execution_data_is_rejected(self):
        """探针文件被替换后即使class摘要相同也不接受。"""
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = self.prepare_target(root)
            EVIDENCE.snapshot(target, "ddd", root / "ddd")
            (root / "ddd" / "jacoco.exec").write_bytes(b"replaced")
            with self.assertRaisesRegex(ValueError, "摘要不一致"):
                EVIDENCE.validate_snapshot(root / "ddd", "ddd")


if __name__ == "__main__":
    unittest.main()
