"""Uso: python tools/build_gtfs.py /pasta/com/GTFS (somente biblioteca padrão)."""
import csv,json,gzip,sys,pathlib,collections
src=pathlib.Path(sys.argv[1]); out=pathlib.Path(__file__).resolve().parents[1]/'app/src/main/assets/gtfs';out.mkdir(parents=True,exist_ok=True)
def rows(name):
 with (src/name).open(encoding='utf-8-sig',newline='') as f: yield from csv.DictReader(f)
index=collections.defaultdict(list); needed=set()
for r in rows('trips.txt'):
 entry=[r['trip_headsign'],r['shape_id']]
 if entry not in index[r['route_id']]: index[r['route_id']].append(entry)
 needed.add(r['shape_id'])
shapes=collections.defaultdict(dict)
for name in ['shapes1.txt','shapes2.txt','shapes3.txt']:
 with (src/name).open(encoding='utf-8-sig',newline='') as stream:
  data=csv.DictReader(stream) if name=='shapes1.txt' else csv.DictReader(stream,fieldnames=['shape_id','shape_pt_lat','shape_pt_lon','shape_pt_sequence','shape_dist_traveled'])
  for r in data:
   if r['shape_id'] in needed: shapes[r['shape_id']][int(r['shape_pt_sequence'])]=[float(r['shape_pt_lat']),float(r['shape_pt_lon'])]
for sid,pts in shapes.items():
 with gzip.open(out/(sid+'.json.gz'),'wt') as f: json.dump([pts[k] for k in sorted(pts)],f,separators=(',',':'))
(out/'index.json').write_text(json.dumps(index,ensure_ascii=False),encoding='utf-8')
print(f'{len(index)} linhas, {len(shapes)} geometrias')
