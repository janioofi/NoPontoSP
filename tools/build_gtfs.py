"""Generate compact GTFS assets used by the Android app."""
import argparse
import collections
import csv
import gzip
import json
import pathlib
import re
import shutil
import unicodedata

root = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description="Generate GTFS assets used by the app.")
parser.add_argument(
    "source",
    nargs="?",
    type=pathlib.Path,
    default=root / "app/src/main/assets/gtfs",
    help="folder containing the source GTFS files",
)
parser.add_argument(
    "--output",
    type=pathlib.Path,
    default=root / "app/src/main/assets/gtfs",
    help="folder for generated app assets",
)
args = parser.parse_args()
src = args.source
out = args.output

required = ["routes.txt", "trips.txt", "stops.txt", "stop_times.txt", "calendar.txt"]
missing = [name for name in required if not (src / name).is_file()]
legacy_shapes = ["shapes1.txt", "shapes2.txt", "shapes3.txt"]
if (src / "shapes.txt").is_file():
    shape_files = ["shapes.txt"]
elif all((src / name).is_file() for name in legacy_shapes):
    shape_files = legacy_shapes
else:
    missing.append("shapes.txt (or shapes1.txt, shapes2.txt and shapes3.txt)")
    shape_files = []
if missing:
    parser.error(f"invalid GTFS folder {src}; missing: {', '.join(missing)}")

out.mkdir(parents=True, exist_ok=True)


def rows(name):
    with (src / name).open(encoding="utf-8-sig", newline="") as stream:
        yield from csv.DictReader(stream)


def normalize_name(value):
    value = unicodedata.normalize("NFD", value.lower())
    value = "".join(char for char in value if not unicodedata.combining(char))
    value = re.sub(r"[^a-z0-9]", "", value)
    return value.replace("terminal", "term")


index = collections.defaultdict(list)
needed_shapes = set()
representative_trip_by_pattern = {}
trip_rows = list(rows("trips.txt"))
trip_row_by_id = {row["trip_id"]: row for row in trip_rows}
for row in trip_rows:
    direction_id = row.get("direction_id", "")
    pattern_key = (row["route_id"], direction_id, row["trip_headsign"], row["shape_id"])
    representative_trip_by_pattern.setdefault(pattern_key, row["trip_id"])
    entry = [row["trip_headsign"], row["shape_id"], direction_id,
             representative_trip_by_pattern[pattern_key]]
    if entry not in index[row["route_id"]]:
        index[row["route_id"]].append(entry)
    needed_shapes.add(row["shape_id"])

shapes = collections.defaultdict(dict)
for name in shape_files:
    with (src / name).open(encoding="utf-8-sig", newline="") as stream:
        if name in ("shapes.txt", "shapes1.txt"):
            data = csv.DictReader(stream)
        else:
            data = csv.DictReader(
                stream,
                fieldnames=[
                    "shape_id",
                    "shape_pt_lat",
                    "shape_pt_lon",
                    "shape_pt_sequence",
                    "shape_dist_traveled",
                ],
            )
        for row in data:
            if row["shape_id"] in needed_shapes:
                shapes[row["shape_id"]][int(row["shape_pt_sequence"])] = [
                    float(row["shape_pt_lat"]),
                    float(row["shape_pt_lon"]),
                ]

for shape_id, points in shapes.items():
    with gzip.open(out / (shape_id + ".json.gz"), "wt") as stream:
        json.dump([points[key] for key in sorted(points)], stream, separators=(",", ":"))

stops = {}
for row in rows("stops.txt"):
    stops[row["stop_id"]] = [
        row["stop_name"],
        float(row["stop_lat"]),
        float(row["stop_lon"]),
    ]

stops_by_trip = collections.defaultdict(list)
for trip_id in representative_trip_by_pattern.values():
    stops_by_trip[trip_id] = []
for row in rows("stop_times.txt"):
    trip_id = row["trip_id"]
    stop = stops.get(row["stop_id"])
    if trip_id in stops_by_trip and stop is not None:
        stops_by_trip[trip_id].append((int(row["stop_sequence"]), row["stop_id"], stop))

