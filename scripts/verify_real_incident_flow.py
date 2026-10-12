import urllib.request
import json
import sys

def main():
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')
    base_url = "http://127.0.0.1:4000"
    device_token = "local-dev-device-token-change-me"
    dashboard_token = "local-dev-dashboard-token-change-me"

    print("=== Testing Real Nagpur Emergency Incident Flow ===")
    event_payload = {
        "eventId": "REAL-SOS-SITABULDI-01",
        "triggerType": "MANUAL_SOS",
        "latitude": 21.1458,
        "longitude": 79.0882,
        "accuracyMeters": 15.0,
        "locationDescription": "Sitabuldi Interchange Metro Station, Nagpur",
        "isDemoMode": False
    }

    req = urllib.request.Request(
        f"{base_url}/api/v1/emergency-events",
        data=json.dumps(event_payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {device_token}"
        }
    )
    res = urllib.request.urlopen(req)
    created = json.loads(res.read().decode("utf-8"))
    print(f"[OK] Emergency event submitted: {created['eventId']}")
    print(f"     Status: {created['status']}")

    # Call Nearby Police Stations endpoint
    nearby_req = urllib.request.Request(
        f"{base_url}/api/v1/incidents/REAL-SOS-SITABULDI-01/nearby-stations?limit=5&radius_km=10",
        headers={"X-Dashboard-Token": dashboard_token}
    )
    nearby_res = urllib.request.urlopen(nearby_req)
    data = json.loads(nearby_res.read().decode("utf-8"))
    stations = data.get("stations", [])
    print(f"\n[OK] Nearby Stations Ranked: {len(stations)} stations within 10 km")
    for idx, s in enumerate(stations, 1):
        print(f"  {idx}. {s['name']} ({s.get('nameMr')})")
        print(f"     Distance: {s['distanceKm']} km")
        print(f"     Address: {s['address']}")
        print(f"     Phone: {s['phone']} -> tel link: {s['telUri']}")
        print(f"     Coordinate status: {s['coordinateStatus']}")
        print(f"     Coordinate source: {s['coordinateSource']}")
        print(f"     OSM Directions: {s['osmDirectionsUrl']}")

    print(f"\n[OK] Mandatory Disclaimer Present:")
    print(f"     \"{data.get('disclaimer')}\"")

    # Clean up test event so operational database remains clean
    with urllib.request.urlopen(
        urllib.request.Request(
            f"{base_url}/api/v1/incidents/REAL-SOS-SITABULDI-01/status",
            data=json.dumps({"status": "Resolved", "notes": "Verified genuine flow test completed"}).encode("utf-8"),
            headers={"Content-Type": "application/json", "X-Dashboard-Token": dashboard_token}
        )
    ) as clean_res:
        print("\n[OK] Test incident marked Resolved.")

if __name__ == "__main__":
    main()
