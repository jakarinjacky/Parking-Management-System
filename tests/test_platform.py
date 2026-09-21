"""HTTP integration tests against the actual Java API using isolated temporary data."""
import http.cookiejar
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import urllib.request
import urllib.error

ROOT = Path(__file__).resolve().parents[1]
CLASSES = str(Path(sys.argv[1] if len(sys.argv) > 1 else ROOT / 'bin').resolve())

class Client:
    def __init__(self):
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.state = None

    def request(self, path, body=None, expected=200, origin=None, authorization=None):
        headers = {'Content-Type': 'application/json'}
        if authorization:
            headers['Authorization'] = authorization
        if origin:
            headers['Origin'] = origin
        req = urllib.request.Request('http://localhost:8080/api/platform/' + path,
                                     data=json.dumps(body).encode() if body is not None else None, headers=headers)
        try:
            response = self.opener.open(req, timeout=10)
        except urllib.error.HTTPError as exc:
            response = exc
        data = json.load(response)
        assert response.status == expected, (path, response.status, data, expected)
        if response.status == 200 and 'revision' in data:
            self.state = data
        return data

    def login(self, username):
        return self.request('login', {'username': username, 'password': 'DemoPass123!'})

    def command(self, action, expected=200, **kwargs):
        self.request('state')
        return self.request('command', {'action': action, 'revision': self.state['revision'], **kwargs}, expected)

