"""Offline regression tests for the exact native archive parser; no platform logins."""
from pathlib import Path
import argparse,tarfile,io,gzip,os,subprocess
parser=argparse.ArgumentParser();parser.add_argument('--app',type=Path,required=True);args=parser.parse_args()
base=Path(__file__).resolve().parents[1];out=base/'build'/'tests';out.mkdir(parents=True,exist_ok=True)
for name,kind in [('valid','valid'),('traversal','path'),('link','link'),('checksum','checksum')]:
 b=io.BytesIO()
 with tarfile.open(fileobj=b,mode='w',format=tarfile.USTAR_FORMAT) as t:
  files={'package/cli.js':b'// parser fixture','package/package.json':b'{"version":"1.62.1"}'} if kind=='valid' else {'package/../../escape.txt':b'blocked'} if kind=='path' else {'package/link':b''}
  for filename,data in files.items():
   h=tarfile.TarInfo(filename);h.size=len(data)
   if kind=='link':h.type=tarfile.SYMTYPE;h.linkname='../../outside'
   t.addfile(h,io.BytesIO(data))
 raw=b.getvalue()
 if kind=='checksum':raw=bytes([raw[0]^1])+raw[1:]
 (out/(name+'.tgz')).write_bytes(gzip.compress(raw))
csc=Path(os.environ['WINDIR'])/'Microsoft.NET/Framework64/v4.0.30319/csc.exe';exe=out/'Tests.exe'
subprocess.run([str(csc),'/nologo','/target:exe','/platform:x64','/reference:System.Windows.Forms.dll','/out:'+str(exe),str(base/'tests/ArchiveContracts.cs')],check=True)
subprocess.run([str(exe),str(args.app.resolve())],check=True)
