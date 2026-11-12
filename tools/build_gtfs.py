"""Gera os arquivos GTFS derivados (somente biblioteca padrão)."""
import argparse,csv,json,gzip,pathlib,collections
root=pathlib.Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description='Gera os arquivos GTFS usados pelo app.')
parser.add_argument('source',nargs='?',type=pathlib.Path,default=root/'app/src/main/assets/gtfs',help='pasta dos arquivos GTFS (padrão: app/src/main/assets/gtfs)')
src=parser.parse_args().source
required=['trips.txt','stops.txt','stop_times.txt']
missing=[name for name in required if not (src/name).is_file()]
legacy_shapes=['shapes1.txt','shapes2.txt','shapes3.txt']
if (src/'shapes.txt').is_file():
 shape_files=['shapes.txt']
elif all((src/name).is_file() for name in legacy_shapes):
 shape_files=legacy_shapes
else:
 missing.append('shapes.txt (ou shapes1.txt, shapes2.txt e shapes3.txt)')
 shape_files=[]
if missing: parser.error(f'pasta GTFS inválida em {src}; arquivos ausentes: {", ".join(missing)}')
out=root/'app/src/main/assets/gtfs';out.mkdir(parents=True,exist_ok=True)
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
for name in shape_files:
 with (src/name).open(encoding='utf-8-sig',newline='') as stream:
  data=csv.DictReader(stream) if name=='shapes.txt' or name=='shapes1.txt' else csv.DictReader(stream,fieldnames=['shape_id','shape_pt_lat','shape_pt_lon','shape_pt_sequence','shape_dist_traveled'])
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
