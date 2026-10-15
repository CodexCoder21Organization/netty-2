from pathlib import Path
import subprocess,os,signal,time,json,shutil,xml.etree.ElementTree as ET
lane=Path(__file__).resolve().parent; repo=lane/'netty-2'; evidence=lane/'evidence'
for cmd in [['git','fetch','origin'],['git','rebase','origin/4.2']]:
 subprocess.run(cmd,cwd=repo,check=True,timeout=120)
head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=repo,text=True).strip()
env=os.environ.copy();env.update(JAVA_OPTS='-Xmx2g',MAVEN_OPTS='-Xmx2g',JAVA_TOOL_OPTIONS='-Xmx2g')
cmd=['./mvnw','-B','-Dmaven.repo.local='+str(lane/'m2'),'-Dcheckstyle.skip','-Dforbiddenapis.skip','-Dxml.skip','-Dnohttp.skip','-pl','transport','test']
reports=repo/'transport/target/surefire-reports'
if reports.exists():shutil.rmtree(reports)
start=time.monotonic()
with (evidence/'full-transport.log').open('w') as out:
 p=subprocess.Popen(cmd,cwd=repo,env=env,stdout=out,stderr=subprocess.STDOUT,start_new_session=True)
 try:p.wait(timeout=900)
 except subprocess.TimeoutExpired:
  os.killpg(p.pid,signal.SIGTERM)
  try:p.wait(timeout=5)
  except subprocess.TimeoutExpired:os.killpg(p.pid,signal.SIGKILL);p.wait()
totals={k:0 for k in ['tests','failures','errors','skipped']}; suites=[]
dest=evidence/'full-transport-reports';dest.mkdir(exist_ok=True)
for f in reports.glob('*'):
 if f.is_file():shutil.copy2(f,dest/f.name)
for f in reports.glob('TEST-*.xml'):
 root=ET.parse(f).getroot();row={k:int(root.get(k,'0')) for k in totals};row['name']=root.get('name');suites.append(row)
 for k in totals:totals[k]+=row[k]
totals.update(head=head,exit_code=p.returncode,seconds=round(time.monotonic()-start,2))
(evidence/'full-transport-results.json').write_text(json.dumps({'totals':totals,'suites':suites,'command':cmd},indent=2)+'\n')
print(totals,flush=True)
raise SystemExit(p.returncode)