# Some GTFS feeds encode a full out-and-back loop as one trip. Split it at the
# stop matching its headsign so each direction gets its own stops and geometry.
for route_id, route_entries in index.items():
    expanded_entries = []
    for entry in route_entries:
        headsign, shape_id, direction_id, trip_id = entry
        trip_stops = sorted(stops_by_trip.get(trip_id, []))
        if (len(trip_stops) < 3
                or trip_stops[0][1] != trip_stops[-1][1]
                or direction_id not in ("0", "1")):
            expanded_entries.append(entry)
            continue

        normalized_headsign = normalize_name(headsign)
        pivot_index = next(
            (i for i, stop_row in enumerate(trip_stops[1:-1], start=1)
             if normalize_name(stop_row[2][0]) == normalized_headsign),
            -1,
        )
        shape_points = shapes.get(shape_id, {})
        ordered_shape = [shape_points[key] for key in sorted(shape_points)]
        if pivot_index < 0 or len(ordered_shape) < 3:
            expanded_entries.append(entry)
            continue

        pivot_stop = stops[trip_stops[pivot_index][1]]
        pivot_lat, pivot_lon = pivot_stop[1], pivot_stop[2]
        pivot_shape_index = min(
            range(1, len(ordered_shape) - 1),
            key=lambda i: (ordered_shape[i][0] - pivot_lat) ** 2
                          + (ordered_shape[i][1] - pivot_lon) ** 2,
        )
        reverse_direction = "1" if direction_id == "0" else "0"
        outbound_trip_id = trip_id + "__direction_" + direction_id
        return_trip_id = trip_id + "__direction_" + reverse_direction
        outbound_shape_id = shape_id + "__" + outbound_trip_id
        return_shape_id = shape_id + "__" + return_trip_id

        expanded_entries.extend([
            [headsign, outbound_shape_id, direction_id, outbound_trip_id],
            [trip_stops[0][2][0], return_shape_id, reverse_direction, return_trip_id],
        ])
        stops_by_trip[outbound_trip_id] = trip_stops[:pivot_index + 1]
        stops_by_trip[return_trip_id] = trip_stops[pivot_index:]
        shapes[outbound_shape_id] = {
            i: point for i, point in enumerate(ordered_shape[:pivot_shape_index + 1])
        }
        shapes[return_shape_id] = {
            i: point for i, point in enumerate(ordered_shape[pivot_shape_index:])
        }
    index[route_id] = expanded_entries

for shape_id, points in shapes.items():
    if "__direction_" not in shape_id:
        continue
    with gzip.open(out / (shape_id + ".json.gz"), "wt") as stream:
        json.dump([points[key] for key in sorted(points)], stream, separators=(",", ":"))

stop_out = out / "stops"
stop_out.mkdir(exist_ok=True)
trip_stop_out = out / "stops_by_trip"
trip_stop_out.mkdir(exist_ok=True)
legacy_stops_by_shape = {}
for trip_id, entries in stops_by_trip.items():
    entries.sort(key=lambda item: item[0])
    seen = set()
    points = []
    for _, stop_id, stop in entries:
        if stop_id not in seen:
            points.append([stop_id, *stop])
            seen.add(stop_id)
    with gzip.open(trip_stop_out / (trip_id + ".json.gz"), "wt", encoding="utf-8") as stream:
        json.dump(points, stream, ensure_ascii=False, separators=(",", ":"))
    legacy_stops_by_shape.setdefault(trip_id, points)

# Keep legacy shape-based files for non-loop trips that still use old indexes.
for trip_id, points in legacy_stops_by_shape.items():
    row = trip_row_by_id.get(trip_id)
    if row is None:
        continue
    pattern_key = (
        row["route_id"], row.get("direction_id", ""),
        row["trip_headsign"], row["shape_id"],
    )
    if representative_trip_by_pattern.get(pattern_key) != trip_id:
        continue
    if any(entry[3] == trip_id for entry in index[row["route_id"]]):
        with gzip.open(stop_out / (row["shape_id"] + ".json.gz"),
                       "wt", encoding="utf-8") as stream:
            json.dump(points, stream, ensure_ascii=False, separators=(",", ":"))

(out / "index.json").write_text(json.dumps(index, ensure_ascii=False), encoding="utf-8")

# These are the tables needed by schedules and stop lookup; keep source .txt files out of the APK.
schedule_files = [
    "routes.txt",
    "trips.txt",
    "stops.txt",
    "stop_times.txt",
    "calendar.txt",
    "frequencies.txt",
]
table_out = out / "tables"
table_out.mkdir(exist_ok=True)
for name in schedule_files:
    source = src / name
    if not source.is_file():
        if name == "frequencies.txt":
            continue
        raise FileNotFoundError(f"required GTFS file not found: {source}")
    target_name = name[:-4] + ".csv.gz"
    with source.open("rb") as input_file, (table_out / target_name).open("wb") as raw_output:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw_output, mtime=0) as compressed:
            shutil.copyfileobj(input_file, compressed)
    legacy_asset = out / (name + ".gz")
    if legacy_asset.is_file():
        legacy_asset.unlink()

compressed_count = sum(
    1 for name in schedule_files if (table_out / (name[:-4] + ".csv.gz")).is_file()
)
print(
    f"{len(index)} routes, {len(shapes)} geometries, {len(stops_by_trip)} trip stop lists, "
    f"{compressed_count} compressed GTFS tables"
)
