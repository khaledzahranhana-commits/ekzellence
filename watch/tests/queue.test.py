"""Run the actual schema SQL and retry query with SQLite, including reopening the DB."""
import json,re,sqlite3,tempfile
from pathlib import Path
source=(Path(__file__).parents[1]/'app/src/main/java/com/ekzellence/watch/Store.java').read_text()
create=[json.loads('"'+s+'"') for s in re.findall(r'd.execSQL\("([^"\n]+)"\)',source) if s.startswith('CREATE')]
with tempfile.TemporaryDirectory() as folder:
    path=Path(folder)/'queue.db'
    db=sqlite3.connect(path)
    for sql in create:db.execute(sql)
    for values in [('usb','file:///pending1',100,200,1024,0),('usb','file:///saved',100,200,1024,1)]:
        db.execute('INSERT INTO recordings(camera,uri,start,end,size,cloud) VALUES (?,?,?,?,?,?)',values)
    db.execute("UPDATE recordings SET attempts=attempts+1,retryAfter=500,lastError='offline' WHERE cloud=0")
    db.commit();db.close()
    db=sqlite3.connect(path)
    assert db.execute('SELECT COUNT(*) FROM recordings WHERE cloud=0').fetchone()[0]==1
    retry='SELECT id FROM recordings WHERE cloud=0 AND retryAfter<=? ORDER BY start LIMIT 64'
    assert db.execute(retry,(499,)).fetchall()==[]
    assert len(db.execute(retry,(500,)).fetchall())==1
    expired='SELECT id FROM recordings WHERE end<? AND (?=0 OR cloud=1)'
    assert len(db.execute(expired,(1000,1)).fetchall())==1,'unuploaded footage must not expire in cloud mode'
    assert len(db.execute(expired,(1000,0)).fetchall())==2,'legacy local retention still works'
    assert db.execute('SELECT attempts,lastError FROM recordings WHERE cloud=0').fetchone()==(1,'offline')
    db.close()
print('SQLite upload queue: schema, persistent pending footage across reopen, retry timing, error state and protection from premature retention deletion passed.')
