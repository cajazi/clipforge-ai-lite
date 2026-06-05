import sqlite3, os
db = os.path.join(os.environ["USERPROFILE"], "Downloads", "clipforge_copy.db")
c = sqlite3.connect(db)
print("=== projects table columns ===")
for r in c.execute("PRAGMA table_info(projects)"):
    print(r[1])
print("=== items WITH a transition set (any project) ===")
for r in c.execute("SELECT projectId, orderIndex, transitionType, transitionDurationMs FROM timeline_items WHERE transitionType IS NOT NULL AND transitionType != 'NONE'"):
    print(r)
print("=== count of items per project ===")
for r in c.execute("SELECT projectId, COUNT(*) FROM timeline_items GROUP BY projectId"):
    print(r)
