#!/usr/bin/env python3
"""Validate a .claude/skills/<id>/ directory against docs/developer-agent/CONTRACT.md.

Usage: check_skill_conformance.py <skill-directory> [<skill-directory> ...]

Requires PyYAML (pip install pyyaml) - not installed by default, this is a local dev-time
check, not yet wired into CI (AIW-114 doesn't require that; a later ticket may add it).
"""

import re
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    print("error: PyYAML is required - run: pip install pyyaml", file=sys.stderr)
    sys.exit(2)

ALLOWED_ACTION_CATEGORIES = {
    "read-only",
    "local-write",
    "local-git",
    "external-read",
    "external-visible",
    "destructive",
}

REQUIRED_SKILL_YAML_FIELDS = [
    "schemaVersion",
    "id",
    "name",
    "version",
    "description",
    "inputs",
    "outputs",
    "stopConditions",
    "allowedActionCategories",
]


def fail(reasons):
    for reason in reasons:
        print(f"  - {reason}", file=sys.stderr)


def check_skill_md_frontmatter(skill_dir):
    """Returns (ok, reasons, frontmatter_name)."""
    skill_md = skill_dir / "SKILL.md"
    if not skill_md.is_file():
        return False, ["SKILL.md is missing"], None

    text = skill_md.read_text(encoding="utf-8")
    match = re.match(r"^---\n(.*?)\n---\n", text, re.DOTALL)
    if not match:
        return False, ["SKILL.md has no YAML frontmatter (must start with '---')"], None

    frontmatter = yaml.safe_load(match.group(1)) or {}
    reasons = []
    for field in ("name", "description"):
        if not frontmatter.get(field):
            reasons.append(f"SKILL.md frontmatter is missing required field '{field}'")

    name = frontmatter.get("name")
    if name and name != skill_dir.name:
        reasons.append(
            f"SKILL.md frontmatter name '{name}' does not match directory name '{skill_dir.name}'"
        )

    return len(reasons) == 0, reasons, name


def check_skill_yaml(skill_dir):
    skill_yaml = skill_dir / "skill.yaml"
    if not skill_yaml.is_file():
        return False, ["skill.yaml is missing"]

    data = yaml.safe_load(skill_yaml.read_text(encoding="utf-8")) or {}
    reasons = []

    for field in REQUIRED_SKILL_YAML_FIELDS:
        if field not in data or data[field] in (None, ""):
            reasons.append(f"skill.yaml is missing required field '{field}'")

    if data.get("id") and data["id"] != skill_dir.name:
        reasons.append(
            f"skill.yaml id '{data['id']}' does not match directory name '{skill_dir.name}'"
        )

    for category in data.get("allowedActionCategories") or []:
        if category not in ALLOWED_ACTION_CATEGORIES:
            reasons.append(
                f"skill.yaml allowedActionCategories entry '{category}' is not in the fixed "
                f"vocabulary ({', '.join(sorted(ALLOWED_ACTION_CATEGORIES))})"
            )

    return len(reasons) == 0, reasons


def check_skill(skill_dir):
    print(f"Checking {skill_dir} ...")
    md_ok, md_reasons, _ = check_skill_md_frontmatter(skill_dir)
    yaml_ok, yaml_reasons = check_skill_yaml(skill_dir)

    reasons = md_reasons + yaml_reasons
    if reasons:
        fail(reasons)
        return False

    print("  OK")
    return True


def main(argv):
    if len(argv) < 2:
        print(__doc__, file=sys.stderr)
        return 2

    all_ok = True
    for raw_path in argv[1:]:
        skill_dir = Path(raw_path)
        if not skill_dir.is_dir():
            print(f"error: {skill_dir} is not a directory", file=sys.stderr)
            all_ok = False
            continue
        if not check_skill(skill_dir):
            all_ok = False

    return 0 if all_ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
