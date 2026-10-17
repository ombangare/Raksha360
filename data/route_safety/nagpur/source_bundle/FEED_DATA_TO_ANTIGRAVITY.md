# How to feed the data and master prompt to Antigravity

## Recommended method (Windows)

1. Download `raksha360_nagpur_route_data.zip` from this conversation.
2. Open Antigravity and open the existing repository folder:
   `C:\Users\omban\Downloads\Raksha360`
   Do not make a new empty project.
3. Extract the ZIP to a temporary folder first (right-click ZIP → Extract All). Confirm you see the CSV, GeoJSON, Python extractor, Overpass `.ql` files, `ROUTE_SCORING_SPEC.md`, `DATA_SOURCE_REGISTER.json`, `README.md`, and `MASTER_ANTIGRAVITY_PROMPT.md`.
4. Copy the extracted bundle folder into the repository at:
   `C:\Users\omban\Downloads\Raksha360\data\route_safety\nagpur\source_bundle\`
   Create the folders if they don't exist. Keep the bundle's original filenames. Do not place API keys/secrets inside it.
5. In Antigravity, attach the ZIP or the `MASTER_ANTIGRAVITY_PROMPT.md` file if the chat/task UI supports attachments. If it cannot read local folders just because they exist on disk, explicitly tell it to open/read the files from the path above. Attaching the ZIP is useful, but also extracting it into the repository makes files accessible to its coding workspace.
6. Open `MASTER_ANTIGRAVITY_PROMPT.md`, copy all its contents and paste into Antigravity's task/chat box. Send the whole prompt in one message. Do not paste only the shorter `ANTIGRAVITY_IMPORT_PROMPT.md`; that file covers the data layer only, while `MASTER_ANTIGRAVITY_PROMPT.md` covers the full repair and integration mission.
7. Ask Antigravity to inspect the working tree and summarize its plan before making broad edits. Review that plan, especially file deletion, database migration, package replacement, or credential changes.
8. Permit it to run local read-only inspection and tests. Do not approve live deploy, production database modification, real SOS email/SMS, or Git push as part of the initial task.

## Run data extraction (only if Antigravity doesn't run it itself)

Open PowerShell and run:

```powershell
cd C:\Users\omban\Downloads\Raksha360\data\route_safety\nagpur\source_bundle
py -m pip install requests "shapely>=2.0"
py extract_nagpur_route_layers.py --out ..\extracted
```

This writes `facilities.geojson`, `green_context.geojson`, `roads_lighting.geojson`, the corresponding `*_raw_osm.json` files, `extraction_report.json`, and `SOURCES_AND_LIMITATIONS.txt` into `data\route_safety\nagpur\extracted` relative to the repository. Depending on the public Overpass service, the request can take several minutes or fail temporarily. If it fails, do not treat an empty file as successful extraction. Share the error with Antigravity and retry later or use the included `.ql` query in https://overpass-turbo.eu/.

## What you should verify before accepting Antigravity's result

- It reports real counts from `extraction_report.json`; it did not invent facility or woodland records.
- It respects the source timestamps, OSM attribution/ODbL, and the bounding-box limitation.
- Unknown lighting remains unknown; forest is context, not automatically “dangerous”.
- It tests candidate route geometry against the data instead of counting POIs near a destination only.
- The build/test results are from commands it actually ran.
- SOS events only show as official dispatch if an actual configured gateway confirms dispatch.
- No production deploy, live alerts, production DB changes, or Git push occurred without your explicit approval.
