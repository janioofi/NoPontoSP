"""Uso: python tools/build_gtfs.py /pasta/com/GTFS (somente biblioteca padrão)."""
import csv,json,gzip,sys,pathlib,collections
src=pathlib.Path(sys.argv[1]); out=pathlib.Path(__file__).resolve().parents[1]/'app/src/main/assets/gtfs';out.mkdir(parents=True,exist_ok=True)
def rows(name):
 with (src/name).open(encoding='utf-8-sig',newline='') as f: yield from csv.DictReader(f)
index=collections.defaultdict(list); needed=set()
trip_by_shape={}
for r in rows('trips.txt'):
 entry=[r['trip_headsign'],r['shape_id']]
 if entry not in index[r['route_id']]: index[r['route_id']].append(entry)
 needed.add(r['shape_id'])
 trip_by_shape.setdefault(r['shape_id'],r['trip_id'])
shapes=collections.defaultdict(dict)
for name in ['shapes1.txt','shapes2.txt','shapes3.txt']:
 with (src/name).open(encoding='utf-8-sig',newline='') as stream:
  data=csv.DictReader(stream) if name=='shapes1.txt' else csv.DictReader(stream,fieldnames=['shape_id','shape_pt_lat','shape_pt_lon','shape_pt_sequence','shape_dist_traveled'])
  for r in data:
   if r['shape_id'] in needed: shapes[r['shape_id']][int(r['shape_pt_sequence'])]=[float(r['shape_pt_lat']),float(r['shape_pt_lon'])]
for sid,pts in shapes.items():
 with gzip.open(out/(sid+'.json.gz'),'wt') as f: json.dump([pts[k] for k in sorted(pts)],f,separators=(',',':'))

stops={}
for r in rows('stops.txt'):
 stops[r['stop_id']]=[r['stop_name'],float(r['stop_lat']),float(r['stop_lon'])]
trip_to_shape={trip:sid for sid,trip in trip_by_shape.items()}
route_stops=collections.defaultdict(list)
for r in rows('stop_times.txt'):
 sid=trip_to_shape.get(r['trip_id'])
 stop=stops.get(r['stop_id'])
 if sid is not None and stop is not None:
  route_stops[sid].append((int(r['stop_sequence']),r['stop_id'],stop))
stop_out=out/'stops';stop_out.mkdir(exist_ok=True)
for sid,entries in route_stops.items():
 entries.sort(key=lambda item:item[0])
 seen=set();points=[]
 for _,stop_id,stop in entries:
  if stop_id not in seen:
   points.append([stop_id,*stop]);seen.add(stop_id)
 with gzip.open(stop_out/(sid+'.json.gz'),'wt',encoding='utf-8') as f:
  json.dump(points,f,ensure_ascii=False,separators=(',',':'))
(out/'index.json').write_text(json.dumps(index,ensure_ascii=False),encoding='utf-8')
print(f'{len(index)} linhas, {len(shapes)} geometrias, {len(route_stops)} listas de paradas')
