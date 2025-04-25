import firebase_admin
from firebase_admin import credentials, db
import time

from ultralytics import YOLO

import requests
from PIL import Image
from io import BytesIO


print("Script started")

# Initialize YOLO model
model = YOLO("./rodentYolo60Epochs.pt")

json_path = 'PUT YOUR PATH TO YOUR SERVICE ACCOUNT KEY HERE'
# see https://firebase.google.com/docs/database/admin/start for help
database_url = 'PUT YOUR FIREBASE DATABASE URL HERE'

cred = credentials.Certificate(json_path)
firebase_admin.initialize_app(cred, {'databaseURL': database_url})

def listener(event):
    print()
    print("running listener")
    if event.event_type == 'put':
        #print("1")
        path_parts = event.path.strip('/').split('/')
        if len(path_parts) == 2:
            #print("2")
            location_id, report_id = path_parts
            data = event.data
            if isinstance(data, dict) and 'imageUrl' in data:
                #print("3")
                image_url = data['imageUrl']
                try:
                    #print("4")
                    response = requests.get(image_url)
                    response.raise_for_status()
                    img = Image.open(BytesIO(response.content))
                    results = model.predict(img, save=False, conf=0.5)
                    if len(results[0].boxes) > 0:
                        db.reference(f'Locations/{location_id}/user_high_volume').set(True)
                        print(f"Rodents detected in {report_id} for location {location_id}, set user_high_volume to True")
                    else:
                        print(f"No rodents detected in {report_id} for location {location_id}")
                except Exception as e:
                    print(f"Error processing image for {report_id} in {location_id}: {e}")

ref = db.reference('Reports')
ref.listen(listener)

print("Listening for new reports...")

while True:
    time.sleep(1)