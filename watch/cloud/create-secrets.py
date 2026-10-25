#!/usr/bin/env python3
"""Prepare private Worker credentials without printing secrets. Do not commit the output."""
import base64,getpass,hashlib,json,os,secrets,sys
from pathlib import Path

def record(secret):
    salt=secrets.token_bytes(16)
    b64=lambda b:base64.urlsafe_b64encode(b).decode().rstrip('=')
    return '100000$'+b64(salt)+'$'+b64(hashlib.pbkdf2_hmac('sha256',secret.encode(),salt,100000))

def main():
    app=getpass.getpass('Tablet app password (comparison only; never saved): ')
    view=getpass.getpass('New cloud viewing password (12+ characters): ')
    if len(view)<12 or view==app:sys.exit('Choose a cloud password different from the tablet password, at least 12 characters.')
    if getpass.getpass('Confirm cloud viewing password: ')!=view:sys.exit('Passwords differ.')
    pin=getpass.getpass('Separate deletion PIN (8+ digits): ')
    if not pin.isdigit() or len(pin)<8 or pin in {app,view}:sys.exit('Use a different deletion PIN with at least 8 digits.')
    if getpass.getpass('Confirm deletion PIN: ')!=pin:sys.exit('PINs differ.')
    output=Path('.cloud-secrets.json')
    flags=os.O_WRONLY|os.O_CREAT|os.O_EXCL
    fd=os.open(output,flags,0o600)
    with os.fdopen(fd,'w') as f:json.dump({'UPLOAD_TOKEN':secrets.token_urlsafe(32),'SESSION_SECRET':secrets.token_urlsafe(48),'VIEW_PASSWORD_RECORD':record(view),'DELETE_PIN_RECORD':record(pin)},f)
    print('Created private credentials file. Deploy with wrangler secret bulk .cloud-secrets.json; never commit it.')
if __name__=='__main__':main()
