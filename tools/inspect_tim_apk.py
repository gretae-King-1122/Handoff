import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys
import zipfile

# Static APK inventory only: signatures and fields are not runtime compatibility proof.
TARGETS = {
    "com.tencent.mobileqq.app.QBaseActivity",
    "com.tencent.mobileqq.app.BaseActivity",
    "com.tencent.mobileqq.activity.QQBrowserDelegationActivity",
    "com.tencent.mobileqq.activity.QQBrowserActivity",
    "com.tencent.mobileqq.activity.QQH5BrowserActivity",
    "com.tencent.mobileqq.activity.QQTranslucentBrowserActivity",
    "com.tencent.mobileqq.activity.QPublicTransFragmentActivity",
    "com.tencent.qqmini.sdk.MiniSDK",
    "com.tencent.qqmini.sdk.server.LaunchManagerService",
    "com.tencent.qqmini.sdk.launcher.model.MiniAppInfo",
    "com.tencent.qqmini.sdk.launcher.model.MiniAppBaseInfo",
    "com.tencent.qqmini.sdk.launcher.model.LaunchParam",
    "com.tencent.qqmini.sdk.launcher.model.FirstPageInfo",
    "com.tencent.mobileqq.mini.appbrand.ui.AppBrandUI",
    "com.tencent.mobileqq.mini.appbrand.ui.AppBrandUI1",
    "com.tencent.mobileqq.mini.appbrand.ui.AppBrandUI2",
    "com.tencent.mobileqq.mini.appbrand.ui.AppBrandUI3",
}


def class_name(descriptor):
    return descriptor[1:-1].replace("/", ".") if descriptor.startswith("L") else descriptor


def check_contract(report):
    """Check only the declared static shape, not APK identity or observed execution."""
    failures = []
    classes = {row["class"]: row for row in report["classes"]}
    if report["missing_classes"]:
        failures.append("Missing classes: " + ", ".join(report["missing_classes"]))
    if len(classes) != len(report["classes"]):
        failures.append("Duplicate target class definitions")

    def method(owner, name, signature, is_static=False):
        matches = [m for m in classes.get(owner, {}).get("methods", [])
                   if m["name"] == name and m["signature"] == signature]
        if len(matches) != 1:
            failures.append(f"Expected one {owner}.{name}{signature}")
            return
        flags = int(matches[0]["access"], 16)
        if not flags & 0x1 or bool(flags & 0x8) != is_static:
            failures.append(f"Unexpected access/static flags: {owner}.{name}{signature}")

    base = "com.tencent.mobileqq.app.QBaseActivity"
    method(base, "startActivity", "(Landroid/content/Intent;)V")
    method(base, "startActivity", "(Landroid/content/Intent;Landroid/os/Bundle;)V")
    mini = "(Landroid/app/Activity;Lcom/tencent/qqmini/sdk/launcher/model/MiniAppInfo;Landroid/os/Bundle;Landroid/os/ResultReceiver;)V"
    method("com.tencent.qqmini.sdk.MiniSDK", "startMiniApp", mini, is_static=True)
    method("com.tencent.qqmini.sdk.server.LaunchManagerService", "startMiniApp", mini)

    model = "com.tencent.qqmini.sdk.launcher.model."
    expected_fields = {
        "MiniAppBaseInfo": {"appId": "Ljava/lang/String;", "firstPage": "Lcom/tencent/qqmini/sdk/launcher/model/FirstPageInfo;"},
        "MiniAppInfo": {"launchParam": "Lcom/tencent/qqmini/sdk/launcher/model/LaunchParam;"},
        "LaunchParam": {"entryPath": "Ljava/lang/String;", "miniAppId": "Ljava/lang/String;"},
        "FirstPageInfo": {"pagePath": "Ljava/lang/String;"},
    }
    for owner, fields in expected_fields.items():
        declared = {f["name"]: f for f in classes.get(model + owner, {}).get("fields", [])}
        for name, descriptor in fields.items():
            field = declared.get(name)
            if field is None or field["type"] != descriptor or int(field["access"], 16) & 0x8:
                failures.append(f"Missing or incompatible instance field: {model}{owner}.{name}:{descriptor}")

    qq_base = classes.get("com.tencent.mobileqq.app.BaseActivity", {})
    if qq_base.get("superclass") != base:
        failures.append("BaseActivity does not directly extend QBaseActivity")
    if any(m["name"] == "startActivity" for m in qq_base.get("methods", [])):
        failures.append("BaseActivity overrides startActivity; reassess dispatch hook ownership")
    if classes.get("com.tencent.mobileqq.activity.QQBrowserDelegationActivity", {}).get("superclass") != "com.tencent.mobileqq.app.BaseActivity":
        failures.append("Unexpected browser delegation superclass")
    if classes.get(model + "MiniAppInfo", {}).get("superclass") != model + "MiniAppBaseInfo":
        failures.append("Unexpected SDK model superclass")
    return {"passed": not failures, "failures": failures,
            "scope": "Static class/method/field shape only; inspect manifest separately with aapt2."}


