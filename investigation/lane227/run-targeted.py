from pathlib import Path
import subprocess,os,signal,time,json,shutil,sys,xml.etree.ElementTree as ET
lane=Path(__file__).resolve().parent
repo=lane/'netty-2'
label=sys.argv[1]; count=int(sys.argv[2]); pattern=sys.argv[3]
env=os.environ.copy();env.update(JAVA_OPTS='-Xmx2g',MAVEN_OPTS='-Xmx2g',JAVA_TOOL_OPTIONS='-Xmx2g')
base=['./mvnw','-B','-Dmaven.repo.local='+str(lane/'m2'),'-Dcheckstyle.skip','-Dforbiddenapis.skip','-Dxml.skip','-Dnohttp.skip','-pl','transport','-Dtest='+pattern]
results=[]
for i in range(1,count+1):
 for cmd in [['git','fetch','origin'],['git','rebase','origin/4.2']]:
  subprocess.run(cmd,cwd=repo,check=True,stdout=subprocess.DEVNULL,timeout=120)
 report=repo/'transport/target/surefire-reports'
 if report.exists():shutil.rmtree(report)
 log=lane/'evidence'/f'{label}-{i}.log'
 with log.open('w') as output:
  p=subprocess.Popen(base+(['test'] if i==1 else ['surefire:test']),cwd=repo,env=env,stdout=output,stderr=subprocess.STDOUT,start_new_session=True)
  try:p.wait(timeout=240)
  except subprocess.TimeoutExpired:
   os.killpg(p.pid,signal.SIGTERM)
   try:p.wait(timeout=5)
   except subprocess.TimeoutExpired:os.killpg(p.pid,signal.SIGKILL);p.wait()
 totals={k:0 for k in ['tests','failures','errors','skipped']}
 dest=lane/'evidence'/f'{label}-{i}-reports';dest.mkdir(exist_ok=True)
 for f in report.glob('*'):
  if f.is_file():shutil.copy2(f,dest/f.name)
 for f in report.glob('TEST-*.xml'):
  root=ET.parse(f).getroot()
  for k in totals:totals[k]+=int(root.get(k,'0'))
 totals.update(run=i,exit_code=p.returncode)
 results.append(totals)
 (lane/'evidence'/f'{label}-results.json').write_text(json.dumps(results,indent=2)+'\n')
 print(label,totals,flush=True)
 if label!='baseline' and p.returncode:raise SystemExit(p.returncode)
