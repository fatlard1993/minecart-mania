# Minecart Mania - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting
clients need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader,
Fabric API) and `fabric.mod.json` (Java).

## Art

`generate_textures.py` cuts every texture out of the vanilla jar by rule: the wooden rail is
the iron rail turned to plank, the copper powered rail is the gold one turned to copper, the
crossing is a rail laid over a rail. `generate_models.py` writes the three-dimensional rail templates, the
diagonal chords, and every model and blockstate. `generate_icon.py` makes the icon. All three
are deterministic; re-run them after a Minecraft version bump.
