#!/usr/bin/env python3
"""Optional geocoding review helper for the Raksha360 public station snapshot.

Queries OSM Nominatim at a respectful rate and writes candidates to a separate CSV.
It never writes coordinates into nagpur_police_stations.json. Human review is required
before a coordinate can be used to pin a station on the operations map.
"""
from __future__ import annotations
import csv, json, time, urllib.parse, urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
SOURCE = HERE / 'nagpur_police_stations.json'
OUTPUT = HERE / 'nagpur_police_stations_geocoded_review.csv'
USER_AGENT = 'Raksha360PublicDirectoryReview/1.0 (manual geocoding review; contact: project maintainer)'


def main() -> None:
    payload = json.loads(SOURCE.read_text(encoding='utf-8'))
    rows = []
    for idx, station in enumerate(payload['records']):
        query = f"{station['name']}, {station.get('address') or 'Nagpur'}, Maharashtra, India"
        url = 'https://nominatim.openstreetmap.org/search?' + urllib.parse.urlencode({
            'q': query, 'format': 'jsonv2', 'limit': 3, 'addressdetails': 1
        })
        req = urllib.request.Request(url, headers={'User-Agent': USER_AGENT, 'Accept': 'application/json'})
        candidates = []
        try:
            with urllib.request.urlopen(req, timeout=20) as response:
                candidates = json.loads(response.read().decode('utf-8'))
        except Exception as exc:
            rows.append({'station_id':station['station_id'],'name':station['name'],'query':query,'candidate_rank':'','latitude':'','longitude':'','display_name':'','osm_type':'','osm_id':'','review_status':'LOOKUP_FAILED','review_notes':str(exc)[:200]})
        else:
            if not candidates:
                rows.append({'station_id':station['station_id'],'name':station['name'],'query':query,'candidate_rank':'','latitude':'','longitude':'','display_name':'','osm_type':'','osm_id':'','review_status':'NO_MATCH_REVIEW_MANUALLY','review_notes':'No result returned; do not guess coordinates.'})
            else:
                for rank, item in enumerate(candidates, 1):
                    rows.append({'station_id':station['station_id'],'name':station['name'],'query':query,'candidate_rank':rank,'latitude':item.get('lat',''),'longitude':item.get('lon',''),'display_name':item.get('display_name',''),'osm_type':item.get('osm_type',''),'osm_id':item.get('osm_id',''),'review_status':'REVIEW_REQUIRED','review_notes':'Candidate from OSM Nominatim. Confirm station identity, city and entrance/location before importing.'})
        if idx < len(payload['records']) - 1:
            time.sleep(1.1)  # Respect the public Nominatim one-request-per-second policy.
    with OUTPUT.open('w', encoding='utf-8-sig', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=['station_id','name','query','candidate_rank','latitude','longitude','display_name','osm_type','osm_id','review_status','review_notes'])
        writer.writeheader(); writer.writerows(rows)
    print(f'Wrote {len(rows)} coordinate candidate rows to {OUTPUT}')
    print('Do not import any coordinate until a human confirms the station identity and map position.')

if __name__ == '__main__':
    main()