class Dex:
    def __init__(self, data):
        if not data.startswith(b"dex\n"):
            raise ValueError("Expected a standard DEX file")
        self.data = data
        string_count, string_off = self.u32(56), self.u32(60)
        self.strings = []
        for i in range(string_count):
            off = self.uleb(self.u32(string_off + 4 * i))[1]
            end = data.index(0, off)
            self.strings.append(data[off:end].decode("utf-8", "replace"))
        self.types = [self.strings[self.u32(self.u32(68) + 4 * i)] for i in range(self.u32(64))]
        self.protos = []
        for i in range(self.u32(72)):
            off = self.u32(76) + 12 * i
            params = self.u32(off + 8)
            args = [] if not params else [self.types[self.u16(params + 4 + 2 * j)] for j in range(self.u32(params))]
            self.protos.append("(" + "".join(args) + ")" + self.types[self.u32(off + 4)])

    def u32(self, off):
        return struct.unpack_from("<I", self.data, off)[0]

    def u16(self, off):
        return struct.unpack_from("<H", self.data, off)[0]

    def uleb(self, off):
        value = shift = 0
        while True:
            byte = self.data[off]
            off += 1
            value |= (byte & 127) << shift
            if byte < 128:
                return value, off
            shift += 7
            if shift > 35:
                raise ValueError("Invalid ULEB128")

    def classes(self):
        for i in range(self.u32(96)):
            off = self.u32(100) + 32 * i
            name = class_name(self.types[self.u32(off)])
            if name not in TARGETS:
                continue
            parent = self.u32(off + 8)
            row = {"class": name, "superclass": None if parent == 0xffffffff else class_name(self.types[parent]),
                   "class_def_off": hex(off), "fields": [], "methods": []}
            cursor = self.u32(off + 24)
            if cursor:
                counts = []
                for _ in range(4):
                    count, cursor = self.uleb(cursor)
                    counts.append(count)
                for count in counts[:2]:
                    idx = 0
                    for _ in range(count):
                        diff, cursor = self.uleb(cursor)
                        idx += diff
                        access, cursor = self.uleb(cursor)
                        field = self.u32(84) + 8 * idx
                        row["fields"].append({"name": self.strings[self.u32(field + 4)],
                                              "type": self.types[self.u16(field + 2)], "access": hex(access)})
                for count in counts[2:]:
                    idx = 0
                    for _ in range(count):
                        diff, cursor = self.uleb(cursor)
                        idx += diff
                        access, cursor = self.uleb(cursor)
                        code, cursor = self.uleb(cursor)
                        method = self.u32(92) + 8 * idx
                        row["methods"].append({"name": self.strings[self.u32(method + 4)],
                                               "signature": self.protos[self.u16(method + 2)],
                                               "access": hex(access), "code_off": hex(code)})
            yield row


def main():
    parser = argparse.ArgumentParser(description="Read bounded TIM hook-class inventory without running APK code")
    parser.add_argument("apk", type=Path)
    parser.add_argument("--output", type=Path, help="Write JSON evidence to this path (otherwise stdout)")
    parser.add_argument("--check-contract", action="store_true", help="Validate the strict hook shape; exit nonzero on mismatch")
    args = parser.parse_args()
    report = {"artifact": args.apk.name, "sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(),
              "evidence_type": "static-dex-inventory", "limitations": "No APK execution, control-flow analysis, hot-patch or device validation.", "classes": []}
    with zipfile.ZipFile(args.apk) as archive:
        for name in archive.namelist():
            if name.endswith(".dex"):
                for row in Dex(archive.read(name)).classes():
                    report["classes"].append({"dex": name, **row})
    report["missing_classes"] = sorted(TARGETS - {row["class"] for row in report["classes"]})
    if args.check_contract:
        report["contract_check"] = check_contract(report)
    text = json.dumps(report, indent=2, ensure_ascii=False) + "\n"
    if args.output:
        args.output.write_text(text, encoding="utf-8")
    else:
        print(text)
    if args.check_contract:
        passed = report["contract_check"]["passed"]
        print("TIM static contract: " + ("PASS" if passed else "FAIL"), file=sys.stderr)
        if not passed:
            sys.exit(1)


if __name__ == "__main__":
    main()
