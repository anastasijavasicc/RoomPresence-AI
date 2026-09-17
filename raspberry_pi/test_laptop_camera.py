import requests

URL = "http://192.168.1.226:5001/snapshot"

response = requests.get(URL, timeout=10)

if response.status_code == 200:
    with open("laptop_snapshot.jpg", "wb") as f:
        f.write(response.content)

    print("Slika sacuvana: laptop_snapshot.jpg")
else:
    print("Greska:", response.status_code)
