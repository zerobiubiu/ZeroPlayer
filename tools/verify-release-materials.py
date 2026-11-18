"""核对已下载的草稿附件与 GitHub 元数据；只读，不解包或执行第三方材料。"""

import hashlib
import json
from pathlib import Path
import re
import sys
import zipfile


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha256(stream):
    digest = hashlib.sha256()
    for block in iter(lambda: stream.read(1024 * 1024), b""):
        digest.update(block)
    return digest.hexdigest()


def verify(tag, directory, metadata):
    require(re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+", tag), "版本标签无效")
    require(metadata["tag_name"] == tag and metadata["draft"] is True and metadata["prerelease"] is False,
            "目标不是该版本的正式发行草稿")
    prefix = "ZeroPlayer-" + tag[1:]
    apk = prefix + "-arm64-v8a.apk"
    kit = prefix + "-native-relink-arm64-v8a.tar.xz"
    names = {apk, kit, apk + ".sha256", kit + ".sha256"}
    assets = metadata["assets"]
    require(len(assets) == 4 and {a["name"] for a in assets} == names, "草稿必须恰有 APK、重链接材料及各自的摘要")
    require({p.name for p in directory.iterdir()} == names, "下载目录与四项草稿附件不一致")

    hashes = {}
    for asset in assets:
        name = asset["name"]
        file = directory / name
        require(file.is_file() and not file.is_symlink(), f"不是普通文件：{name}")
        require(asset["state"] == "uploaded" and file.stat().st_size == asset["size"] > 0, f"上传状态或大小不符：{name}")
        with file.open("rb") as source:
            hashes[name] = sha256(source)
        require(asset["digest"] == "sha256:" + hashes[name], f"下载字节与 GitHub 摘要不符：{name}")

    for name in (apk, kit):
        expected = f"{hashes[name]}  {name}".encode("ascii")
        sidecar = directory / (name + ".sha256")
        require(sidecar.stat().st_size <= len(expected) + 2 and sidecar.read_bytes().splitlines() == [expected],
                f"摘要文件必须只指向同名附件并与实际字节一致：{name}")

    lock = Path(__file__).resolve().parent / "mpv-static" / "relink-materials.json"
    identity = json.loads(lock.read_text(encoding="utf-8"))
    require((directory / kit).stat().st_size == identity["kit"]["bytes"] and hashes[kit] == identity["kit"]["sha256"],
            "重链接包不是该标签已实测的材料")
    with zipfile.ZipFile(directory / apk) as archive:
        libs = [i for i in archive.infolist() if i.filename.startswith("lib/") and i.filename.endswith("/libplayer.so")]
        require(len(libs) == 1 and libs[0].filename == "lib/arm64-v8a/libplayer.so", "APK 中的 libplayer.so 条目不唯一或架构不符")
        require(libs[0].file_size == identity["libplayer"]["bytes"], "APK 原生库大小与重链接实测不符")
        with archive.open(libs[0]) as source:
            require(sha256(source) == identity["libplayer"]["sha256"], "APK 原生库哈希与重链接实测不符")
    print("四项草稿附件、摘要文件、已重链接材料和 APK 内原生库均已核对。")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit("用法：python3 tools/verify-release-materials.py vX.Y.Z 下载目录 草稿元数据.json")
    try:
        verify(sys.argv[1], Path(sys.argv[2]), json.loads(Path(sys.argv[3]).read_text(encoding="utf-8")))
    except (OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile) as error:
        sys.exit(f"拒绝发布：{error}")
