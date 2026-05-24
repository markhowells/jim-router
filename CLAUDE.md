# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

**JIM** (Jim's Isochrone Method) — a Java Swing desktop application for offshore sailing route optimisation, also known as **Course2Steer**. It computes optimal sailing routes using isochrone expansion against GFS wind/wave GRIB forecasts and Copernicus ocean currents, then refines them with a Differential Evolution solver to produce a timed course-to-steer.

## Build & Run

No build system file is present — this is an **IntelliJ IDEA** project. Build and run through the IDE:

- Source root: `src/`
- Dependencies in `jar/`: `netcdfAll-5.8.0.jar` (NetCDF/Copernicus currents), `sqlite-jdbc-3.51.1.0.jar` (MBTile chart support)
- Entry point: `uk.co.sexeys.Main`
- JVM needs enough heap for large grids (e.g. `-Xmx4g`)

To compile manually from repo root:
```bash
javac -cp "jar/netcdfAll-5.8.0.jar:jar/sqlite-jdbc-3.51.1.0.jar" \
  -d out $(find src -name "*.java")
java -cp "out:jar/netcdfAll-5.8.0.jar:jar/sqlite-jdbc-3.51.1.0.jar" \
  uk.co.sexeys.Main
```

## Configuration

All run-time parameters live in **`Main.java`** as static constants — edit them directly before building:

- `ROUTE` — the route definition DSL (see below)
- `root` — path to the `database/` directory (default `"./database/"`)
- `WindSource` — NOAA GFS download URL
- `WindResolution` — GFS grid resolution (`"0p25"`, `"0p50"`, `"1p00"`)
- `useWater`, `useIceZone`, `crossDateLine` — feature toggles
- `C2SLegs`, `C2SAgents`, `C2SCR` — Differential Evolution tuning

## Route DSL

Routes are defined as a multi-line string in `Main.ROUTE`. Each line is a directive:

```
Search Box: 37*00'N 65*00'W 0*00'N 09*0'W   # map viewport and WVS lon limits
Using Polar: ELEMENTAL                        # folder name under database/
Using Wind: GFS20260104193901703.grb          # file under database/grib/
Using Waves: GFS20260104193901705.grb         # file under database/grib/
Using Current: current20260104.nc             # NetCDF file under database/grib/
Depart: 28*54'41"N 013*42'26"W 2026/01/10 09:00 UTC
Obstruction: lat;lon;...;lat TSS description  # polygon barrier
Expand: 300 nm 360 bins 0.1 hour step        # isochrone fan-out phase
Leg: 13*29'05"N 058*58'06"W 500 bins of 4 nm 2 hour step
Destination: 13*15'43"N 059*38'47"W 1 nm 360 bins 0.5 hour step
```

Waypoint types: `Depart`, `Expand`, `Leg`, `Destination`, `Buoy`, `Gate`, `Diode`, `InterimFix` (prefix `⎈` or `Fix`), `Obstruction`.

## Database Directory Layout

```
database/
  grib/          GFS .grb files and Copernicus .nc current files
  charts/
    tides/       HARMONIC tidal harmonic data + tidal stream chart PNGs
    Bathymetry/  GEBCO_2020.dat for depth shading
  <POLAR>/       One or more polar CSV files (Virtual Regatta format)
  ELEMENTAL/     Example polar folder
```

GFS data is downloaded via `copernicusmarine` CLI (currents) and the NOAA NOMADS server (wind/waves). See the comments at the top of `Main.java` for the exact commands.

## Architecture

### Core Algorithm Flow

1. **`Main.main()`** → creates `StreamFrame` (JFrame)
2. **`StreamPanel.ParseRoute()`** parses the route DSL, instantiates `Wind`, `Water`, `Waves`, boat `Polar`
3. **`StreamPanel.newRoute()`** creates a `CrossTrack` (concrete `JIM`) and calls `SearchInit()` then `Search()`
4. **`JIM.Search()`** runs the isochrone expansion: each time-step creates a new `Agent` fan that advances `Fix`-chain linked lists across the grid, respecting `Shoreline`, `Obstruction`, and tidal currents
5. **`DifferentialEvolution`** (Course-To-Steer) takes the JIM track and optimises a sequence of waypoints using DE, producing timed navigation instructions via `PrintInstructions()`

### Key Classes

| Class | Role |
|---|---|
| `Main` | Configuration constants + entry point |
| `StreamFrame` / `StreamPanel` | Swing GUI, keyboard/mouse handling, render loop |
| `JIM` (abstract) | Isochrone core: agent expansion, drawing, GPX export |
| `CrossTrack extends JIM` | Concrete isochrone using cross-track corridor bins |
| `Agent` (in `JIM` pkg) | One step in the isochrone; singly linked list back to departure |
| `DifferentialEvolution` | Course-To-Steer optimiser; wraps a `JIM.Agent` chain |
| `Boat` | Polar lookup, `courseToSteer()`, `bestTack()`, `constantSail()` |
| `Fix` | Position/time/wind/tide/velocity snapshot; **pool-allocated** via `Fix.spare` |
| `Polar` / `BiLinear` | Boat speed polars; `raw` and `VMG`-optimised variants |
| `Mercator` | Screen↔lat-lon projection |
| `Shoreline` / `WVS` | World Vector Shoreline land-avoidance |

### Sub-packages

- **`wind`** — `Wind` (abstract) + `Prevailing`, `SailDocs` (GRIB1/2), `VRWind` (Virtual Regatta live)
- **`water`** — `Water` (abstract) + `PrevailingCurrent`, `Current` (NetCDF), `Tide` (GRIB)
- **`waypoint`** — all waypoint types; each knows its bin geometry for isochrone expansion
- **`JIM`** — `JIM`, `CrossTrack`, `Agent`, `Route`
- **`CMap`** — BSB/KAP nautical chart decoder + renderer
- **`jgribx`** — embedded GRIB1/GRIB2 parser (grib1/ and grib2/ sub-packages)

### Fix Pool

`Fix` objects are expensive to allocate. A static pool (`Fix.spare`) is pre-populated with 50 000 objects at startup via `Fix.InitSpares()`. Always call `Fix.recycle(track)` to return lists to the pool rather than discarding them.

## Keyboard Shortcuts (runtime)

Press `?` in the app for the full list. Key ones:

| Key | Action |
|---|---|
| `a` | Advance JIM search one step |
| `space` / `o` | Toggle continuous DE iteration |
| `6`–`9` | Run 100 DE iterations (mutation factor 0.5→0.2) |
| `q` / `Q` | Double DE waypoints / toggle raw vs VMG polar |
| `h` | Halve DE route length |
| `r` | Recompute DE with random waypoints |
| `i` / `I` | Show/copy course-to-steer (waypoint / 12 h window) |
| `g` / `G` | Write DE / JIM GPX to file |
| `R` | Route analysis window |
| `p` | Polar plot window |
| `[` / `]` | Decrease / increase chart scale level |
| `ctrl-z` | Undo last calculation |
| `ctrl-s` | Save screenshot to `database/` |

Mouse: left-drag to pan, wheel to zoom, shift-wheel to step time, `d`+left-click to set new JIM end-point, `s`+left-click to set new start-point.