with tempfile.TemporaryDirectory(prefix='parking-platform-tests-') as temporary:
    env = dict(os.environ, PLATFORM_DEMO='true', PLATFORM_DEVICE_GATEWAY='true', PLATFORM_DATA_DIR=str(Path(temporary) / 'platform'))
    def start():
        process = subprocess.Popen(['java', '-cp', CLASSES, 'server.ParkingServer'], cwd=temporary, env=env,
                                   stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        for _ in range(100):
            if process.poll() is not None:
                raise AssertionError(process.stderr.read().decode())
            try:
                Client().request('state', expected=401)
                return process
            except (OSError, urllib.error.URLError):
                time.sleep(.1)
        process.terminate()
        raise AssertionError('Server failed to start')
    process = start()
    try:
        owner, other, admin, staff, superuser = [Client() for _ in range(5)]
        owner.login('owner'); other.login('owner2'); admin.login('admin'); staff.login('staff'); superuser.login('superadmin')
        assert len(owner.state['sites']) == 3
        assert len(other.state['sites']) == 1
        assert len(staff.state['sites']) == 1
        site = owner.state['sites'][0]
        sid = site['id']
        assert 'hash' not in json.dumps(owner.state) and 'salt' not in json.dumps(owner.state)
        other.command('configure', expected=403, siteId=sid)
        staff.command('saveLayout', expected=403, siteId=sid, cells=site['draft'])
        admin.command('publish', expected=403, siteId=sid)
        owner.command('publish', siteId=sid)
        owner.command('createTenant', expected=403, name='forbidden')
        staff.command('checkin', siteId=sid, plate='TEST-01', slotId='A4', vehicleType='CAR')
        ticket = next(t for t in staff.state['sites'][0]['tickets'] if t['licensePlate'] == 'TEST-01')
        owner.command('checkin', expected=400, siteId=sid, plate='TEST-01', slotId='A6', vehicleType='CAR')
        owner.command('checkin', expected=400, siteId=sid, plate='MOTO', slotId='A6', vehicleType='MOTORCYCLE')
        cells = [c for c in site['draft'] if c['id'] != 'A4']
        owner.command('saveLayout', siteId=sid, cells=cells)
        owner.command('publish', expected=400, siteId=sid)
        staff.command('checkout', siteId=sid, ticketId=ticket['ticketId'])
        assert all(t['status']=='ACTIVE' for t in staff.state['sites'][0]['tickets'])
        staff.command('checkout', expected=400, siteId=sid, ticketId=ticket['ticketId'])
        owner.command('publish', siteId=sid)
        owner.command('saveLayout', siteId=sid, cells=[c for c in cells if c['id'] != 'R1'])
        owner.command('publish', expected=400, siteId=sid)
        owner.command('saveLayout', siteId=sid, cells=cells)
        version = owner.state['sites'][0]['versions'][0]['id']
        owner.command('restore', siteId=sid, versionId=version)
        owner.command('publish', siteId=sid)
        owner.command('createSite', name='Custom test', businessType='CUSTOM')
        custom=owner.state['sites'][-1]
        owner.command('publish', expected=400, siteId=custom['id'])
        owner.command('configure', siteId=sid, name='Configured', address='Bangkok', rate=35, freeMinutes=10,
                      active=True, features={'membership':True,'reservation':True})
        owner.command('addMember', siteId=sid, plate='MEMBER', name='Test member', room='101', expires='2030-12-01')
        owner.command('reserve', siteId=sid, slotId='A6', plate='BOOKED', **{'from':'2030-01-01T10:00:00Z','to':'2030-01-01T12:00:00Z'})
        owner.command('reserve', expected=400, siteId=sid, slotId='A6', plate='OVERLAP', **{'from':'2030-01-01T11:00:00Z','to':'2030-01-01T13:00:00Z'})
        owner.command('checkin', expected=400, siteId=sid, slotId='A6', plate='OTHER', vehicleType='CAR')
        owner.command('addDevice', siteId=sid, name='Entry camera', type='CAMERA')
        owner.command('createUser', username='newstaff', password='LongPassword123!', role='staff', siteIds=[sid])
        superuser.command('createTenant', name='New customer', username='customer', password='LongPassword123!')
        assert len(superuser.state['tenants']) == 3
        Client().request('command', {'action':'publish'}, expected=401)
        owner.request('command', {'action':'publish','siteId':sid,'revision':-1}, expected=400)
        owner.request('command', {'action':'publish','siteId':sid,'revision':0}, expected=409)
        owner.request('command', {}, expected=403, origin='https://attacker.example')
        owner.request('state'); assert owner.state['audit']
        assert all(s['tenantId']=='DEMO-A' for s in owner.state['sites'])
        process.terminate(); process.wait(timeout=5)
        process=start()
        owner=Client(); owner.login('owner')
        assert owner.state['sites'][0]['name']=='Configured'
        assert owner.state['sites'][0]['memberships'][0]['room']=='101'
        assert len(owner.state['sites'])==4
        # Account changes preserve business data and invalidate existing sessions.
        staff=Client(); staff.login('staff')
        policy={k:-1 for k in ['carRate','evRate','motorcycleRate','truckRate','weekendRate','holidayRate']}
        policy.update(dailyCap=100,memberDiscountPercent=50,roomQuota=1,requireVisitorApproval=True,holidays=[])
        staff.command('configurePolicy',expected=403,siteId=sid,policy=policy)
        owner.command('configurePolicy',siteId=sid,policy=policy)
        from datetime import datetime, timedelta, timezone
        expires=(datetime.now(timezone.utc)+timedelta(hours=1)).isoformat().replace('+00:00','Z')
        staff.command('requestVisitor',siteId=sid,plate='VISITOR',room='201',expires=expires)
        staff.command('checkin',expected=400,siteId=sid,plate='VISITOR',slotId='A4',vehicleType='CAR')
        owner.request('state')
        visitor=next(s for s in owner.state['sites'] if s['id']==sid)['visitors'][-1]
        staff.command('approveVisitor',expected=403,siteId=sid,visitorId=visitor['id'],approved=True)
        owner.command('approveVisitor',siteId=sid,visitorId=visitor['id'],approved=True)
        staff.command('checkin',siteId=sid,plate='VISITOR',slotId='A4',vehicleType='CAR')
        owner.request('state')
        mall=next(s for s in owner.state['sites'] if s['businessType']=='MALL')
        hotel=next(s for s in owner.state['sites'] if s['businessType']=='HOTEL')
        owner.command('checkin',siteId=mall['id'],plate='COUPON',slotId='A4',vehicleType='CAR')
        ticket=next(s for s in owner.state['sites'] if s['id']==mall['id'])['tickets'][-1]
        owner.command('applyCoupon',siteId=mall['id'],ticketId=ticket['ticketId'],code='RECEIPT-1',percent=25)
        owner.command('applyCoupon',expected=400,siteId=mall['id'],ticketId=ticket['ticketId'],code='RECEIPT-1',percent=25)
        owner.command('checkin',siteId=hotel['id'],plate='VALET',slotId='A4',vehicleType='CAR')
        ticket=next(s for s in owner.state['sites'] if s['id']==hotel['id'])['tickets'][-1]
        owner.command('valet',siteId=hotel['id'],ticketId=ticket['ticketId'],stage='RECEIVED')
        owner.command('checkout',expected=400,siteId=hotel['id'],ticketId=ticket['ticketId'])
        for stage in ['PARKED','RETURNED']:owner.command('valet',siteId=hotel['id'],ticketId=ticket['ticketId'],stage=stage)
        owner.command('checkout',siteId=hotel['id'],ticketId=ticket['ticketId'])
        owner.command('addRoadCurve',siteId=sid,label='Curve',floor=1,width=6,x1=10,y1=10,cx=80,cy=10,x2=80,y2=80)
        owner.command('addRoadCurve',expected=400,siteId=sid,label='Invalid',floor=1,width=6,x1=-1,y1=10,cx=80,cy=10,x2=80,y2=80)
        owner.command('addDevice',siteId=sid,name='Bench',type='ESP32')
        device=next(s for s in owner.state['sites'] if s['id']==sid)['devices'][-1]
        staff.command('provisionDevice',expected=403,siteId=sid,deviceId=device['id'])
        owner.command('provisionDevice',siteId=sid,deviceId=device['id'])
        device_token=owner.state['deviceSecret']
        event={'siteId':sid,'deviceId':device['id'],'type':'HEARTBEAT'}
        Client().request('device',event,expected=403)
        Client().request('device',event,authorization='Bearer '+device_token)
        owner.request('state')
        assert 'deviceSecret' not in owner.state and 'tokenHash' not in json.dumps(owner.state)
        owner.command('queueLed',siteId=sid,deviceId=device['id'])
        response=Client().request('device',event,authorization='Bearer '+device_token)
        ack={**event,'type':'ACK','commandId':response['command']['id']}
        Client().request('device',ack,authorization='Bearer '+device_token)
        Client().request('device',ack,authorization='Bearer '+device_token)
        owner.command('disableDevice',siteId=sid,deviceId=device['id'])
        Client().request('device',event,expected=403,authorization='Bearer '+device_token)
        owner.request('state')
        staff_id=next(u['id'] for u in owner.state['users'] if u['username']=='staff')
        other=Client(); other.login('owner2')
        other.command('setUserActive',expected=403,userId=staff_id,active=False)
        owner.command('setUserActive',userId=staff_id,active=False)
        staff.request('state',expected=401)
        Client().request('login',{'username':'staff','password':'DemoPass123!'},expected=403)
        owner.command('setUserActive',userId=staff_id,active=True)
        staff=Client(); staff.login('staff')
        owner.command('revokeSessions',userId=staff_id)
        staff.request('state',expected=401)
        owner.command('setUserActive',expected=403,userId=owner.state['user']['id'],active=False)
        owner.command('changePassword',expected=403,currentPassword='WrongPassword123!',newPassword='ChangedPassword123!')
        owner.command('changePassword',currentPassword='DemoPass123!',newPassword='ChangedPassword123!')
        owner.request('state',expected=401)
        owner.request('login',{'username':'owner','password':'ChangedPassword123!'})
        assert len(owner.state['sites'])==4
        assert 'ChangedPassword123!' not in json.dumps(owner.state)
        print('Platform HTTP PASS: tenant isolation, roles, layouts, publication guards, transactions, reservations, users, XLSX source data, restart persistence')
    finally:
        process.terminate(); process.wait(timeout=5)
