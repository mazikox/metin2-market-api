import contextlib
import io
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import import_sqlite as importer


class MapImportsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.database = Path(self.temp.name) / "scanner.db"
        with contextlib.closing(sqlite3.connect(self.database)) as db, db:
            db.executescript("""
                CREATE TABLE shop_scan_run(run_id TEXT,state INTEGER,started_at TEXT,ended_at TEXT,map_id TEXT,
                    channel INTEGER,total_targets INTEGER,visited_targets INTEGER,failed_targets INTEGER);
                CREATE TABLE shop_observation(observation_id TEXT,run_id TEXT,shop_vid INTEGER,shop_title TEXT,
                    owner_name TEXT,map_id TEXT,channel INTEGER,x REAL,y REAL,z REAL,observed_at TEXT,
                    content_fingerprint TEXT,item_count INTEGER);
                CREATE TABLE shop_listing(listing_id INTEGER,observation_id TEXT,slot_index INTEGER,vnum INTEGER,
                    item_name TEXT,count INTEGER,price_raw INTEGER,unit_price INTEGER,tail_field INTEGER);
                CREATE TABLE shop_listing_attribute(listing_id INTEGER,slot_index INTEGER,attr_type INTEGER,attr_value INTEGER);
                CREATE TABLE shop_listing_socket(listing_id INTEGER,socket_index INTEGER,socket_value INTEGER);
            """)
            for run, map_id, day, state in [("old-a","metin2_map_a1",1,3),("new-a","metin2_map_a1_summer",2,3),
                                            ("new-b","metin2_map_b1",1,3),("pending-c","metin2_map_c1",3,2)]:
                date = f"2026-10-{day:02}T09:00:00Z"
                db.execute("INSERT INTO shop_scan_run VALUES(?,?,?,?,?,1,0,0,0)",(run,state,date,date,map_id))
    def tearDown(self):
        self.temp.cleanup()
    def invoke(self, maps, dry=False):
        args = ["import_sqlite.py","--server","beavium","--database",str(self.database),"--source-id","test",
                "--token","only-test","--latest-completed","--publishable"]
        for map_id in maps: args += ["--map",map_id]
        if dry: args += ["--dry-run"]
        with patch.object(sys,"argv",args),contextlib.redirect_stdout(io.StringIO()),contextlib.redirect_stderr(io.StringIO()):
            return importer.main()
    def test_latest_per_map_alias_and_manifests_without_writing_sqlite(self):
        payloads=[]
        def post(url,token,payload,server):
            payloads.append(payload)
            return {"importedRuns":1,"importedObservations":0,"importedListings":0}
        with patch.object(importer,"post_batch",side_effect=post):
            self.assertEqual(self.invoke(["metin2_map_a1","metin2_map_b1"]),0)
        self.assertEqual([p["runs"][0]["runId"] for p in payloads],["new-a","new-b"])
        self.assertTrue(all(p["runs"][0]["expectedObservations"] == 0 for p in payloads))
        self.assertTrue(all(p["batchId"].startswith("sqlite-v2-") for p in payloads))
        with contextlib.closing(sqlite3.connect(self.database)) as db, db:
            self.assertEqual(db.execute("SELECT count(*) FROM shop_scan_run").fetchone()[0],4)
    def test_missing_second_map_is_detected_before_any_network_request(self):
        with patch.object(importer,"post_batch") as post:
            self.assertEqual(self.invoke(["metin2_map_a1","metin2_map_c1"]),1)
            post.assert_not_called()
    def test_dry_run_and_duplicate_aliases_never_send_imports(self):
        with patch.object(importer,"post_batch") as post:
            self.assertEqual(self.invoke(["metin2_map_a1","metin2_map_b1"],True),0)
            self.assertEqual(self.invoke(["metin2_map_a1","metin2_map_a1_summer"]),1)
            post.assert_not_called()

if __name__ == "__main__":
    unittest.main()
