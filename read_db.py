import sqlite3, os
db = os.path.join(os.environ["USERPROFILE"], "Downloads", "clipforge_copy.db")
c = sqlite3.connect(db)
pid = "e69e8294-0529-4a17-a36e-851247a5b96c"
print("=== timeline_items for project ===")
for r in c.execute("SELECT orderIndex, mediaAssetId, transitionType, transitionDurationMs, trimStartMs, trimEndMs FROM timeline_items WHERE projectId=? ORDER BY orderIndex", (pid,)):
    print(r)
print("=== distinct transitionType values across ALL items ===")
for r in c.execute("SELECT DISTINCT transitionType FROM timeline_items"):
    print(r)
