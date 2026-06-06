import sqlite3, os
db = os.path.join(os.environ["USERPROFILE"], "Downloads", "clipforge_copy.db")
c = sqlite3.connect(db)
print("=== distinct aspectRatio + exportQuality ===")
for r in c.execute("SELECT DISTINCT aspectRatio, exportQuality FROM projects"):
    print(r)
